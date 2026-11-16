package dev.modmind.stellar_evolution.bio;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.large.LargeMachine;
import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.large.LargeMachineIo;
import dev.modmind.stellar_evolution.large.LargeMachineItemHandler;
import dev.modmind.stellar_evolution.large.LargeMachines;
import dev.modmind.stellar_evolution.thermal.CopperHeat;
import dev.modmind.stellar_evolution.thermal.ThermalConsumer;
import dev.modmind.stellar_evolution.thermal.ThermalLoad;
import dev.modmind.stellar_evolution.thermal.ThermalNetwork;
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
 * 生化机器方块实体：维护「运行时长」（供渲染器推算各部件转角），并按机器种类接入不同机制。
 *
 * <p>六台机器共用这一个方块实体，且<strong>全部</strong>是「大型机器」（见
 * {@link LargeMachine}）：它们都是热压差网络的<strong>消耗端</strong>（只经端口铜块接入），
 * 并对外提供大机器物流能力（物品 / 流体，来自端口机械管连着的铜箱子 / 储罐）。
 *
 * <p><strong>客户端</strong>：只推进时长，不存盘、不发包（与热力引擎 / 燃烧引擎的曲轴角同一口径）；
 * <strong>服务端</strong>：补齐填充格、刷新端口，大型机器再每 tick 结算一次热压差（并让超载的铜管发红闪）。
 */
public class BioMachineBlockEntity extends BlockEntity implements LargeMachine, ThermalConsumer {
    /** 大型机器（生化 6 台共用）的基础热阻：占用 = 本值 × 有效热流率 ⇒ 满速占用 = 2 × 基准 × 8（2048–5120 DP）。 */
    public static final double THERMAL_RESISTANCE = 2.0D;
    /** 单条并行的基准热流率缺省值（HP/t）；实际按机种取 {@link BioMachineKind#baseFlowRate()}（128–320）。 */
    public static final double BASE_FLOW_RATE = 128.0D;

    /** 并行数（条）：机器自有、固定 8；满速阈值 = 该机基准 × 8（1024–2560 HP/t）。 */
    public static final int PARALLEL = 8;

    /** 过热粒子的发射间隔（刻）。 */
    private static final long HEAT_PARTICLE_INTERVAL = 5L;

    /** 罐里水的涨落速度（每 tick 的填充比例）——约 2 秒灌满。 */
    private static final float FLUID_FILL_SPEED = 0.025F;

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
    /** 生物反应釜当前加工进度（刻·倍速）；加工中的配方不落盘，重进世界重新计时。 */
    private double progress;
    /** 当前周期的刻数（1.0 倍速）——跟随<strong>结算配方</strong>的 {@code baseTicks} 走，缺省 {@link BioRecipes#DEFAULT_TICKS}。 */
    private double cycleTicks = BioRecipes.DEFAULT_TICKS;
    /** 服务端权威：当前是否在工作（有热流率、未超载、<b>且真的有活干</b>）—— 随更新包同步给客户端，客户端据此决定要不要转。 */
    private boolean working;
    /** 服务端权威：机器网络里是否有流体（供渲染「罐里半满水」）—— 同样随更新包同步。 */
    private boolean hasFluid;
    /** 客户端视觉：罐里水的填充比例 0..1（**只在客户端**向目标平滑逼近，不存盘、不同步）。 */
    private float fluidFill;
    private float lastFluidFill;
    /** Jade：工作中的「剩余配方时长」（刻）—— {@code -1} 没在工作 / {@code 0} 已饱和（每刻至少结算一条）。 */
    private int jadeRecipeTicks = -1;
    /** Jade：当前周期进度 0..1（绿色「配方」槽的填充比例）。 */
    private double jadeRecipeProgress;
    /** 本机是否<strong>有活可干</strong>（条件满足 + 料够 + 放得下）—— 没活就停在这一刻：进度清零、Jade 不画配方槽、可动部件也停。 */
    private boolean hasWork;

