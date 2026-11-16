package dev.modmind.stellar_evolution.steam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.modmind.stellar_evolution.fission.SteamLoop;
import org.junit.jupiter.api.Test;

/** {@link SteamState} 的等级 / 压力加成 / 混合口径（纯数值，不依赖 Minecraft 运行时）。 */
class SteamStateTest {

    @Test
    void noSteamBelowBoiling() {
        assertEquals(SteamLoop.SteamGrade.NONE, SteamState.NONE.grade());
        assertEquals(SteamLoop.SteamGrade.NONE, new SteamState(5.0D, 99.9D, 50.0D).grade());
        assertTrue(SteamState.NONE.isEmpty());
    }

    @Test
    void saturatedAndSuperheated() {
        assertEquals(SteamLoop.SteamGrade.SATURATED, new SteamState(5.0D, 150.0D, 80.0D).grade());
        assertEquals(SteamLoop.SteamGrade.SUPERHEATED, new SteamState(5.0D, 300.0D, 120.0D).grade());
    }

    @Test
    void supercriticalNeedsBothTemperatureAndPressure() {
        SteamState hotButLowPressure = new SteamState(5.0D, 400.0D, 100.0D);
        assertEquals(SteamLoop.SteamGrade.SUPERHEATED, hotButLowPressure.grade());
        assertFalse(hotButLowPressure.supercritical());

        SteamState supercritical = new SteamState(5.0D, 400.0D, 230.0D);
        assertEquals(SteamLoop.SteamGrade.SUPERCRITICAL, supercritical.grade());
        assertTrue(supercritical.supercritical());
    }

    @Test
    void pressureBoostsDpAndCaps() {
        assertEquals(1.0D, new SteamState(1.0D, 400.0D, 0.0D).dpMultiplier(), 1.0E-9D);
        assertEquals(1.6D, new SteamState(1.0D, 400.0D, 300.0D).dpMultiplier(), 1.0E-9D);
        assertEquals(2.2D, new SteamState(1.0D, 400.0D, 600.0D).dpMultiplier(), 1.0E-9D);
        assertEquals(2.8D, new SteamState(1.0D, 400.0D, 900.0D).dpMultiplier(), 1.0E-9D);
        assertEquals(2.8D, new SteamState(1.0D, 400.0D, 1200.0D).dpMultiplier(), 1.0E-9D);
    }

    @Test
    void ultraSupercriticalNeedsHigherTemperatureAndPressure() {
        SteamState justSupercritical = new SteamState(5.0D, 500.0D, 250.0D);
        assertEquals(SteamLoop.SteamGrade.SUPERCRITICAL, justSupercritical.grade());

        SteamState ultra = new SteamState(5.0D, 650.0D, 600.0D);
        assertEquals(SteamLoop.SteamGrade.ULTRA_SUPERCRITICAL, ultra.grade());
        assertTrue(ultra.supercritical());

        // 温度到顶但压力卡在 500：仍是超临界（聚变工质封顶 700℃ 时的典型工况）
        assertEquals(SteamLoop.SteamGrade.SUPERCRITICAL, new SteamState(5.0D, 700.0D, 500.0D).grade());
    }

    @Test
    void ultraSupercriticalIsOutOfReachForFission() {
        // 裂变堆的 MOX 压力硬顶正好 500 bar（严格大于才达标）：永远够不到过超临界
        SteamState fission = new SteamState(5.0D, 1799.0D, 500.0D);
        assertEquals(SteamLoop.SteamGrade.SUPERCRITICAL, fission.grade());
        assertTrue(fission.supercritical());

        // 严格大于：刚好 600℃ / 500 bar 不算
        assertEquals(SteamLoop.SteamGrade.SUPERCRITICAL, new SteamState(5.0D, 600.0D, 500.0D).grade());

        // 温度够、压力不够也不算
        assertEquals(SteamLoop.SteamGrade.SUPERCRITICAL, new SteamState(5.0D, 700.0D, 480.0D).grade());
    }

    @Test
    void blendIsWeightedByAmount() {
        SteamState blended = new SteamState(2.0D, 200.0D, 100.0D)
                .blend(new SteamState(6.0D, 400.0D, 300.0D));
        assertEquals(8.0D, blended.amount(), 1.0E-9D);
        assertEquals(350.0D, blended.temperatureC(), 1.0E-9D);
        assertEquals(250.0D, blended.pressureBar(), 1.0E-9D);
        assertEquals(8.0D, SteamState.NONE.blend(new SteamState(8.0D, 300.0D, 100.0D)).amount(), 1.0E-9D);
    }
}
