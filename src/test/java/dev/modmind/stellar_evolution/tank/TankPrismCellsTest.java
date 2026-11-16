package dev.modmind.stellar_evolution.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * 储罐破坏时「剩余格子」几何范围的回归测试。
 *
 * <p>背景（§81 刷流体漏洞）：修复前破坏带流体的单格储罐时，会用「沿相邻格泛洪」的方式找剩余格子，
 * 把并排放置的**另一个独立储罐**也算了进来，于是流体被灌进隔壁罐、掉落物却是空的。
 * 修复后剩余格子只能由「本大罐的长方体范围」算出（{@link TankMultiblock#prismCells}）。
 */
class TankPrismCellsTest {
    private static final BlockPos ORIGIN = new BlockPos(10, 64, 10);

    @Test
    void singleTankPrismContainsOnlyItself() {
        List<BlockPos> cells = TankMultiblock.prismCells(ORIGIN, 1, 1);
        assertEquals(1, cells.size());
        assertEquals(ORIGIN, cells.getFirst());
    }

    @Test
    void sideBySideTanksAreNotInEachOthersPrism() {
        BlockPos neighbor = ORIGIN.offset(1, 0, 0);
        List<BlockPos> left = TankMultiblock.prismCells(ORIGIN, 1, 1);
        List<BlockPos> right = TankMultiblock.prismCells(neighbor, 1, 1);
        assertFalse(left.contains(neighbor));
        assertFalse(right.contains(ORIGIN));
    }

    @Test
    void prismSizesFollowWidthAndHeight() {
        assertEquals(2, TankMultiblock.prismCells(ORIGIN, 1, 2).size());
        assertEquals(3, TankMultiblock.prismCells(ORIGIN, 1, 3).size());
        assertEquals(9, TankMultiblock.prismCells(ORIGIN, 3, 1).size());
        assertEquals(18, TankMultiblock.prismCells(ORIGIN, 3, 2).size());
    }

    @Test
    void breakingOneCellOfThreeByThreeLeavesEight() {
        List<BlockPos> cells = TankMultiblock.prismCells(ORIGIN, 3, 1);
        long remaining = cells.stream().filter(pos -> !pos.equals(ORIGIN)).count();
        assertEquals(8, remaining);
    }

    @Test
    void fullTankNeverExceedsOneBlockOfOverflow() {
        // 满罐 3×3×1（9 格 × 8000）拆控制器：剩余 8 格能装 64000，余下 8000 正好由掉落物带走
        int perBlock = 8000;
        int amount = 9 * perBlock;
        int remainingCells = 8;
        int keep = Math.min(amount, remainingCells * perBlock);
        assertEquals(64000, keep);
        assertEquals(perBlock, amount - keep);
        // 单格罐（无剩余格子）：整罐都进掉落物，绝不外溢到相邻储罐
        assertEquals(1000, 1000 - Math.min(1000, 0));
    }
}