    public BioMachineBlockEntity(BlockPos pos, BlockState state) {
        super(BioMachineBlocks.BIO_MACHINE_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BioMachineBlockEntity machine) {
        if (level.isClientSide()) {
            // 大型机器（生物反应釜）：**只在工作时才转**（working 由服务端同步）；其余机器暂时持续转
            boolean animating = !machine.isLargeMachine() || machine.working;
            machine.lastAge = machine.age;
            if (animating) {
                machine.age += 1.0F;
            }
            // 罐里的水「升起 / 落下」：向目标（有流体 = 灌满、无流体 = 排空）平滑逼近，别突然出现
            machine.lastFluidFill = machine.fluidFill;
            float target = machine.hasFluid ? 1.0F : 0.0F;
            if (machine.fluidFill < target) {
                machine.fluidFill = Math.min(target, machine.fluidFill + FLUID_FILL_SPEED);
            } else if (machine.fluidFill > target) {
                machine.fluidFill = Math.max(target, machine.fluidFill - FLUID_FILL_SPEED);
            }
            return;
        }
        // 存档里的光照是持久化的：早期版本填充格会挡光，留下的暗块不会自动刷新 → 每次载入强制重算一次
        if (!machine.lightRefreshed) {
            machine.lightRefreshed = true;
            BioMachineBlocks.refreshLight(level, pos);
            BioMachineBlocks.fillShells(level, pos, BioMachineBlocks.kindOf(state.getBlock()));
            BioMachineBlocks.refreshLight(level, pos);
        }
        // 服务端：每 tick 扫一次外沿，刷新「接管口」状态（铜管接上 / 拆掉 → 该面模型切 capped / open）
        BioMachineBlocks.refreshPorts(level, pos);
        // 服务端：错开相位地定期自检填充格（完整 8 格碰撞的兜底修复）
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 40L) == 0L) {
            int filled = BioMachineBlocks.fillShells(level, pos, BioMachineBlocks.kindOf(state.getBlock()));
            if (filled > 0) {
                StellarEvolution.LOGGER.info("生化机器 {} 补齐了 {} 个填充格（完整 8 格碰撞）", pos, filled);
                BioMachineBlocks.refreshLight(level, pos);
            }
        }
        // 大型机器（生物反应釜）：结算热压差 + 同步超载告警
        if (machine.isLargeMachine() && level instanceof ServerLevel serverLevel) {
            ThermalLoad load = ThermalNetwork.loadAt(serverLevel, pos);
            ThermalNetwork.syncAlarm(serverLevel, pos);
            machine.onThermalTick(load);
            boolean working = load.speedFor(machine.baseFlowRate()) > 0.0D && machine.hasWork;
            boolean hasFluid = machine.fluidHandlerHasContent();
            if (working != machine.working || hasFluid != machine.hasFluid) {
                machine.working = working;
                machine.hasFluid = hasFluid;
                machine.setChanged();
                // 把「工作中 / 有流体」同步给客户端 —— 客户端据此决定要不要转、罐里要不要画水（只在变化时发包）
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
            if (working && serverLevel.getGameTime() % HEAT_PARTICLE_INTERVAL == 0L) {
                // 2×2×2 大机器：热尘撒在整机顶面（随机一格），不再固定在主方块
                CopperHeat.spawnHeatParticles(serverLevel, machine.occupiedCells());
            }
        }
    }

    /** 机器网络里是否有流体（输入 / 输出容器里任意一格有量即算）。 */
    private boolean fluidHandlerHasContent() {
        for (int i = 0; i < fluidHandler.size(); i++) {
            if (fluidHandler.getAmountAsLong(i) > 0L) {
                return true;
            }
        }
        return false;
    }

    /** 机器网络里是否装着流体（渲染器据此在罐里画「半满水」）。 */
    public boolean hasFluid() {
        return hasFluid;
    }

    /** 供渲染器取插值后的「罐里水的高度比例」0..1（升起 / 落下动画）。 */
    public float fluidFill(float partialTick) {
        return Mth.lerp(partialTick, lastFluidFill, fluidFill);
    }

    /** 供渲染器取插值后的运行时长。 */
    public float age(float partialTick) {
        return Mth.lerp(partialTick, lastAge, age);
    }

    // ------------------------------------------------------------------ LargeMachine

    /** 本机机种（由方块实例决定）。 */
    public BioMachineKind kind() {
        return BioMachineBlocks.kindOf(getBlockState().getBlock());
    }

