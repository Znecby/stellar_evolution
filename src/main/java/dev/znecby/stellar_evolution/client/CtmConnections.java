package dev.znecby.stellar_evolution.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 连接材质（CTM）的连接判定与子图映射，移植自 GT「高负荷机械方块」所用的
 * ConnectedTexturesMod 逻辑（{@code TextureConnections} / {@code OctagonalOrientation}）。
 *
 * <p>一张面被拆成 4 个 8 × 8 象限；每个象限根据「左 / 右 / 上 / 下 / 四个对角」共 8 个方向
 * 是否与同种方块相连，从基础贴图（16 × 16，四个外侧角）或 CTM 图集（32 × 32，其余象限）里取图。
 *
 * <p>连接判定：相邻方块必须是<strong>同一种方块</strong>，且它「再往外」的那格不是同种方块
 * （避免把一条已经连出去的边重复算成内侧）。
 */
final class CtmConnections {
    /** 八方向（含对角），顺序即连接位掩码的位序。 */
    enum Dir {
        TOP(Direction.UP),
        TOP_RIGHT(Direction.UP, Direction.EAST),
        RIGHT(Direction.EAST),
        BOTTOM_RIGHT(Direction.DOWN, Direction.EAST),
        BOTTOM(Direction.DOWN),
        BOTTOM_LEFT(Direction.DOWN, Direction.WEST),
        LEFT(Direction.WEST),
        TOP_LEFT(Direction.UP, Direction.WEST);

        static final Dir[] VALUES = values();
        private static final Direction NORMAL = Direction.SOUTH;

        private final Direction[] dirs;
        private final BlockPos[] offsets = new BlockPos[6];

        Dir(Direction... dirs) {
            this.dirs = dirs;
        }

        static {
            for (Dir dir : VALUES) {
                dir.buildCaches();
            }
        }

        private void buildCaches() {
            for (Direction normal : Direction.values()) {
                Direction[] normalized;
                if (normal == NORMAL) {
                    normalized = this.dirs;
                } else if (normal == NORMAL.getOpposite()) {
                    Direction[] mirrored = new Direction[this.dirs.length];
                    for (int i = 0; i < mirrored.length; i++) {
                        mirrored[i] = this.dirs[i].getStepY() != 0 ? this.dirs[i] : this.dirs[i].getOpposite();
                    }
                    normalized = mirrored;
                } else {
                    Direction axis;
                    if (normal.getAxis() != Direction.Axis.Y) {
                        axis = normal == NORMAL.getClockWise() ? Direction.UP : Direction.DOWN;
                    } else {
                        axis = normal == Direction.UP
                                ? NORMAL.getCounterClockWise() : NORMAL.getClockWise();
                    }
                    Direction[] rotated = new Direction[this.dirs.length];
                    for (int i = 0; i < rotated.length; i++) {
                        rotated[i] = rotate(this.dirs[i], axis);
                    }
                    normalized = rotated;
                }
                BlockPos offset = BlockPos.ZERO;
                for (Direction direction : normalized) {
                    offset = offset.relative(direction);
                }
                this.offsets[normal.ordinal()] = offset;
            }
        }

        private static Direction rotate(Direction facing, Direction axisFacing) {
            Direction.Axis axis = axisFacing.getAxis();
            if (axisFacing.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                return facing.getClockWise(axis);
            }
            return facing.getCounterClockWise(axis);
        }

        BlockPos offset(Direction normal) {
            return this.offsets[normal.ordinal()];
        }
    }

    /** 每个象限的 [水平, 垂直, 对角] 方向。 */
    private static final Dir[][][] SUBMAP_MAP = {
            { { Dir.LEFT, Dir.TOP, Dir.TOP_LEFT }, { Dir.RIGHT, Dir.TOP, Dir.TOP_RIGHT } },
            { { Dir.LEFT, Dir.BOTTOM, Dir.BOTTOM_LEFT }, { Dir.RIGHT, Dir.BOTTOM, Dir.BOTTOM_RIGHT } }
    };

    /** 子图 id 编码为 {@code (row << 4) | column}；row / column ≥ 4 表示来自基础贴图。 */
    private static final int[][] DEFAULT_IDS = { { (4 << 4) | 4, (4 << 4) | 5 }, { (5 << 4) | 4, (5 << 4) | 5 } };

    private CtmConnections() {}

    /** 面在给定世界位置的 8 方向连接位掩码。 */
    static int mask(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction side) {
        int mask = 0;
        for (Dir dir : Dir.VALUES) {
            BlockPos neighbor = pos.offset(dir.offset(side));
            if (sameBlock(level, neighbor, state) && !sameBlock(level, neighbor.relative(side), state)) {
                mask |= 1 << dir.ordinal();
            }
        }
        return mask;
    }

    private static boolean sameBlock(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos).getBlock() == state.getBlock();
    }

    /** 4 个象限的子图 id，索引 [x][y]（x = 左 / 右，y = 下 / 上）。 */
    static int[][] submapIds(int mask) {
        int[][] cache = {
                { DEFAULT_IDS[0][0], DEFAULT_IDS[0][1] },
                { DEFAULT_IDS[1][0], DEFAULT_IDS[1][1] }
        };
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                fillSubmap(cache, x, y, mask);
            }
        }
        return cache;
    }

    private static void fillSubmap(int[][] cache, int x, int y, int mask) {
        Dir[] dirs = SUBMAP_MAP[x][y];
        boolean horizontal = connected(mask, dirs[0]);
        boolean vertical = connected(mask, dirs[1]);
        if (!horizontal && !vertical) {
            return;
        }
        if (horizontal && vertical && connected(mask, dirs[2])) {
            cache[x][y] = (x << 4) | y;
        } else {
            cache[x][y] = ((x + (horizontal ? 2 : 0)) << 4) | (y + (vertical ? 2 : 0));
        }
    }

    private static boolean connected(int mask, Dir dir) {
        return ((mask >> dir.ordinal()) & 1) == 1;
    }
}
