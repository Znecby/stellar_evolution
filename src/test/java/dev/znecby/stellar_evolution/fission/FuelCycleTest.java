package dev.znecby.stellar_evolution.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 燃料循环账本的口径测试（纯数值层，不需要 Minecraft 环境）。
 */
class FuelCycleTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("酸浸 / 氟化 / 制棒：产量随进料单调上升")
    void conversionsAreMonotonic() {
        assertTrue(FuelCycle.leachOutput(200.0D) > FuelCycle.leachOutput(100.0D));
        assertTrue(FuelCycle.fluorinate(20.0D) > FuelCycle.fluorinate(10.0D));
        assertTrue(FuelCycle.rodsFromEnriched(5.0D) > FuelCycle.rodsFromEnriched(2.0D));
    }

    @Test
    @DisplayName("非正进料 → 全零（不报错）")
    void nonPositiveFeedGivesZero() {
        assertEquals(0.0D, FuelCycle.leachOutput(0.0D), EPSILON);
        assertEquals(0.0D, FuelCycle.leachOutput(-3.0D), EPSILON);
        assertEquals(0.0D, FuelCycle.fluorinate(-1.0D), EPSILON);
        assertEquals(0.0D, FuelCycle.rodsFromEnriched(0.0D), EPSILON);
    }

    @Test
    @DisplayName("酸浸收率小于 1：产出 ≤ 进料")
    void leachYieldsLessThanInput() {
        assertTrue(FuelCycle.leachOutput(100.0D) < 100.0D);
        assertTrue(FuelCycle.leachOutput(100.0D) > 0.0D);
    }

    @Test
    @DisplayName("后处理守恒：回收 + 废料 = 乏燃料")
    void reprocessIsConserved() {
        FuelCycle.ReprocessResult r = FuelCycle.reprocess(1000.0D, 0.6D);
        assertEquals(1000.0D, r.recovered() + r.waste(), EPSILON);
        assertTrue(r.recovered() > 0.0D);
        assertTrue(r.waste() > 0.0D);
    }

    @Test
    @DisplayName("回收率越界被夹到 [0,1]；非正乏燃料 → 全零")
    void reprocessClampsAndHandlesZero() {
        assertEquals(0.0D, FuelCycle.reprocess(100.0D, -1.0D).recovered(), EPSILON);
        assertEquals(100.0D, FuelCycle.reprocess(100.0D, 5.0D).recovered(), EPSILON);
        assertEquals(0.0D, FuelCycle.reprocess(0.0D, 0.5D).recovered(), EPSILON);
    }
}