    @Override
    public boolean isLargeMachine() {
        return BioMachineBlocks.isLargeMachine(getBlockState().getBlock());
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
        List<BlockPos> cells = new java.util.ArrayList<>(BioMachineBlocks.CELL_OFFSETS.length);
        for (int[] offset : BioMachineBlocks.CELL_OFFSETS) {
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

    /** 大机器物品能力：物流网络里铜箱子的合并视图；非大型机器返回 null（不暴露能力）。 */
    @Nullable
    public ResourceHandler<ItemResource> itemCapability() {
        return isLargeMachine() ? itemHandler : null;
    }

    /** 大机器流体能力：物流网络里储罐的合并视图；非大型机器返回 null。 */
    @Nullable
    public ResourceHandler<FluidResource> fluidCapability() {
        return isLargeMachine() ? fluidHandler : null;
    }

    // ------------------------------------------------------------------ ThermalConsumer

    /** 只有大型机器才占压；其余生化机器（同用一个方块实体）返回 0，不进网络账本。 */
    @Override
    public double thermalResistance() {
        return isLargeMachine() ? THERMAL_RESISTANCE : 0.0D;
    }

    @Override
    public double baseFlowRate() {
        return isLargeMachine() ? kind().baseFlowRate() : 1.0D;
    }

    /**
     * 大机器的「有效热流率」上限 = 并行数 × 单条并行的基准热流率（{@code PARALLEL × kind.baseFlowRate()}）。
     * 超过它的 HP 对本机再无效果（8 条已经全跑满），占用 DP 也就不再计（见 {@link ThermalLoad#occupancy()}）。
     */
    @Override
    public double maxUsefulFlowRate() {
        return isLargeMachine() ? kind().baseFlowRate() * PARALLEL : 0.0D;
    }

    /** Jade：工作中的剩余配方时长（刻）—— 非大机器 / 停机 / 待料为 {@code -1}。 */
    @Override
    public int remainingRecipeTicks() {
        return isLargeMachine() && hasWork ? jadeRecipeTicks : -1;
    }

    /** Jade：当前周期进度 0..1（绿色「配方」槽的填充比例）。 */
    @Override
    public double recipeProgress() {
        return isLargeMachine() ? jadeRecipeProgress : 0.0D;
    }

    /**
     * 按网络热流率推进当前周期（周期刻数跟随<strong>结算配方</strong>的 {@code baseTicks}）；
     * 断电 / 超载 / 条件不满足 / <strong>待料</strong>则停转并清零（见 {@link #stall()} —— 没活就不空转）。
     * <p>周期完成时按本机的配方类型（{@link BioRecipeType}）尝试结算一次：条件（温度带 / 热流率门槛）+
     * 原料（物品 / 流体，从输入容器取）+ 产物（进输出容器），全部走 {@link BioRecipes#craftOnce} 的事务。
     * <p><strong>并行</strong>：机器固定 {@link #PARALLEL} 条并行，每条要 {@link #BASE_FLOW_RATE}（正好一台热力引擎）；
     * 每刻推进的「配方刻」= min(热流率 ÷ 基准, 并行数)，所以热流率越低越慢，8 条全开时每刻最多结算 {@link #PARALLEL} 次。
     * <p><strong>温度带看机器上的供能铜块数量</strong>（{@link #currentBand()}），与快慢（网络热流率）解耦。
     */
    @Override
    public void onThermalTick(ThermalLoad load) {
        if (!isLargeMachine()) {
            return;
        }
        double flowRate = load.flowRate();
        BioRecipeType type = BioRecipeType.of(BioMachineBlocks.kindOf(getBlockState().getBlock()));
        FermentationKinetics.Band band = currentBand();
        // 断电 / 超载 / 条件不满足 / 待料（料不够、输出放不下）→ 停在这一刻：不推进进度、Jade 不画配方槽
        if (load.overloaded() || flowRate <= 0.0D
                || !BioRecipes.canCraft(type, itemHandler, fluidHandler, band, flowRate)) {
            stall();
            return;
        }
        hasWork = true;
        // 并行条数：每条并行要一份基准热流率（按机种），最多 PARALLEL 条；每刻推进「条数」刻配方进度
        double speed = Math.min(flowRate / baseFlowRate(), PARALLEL);
        progress += speed;
        int done = 0;
        while (done < PARALLEL && progress >= cycleTicks) {
            BioRecipe crafted = BioRecipes.craftOnce(type, itemHandler, fluidHandler, band, flowRate);
            if (crafted == null) {
                break;   // 这一 tick 里料被前几条吃完了，剩下的留给下一 tick（不扣进度）
            }
            progress -= cycleTicks;
            done++;
            // 周期跟随配方：下个周期按刚结算那条的 baseTicks 走
            cycleTicks = crafted.baseTicks();
        }
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

    /** 本机当前温度带：由「贴在机器上的供能铜块数量」决定（1 厌氧 / 2 中温 / ≥4 高温）。 */
    public FermentationKinetics.Band currentBand() {
        Level level = level();
        if (level == null) {
            return FermentationKinetics.Band.ANAEROBIC;
        }
        return FermentationKinetics.bandForCopper(LargeMachines.countCopperBlocks(level, this));
    }

    /** Jade 用：本机温度带序数；本类配方若不带温度带条件则返回 -1（客户端照旧显示通用状态行）。 */
    public int jadeBandOrdinal() {
        BioRecipeType type = BioRecipeType.of(BioMachineBlocks.kindOf(getBlockState().getBlock()));
        return BioRecipes.requiresBand(type) ? currentBand().ordinal() : -1;
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
        output.putBoolean("has_fluid", hasFluid);
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
        hasFluid = input.getBooleanOr("has_fluid", false);
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
