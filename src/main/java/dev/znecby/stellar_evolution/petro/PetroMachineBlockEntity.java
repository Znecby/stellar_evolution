package dev.znecby.stellar_evolution.petro;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.large.LargeMachine;
import dev.znecby.stellar_evolution.large.LargeMachineFluidHandler;
import dev.znecby.stellar_evolution.large.LargeMachineIo;
import dev.znecby.stellar_evolution.large.LargeMachineItemHandler;
import dev.znecby.stellar_evolution.thermal.CopperHeat;
import dev.znecby.stellar_evolution.thermal.ThermalConsumer;
import dev.znecby.stellar_evolution.thermal.ThermalLoad;
import dev.znecby.stellar_evolution.thermal.ThermalNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 石化机器方块实体（六台共用）：维护「运行时长」（供渲染器推算各部件转角）+ 接入大机器框架做工艺结算。
 *
 * <p><strong>照生化线 {@code BioMachineBlockEntity} 的「生物反应釜」那套</strong>：机器是热压差网络的
 * <strong>消耗端</strong>（只经端口铜块接入），并对外提供大机器物流能力（物品 / 流体，来自端口机械管连着的铜箱子 / 储罐）；
 * 有热流率且未超载时按<strong>并行数</strong>推进周期，周期完成即按机种结算 {@link PetroRecipes}。
 *
 * <p><strong>客户端</strong>：只推进时长，不存盘、不发包（与热力引擎 / 生化机器同一口径）；
 * <strong>服务端</strong>：补齐填充格、结算热压差（并让超载的铜管发红闪）。
 */
public class PetroMachineBlockEntity extends BlockEntity implements LargeMachine, ThermalConsumer {
    /** 单条并行的基准热流率缺省值（HP/t）；实际按机种取 {@link PetroMachineKind#baseFlowRate()}（192–480）。 */
    public static final double BASE_FLOW_RATE = 128.0D;
    /** 并行数（条）：机器自有、固定 8；满速阈值 = 该机基准 × 8（1536–3840 HP/t）。 */
    public static final int PARALLEL = 8;
    /** 一个周期的基准刻数（1.0 倍速）；实际周期跟随配方。 */
    public static final int CYCLE_TICKS = PetroRecipes.DEFAULT_TICKS;

    /** 过热粒子的发射间隔（刻）。 */
    private static final long HEAT_PARTICLE_INTERVAL = 5L;

