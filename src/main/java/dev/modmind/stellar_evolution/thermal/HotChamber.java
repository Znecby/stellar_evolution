package dev.modmind.stellar_evolution.thermal;

import dev.modmind.stellar_evolution.equipment.CopperGear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * <strong>热室</strong>：<strong>铜箱子</strong>（含双联的<strong>大铜箱子</strong>）底部垫<strong>铜块</strong>
 * 组成的特殊结构 —— 铜箱子自己不再是热压差消耗端，只有「坐在铜块上」才算接入网络：
 * <ul>
 *   <li><strong>不接铜管</strong>：普通铜管不再与铜箱子连接（连臂也不生，见 {@code CopperPipeBlock}）；</li>
 *   <li><strong>不接机械管</strong>：大机器物流网络也不认热室（机械管在热室旁边不生管臂，见 {@code MachinePipeBlock}）；</li>
 *   <li><strong>接入热压差</strong>：结构完整时按<strong>一台</strong>消耗端记账（热阻 {@value #RESISTANCE}，比机械土壤贵一档）；</li>
 *   <li><strong>加工</strong>：走<strong>单独的配方类型</strong> {@link HotChamberRecipe}（只吃物品、不吃流体），
 *       能源来自网络（网络热流率决定倍速）。配方表见 {@link HotChamberRecipes}（约 90+ 条，按等压分组）。</li>
 * </ul>
 *
 * <p><strong>大铜箱子</strong>：双联的两格<strong>底部都要有铜块</strong>，整箱算<strong>一台</strong>热室
 * （共享 54 格库存、只算 1 份热耗 —— 靠 {@link #controller} 去重）。只垫了一格 → {@link State#MISSING}，
 * Jade 提示缺失、整箱停摆。
 *
 * <p>结构状态（{@link #stateAt}）以「整个箱子」（双联 = 两格）为单位判定：底部铜块数 0 →
 * {@link State#NONE}（就是普通铜箱子）；全有 → {@link State#COMPLETE}；只垫了一部分 → {@link State#MISSING}。
 */
public final class HotChamber {
    /** 热室的基础热阻（无量纲）：比机械土壤（2）贵一档 ⇒ 64 HP/t（基准）时占用 256 DP。 */
    public static final double RESISTANCE = 4.0D;

    /** 热室结构状态。 */
    public enum State {
        /** 不是热室（底部一格铜块都没有）—— 就是普通铜箱子，照旧作大机器物流容器。 */
        NONE,
        /** 结构完整（箱子的每一格底部都有铜块）。 */
        COMPLETE,
        /** 结构缺失（只垫了一部分铜块，如大铜箱子只垫了一格）。 */
        MISSING
    }

    /** 这一格是不是铜箱子。 */
    public static boolean isChest(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(CopperHeat.COPPER_CHESTS);
    }

    /**
     * 铜箱子（可能双联）占的<strong>全部格</strong>；{@code pos} 不是铜箱子时返回空表。
     * <p>判定走原版 {@code chest_type}：非单联时，另一格在 {@link ChestBlock#getConnectedBlockPos}；
     * 再复核那一格确实是铜箱子，避免把木箱之类的邻居算进来。
     */
    public static List<BlockPos> cells(BlockGetter level, BlockPos pos) {
        List<BlockPos> cells = new ArrayList<>(2);
        BlockState state = level.getBlockState(pos);
        if (!state.is(CopperHeat.COPPER_CHESTS)) {
            return cells;
        }
        cells.add(pos.immutable());
        if (state.getBlock() instanceof ChestBlock
                && ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
            if (level.getBlockState(other).is(CopperHeat.COPPER_CHESTS)) {
                cells.add(other.immutable());
            }
        }
        return cells;
    }

    /** 整个箱子的结构状态（判定口径见类注释）。 */
    public static State stateAt(BlockGetter level, BlockPos pos) {
        List<BlockPos> cells = cells(level, pos);
        if (cells.isEmpty()) {
            return State.NONE;
        }
        int coppers = 0;
        for (BlockPos cell : cells) {
            if (CopperHeat.isCopperBlock(level, cell.below())) {
                coppers++;
            }
        }
        if (coppers <= 0) {
            return State.NONE;
        }
        return coppers >= cells.size() ? State.COMPLETE : State.MISSING;
    }

    /** 这格是不是「热室的一员」（<strong>含</strong>结构缺失态）。 */
    public static boolean isPart(BlockGetter level, BlockPos pos) {
        return stateAt(level, pos) != State.NONE;
    }

    /** 结构完整的（可用）热室。 */
    public static boolean isComplete(BlockGetter level, BlockPos pos) {
        return stateAt(level, pos) == State.COMPLETE;
    }

    /**
     * 热室的<strong>代表格</strong>：双联取坐标最小的一格 —— 与「从哪一格查询」无关，稳定可复现。
     * 不是热室（{@link State#NONE}）返回 {@code null}。网络记账与驱动只认代表格，双联因此只算一台。
     */
    @Nullable
    public static BlockPos controller(BlockGetter level, BlockPos pos) {
        List<BlockPos> cells = cells(level, pos);
        if (cells.isEmpty() || stateAt(level, pos) == State.NONE) {
            return null;
        }
        BlockPos best = cells.getFirst();
        for (BlockPos cell : cells) {
            if (cell.compareTo(best) < 0) {
                best = cell;
            }
        }
        return best;
    }

    /** 这格是不是热室的代表格。 */
    public static boolean isController(BlockGetter level, BlockPos pos) {
        BlockPos controller = controller(level, pos);
        return controller != null && controller.equals(pos);
    }

    /** 结构完整<strong>且</strong>这格是代表格 —— 真正当「一台消耗端」记账 / 工作的那一格。 */
    public static boolean isWorkingCell(BlockGetter level, BlockPos pos) {
        return isComplete(level, pos) && isController(level, pos);
    }

    /**
     * 热室结构里<strong>任意一格底部的铜块</strong>（记账锚点 / 网络接入点）。
     * 结构缺失时拿到的是「垫了铜块的那一格」底下的铜块；一格都没垫（不是热室）返回 {@code null}。
     */
    @Nullable
    public static BlockPos anchorCopper(BlockGetter level, BlockPos pos) {
        for (BlockPos cell : cells(level, pos)) {
            BlockPos below = cell.below();
            if (CopperHeat.isCopperBlock(level, below)) {
                return below.immutable();
            }
        }
        return null;
    }

    /** 热室的<strong>合并库存</strong>（双联 = 54 格）；不是热室 / 箱子已卸载返回 {@code null}。 */
    @Nullable
    public static Container containerAt(Level level, BlockPos pos) {
        BlockPos controller = controller(level, pos);
        if (controller == null) {
            return null;
        }
        BlockState state = level.getBlockState(controller);
        if (state.getBlock() instanceof ChestBlock chest) {
            // ignoreBeingBlocked=true：顶上压着方块 / 坐着猫也要能合出 54 格（原版那套「挡住就不合并」只服务开箱动画）
            return ChestBlock.getContainer(chest, state, level, controller, true);
        }
        return null;
    }

    /**
     * 热室「满速（每刻完成一次）」所需的热流率（HP/t）＝ 基准热流率 × 当前库存匹配到的配方时长；
     * 查不到配方 / 没有库存时返回 {@code 0}（不封顶）。
     * <p>Jade 拿它给「当前热流率」封顶（与熔炉 / 木桶同一口径）。
     */
    public static double maxUsefulFlowRate(Level level, BlockPos pos) {
        Container container = containerAt(level, pos);
        if (container == null) {
            return 0.0D;
        }
        // 铜装备修复：满速所需热流率固定为「基准 × 修复周期」
        if (CopperGear.findDamaged(container) >= 0) {
            return CopperHeat.BASE_FLOW_RATE * CopperGear.REPAIR_DURATION;
        }
        int pressure = 0;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            BlockPos controller = controller(level, pos);
            if (controller != null) {
                pressure = HotChamberPressureData.get(serverLevel).get(controller);
            }
        }
        return HotChamberRecipes.byInputs(container, pressure)
                .map(recipe -> CopperHeat.BASE_FLOW_RATE * Math.max(1, recipe.duration()))
                .orElse(0.0D);
    }

    private HotChamber() {}
}
