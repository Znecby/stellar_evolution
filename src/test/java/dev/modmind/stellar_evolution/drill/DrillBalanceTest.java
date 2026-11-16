package dev.modmind.stellar_evolution.drill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 开采钻「精准采集」新平衡的回归测试（§84）。
 *
 * <p>锚点：1×1 与 3×3 的 1.0× 热流都是 2,048 HP/t；1×1 热阻 4（8,192 DP，1 台柴油引擎），
 * 3×3 热阻 8（16,384 DP，正好 2 台柴油引擎）；周期 3×3 = 260 t 且按 N/3 线性变长；
 * 15×15 由 2 台 20,000 RPM 汽轮机（各 1,280,000 DP）覆盖。
 */
class DrillBalanceTest {
    /** 单台普通柴油燃烧引擎的差压容量（DP）。 */
    private static final double DIESEL_DP = 9_216.0D;
    /** 20,000 RPM 汽轮机的差压容量（DP/RPM × RPM）。 */
    private static final double TURBINE_DP = 20_000.0D * 64.0D;
    private static final double EPS = 1.0E-6D;

    @Test
    void oneByOneNeedsOneDieselEngine() {
        assertEquals(2_048.0D, DrillBalance.baseFlow(1), EPS);
        assertEquals(4.0D, DrillBalance.resistance(1), EPS);
        assertEquals(8_192.0D, DrillBalance.dpOccupancy(1), EPS);
        assertEquals(1.0D, Math.ceil(DrillBalance.dpOccupancy(1) / DIESEL_DP), EPS);
    }

    @Test
    void threeByThreeNeedsExactlyTwoDieselEngines() {
        assertEquals(2_048.0D, DrillBalance.baseFlow(3), EPS);
        assertEquals(8.0D, DrillBalance.resistance(3), EPS);
        assertEquals(16_384.0D, DrillBalance.dpOccupancy(3), EPS);
        assertEquals(2.0D, Math.ceil(DrillBalance.dpOccupancy(3) / DIESEL_DP), EPS);
    }

    @Test
    void flowGrowsLinearlyAndResistanceQuadratically() {
        assertEquals(2_048.0D * 5.0D / 3.0D, DrillBalance.baseFlow(5), EPS);
        assertEquals(8.0D * (5.0D / 3.0D) * (5.0D / 3.0D), DrillBalance.resistance(5), EPS);
        assertEquals(10_240.0D, DrillBalance.baseFlow(15), EPS);
        assertEquals(200.0D, DrillBalance.resistance(15), EPS);
    }

    @Test
    void cycleGrowsLinearlyFromThirtyPercentLongerBase() {
        assertEquals(260.0D, DrillBalance.cycleTicks(3), EPS);
        assertEquals(260.0D / 3.0D, DrillBalance.cycleTicks(1), EPS);
        assertEquals(1_300.0D, DrillBalance.cycleTicks(15), EPS);
    }

    @Test
    void fifteenByFifteenIsCoveredByTwoTwentyThousandRpmTurbines() {
        assertEquals(2_048_000.0D, DrillBalance.dpOccupancy(15), EPS);
        assertEquals(1_280_000.0D, TURBINE_DP, EPS);
        assertEquals(2.0D, Math.ceil(DrillBalance.dpOccupancy(15) / TURBINE_DP), EPS);
    }

    @Test
    void areaResistanceIsLinearAndCycleScalesByArea() {
        // 5×5 基准：热阻 4、DP 1,024、周期 200 t
        assertEquals(4.0D, DrillBalance.areaResistance(5), EPS);
        assertEquals(1_024.0D, DrillBalance.areaDpOccupancy(5), EPS);
        assertEquals(200.0D, DrillBalance.areaCycleTicks(5), EPS);
        // 15×15：热阻线性 12、DP 3,072、周期按面积 1,800 t
        assertEquals(12.0D, DrillBalance.areaResistance(15), EPS);
        assertEquals(3_072.0D, DrillBalance.areaDpOccupancy(15), EPS);
        assertEquals(1_800.0D, DrillBalance.areaCycleTicks(15), EPS);
        // 31×31：DP 6,348.8、周期 7,688 t
        assertEquals(6_348.8D, DrillBalance.areaDpOccupancy(31), EPS);
        assertEquals(7_688.0D, DrillBalance.areaCycleTicks(31), EPS);
        // 63×63：热阻 50.4、DP 12,902.4（不再是平方的 16 万）、周期 31,752 t
        assertEquals(50.4D, DrillBalance.areaResistance(63), EPS);
        assertEquals(12_902.4D, DrillBalance.areaDpOccupancy(63), EPS);
        assertEquals(31_752.0D, DrillBalance.areaCycleTicks(63), EPS);
    }

    @Test
    void fourTimesSpeedCapScalesWithRange() {
        // 满速上限 = 该范围 1× 热流的 4 倍（不是固定的 8,192）
        assertEquals(8_192.0D, DrillBalance.maxUsefulFlow(3), EPS);
        assertEquals(40_960.0D, DrillBalance.maxUsefulFlow(15), EPS);
    }
}
