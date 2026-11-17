package dev.znecby.stellar_evolution.large;

import java.util.List;
import net.minecraft.core.Direction;

/**
 * 2×2×2 大机器的<strong>结构骨架</strong>（纯数据，不碰注册表）：8 格占位 + 12 个机械管接点。
 *
 * <p>生化 / 石化 / 裂变 / 三台机组此前各自抄了一份同构表；这里作为唯一事实源，
 * 各线只把 {@link PortSpec} 包成自己的 {@code Port} record（补上 {@code BooleanProperty}）。
 * 偏移与接点几何被 {@code LargeMachineStructureTest} 钉死，任何一线改动都必须同步到这里。
 */
public final class LargeMachineStructure {
    /** 一台机器占据的 8 格偏移（主方块在 (0,0,0)，其余 7 格由填充格占位）。 */
    public static final int[][] CELL_OFFSETS = {
            {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
            {1, 1, 0}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}
    };

    /**
     * 一个接点：名字（同时是 blockstate 属性名）+ 贴着的那一格（相对主方块）+ 铜管指向机器的方向。
     * <p>名字与生成脚本里的接点名必须一致（三处：Java / blockstate / 脚本）。
     */
    public record PortSpec(String name, int dx, int dy, int dz, Direction towardMachine) {}

    /** 12 个接点：四个水平侧面各 2 个（机器 2 格宽，底排每格一个）+ 底面 4 个。 */
    public static final List<PortSpec> PORTS = List.of(
            new PortSpec("port_north_a", 0, 0, -1, Direction.SOUTH),
            new PortSpec("port_north_b", 1, 0, -1, Direction.SOUTH),
            new PortSpec("port_east_a", 2, 0, 0, Direction.WEST),
            new PortSpec("port_east_b", 2, 0, 1, Direction.WEST),
            new PortSpec("port_south_a", 0, 0, 2, Direction.NORTH),
            new PortSpec("port_south_b", 1, 0, 2, Direction.NORTH),
            new PortSpec("port_west_a", -1, 0, 0, Direction.EAST),
            new PortSpec("port_west_b", -1, 0, 1, Direction.EAST),
            new PortSpec("port_down_a", 0, -1, 0, Direction.UP),
            new PortSpec("port_down_b", 1, -1, 0, Direction.UP),
            new PortSpec("port_down_c", 0, -1, 1, Direction.UP),
            new PortSpec("port_down_d", 1, -1, 1, Direction.UP));

    private LargeMachineStructure() {}
}
