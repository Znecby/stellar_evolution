package dev.znecby.stellar_evolution.large;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 2×2×2 大机器结构骨架的回归测试：8 格占位 + 12 个接管口。
 *
 * <p>生化 / 石化 / 裂变 / 三台机组现在都引用 {@link LargeMachineStructure}；
 * 这里把几何钉死，后续任何线做去重或调整接点时必须先让本测试保持全绿。
 */
class LargeMachineStructureTest {

    @Test
    void offsetsCoverExactlyTheTwoByTwoByTwoBody() {
        int[][] cells = LargeMachineStructure.CELL_OFFSETS;
        assertEquals(8, cells.length, "应占 8 格");
        Set<String> seen = new HashSet<>();
        boolean hasOrigin = false;
        for (int[] offset : cells) {
            assertEquals(3, offset.length, "偏移必须是三元组");
            for (int value : offset) {
                assertTrue(value == 0 || value == 1, "偏移必须落在 0..1，实际 " + value);
            }
            assertTrue(seen.add(offset[0] + "," + offset[1] + "," + offset[2]), "偏移不能重复");
            hasOrigin |= offset[0] == 0 && offset[1] == 0 && offset[2] == 0;
        }
        assertTrue(hasOrigin, "必须包含主方块 (0,0,0)");
        assertEquals(8, seen.size(), "8 格必须互不相同");
    }

    @Test
    void portsAreTwelveUniquePointsAroundTheBody() {
        var ports = LargeMachineStructure.PORTS;
        assertEquals(12, ports.size(), "应有 12 个接点");

        Set<String> body = new HashSet<>();
        for (int[] offset : LargeMachineStructure.CELL_OFFSETS) {
            body.add(offset[0] + "," + offset[1] + "," + offset[2]);
        }

        Set<String> names = new HashSet<>();
        Set<String> positions = new HashSet<>();
        for (LargeMachineStructure.PortSpec port : ports) {
            assertTrue(names.add(port.name()), "接点名重复：" + port.name());
            String pos = port.dx() + "," + port.dy() + "," + port.dz();
            assertTrue(positions.add(pos), "接点位置重复：" + pos);
            assertFalse(body.contains(pos), "接点不能落在机体 8 格内：" + pos);

            String target = (port.dx() + port.towardMachine().getStepX()) + ","
                    + (port.dy() + port.towardMachine().getStepY()) + ","
                    + (port.dz() + port.towardMachine().getStepZ());
            assertTrue(body.contains(target),
                    "接点 " + port.name() + " 的朝向必须指向机体内一格，实际 " + target);
        }
    }

    @Test
    void portNamesFollowTheFourSidesPlusDownLayout() {
        Set<String> names = new HashSet<>();
        LargeMachineStructure.PORTS.forEach(port -> names.add(port.name()));
        for (String expected : new String[]{
                "port_north_a", "port_north_b", "port_east_a", "port_east_b",
                "port_south_a", "port_south_b", "port_west_a", "port_west_b",
                "port_down_a", "port_down_b", "port_down_c", "port_down_d"}) {
            assertTrue(names.contains(expected), "缺少接点：" + expected);
        }
        assertEquals(12, names.size(), "接点名必须唯一");
    }
}
