package dev.modmind.stellar_evolution.hep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link CyclotronCharge} 的纯数值口径：64 HP/t 换 1 差速、配方需求为差速上限、
 * DP 决定并行（不足 1 份时保底 1）。
 */
class CyclotronChargeTest {

    private static final double EPS = 1.0E-6D;

    /** 2048 HP/t 下的前期配方需求：180 秒。 */
    private static final double DEMAND_180S = 115_200.0D;

    @Test
    void sixtyFourHpPerTickIsOneStep() {
        assertEquals(1.0D, CyclotronCharge.charge(0.0D, 100.0D, 64.0D), EPS);
        assertEquals(0.5D, CyclotronCharge.charge(0.0D, 100.0D, 32.0D), EPS);
        assertEquals(0.0D, CyclotronCharge.charge(0.0D, 100.0D, 0.0D), EPS);
    }

    @Test
    void chargesAtNetworkHpDividedBySixtyFour() {
        double charge = CyclotronCharge.charge(0.0D, DEMAND_180S, 2_048.0D);
        assertEquals(32.0D, charge, EPS);
        assertFalse(CyclotronCharge.ready(charge, DEMAND_180S));
    }

    @Test
    void highHpFillsInOneTick() {
        double charge = CyclotronCharge.charge(0.0D, DEMAND_180S, 64_000_000.0D);
        assertEquals(DEMAND_180S, charge, EPS);
        assertTrue(CyclotronCharge.ready(charge, DEMAND_180S));
    }

    @Test
    void chargeNeverExceedsRecipeDemand() {
        double charge = CyclotronCharge.charge(DEMAND_180S, DEMAND_180S, 64_000_000.0D);
        assertEquals(DEMAND_180S, charge, EPS);
    }

    @Test
    void twoThousandFortyEightHpFillsTheEarlyRecipeInThreeMinutes() {
        int ticks = 0;
        double charge = 0.0D;
        while (!CyclotronCharge.ready(charge, DEMAND_180S)) {
            charge = CyclotronCharge.charge(charge, DEMAND_180S, 2_048.0D);
            ticks++;
            assertTrue(ticks <= 3_600, "2048 HP 下 115,200 差速应在 3600 tick 内充满");
        }
        assertEquals(3_600, ticks);
    }

    @Test
    void parallelFloorsAtOneWhenDpIsShort() {
        assertEquals(1, CyclotronCharge.parallel(0.0D, DEMAND_180S, 100, 64));
        assertEquals(1, CyclotronCharge.parallel(1_000.0D, DEMAND_180S, 100, 64));
        assertEquals(1, CyclotronCharge.parallel(DEMAND_180S, DEMAND_180S, 100, 64));
    }

    @Test
    void parallelIsDpDividedByDemandAboveTheFloor() {
        assertEquals(2, CyclotronCharge.parallel(DEMAND_180S * 2.0D, DEMAND_180S, 100, 64));
        assertEquals(3, CyclotronCharge.parallel(DEMAND_180S * 3.9D, DEMAND_180S, 100, 64));
        assertEquals(64, CyclotronCharge.parallel(DEMAND_180S * 1_000.0D, DEMAND_180S, 10_000, 64));
    }

    @Test
    void parallelIsCappedAtSixtyFourAndPowder() {
        assertEquals(5, CyclotronCharge.parallel(DEMAND_180S * 1_000.0D, DEMAND_180S, 5, 64));
        assertEquals(3, CyclotronCharge.parallel(DEMAND_180S * 1_000.0D, DEMAND_180S, 10_000, 3));
    }

    @Test
    void parallelIsZeroWithoutPowder() {
        assertEquals(0, CyclotronCharge.parallel(DEMAND_180S * 1_000.0D, DEMAND_180S, 0, 64));
    }

    @Test
    void zeroDemandNeverChargesOrCrafts() {
        assertEquals(0.0D, CyclotronCharge.charge(100.0D, 0.0D, 4_096.0D), EPS);
        assertFalse(CyclotronCharge.ready(100.0D, 0.0D));
        assertEquals(0, CyclotronCharge.parallel(DEMAND_180S * 1_000.0D, 0.0D, 10, 64));
    }
}