    private final LargeMachineItemHandler itemHandler = new LargeMachineItemHandler(this);
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);

    /** 物流容器的方向（只记 {@link LargeMachineIo#OUTPUT}；缺省即输入）。 */
    private final Map<BlockPos, LargeMachineIo> ioModes = new HashMap<>();

    /** 已运行时长（tick）。 */
    private float age;
    /** 上一 tick 的时长，供插值。 */
    private float lastAge;
    /** 本次载入后是否已强制刷新过一次光照（不存盘）。 */
    private boolean lightRefreshed;
    /** 当前加工进度（刻·倍速）；加工中的配方不落盘，重进世界重新计时。 */
    private double progress;
    /** 当前周期的刻数（1.0 倍速）——跟随<strong>结算配方</strong>走，缺省 {@link PetroRecipes#DEFAULT_TICKS}。 */
    private double cycleTicks = PetroRecipes.DEFAULT_TICKS;
    /** 服务端权威：当前是否在工作（有热流率、未超载、<b>且真的有活干</b>）—— 随更新包同步给客户端，客户端据此决定要不要转。 */
    private boolean working;
    /** 状态码（供命令）：0 无热压差 / 超载 · 1 待料 · 2 工作中。 */
    private int status;
    /** Jade：工作中的「剩余配方时长」（刻）—— {@code -1} 没在工作 / {@code 0} 已饱和（每刻至少结算一条）。 */
    private int jadeRecipeTicks = -1;
    /** Jade：当前周期进度 0..1（绿色「配方」槽的填充比例）。 */
    private double jadeRecipeProgress;
    /** 本机是否<strong>有活可干</strong>（料够 + 放得下）—— 没活就停在这一刻：进度清零、Jade 不画配方槽、可动部件也停。 */
    private boolean hasWork;

    public PetroMachineBlockEntity(BlockPos pos, BlockState state) {
        super(PetroMachineBlocks.PETRO_MACHINE_ENTITY.get(), pos, state);
    }

    /** 本机机种（由方块实例决定）。 */
    public PetroMachineKind kind() {
        return PetroMachineBlocks.kindOf(getBlockState().getBlock());
    }

    /** 当前进度（供命令 / 调试读取）。 */
    public double progress() {
        return progress;
    }

    /** 状态码：0 无热压差 / 超载 · 1 待料 · 2 工作中。 */
    public int status() {
        return status;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PetroMachineBlockEntity machine) {
        if (level.isClientSide()) {
            // 「只在工作时才转」：working 由服务端同步
            machine.lastAge = machine.age;
            if (machine.working) {
                machine.age += 1.0F;
            }
            return;
        }
        // 存档里的光照是持久化的：每次载入强制重算一次，并补一次填充格
        if (!machine.lightRefreshed) {
            machine.lightRefreshed = true;
            PetroMachineBlocks.refreshLight(level, pos);
            PetroMachineBlocks.fillShells(level, pos, machine.kind());
            PetroMachineBlocks.refreshLight(level, pos);
        }
        // 服务端：每 tick 扫一次外沿，刷新「接管口」状态（铜管接上 / 拆掉 → 该面模型切 capped / open）
        PetroMachineBlocks.refreshPorts(level, pos);
        // 服务端：错开相位地定期自检填充格（完整 8 格碰撞的兜底修复）
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 40L) == 0L) {
            int filled = PetroMachineBlocks.fillShells(level, pos, machine.kind());
            if (filled > 0) {
                StellarEvolution.LOGGER.info("石化机器 {} 补齐了 {} 个填充格（完整 8 格碰撞）", pos, filled);
                PetroMachineBlocks.refreshLight(level, pos);
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            ThermalLoad load = ThermalNetwork.loadAt(serverLevel, pos);
            ThermalNetwork.syncAlarm(serverLevel, pos);
            machine.onThermalTick(load);
            boolean working = load.speedFor(machine.baseFlowRate()) > 0.0D && machine.hasWork;
            if (working != machine.working) {
                machine.working = working;
                machine.setChanged();
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
            if (working && serverLevel.getGameTime() % HEAT_PARTICLE_INTERVAL == 0L) {
                // 2×2×2 大机器：热尘撒在整机顶面（随机一格），不再固定在主方块
                CopperHeat.spawnHeatParticles(serverLevel, machine.occupiedCells());
            }
        }
    }

    /** 供渲染器取插值后的运行时长。 */
    public float age(float partialTick) {
        return Mth.lerp(partialTick, lastAge, age);
    }

    // ------------------------------------------------------------------ LargeMachine

    @Override
    public boolean isLargeMachine() {
        return true;
    }

    @Override
    public Level level() {
        return getLevel();
    }

    @Override
    public BlockPos origin() {
        return getBlockPos();
    }

    @Override
    public List<BlockPos> occupiedCells() {
        BlockPos origin = getBlockPos();
        List<BlockPos> cells = new ArrayList<>(PetroMachineBlocks.CELL_OFFSETS.length);
        for (int[] offset : PetroMachineBlocks.CELL_OFFSETS) {
            cells.add(origin.offset(offset[0], offset[1], offset[2]));
        }
        return cells;
    }

    @Override
    public LargeMachineIo ioMode(BlockPos container) {
        return ioModes.getOrDefault(container, LargeMachineIo.INPUT);
    }

    @Override
    public void setIoMode(BlockPos container, LargeMachineIo mode) {
        ioModes.put(container.immutable(), mode);
        setChanged();
    }

    /** 大机器物品能力：物流网络里铜箱子的合并视图。 */
    @Nullable
    public ResourceHandler<ItemResource> itemCapability() {
        return itemHandler;
    }

    /** 大机器流体能力：物流网络里储罐的合并视图。 */
    @Nullable
    public ResourceHandler<FluidResource> fluidCapability() {
        return fluidHandler;
    }

    // ------------------------------------------------------------------ ThermalConsumer

    /**
     * 基础热阻<strong>按机种取</strong>（见 {@link PetroMachineKind#thermalResistance()}）：占用 DP = 本值 × 有效热流率。
     * <p>机种差异由此表达（分馏塔 4 / 裂解炉 8 相对标准 2 更吃容量），<strong>不设热流率门槛</strong> ——
     * 门槛会在低热流率处产生「停 → 直接 N 倍速」的跳变，破坏线性加速。
     */
    @Override
    public double thermalResistance() {
        return kind().thermalResistance();
    }

    @Override
    public double baseFlowRate() {
        return kind().baseFlowRate();
    }

    /**
     * 大机器「有效热流率」上限 = 并行数 × 单条并行的基准热流率（{@code PARALLEL × kind.baseFlowRate()}）；
     * 超过它 HP 对本机再无效果，占用 DP 也不再计。
     */
    @Override
    public double maxUsefulFlowRate() {
        return kind().baseFlowRate() * PARALLEL;
    }

    /** Jade：工作中的剩余配方时长（刻）—— 停机 / 未达热流率门槛 / 待料为 {@code -1}。 */
    @Override
    public int remainingRecipeTicks() {
        return hasWork ? jadeRecipeTicks : -1;
    }

    /** Jade：当前周期进度 0..1（绿色「配方」槽的填充比例）。 */
    @Override
    public double recipeProgress() {
        return jadeRecipeProgress;
    }

    /**
     * 按网络热流率推进当前周期（周期刻数跟随<strong>结算配方</strong>）；断电 / 超载 / <strong>待料</strong>则停转并清零。
     * <p>周期完成时按本机机种（{@link PetroMachineKind}）尝试结算一次：原料（物品 / 流体，从输入容器取）+
     * 产物（进输出容器），全部走 {@link PetroRecipes#craftOnce} 的事务。
     * <p><strong>并行</strong>：机器固定 {@link #PARALLEL} 条并行，每条要 {@link #BASE_FLOW_RATE}（正好一台热力引擎）；
     * 每刻推进的「配方刻」= min(热流率 ÷ 基准, 并行数)，所以热流率越低越慢，8 条全开时每刻最多结算 {@link #PARALLEL} 次。
     */
    @Override
    public void onThermalTick(ThermalLoad load) {
        double flowRate = load.flowRate();
        PetroMachineKind kind = kind();
        if (load.overloaded() || flowRate <= 0.0D) {
            stall();
            status = 0;
            return;
        }
        // 待料（料不够 / 输出放不下）→ 停在这一刻：不推进进度、Jade 不画配方槽、可动部件也停
        if (!PetroRecipes.canCraft(kind, itemHandler, fluidHandler)) {
            stall();
            status = 1;
            return;
        }
        hasWork = true;
        cycleTicks = kind.cycleTicks();
        // 并行条数：每条并行要一份基准热流率（按机种），最多 PARALLEL 条；每刻推进「条数」刻配方进度
        double speed = Math.min(flowRate / baseFlowRate(), PARALLEL);
        progress += speed;
        int done = 0;
        boolean crafted = false;
        while (done < PARALLEL && progress >= cycleTicks) {
            if (!PetroRecipes.craftOnce(kind, itemHandler, fluidHandler)) {
                break;   // 这一 tick 里料被前几条吃完了，剩下的留给下一 tick（不扣进度）
            }
            progress -= cycleTicks;
            done++;
            crafted = true;
        }
        status = crafted ? 2 : 1;
        // Jade：剩余配方时长 = (本周期总刻数 − 已完成刻) ÷ 每刻推进刻数；8 条并行全开（speed ≥ 并行数）即「满速」
        jadeRecipeProgress = Math.min(1.0D, progress / cycleTicks);
        jadeRecipeTicks = speed >= PARALLEL ? 0 : (int) Math.ceil((cycleTicks - progress) / speed);
    }

    /** 没活可干（断电 / 超载 / 待料）：进度清零、Jade 不画配方槽、可动部件停。 */
    private void stall() {
        progress = 0.0D;
        hasWork = false;
        jadeRecipeTicks = -1;
        jadeRecipeProgress = 0.0D;
    }

    // ------------------------------------------------------------------ 存盘

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // 只存「输出」容器（输入是缺省值），位置按 x,y,z 三个 int 平铺
        int count = 0;
        for (LargeMachineIo mode : ioModes.values()) {
            if (mode == LargeMachineIo.OUTPUT) {
                count++;
            }
        }
        int[] outs = new int[count * 3];
        int index = 0;
        for (Map.Entry<BlockPos, LargeMachineIo> entry : ioModes.entrySet()) {
            if (entry.getValue() != LargeMachineIo.OUTPUT) {
                continue;
            }
            BlockPos pos = entry.getKey();
            outs[index++] = pos.getX();
            outs[index++] = pos.getY();
            outs[index++] = pos.getZ();
        }
        output.putIntArray("large_io_output", outs);
        output.putBoolean("working", working);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ioModes.clear();
        int[] outs = input.getIntArray("large_io_output").orElse(new int[0]);
        for (int i = 0; i + 2 < outs.length; i += 3) {
            ioModes.put(new BlockPos(outs[i], outs[i + 1], outs[i + 2]), LargeMachineIo.OUTPUT);
        }
        progress = 0.0D;
        working = input.getBooleanOr("working", false);
    }

    /** 整份带去客户端：进世界 / 区块加载时同步「是否在工作中」（供动画）。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
