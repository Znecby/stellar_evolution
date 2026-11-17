package dev.znecby.stellar_evolution.pipe;

import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 四种管道（铜管 / 机械管 / 压力管 / 束流管）共用的<strong>连接属性与管身几何</strong>。
 *
 * <p>此前每套管子各自抄了一份 6 个连接属性、6 个切断属性、方向表、掩码与 64 个合并形状；
 * 这里作为唯一事实源。各管自己的互连规则、放置规则、分流 / 切断交互仍留在各自的方块类里。
 */
public final class PipeParts {
    /** 六个方向 → 连接状态属性（沿用原版 {@code BlockStateProperties} 的那六个）。 */
    public static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN);

    /** 六个方向 → 「该端被手动切断」属性。为 true 时强制该方向不连接（剪刀右键切换）。 */
    public static final Map<Direction, BooleanProperty> CUTS = Map.of(
            Direction.NORTH, BooleanProperty.create("north_cut"),
            Direction.EAST, BooleanProperty.create("east_cut"),
            Direction.SOUTH, BooleanProperty.create("south_cut"),
            Direction.WEST, BooleanProperty.create("west_cut"),
            Direction.UP, BooleanProperty.create("up_cut"),
            Direction.DOWN, BooleanProperty.create("down_cut"));

    /** 固定顺序的方向表，用于把 6 个连接属性打包成一个 6 位掩码（索引对应 {@link #shapes} 的位）。 */
    public static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};

    /** 把连接位与切断位全部置 false（{@code BooleanProperty} 的隐式默认值是 true，必须显式登记）。 */
    public static BlockState allDisconnected(BlockState state) {
        BlockState result = state;
        for (BooleanProperty connection : CONNECTIONS.values()) {
            result = result.setValue(connection, false);
        }
        for (BooleanProperty cut : CUTS.values()) {
            result = result.setValue(cut, false);
        }
        return result;
    }

    /** 把当前连接状态打包成 6 位掩码（位序同 {@link #DIRECTIONS}），用作形状表索引。 */
    public static int mask(BlockState state) {
        int mask = 0;
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (state.getValue(CONNECTIONS.get(DIRECTIONS[i]))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** 一套管身几何：单根管臂（剪刀瞄准用）+ 64 个预合并形状（选择框 / 碰撞箱用）。 */
    public record ShapeSet(VoxelShape[] arms, VoxelShape[] shapes) {}

    /**
     * 生成一套管身几何：中心立方 + 六个方向的管臂。
     *
     * @param coreMin 管身截面的最小坐标（铜管 6，其余 5）
     * @param coreMax 管身截面的最大坐标（铜管 10，其余 11）
     */
    public static ShapeSet shapeSet(double coreMin, double coreMax) {
        VoxelShape center = Block.box(coreMin, coreMin, coreMin, coreMax, coreMax, coreMax);
        VoxelShape[] arms = {
                Block.box(coreMin, coreMin, 0.0, coreMax, coreMax, coreMin),          // NORTH (-Z)
                Block.box(coreMax, coreMin, coreMin, 16.0, coreMax, coreMax),        // EAST  (+X)
                Block.box(coreMin, coreMin, coreMax, coreMax, coreMax, 16.0),        // SOUTH (+Z)
                Block.box(0.0, coreMin, coreMin, coreMin, coreMax, coreMax),         // WEST  (-X)
                Block.box(coreMin, coreMax, coreMin, coreMax, 16.0, coreMax),        // UP    (+Y)
                Block.box(coreMin, 0.0, coreMin, coreMax, coreMin, coreMax)};        // DOWN  (-Y)
        VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = center;
            for (int i = 0; i < DIRECTIONS.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    shape = Shapes.or(shape, arms[i]);
                }
            }
            shapes[mask] = shape;
        }
        return new ShapeSet(arms, shapes);
    }

    /** 把 6 个连接属性与 6 个切断属性登记进方块状态定义。 */
    public static void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        CUTS.values().forEach(builder::add);
    }

    private PipeParts() {}
}
