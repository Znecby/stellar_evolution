package dev.znecby.stellar_evolution.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/**
 * 储罐物品 ↔ 储罐方块 批量转移的纯逻辑 / 边界单测（§83）。
 *
 * <p>覆盖：正常装满、装满即止、类型不匹配、目标已满、源为空、容量边界（刚好填满 / 溢出留在物品）、
 * 非法负数输入，以及流体组件 Codec 的往返一致性。
 */
class TankItemTransferTest {
    private static final int PER_BLOCK = 8000;

    @Test
    void poursEverythingWhenTargetHasRoom() {
        assertEquals(1000, TankItemTransfer.amountToMove(1000, PER_BLOCK));
    }

    @Test
    void fillsUntilFullWhenItemHasMore() {
        // 物品 9000 / 目标空 8000 → 只倒 8000，剩 1000 留在物品
        int moved = TankItemTransfer.amountToMove(9000, PER_BLOCK);
        assertEquals(8000, moved);
        assertEquals(1000, 9000 - moved);
    }

    @Test
    void takingIsLimitedByItemCapacity() {
        // 方块里 72000、手持罐容量 8000 → 只取 8000
        assertEquals(PER_BLOCK, TankItemTransfer.amountToMove(72000, PER_BLOCK));
    }

    @Test
    void targetFullOrSourceEmptyMovesNothing() {
        assertEquals(0, TankItemTransfer.amountToMove(1000, 0));
        assertEquals(0, TankItemTransfer.amountToMove(0, PER_BLOCK));
    }

    @Test
    void negativeInputsAreClampedToZero() {
        assertEquals(0, TankItemTransfer.amountToMove(-5, PER_BLOCK));
        assertEquals(0, TankItemTransfer.amountToMove(1000, -1));
    }

    @Test
    void exactCapacityLeavesItemEmpty() {
        int moved = TankItemTransfer.amountToMove(PER_BLOCK, PER_BLOCK);
        assertEquals(PER_BLOCK, moved);
        assertEquals(0, PER_BLOCK - moved);
    }

    @Test
    void emptyTankAcceptsAnyFluid() {
        assertTrue(TankItemTransfer.typeMatches(null, "minecraft:water"));
        assertTrue(TankItemTransfer.typeMatches(null, "minecraft:lava"));
    }

    @Test
    void sameFluidMatchesDifferentFluidRejects() {
        assertTrue(TankItemTransfer.typeMatches("minecraft:water", "minecraft:water"));
        assertFalse(TankItemTransfer.typeMatches("minecraft:lava", "minecraft:water"));
        assertFalse(TankItemTransfer.typeMatches("minecraft:water", "minecraft:lava"));
    }

    @Test
    void fluidComponentRoundTripsThroughCodec() {
        TankComponents.TankFluid fluid = new TankComponents.TankFluid("minecraft:water", 1000);
        var encoded = TankComponents.TankFluid.CODEC.encodeStart(JsonOps.INSTANCE, fluid).getOrThrow();
        assertEquals(fluid, TankComponents.TankFluid.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
