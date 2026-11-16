package dev.znecby.stellar_evolution.multiblock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MultiblockRotationTest {
    private static final BlockPos CONTROLLER = new BlockPos(10, 64, 10);

    @Test
    void northKeepsLocalAxes() {
        assertEquals(new BlockPos(11, 64, 10),
                MultiblockRotation.localToWorld(CONTROLLER, Direction.NORTH, new BlockPos(1, 0, 0)));
        assertEquals(new BlockPos(10, 64, 11),
                MultiblockRotation.localToWorld(CONTROLLER, Direction.NORTH, new BlockPos(0, 0, 1)));
    }

    @Test
    void eastTurnsRightIntoSouthAndBackIntoWest() {
        assertEquals(new BlockPos(10, 64, 11),
                MultiblockRotation.localToWorld(CONTROLLER, Direction.EAST, new BlockPos(1, 0, 0)));
        assertEquals(new BlockPos(9, 64, 10),
                MultiblockRotation.localToWorld(CONTROLLER, Direction.EAST, new BlockPos(0, 0, 1)));
    }

    @Test
    void southTurnsRightIntoWestAndBackIntoNorth() {
        assertEquals(new BlockPos(9, 64, 10),
                MultiblockRotation.localToWorld(CONTROLLER, Direction.SOUTH, new BlockPos(1, 0, 0)));
        assertEquals(new BlockPos(10, 64, 9),
                MultiblockRotation.localToWorld(CONTROLLER, Direction.SOUTH, new BlockPos(0, 0, 1)));
    }

    @Test
    void westTurnsRightIntoNorthAndBackIntoEast() {
        assertEquals(new BlockPos(10, 64, 9),
                MultiblockRotation.localToWorld(CONTROLLER, Direction.WEST, new BlockPos(1, 0, 0)));
        assertEquals(new BlockPos(11, 64, 10),
                MultiblockRotation.localToWorld(CONTROLLER, Direction.WEST, new BlockPos(0, 0, 1)));
    }

    @Test
    void boundsContainOnlyTheRotatedBox() {
        assertTrue(MultiblockRotation.boundsContains(CONTROLLER, Direction.NORTH,
                -1, 0, -1, 1, 2, 1, new BlockPos(11, 66, 11)));
        assertFalse(MultiblockRotation.boundsContains(CONTROLLER, Direction.NORTH,
                -1, 0, -1, 1, 2, 1, new BlockPos(12, 64, 10)));
    }

    @Test
    void boundsChunksCoverRotatedExtent() {
        int[] range = MultiblockRotation.chunkRange(CONTROLLER, Direction.EAST,
                -1, 0, -1, 1, 2, 1);
        // 10±1 仍全在区块 0 内
        assertArrayEquals(new int[] { 0, 0, 0, 0 }, range);
    }

    @Test
    void chunkRangeSpansChunkBorder() {
        // 控制器在区块边界上：局部两格宽的图案会跨到下一区块
        int[] range = MultiblockRotation.chunkRange(new BlockPos(15, 64, 15), Direction.NORTH,
                0, 0, 0, 1, 0, 1);
        assertArrayEquals(new int[] { 0, 0, 1, 1 }, range);
    }
}
