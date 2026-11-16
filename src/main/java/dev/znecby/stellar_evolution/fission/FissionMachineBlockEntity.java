package dev.znecby.stellar_evolution.fission;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.bio.FermentationKinetics;
import dev.znecby.stellar_evolution.large.LargeMachine;
import dev.znecby.stellar_evolution.large.LargeMachineFluidHandler;
import dev.znecby.stellar_evolution.large.LargeMachineIo;
import dev.znecby.stellar_evolution.large.LargeMachineItemHandler;
import dev.znecby.stellar_evolution.large.LargeMachines;
import dev.znecby.stellar_evolution.thermal.CopperHeat;
import dev.znecby.stellar_evolution.thermal.ThermalConsumer;
import dev.znecby.stellar_evolution.thermal.ThermalLoad;
import dev.znecby.stellar_evolution.thermal.ThermalNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
 * 裂变线燃料机器方块实体（4 台共用）：<strong>大机器框架 + 热压差消耗端</strong>。
 *
 * <p><strong>物流</strong>：不再自带物品 / 流体缓冲 —— 与生化 / 石化机器一致，经<strong>机械管</strong>
 * 连到铜箱子（物品）与储罐（流体）；机器从输入容器取料、往输出容器放产物（事务式）。
 * <p><strong>能源</strong>：任意一格任意一面贴铜块接入热压差；热流率只决定<strong>倍速</strong>（并行 8 条）。
 * <p><strong>动画</strong>：只有真的有热压差且能结算时才转（{@code working} 同步给客户端）。
 */
