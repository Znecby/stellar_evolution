package dev.modmind.stellar_evolution.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 蒸汽循环账本的口径测试（纯数值层，不需要 Minecraft 环境）。
 */
class SteamLoopTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("堆温 → 蒸汽等级：不足不起蒸汽，逐级升到超临界")
    void gradeByTemperature() {
        assertEquals(SteamLoop.SteamGrade.NONE, SteamLoop.gradeFor(300.0D));
        assertEquals(SteamLoop.SteamGrade.NONE, SteamLoop.gradeFor(372.9D));
        assertEquals(SteamLoop.SteamGrade.SATURATED, SteamLoop.gradeFor(400.0D));
        assertEquals(SteamLoop.SteamGrade.SUPERHEATED, SteamLoop.gradeFor(550.0D));
        assertEquals(SteamLoop.SteamGrade.SUPERCRITICAL, SteamLoop.gradeFor(700.0D));
    }

    @Test
    @DisplayName("出功倍率随等级单调递增（超临界最高）")
    void turbineFactorIncreasesWithGrade() {
        assertTrue(SteamLoop.SteamGrade.NONE.turbineFactor() < SteamLoop.SteamGrade.SATURATED.turbineFactor());
        assertTrue(SteamLoop.SteamGrade.SATURATED.turbineFactor() < SteamLoop.SteamGrade.SUPERHEATED.turbineFactor());
        assertTrue(SteamLoop.SteamGrade.SUPERHEATED.turbineFactor() < SteamLoop.SteamGrade.SUPERCRITICAL.turbineFactor());
    }

    @Test
    @DisplayName("蒸汽量：低于饱和线为 0，越热越多且封顶 1")
    void steamFractionRampsAndClamps() {
        assertEquals(0.0D, SteamLoop.steamFraction(300.0D, 1200.0D), EPSILON);
        assertEquals(0.0D, SteamLoop.steamFraction(SteamLoop.SATURATED_TEMP, 1200.0D), EPSILON);
        assertTrue(SteamLoop.steamFraction(500.0D, 1200.0D) > 0.0D);
        assertEquals(1.0D, SteamLoop.steamFraction(1500.0D, 1200.0D), EPSILON);
    }
}
