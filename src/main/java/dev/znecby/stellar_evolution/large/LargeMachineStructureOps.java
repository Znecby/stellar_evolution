package dev.znecby.stellar_evolution.large;

import dev.znecby.stellar_evolution.pipe.CopperPipeBlock;
import dev.znecby.stellar_evolution.pipe.MachinePipeBlock;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * 2×2×2 大机器「结构操作」的公共实现：占位检查、填充格补齐 / 清理、接管口刷新、原点反查、光照重算。
 *
 * <p>生化 / 石化两条线此前是逐行重复的两份；数值与几何见 {@link LargeMachineStructure}。
 * 各线的方块类只保留薄包装（把自己的填充格 / 主方块 / FACING 传进来），行为必须保持一致。
 */
public final class LargeMachineStructureOps {

    /** 接管口的公共读取面：各线的 {@code Port} record 实现它，就能共用 {@link #refreshPorts}。 */
    public interface BoundPort {
        BooleanProperty property();

        int dx();

        int dy();

        int dz();

        Direction towardMachine();
    }

    /** 8 格里除主方块外的 7 格是否都能被占用（放机器前的检查）。 */
    public static boolean canOccupy(BlockGetter level, BlockPos origin) {
        for (int[] offset : LargeMachineStructure.CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
            if (!level.getBlockState(pos).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** 把一台机器占的另外 7 格填成不可见填充格（凑齐完整 8 格碰撞）；返回本次补了几格。 */
    public static int fillShells(Level level, BlockPos origin, BlockState shell) {
        int filled = 0;
        for (int[] offset : LargeMachineStructure.CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
            if (level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, shell, 3);
                filled++;
            }
        }
        return filled;
    }

    /** 清掉一台机器占的另外 7 格填充格（按类型匹配，不区分机种）。 */
    public static void clearShells(Level level, BlockPos origin, Class<? extends Block> shellClass) {
        for (int[] offset : LargeMachineStructure.CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
            if (shellClass.isInstance(level.getBlockState(pos).getBlock())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** 旧存档 / 自愈调用：朝向缺失时按北处理。 */
    public static Direction facingOf(BlockGetter level, BlockPos origin, Property<Direction> facing) {
        BlockState state = level.getBlockState(origin);
        return state.hasProperty(facing) ? state.getValue(facing) : Direction.NORTH;
    }

    /**
     * 从某个填充格出发，找出它所属机器的主方块位置（破坏填充格时用）。
     * <p>{@code isMachine} 用谓词而不是单个 Class：裂变线的主方块有机器 / 反应堆 / 汽轮机三种类型。
     */
    @Nullable
    public static BlockPos findMachine(BlockGetter level, BlockPos shellPos, Predicate<Block> isMachine) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos candidate = shellPos.offset(-dx, -dy, -dz);
                    if (isMachine.test(level.getBlockState(candidate).getBlock())) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** 强制重算这 8 格的光照（修复历史存档里「旧填充格挡光」留下的暗块）。 */
    public static void refreshLight(Level level, BlockPos origin) {
        for (int[] offset : LargeMachineStructure.CELL_OFFSETS) {
            level.getLightEngine().checkBlock(origin.offset(offset[0], offset[1], offset[2]));
        }
    }

    /**
     * 扫一遍机器外沿的 12 个接管口格子，把「该面是否接着铜管」写回本方块的状态。
     * <p>为什么要轮询而不是靠 {@code updateShape}：机器占 2×2×2，而主方块只占其中一格 ——
     * 东/南面（+X/+Z）和底面另外 3 格外面贴的管子，主方块**根本收不到邻居更新**。
     *
     * @return 是否真的改了状态
     */
    public static boolean refreshPorts(Level level, BlockPos origin, Predicate<Block> isMachine,
                                       List<? extends BoundPort> ports) {
        BlockState state = level.getBlockState(origin);
        if (!isMachine.test(state.getBlock())) {
            return false;
        }
        BlockState updated = state;
        for (BoundPort port : ports) {
            BlockPos portPos = origin.offset(port.dx(), port.dy(), port.dz());
            BlockState pipeState = level.getBlockState(portPos);
            boolean connected = (pipeState.getBlock() instanceof MachinePipeBlock
                    && pipeState.getValue(MachinePipeBlock.CONNECTIONS.get(port.towardMachine())))
                    || (pipeState.getBlock() instanceof CopperPipeBlock
                    && CopperPipeBlock.connectsToMachine(level, portPos,
                    portPos.relative(port.towardMachine()), port.towardMachine()));
            if (updated.getValue(port.property()) != connected) {
                updated = updated.setValue(port.property(), connected);
            }
        }
        if (updated == state) {
            return false;
        }
        level.setBlock(origin, updated, Block.UPDATE_ALL);
        return true;
    }

    private LargeMachineStructureOps() {}
}
