package dev.znecby.stellar_evolution.particle;

import dev.znecby.stellar_evolution.fission.FissionMachineBlockEntity;
import dev.znecby.stellar_evolution.fission.FissionMachines;
import dev.znecby.stellar_evolution.stream.StreamEndpoint;
import dev.znecby.stellar_evolution.stream.StreamNetwork;
import dev.znecby.stellar_evolution.stream.StreamType;
import dev.znecby.stellar_evolution.thermal.ThermalLoad;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * <strong>粒子源</strong>：把压力罐里的粒子打成束流，直接接束流管（没有靶站）。
 *
 * <p>口径（《高能物理线资料》§4.3.5 原样）：
 * <ul>
 *   <li>输入容器放<strong>带粒子类型的压力罐</strong>，输出容器收<strong>空罐</strong>；</li>
 *   <li>每 20t 一轮，耐久 −{@link ParticleType#wearPerCycle()}；耐久归零 → 移除组件、空罐进输出；</li>
 *   <li>供能不足（DP 或 HP 低于配方要求）<strong>完全停机</strong>，不耗罐；</li>
 *   <li>缓冲 {@value #BUFFER_MAX}；满仓停产（不耗罐），但存量仍可发货；</li>
 *   <li>不随 HP 变速：要么全速，要么不跑。</li>
 * </ul>
 */
public class ParticleSourceBlockEntity extends FissionMachineBlockEntity implements StreamEndpoint {
    /** 粒子缓冲上限（单位）。 */
    public static final double BUFFER_MAX = 10_000.0D;
    /** 一轮的刻数。 */
    public static final int CYCLE_TICKS = 20;
    /** 增压模块：束流强度倍率。 */
    public static final double BOOST_OUTPUT = 1.5D;
    /** 增压模块：能耗门槛（DP / HP）倍率。 */
    public static final double BOOST_POWER = 1.5D;

    /** 机器状态（Jade / 调试用）。 */
    public enum State {
        IDLE,
        NO_TANK,
        NO_POWER,
        BUFFER_FULL,
        RUNNING,
        OUTPUT_BLOCKED,
        /** 有罐但找不到能放空罐的输出容器：直接停机，避免「不磨损还继续发货」。 */
        NO_OUTPUT
    }

    private double buffer;
    private double emitted;
    private int cycleTimer;
    private State state = State.IDLE;
    /** 当前罐里的粒子类型（Jade 用；无罐为 null）。 */
    private ParticleType lastType;
    /** 最近一次有效的粒子类型（决定这根束流管属于哪种流；无罐时保留，存盘）。 */
    private ParticleType pipeType;
    private int lastDamage;
    private int lastMaxDamage;
    /** 本 tick 实际输出的速率（{@link #streamRate()} 用）。 */
    private double lastRate;
    /**
     * 本 tick <strong>可供货量</strong>（存量 + 产量），也就是网络侧看到的「源速率」。
     *
     * <p>必须报<strong>供货能力</strong>而不是「实际被抽走多少」：否则一旦某条流暂时没需求（例如对撞机那一路已吃满），
     * 源速率就会变成 0，整段管子在 Jade 里显示 0 t⁻¹，看起来像「管道不传流」。
     */
    private double lastSupply;
    /** 本 tick 的超频档位（1 档 = 基础速率，不设上限）。 */
    private int lastTier = 1;
    /** 本 tick 是否装了束流增压模块（Jade 展示用）。 */
    private boolean boosted;

    public ParticleSourceBlockEntity(BlockPos pos, BlockState state) {
        super(FissionMachines.PARTICLE_SOURCE_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ 结算

    @Override
    public void onThermalTick(ThermalLoad load) {
        if (getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        // 注意：这里**不能**先把 lastRate 清零。
        // 下面 StreamNetwork.stateAt(...) 会生成「本 tick 全网唯一的一份快照」，快照里的
        // rate 就是各源此刻的 streamRate()（= lastRate）。若在取快照前清零，快照里这张网络的
        // 输出永远是 0，于是所有消费者分到的份额都是 0（辐照仓进度永远不动），
        // 而源自己却按配额照样发货、缓冲一直是 0 —— 表现为「都在工作但管道 0 n/t」。
        // 若本机确实不产流，下面的 NO_TANK / NO_POWER / NO_OUTPUT 分支会把它清零。
        ItemResource tank = findTank();
        ParticleType type = tank == null ? null : typeOf(tank);
        if (type == null) {
            lastRate = 0.0D;
            lastSupply = 0.0D;
            buffer = Math.min(buffer, BUFFER_MAX);
            state = State.NO_TANK;
            working = false;
            lastType = null;
            lastDamage = 0;
            lastMaxDamage = 0;
            boosted = false;
            syncState();
            return;
        }
        lastType = type;
        pipeType = type;
        lastDamage = tank.toStack(1).getDamageValue();
        lastMaxDamage = PressureTankItem.MAX_DAMAGE;
        // 管道类型不再自动纠偏：束流管默认断开，由玩家用剪刀逐端接上（接上时继承/指定类型）。

        // 增压模块：束流强度 ×1.5，但能耗门槛同样 ×1.5
        boolean booster = hasBooster();
        boosted = booster;
        double powerScale = booster ? BOOST_POWER : 1.0D;
        double needHp = type.requiredHp() * powerScale;
        double needDp = type.requiredDp() * powerScale;

        double flow = load.flowRate();
        if (load.overloaded() || flow < needHp || load.capacity() < needDp) {
            state = State.NO_POWER;
            working = false;
            cycleTimer = 0;
            lastRate = 0.0D;
            lastSupply = 0.0D;
            syncState();
            return;
        }

        // 没有能放空罐的输出容器就停机：否则压力罐耗尽时无法换罐，却仍在免费出货。
        if (!canOutputEmptyTank()) {
            state = State.NO_OUTPUT;
            working = false;
            lastRate = 0.0D;
            lastSupply = 0.0D;
            syncState();
            return;
        }

        // 网络 HP 决定超频档位：达到该粒子基准 = 1 档（基础速率），之后每多一份基准 +1/t，不设上限。
        double tier = Math.max(1.0D, Math.floor(flow / needHp));
        lastTier = (int) tier;

        // 缓冲参与发货：可发量 = 存量 + 本 tick 产量；按「网络总需求 ÷ 源数量」分自己的配额，
        // 发货优先从存量扣，剩下的才留在缓冲里（满仓时只发货、不再生产）。
        double produced = buffer < BUFFER_MAX
                ? (type.rate() + (tier - 1.0D)) * (booster ? BOOST_OUTPUT : 1.0D)
                : 0.0D;
        StreamNetwork.State network = StreamNetwork.stateAt(getLevel(), getBlockPos());
        int sources = Math.max(1, network.sources().size());
        double quota = network.demand() / sources;
        double available = buffer + produced;
        double shipped = Math.min(available, quota);
        buffer = Math.max(0.0D, Math.min(BUFFER_MAX, available - shipped));
        lastRate = shipped;
        lastSupply = available;
        emitted += shipped;
        state = shipped > 0.0D ? State.RUNNING
                : buffer >= BUFFER_MAX ? State.BUFFER_FULL : State.IDLE;
        working = shipped > 0.0D;
        if (produced > 0.0D) {
            wear(tank, type);
        }
        syncState();
    }

    /** 事务试放一个空罐（不提交，自动回滚），判断输出容器是否有空间。 */
    private boolean canOutputEmptyTank() {
        ItemResource empty = ItemResource.of(new ItemStack(ParticleItems.PRESSURE_TANK.get()));
        try (Transaction transaction = Transaction.openRoot()) {
            return items().insertIntoOutputs(empty, 1, transaction) >= 1;
        }
    }

    /** 每 {@value #CYCLE_TICKS} 刻磨损一次；耐久归零则换空罐进输出。 */
    private void wear(ItemResource tank, ParticleType type) {
        cycleTimer++;
        if (cycleTimer < CYCLE_TICKS) {
            return;
        }
        cycleTimer = 0;
        ItemStack stack = tank.toStack(1);
        int damage = stack.getDamageValue() + type.wearPerCycle();
        boolean broken = damage >= stack.getMaxDamage();
        if (broken) {
            stack.remove(ParticleComponents.PARTICLE_TYPE.get());
            stack.setDamageValue(0);
        } else {
            stack.setDamageValue(damage);
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (items().extractFromInputs(tank, 1, transaction) < 1) {
                return;
            }
            int moved = broken
                    ? items().insertIntoOutputs(ItemResource.of(stack), 1, transaction)
                    : items().insertIntoInputs(ItemResource.of(stack), 1, transaction);
            if (moved < 1) {
                state = State.OUTPUT_BLOCKED;
                working = false;
                return;   // 输出堵塞：整笔回滚，罐停在原耐久
            }
            transaction.commit();
        }
    }

    /** 输入容器里有没有束流增压模块（只判定存在，不消耗）。 */
    private boolean hasBooster() {
        return items().hasInInputs(ItemResource.of(new ItemStack(ParticleItems.BOOSTER_MODULE.get())), 1);
    }

    /** 输入容器里第一个「带粒子类型」的压力罐。 */
    private ItemResource findTank() {
        for (int i = 0; i < items().size(); i++) {
            ItemResource resource = items().getResource(i);
            if (typeOf(resource) != null) {
                return resource;
            }
        }
        return null;
    }

    private static ParticleType typeOf(ItemResource resource) {
        if (resource.getItem() != ParticleItems.PRESSURE_TANK.get()) {
            return null;
        }
        return resource.toStack(1).get(ParticleComponents.PARTICLE_TYPE.get());
    }

    /** 基类的普通配方推进不适用；全部逻辑在 {@link #onThermalTick(ThermalLoad)}。 */
    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state, ThermalLoad load) {
        // no-op
    }

    @Override
    protected boolean animating() {
        return state == State.RUNNING;
    }

    private void syncState() {
        setChanged();
        Level level = getLevel();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ ThermalConsumer

    /** 无压力罐时不登记为消耗端（不占 DP / HP）。 */
    @Override
    public double thermalResistance() {
        if (lastType == null) {
            return 0.0D;
        }
        return super.thermalResistance() * (hasBooster() ? BOOST_POWER : 1.0D);
    }

    /** 1 档的基准热流率 = 当前粒子类型的 requiredHp（增压时同步抬高）。 */
    @Override
    public double baseFlowRate() {
        if (lastType == null) {
            return 1.0D;
        }
        return lastType.requiredHp() * (hasBooster() ? BOOST_POWER : 1.0D);
    }

    /** 不封顶：档位随网络 HP 无限增长，占用也线性上涨。 */
    @Override
    public double maxUsefulFlowRate() {
        return 0.0D;
    }

    // ------------------------------------------------------------------ StreamEndpoint

    @Override
    public StreamType streamType() {
        return pipeType == null ? StreamType.MUON : pipeType.stream();
    }

    @Override
    public double streamRate() {
        return lastSupply;
    }

    @Override
    public double streamEmitted() {
        return emitted;
    }

    @Override
    public List<BlockPos> streamCells() {
        return occupiedCells();
    }

    @Override
    public BlockPos streamOrigin() {
        return getBlockPos();
    }

    // ------------------------------------------------------------------ 展示 / 存盘

    public double buffer() {
        return buffer;
    }

    public ParticleType particleType() {
        return lastType;
    }

    public int tankDamage() {
        return lastDamage;
    }

    public int tankMaxDamage() {
        return lastMaxDamage;
    }

    public State particleState() {
        return state;
    }

    /** 当前超频档位（1 = 基础速率）。 */
    public int particleTier() {
        return lastTier;
    }

    /** 当前实际产出速率（基础 + 超频 + 增压），供 Jade 显示。 */
    public double particleOutputRate() {
        if (lastType == null) {
            return 0.0D;
        }
        return (lastType.rate() + (lastTier - 1)) * (boosted ? BOOST_OUTPUT : 1.0D);
    }

    /** 是否正在使用束流增压模块。 */
    public boolean boosterActive() {
        return boosted;
    }

    /** 本周期（20t）进度 0..1。 */
    public double particleCycleProgress() {
        return Math.min(1.0D, cycleTimer / (double) CYCLE_TICKS);
    }

    @Override
    public int remainingRecipeTicks() {
        return working ? Math.max(0, CYCLE_TICKS - cycleTimer) : -1;
    }

    @Override
    public double recipeProgress() {
        return particleCycleProgress();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("particle_buffer", buffer);
        output.putDouble("particle_emitted", emitted);
        output.putInt("particle_cycle", cycleTimer);
        output.putString("particle_pipe_type", pipeType == null ? "" : pipeType.id());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer = input.getDoubleOr("particle_buffer", 0.0D);
        emitted = input.getDoubleOr("particle_emitted", 0.0D);
        cycleTimer = input.getIntOr("particle_cycle", 0);
        String pipeId = input.getStringOr("particle_pipe_type", "");
        pipeType = pipeId.isEmpty() ? null : ParticleType.byId(pipeId);
    }
}