public class FissionMachineBlockEntity extends BlockEntity
        implements LargeMachine, ThermalConsumer, FissionAnimated, FissionMachineHost {
    /** 单条并行的基准热流率缺省值（HP/t）；实际按机种取 {@link FissionMachineKind#baseFlowRate()}（128–1024）。 */
    public static final double BASE_FLOW_RATE = 128.0D;
    /** 并行数（条）：固定 8；满速阈值 = 该机基准 × 8（1024–8192 HP/t）。 */
    public static final int PARALLEL = 8;

    private final LargeMachineItemHandler itemHandler = new LargeMachineItemHandler(this);
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);

    /** 物流容器的方向（只记 {@link LargeMachineIo#OUTPUT}；缺省即输入）。 */
    private final Map<BlockPos, LargeMachineIo> ioModes = new HashMap<>();

    /** 当前周期进度（刻·倍速）。 */
    private double progress;
    /** 当前配方的基准周期（刻）：按配方走，允许同机不同时长。 */
    private int currentCycleTicks = 1;
    /** 已运行时长（tick，供可动部件；只在工作时推进）。 */
    private float age;
    /** 上一 tick 的时长，供插值。 */
    private float lastAge;
    /** 客户端平滑转速 0..1（起转 / 停机时缓入缓出，避免瞬间跳变）。 */
    private float spin;
    /** 服务端权威：当前是否在工作（有热压差、未超载、且真的有活干）。 */
    protected boolean working;
    /** 本次载入后是否已强制刷新过一次光照 / 填充格（不存盘）。 */
    private boolean structureChecked;
    /** Jade：工作中的「剩余配方时长」（刻）—— {@code -1} 没在工作 / {@code 0} 已饱和。 */
    private int jadeRecipeTicks = -1;
    /** Jade：当前周期进度 0..1。 */
    private double jadeRecipeProgress;

    public FissionMachineBlockEntity(BlockPos pos, BlockState state) {
        this(FissionMachines.MACHINE_ENTITY.get(), pos, state);
    }

    protected FissionMachineBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                        BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 本机机种。 */
    public FissionMachineKind kind() {
        return FissionMachines.kindOf(getBlockState().getBlock());
    }

    /** 对外物品能力（物流网络里铜箱子的合并视图）。 */
    public ResourceHandler<ItemResource> itemCapability() {
        return itemHandler;
    }

    /** 机器自己取放料用的物品句柄（输入 / 输出容器摊平视图）。 */
    public LargeMachineItemHandler items() {
        return itemHandler;
    }

    /** 机器自己取放料用的流体句柄。 */
    public LargeMachineFluidHandler fluids() {
        return fluidHandler;
    }

    /** 对外流体能力（物流网络里储罐的合并视图）。 */
    public ResourceHandler<FluidResource> fluidCapability() {
        return fluidHandler;
    }

    /** 当前进度比例 0..1。 */
    public double progressRatio() {
        int cycle = Math.max(1, currentCycleTicks);
        return Math.min(1.0D, progress / cycle);
    }

    /** 供渲染器取插值后的运行时长。 */
    @Override
    public float age(float partialTick) {
        return Mth.lerp(partialTick, lastAge, age);
    }

    /** 两端都 tick：客户端只推进时长，服务端做结构与工艺结算。 */
    public static void tick(Level level, BlockPos pos, BlockState state, FissionMachineBlockEntity machine) {
        if (level.isClientSide()) {
            machine.lastAge = machine.age;
            boolean animating = machine.working || machine.kind().displayOnly() || machine.animating();
            machine.spin = Mth.clamp(machine.spin + (animating ? 0.06F : -0.12F), 0.0F, 1.0F);
            machine.age += machine.spin;
            if (animating) {
                spawnMachineSparks(level, pos, machine.kind());
            }
            return;
        }
        if (!machine.structureChecked) {
            machine.structureChecked = true;
            FissionMachineStructure.refreshLight(level, pos);
            FissionMachineStructure.fillShells(level, pos);
            FissionMachineStructure.refreshLight(level, pos);
        }
        FissionMachineStructure.refreshPorts(level, pos);
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 40L) == 0L) {
            int filled = FissionMachineStructure.fillShells(level, pos);
            if (filled > 0) {
                StellarEvolution.LOGGER.info("裂变机器 {} 补齐了 {} 个填充格（完整 8 格碰撞）", pos, filled);
                FissionMachineStructure.refreshLight(level, pos);
            }
        }
        ThermalLoad load = ThermalNetwork.loadAt(level, pos);
        if (level instanceof ServerLevel serverLevel) {
            ThermalNetwork.syncAlarm(serverLevel, pos);
        }
        machine.onThermalTick(load);
        machine.serverTick(level, pos, state, load);
    }

    /** 本机当前温度带：由「贴在机器上的供能铜块数量」决定（1 厌氧 / 2–3 中温 / ≥4 高温）。 */
    public FermentationKinetics.Band currentBand() {
        Level level = getLevel();
        if (level == null) {
            return FermentationKinetics.Band.ANAEROBIC;
        }
        return FermentationKinetics.bandForCopper(LargeMachines.countCopperBlocks(level, this));
    }

    /**
     * 服务端主逻辑（燃料循环机器的默认实现：按配方推进周期）。
     * <p>子类（缪子聚变仪）可整体覆盖，换成自己的点火 / 温度 / 出束逻辑。
     */
    protected void serverTick(Level level, BlockPos pos, BlockState state, ThermalLoad load) {
        boolean working = load.speedFor(baseFlowRate()) > 0.0D
                && FissionRecipes.canCraft(kind(), itemHandler, fluidHandler, currentBand(), load.flowRate());
        if (working != this.working) {
            this.working = working;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        if (working && level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 5L == 0L) {
            // 2×2×2 大机器：热尘撒在整机顶面（随机一格），不再固定在主方块
            CopperHeat.spawnHeatParticles(serverLevel, occupiedCells());
        }
    }

    /**
     * 客户端动画的额外判据：默认只有 {@code working} / 展示机才动；
     * 缪子聚变仪在「点火中」也要让插栓与电弧动起来。
     */
    protected boolean animating() {
        return false;
    }

    /**
     * 缪子聚变仪：球壳格缝之间偶尔冒出的电弧火花（只在客户端）。
     *
     * <p>在球壳外表面随机取一点，给一个切向速度，让它贴着球面"游走"。
     */
    private static void spawnArcSparks(Level level, BlockPos pos) {
        if (level.getRandom().nextInt(7) != 0) {
            return;
        }
        double theta = level.getRandom().nextDouble() * Math.PI * 2.0D;
        double cosPhi = level.getRandom().nextDouble() * 2.0D - 1.0D;
        double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
        // 球壳外表面半径约 13.1/16 格，粒子贴在壳外一点点
        double radius = 0.83D + level.getRandom().nextDouble() * 0.03D;
        double x = pos.getX() + 1.0D + radius * sinPhi * Math.cos(theta);
        double y = pos.getY() + 1.0D + radius * cosPhi;
        double z = pos.getZ() + 1.0D + radius * sinPhi * Math.sin(theta);
        double speed = 0.015D + level.getRandom().nextDouble() * 0.02D;
        double vx = -Math.sin(theta) * speed;
        double vz = Math.cos(theta) * speed;
        double vy = (level.getRandom().nextDouble() - 0.5D) * 0.012D;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, vx, vy, vz);
    }

    /** 客户端：按机器类型在各自「该冒电」的位置撒电弧 / 电火花（只在机器工作时）。 */
    private static void spawnMachineSparks(Level level, BlockPos pos, FissionMachineKind kind) {
        switch (kind) {
            case MUON_FUSION -> spawnArcSparks(level, pos);
            case PARTICLE_SOURCE -> spawnParticleSourceSparks(level, pos);
            case IRRADIATION_CHAMBER -> spawnIrradiationSparks(level, pos);
            case COLLIDER -> spawnColliderSparks(level, pos);
            default -> {
            }
        }
    }

    /** 用模型坐标（0..32，1 单位 = 1/16 格）撒一颗电火花。 */
    private static void spark(Level level, BlockPos pos, double x, double y, double z,
                              double vx, double vy, double vz) {
        level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                pos.getX() + x / 16.0D, pos.getY() + y / 16.0D, pos.getZ() + z / 16.0D, vx, vy, vz);
    }

    /**
     * 粒子源：高压绝缘子与母线之间的电晕、四极线圈表面的放电、引出锥口的束流火花。
     */
    private static void spawnParticleSourceSparks(Level level, BlockPos pos) {
        var random = level.getRandom();
        // 高压绝缘子顶帽 ↔ 高压母线：电晕
        if (random.nextInt(4) == 0) {
            spark(level, pos,
                    14.4D + random.nextDouble() * 3.2D,
                    22.9D + random.nextDouble() * 0.7D,
                    5.9D + random.nextDouble() * 1.0D,
                    (random.nextDouble() - 0.5D) * 0.03D,
                    random.nextDouble() * 0.03D,
                    (random.nextDouble() - 0.5D) * 0.03D);
        }
        // 四极线圈上表面放电（三组里随机一组）
        if (random.nextInt(6) == 0) {
            double z = switch (random.nextInt(3)) {
                case 0 -> 11.4D;
                case 1 -> 14.8D;
                default -> 18.2D;
            };
            spark(level, pos,
                    12.2D + random.nextDouble() * 7.6D,
                    17.3D + random.nextDouble() * 0.6D,
                    z + random.nextDouble() * 1.0D,
                    (random.nextDouble() - 0.5D) * 0.03D,
                    (0.2D + random.nextDouble()) * 0.03D,
                    (random.nextDouble() - 0.5D) * 0.03D);
        }
        // 引出锥口：顺着束流方向喷出的火花
        if (random.nextInt(5) == 0) {
            spark(level, pos,
                    15.0D + random.nextDouble() * 2.0D,
                    12.0D + random.nextDouble() * 2.0D,
                    30.1D,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    0.04D + random.nextDouble() * 0.04D);
        }
    }

    /**
     * 辐照仓：三面束流窗口的电弧 + 顶部靶盘上的静电放电。
     */
    private static void spawnIrradiationSparks(Level level, BlockPos pos) {
        var random = level.getRandom();
        if (random.nextInt(6) == 0) {
            double y = 12.0D + random.nextDouble() * 2.0D;
            double t = 14.5D + random.nextDouble() * 3.0D;
            switch (random.nextInt(3)) {
                case 0 -> spark(level, pos, 14.5D + random.nextDouble() * 3.0D, y, 30.15D,
                        (random.nextDouble() - 0.5D) * 0.02D, (random.nextDouble() - 0.5D) * 0.02D,
                        0.04D + random.nextDouble() * 0.03D);
                case 1 -> spark(level, pos, 2.45D, y, t,
                        -0.04D - random.nextDouble() * 0.03D, (random.nextDouble() - 0.5D) * 0.02D,
                        (random.nextDouble() - 0.5D) * 0.02D);
                default -> spark(level, pos, 30.15D, y, t,
                        0.04D + random.nextDouble() * 0.03D, (random.nextDouble() - 0.5D) * 0.02D,
                        (random.nextDouble() - 0.5D) * 0.02D);
            }
        }
        // 顶部靶盘边缘：绕中心游走的静电火花
        if (random.nextInt(9) == 0) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 4.0D + random.nextDouble() * 0.6D;
            spark(level, pos,
                    16.0D + Math.cos(angle) * radius,
                    23.4D + random.nextDouble() * 0.8D,
                    16.0D + Math.sin(angle) * radius,
                    -Math.sin(angle) * 0.02D,
                    random.nextDouble() * 0.02D,
                    Math.cos(angle) * 0.02D);
        }
    }

    /**
     * 对撞机：对撞点向四周迸射的火花（玻璃管里可见）+ 两端束流闸门的电弧。
     */
    private static void spawnColliderSparks(Level level, BlockPos pos) {
        var random = level.getRandom();
        // 对撞点：每 2 tick 迸一次，沿随机方向外飞
        if (random.nextInt(2) == 0) {
            double dx = random.nextDouble() - 0.5D;
            double dy = random.nextDouble() - 0.5D;
            double dz = random.nextDouble() - 0.5D;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4D;
            double speed = 0.06D + random.nextDouble() * 0.05D;
            spark(level, pos,
                    16.0D + dx * 1.6D, 13.5D + dy * 1.6D, 16.0D + dz * 1.6D,
                    dx / length * speed, dy / length * speed, dz / length * speed);
        }
        // 两端闸门边缘放电
        if (random.nextInt(7) == 0) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 2.8D + random.nextDouble() * 0.6D;
            spark(level, pos,
                    16.0D + Math.cos(angle) * radius, 13.5D + Math.sin(angle) * radius, 1.35D,
                    Math.cos(angle) * 0.02D, Math.sin(angle) * 0.02D,
                    -0.02D - random.nextDouble() * 0.02D);
        }
        if (random.nextInt(7) == 0) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 2.8D + random.nextDouble() * 0.6D;
            spark(level, pos,
                    16.0D + Math.cos(angle) * radius, 13.5D + Math.sin(angle) * radius, 30.7D,
                    Math.cos(angle) * 0.02D, Math.sin(angle) * 0.02D,
                    0.02D + random.nextDouble() * 0.02D);
        }
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
        List<BlockPos> cells = new ArrayList<>(FissionMachineStructure.CELL_OFFSETS.length);
        for (int[] offset : FissionMachineStructure.CELL_OFFSETS) {
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

    @Override
    public int remainingRecipeTicks() {
        return working ? jadeRecipeTicks : -1;
    }

    @Override
    public double recipeProgress() {
        return jadeRecipeProgress;
    }

    // ------------------------------------------------------------------ ThermalConsumer

    @Override
    public double thermalResistance() {
        return kind().resistance();
    }

    @Override
    public double baseFlowRate() {
        return kind().baseFlowRate();
    }

    @Override
    public double maxUsefulFlowRate() {
        return kind().baseFlowRate() * PARALLEL;
    }

    /**
     * 按网络热流率推进当前周期；断电 / 超载 / 待料则停转并清零。
     * 每刻推进「配方刻」= min(热流率 ÷ 基准, 并行数)；周期完成时走事务结算。
     */
    @Override
    public void onThermalTick(ThermalLoad load) {
        if (kind().displayOnly()) {
            stall();
            return;
        }
        double flowRate = load.flowRate();
        if (load.overloaded() || flowRate <= 0.0D) {
            stall();
            return;
        }
        FissionRecipe recipe = FissionRecipes.find(kind(), itemHandler, fluidHandler, currentBand(), flowRate);
        if (recipe == null) {
            stall();
            return;
        }
        currentCycleTicks = Math.max(1, recipe.ticks());
        int cycle = currentCycleTicks;
        double speed = Math.min(flowRate / baseFlowRate(), PARALLEL);
        progress += speed;
        int done = 0;
        while (done < PARALLEL && progress >= cycle) {
            // 先结算再扣进度：输出放不下时整笔回滚，不能白白吃掉进度。
            if (!FissionRecipes.craftOnce(kind(), itemHandler, fluidHandler, currentBand(), flowRate)) {
                break;
            }
            progress -= cycle;
            done++;
        }
        jadeRecipeProgress = Math.min(1.0D, progress / cycle);
        jadeRecipeTicks = speed >= PARALLEL ? 0 : (int) Math.ceil((cycle - progress) / speed);
    }

    /** 没活可干：进度清零、Jade 不画配方槽、可动部件停。 */
    private void stall() {
        progress = 0.0D;
        jadeRecipeTicks = -1;
        jadeRecipeProgress = 0.0D;
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
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
