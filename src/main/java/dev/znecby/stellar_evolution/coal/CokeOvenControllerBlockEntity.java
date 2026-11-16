package dev.znecby.stellar_evolution.coal;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.hatch.HatchBlock;
import dev.znecby.stellar_evolution.hatch.HatchBlockEntity;
import dev.znecby.stellar_evolution.hatch.HatchBlocks;
import dev.znecby.stellar_evolution.hatch.HatchOwner;
import dev.znecby.stellar_evolution.large.LargeMachine;
import dev.znecby.stellar_evolution.large.LargeMachineFluidHandler;
import dev.znecby.stellar_evolution.large.LargeMachineIo;
import dev.znecby.stellar_evolution.large.LargeMachineItemHandler;
import dev.znecby.stellar_evolution.multiblock.MultiblockCell;
import dev.znecby.stellar_evolution.multiblock.MultiblockControllerBlockEntity;
import dev.znecby.stellar_evolution.multiblock.MultiblockPattern;
import dev.znecby.stellar_evolution.petro.PetroMachineKind;
import dev.znecby.stellar_evolution.petro.PetroRecipes;
import dev.znecby.stellar_evolution.thermal.ThermalLoad;
import dev.znecby.stellar_evolution.thermal.ThermalNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 焦化炉控制器方块实体：<strong>3×3×4 砖块炉体</strong>的干馏结算。
 *
 * <p><strong>结构</strong>：成型后三个机械物流仓被认领，整台机器作为一台<strong>大机器</strong>
 * 接入机械管物流网络（与质量投射轨道炮同一套框架）；铜块 / 底排铜管贴着物流仓即接入热压差网络。
 *
 * <p><strong>供热</strong>：本机<strong>不在自己身上记账</strong>（控制器不是热压差成员），
 * 热阻 / 基准热流率由三个物流仓转发给网络；每 tick 取「容量最大的那个仓室」的网络账本，
 * 按 {@code 热流率 ÷ 基准} 推进并行（最多 {@value #PARALLEL} 条），周期完成即结算一行焦化配方。
 *
 * <p><strong>配方</strong>：与石化机器同表（{@link PetroMachineKind#COKE_OVEN}），
 * 实际内容在 {@link PetroRecipes}；本机只负责「热 → 倍速 → 结算」。
 */
public class CokeOvenControllerBlockEntity extends MultiblockControllerBlockEntity
        implements LargeMachine, HatchOwner {
    /** 基础热阻：占用 DP = 4 × 有效热流率。 */
    public static final double THERMAL_RESISTANCE = 4.0D;
    /** 基准热流率（HP/t）：一条并行 1.0 倍速。 */
    public static final double BASE_FLOW_RATE = 320.0D;
    /** 并行数（条）：热流率每多一份基准，多推进一条。 */
    public static final int PARALLEL = 8;
    /** 一个干馏周期的基准刻数（1.0 倍速）。 */
    public static final int CYCLE_TICKS = 400;

    /** 焦化炉在配方表里的机种（只借用配方类型，本体不注册 2×2×2 方块）。 */
    public static final PetroMachineKind KIND = PetroMachineKind.COKE_OVEN;

    private final Set<BlockPos> hatchPositions = new HashSet<>();
    private final Map<BlockPos, LargeMachineIo> ioModes = new HashMap<>();
    private final LargeMachineItemHandler itemHandler = new LargeMachineItemHandler(this);
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);

    /** 当前周期进度（刻·倍速）；不落盘，重进世界重新计时。 */
    private double progress;
    /** 服务端权威：当前是否在干馏（同步到 {@link CokeOvenControllerBlock#LIT}）。 */
    private boolean working;
    /** 本机是否<strong>有活可干</strong>（料够 + 放得下）。 */
    private boolean hasWork;
    /** Jade：工作中的「剩余配方时长」（刻）；{@code -1} 没在工作 / {@code 0} 已饱和。 */
    private int jadeRecipeTicks = -1;
    /** Jade：当前周期进度 0..1。 */
    private double jadeRecipeProgress;

    public CokeOvenControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CokeOvenBlocks.CONTROLLER_ENTITY.get(), pos, state);
    }

    @Override
    protected MultiblockPattern createPattern() {
        return CokeOvenStructure.create();
    }

    // ------------------------------------------------------------------ 成型 / 失效

    @Override
    protected void onFormed() {
        hatchPositions.clear();
        MultiblockPattern currentPattern = pattern();
        if (currentPattern != null) {
            for (Map.Entry<BlockPos, MultiblockCell> entry : currentPattern.cells().entrySet()) {
                if (entry.getValue() instanceof MultiblockCell.BlockCell blockCell
                        && blockCell.block() == HatchBlocks.LOGISTICS.get()) {
                    BlockPos hatchPos = currentPattern.toWorld(worldPosition, facing(), entry.getKey());
                    hatchPositions.add(hatchPos);
                    if (level != null && level.getBlockEntity(hatchPos) instanceof HatchBlockEntity hatch) {
                        hatch.link(worldPosition);
                        hatch.setCasing(hatchCasing());
                        faceOutward(hatchPos, entry.getKey());
                        // 认领关系变化后，让贴着仓室的机械管重算一次连接
                        level.updateNeighborsAt(hatchPos, level.getBlockState(hatchPos).getBlock());
                    }
                }
            }
        }
        progress = 0.0D;
        hasWork = false;
        jadeRecipeTicks = -1;
        jadeRecipeProgress = 0.0D;
        StellarEvolution.LOGGER.info("焦化炉 {} 成型（{} 个机械物流仓）", worldPosition, hatchPositions.size());
    }

    @Override
    protected void onUnformed() {
        for (BlockPos hatchPos : hatchPositions) {
            if (level != null && level.getBlockEntity(hatchPos) instanceof HatchBlockEntity hatch) {
                hatch.unlink();
                hatch.setCasing(HatchBlock.Casing.STAINLESS);
                level.updateNeighborsAt(hatchPos, level.getBlockState(hatchPos).getBlock());
            }
        }
        hatchPositions.clear();
        progress = 0.0D;
        hasWork = false;
        jadeRecipeTicks = -1;
        jadeRecipeProgress = 0.0D;
        setLit(false);
    }

    /**
     * 让物流仓的覆盖层朝<strong>炉外</strong>：局部 (0,0,1) 是结构中心，
     * 仓室相对中心的水平方向就是它的朝外方向。
     */
    private void faceOutward(BlockPos hatchPos, BlockPos local) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        int lx = local.getX();
        int lz = local.getZ() - 1;
        Direction right = facing().getClockWise();
        Direction back = facing().getOpposite();
        int dx = right.getStepX() * lx + back.getStepX() * lz;
        int dz = right.getStepZ() * lx + back.getStepZ() * lz;
        Direction outward;
        if (dx > 0) {
            outward = Direction.EAST;
        } else if (dx < 0) {
            outward = Direction.WEST;
        } else if (dz > 0) {
            outward = Direction.SOUTH;
        } else if (dz < 0) {
            outward = Direction.NORTH;
        } else {
            return;
        }
        BlockState state = level.getBlockState(hatchPos);
        if (state.getBlock() instanceof HatchBlock && state.getValue(HatchBlock.FACING) != outward) {
            level.setBlock(hatchPos, state.setValue(HatchBlock.FACING, outward), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ 每 tick

    @Override
    protected void tickFormed(ServerLevel level) {
        tickCoking(level, networkLoad(level));
    }

    /**
     * 本机所在的热压差网络：逐个物流仓取账本，取<strong>容量最大</strong>的那张
     * （通常只有一个仓室接了铜块）。没有物流仓 / 都没接入时返回 {@code null}。
     */
    @Nullable
    private ThermalLoad networkLoad(ServerLevel level) {
        ThermalLoad best = null;
        double bestCapacity = 0.0D;
        for (BlockPos hatch : hatchPositions) {
            ThermalLoad load = ThermalNetwork.loadAt(level, hatch);
            if (load.capacity() > bestCapacity) {
                best = load;
                bestCapacity = load.capacity();
            }
        }
        return best;
    }

    /** Jade 用：优先报「真的接了铜块」的那个物流仓；都没接就退回第一个物流仓（没有则控制器）。 */
    public BlockPos thermalAnchor() {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos best = null;
            double bestCapacity = 0.0D;
            for (BlockPos hatch : hatchPositions) {
                double capacity = ThermalNetwork.loadAt(serverLevel, hatch).capacity();
                if (capacity > bestCapacity) {
                    best = hatch;
                    bestCapacity = capacity;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return hatchPositions.isEmpty() ? worldPosition : hatchPositions.iterator().next();
    }

    /** 按网络热流率推进干馏周期；断电 / 超载 / 待料则停转（不推进、不发光）。 */
    private void tickCoking(ServerLevel level, @Nullable ThermalLoad load) {
        if (load == null || load.overloaded() || load.flowRate() <= 0.0D) {
            stall();
            setLit(false);
            return;
        }
        if (!PetroRecipes.canCraft(KIND, itemHandler, fluidHandler)) {
            stall();
            setLit(false);
            return;
        }
        hasWork = true;
        double speed = Math.min(load.flowRate() / BASE_FLOW_RATE, PARALLEL);
        progress += speed;
        int done = 0;
        while (done < PARALLEL && progress >= CYCLE_TICKS) {
            if (!PetroRecipes.craftOnce(KIND, itemHandler, fluidHandler)) {
                break;   // 这一 tick 里料被前面的并行吃完了，剩下的留给下一 tick
            }
            progress -= CYCLE_TICKS;
            done++;
        }
        jadeRecipeProgress = Math.min(1.0D, progress / CYCLE_TICKS);
        jadeRecipeTicks = speed >= PARALLEL ? 0 : (int) Math.ceil((CYCLE_TICKS - progress) / speed);
        setLit(true);
    }

    /** 没活可干（断电 / 超载 / 待料）：进度清零、Jade 不画配方槽。 */
    private void stall() {
        progress = 0.0D;
        hasWork = false;
        jadeRecipeTicks = -1;
        jadeRecipeProgress = 0.0D;
    }

    /** 干馏中 → 正面炉门发亮（只在状态变化时写方块状态，避免每 tick 重设）。 */
    private void setLit(boolean lit) {
        boolean changed = working != lit;
        working = lit;
        if (changed) {
            setChanged();
        }
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            if (state.getBlock() == CokeOvenBlocks.CONTROLLER.get()
                    && state.getValue(CokeOvenControllerBlock.LIT) != lit) {
                serverLevel.setBlock(worldPosition, state.setValue(CokeOvenControllerBlock.LIT, lit), Block.UPDATE_ALL);
            }
        }
    }

    // ------------------------------------------------------------------ LargeMachine

    @Override
    public boolean isLargeMachine() {
        return isFormed();
    }

    @Override
    public Level level() {
        return getLevel();
    }

    @Override
    public BlockPos origin() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> occupiedCells() {
        // 只有物流仓是热接入点 / 物流端口：铜块（或铜管）与机械管都贴物流仓接入。
        return new ArrayList<>(hatchPositions);
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

    @Nullable
    public ResourceHandler<ItemResource> itemCapability() {
        return isFormed() ? itemHandler : null;
    }

    @Nullable
    public ResourceHandler<FluidResource> fluidCapability() {
        return isFormed() ? fluidHandler : null;
    }

    @Override
    public int remainingRecipeTicks() {
        return hasWork ? jadeRecipeTicks : -1;
    }

    @Override
    public double recipeProgress() {
        return jadeRecipeProgress;
    }

    // ------------------------------------------------------------------ HatchOwner（热压差转发）

    @Override
    public double thermalResistance() {
        return THERMAL_RESISTANCE;
    }

    @Override
    public double baseFlowRate() {
        return BASE_FLOW_RATE;
    }

    @Override
    public double maxUsefulFlowRate() {
        return BASE_FLOW_RATE * PARALLEL;
    }

    /** 炉体是红砖，成型后物流仓基底也切成红砖块。 */
    @Override
    public HatchBlock.Casing hatchCasing() {
        return HatchBlock.Casing.BRICKS;
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
}
