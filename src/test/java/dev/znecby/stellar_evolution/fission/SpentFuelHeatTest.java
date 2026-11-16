package dev.znecby.stellar_evolution.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link SpentFuelHeat} 的配平口径（纯数值，不依赖 Minecraft）。 */
class SpentFuelHeatTest {

    @Test
    void initialHeatByFamilyAndGrade() {
        assertEquals(400.0F, SpentFuelHeat.initial(FuelSpec.U_LOW), 1.0E-6F);
        assertEquals(800.0F, SpentFuelHeat.initial(FuelSpec.U_HIGH), 1.0E-6F);
        assertEquals(1000.0F, SpentFuelHeat.initial(FuelSpec.MOX_HIGH), 1.0E-6F);
        assertEquals(300.0F, SpentFuelHeat.initial(FuelSpec.TH_LOW), 1.0E-6F);
    }

    @Test
    void eightWaterSourcesIsFullSpeed() {
        assertEquals(2.0F, SpentFuelHeat.coolingPerSecond(8), 1.0E-6F);
        assertEquals(2.0F, SpentFuelHeat.coolingPerSecond(12), 1.0E-6F);
        assertEquals(0.25F, SpentFuelHeat.coolingPerSecond(1), 1.0E-6F);
        assertEquals(0.0F, SpentFuelHeat.coolingPerSecond(0), 1.0E-6F);
    }

    @Test
    void halfOfPreviousCoolingTime() {
        // 1000 温度 / 2 每秒 = 500 秒（早期草案 1000 秒的一半）
        assertEquals(0.0F, SpentFuelHeat.cool(1000.0F, 8, 500.0F), 1.0E-6F);
        // 1 个水源时慢 8 倍，需要 4000 秒
        assertEquals(0.0F, SpentFuelHeat.cool(1000.0F, 1, 4000.0F), 1.0E-6F);
        assertEquals(500.0F, SpentFuelHeat.cool(1000.0F, 1, 2000.0F), 1.0E-6F);
    }

    @Test
    void coolingNeverGoesBelowZero() {
        assertEquals(0.0F, SpentFuelHeat.cool(10.0F, 8, 100.0F), 1.0E-6F);
        assertEquals(0.0F, SpentFuelHeat.cool(0.0F, 8, 100.0F), 1.0E-6F);
        assertEquals(10.0F, SpentFuelHeat.cool(10.0F, 0, 100.0F), 1.0E-6F);
    }

    @Test
    void hotThresholdAndFluxBonus() {
        assertFalse(SpentFuelHeat.isHot(SpentFuelHeat.HOT));
        assertTrue(SpentFuelHeat.isHot(SpentFuelHeat.HOT + 1.0F));
        assertEquals(400.0F, SpentFuelHeat.initialWithFlux(FuelSpec.U_LOW, 0.0D), 1.0E-6F);
        assertEquals(500.0F, SpentFuelHeat.initialWithFlux(FuelSpec.U_LOW, 50.0D), 1.0E-6F);
        assertEquals(1000.0F, SpentFuelHeat.initialWithFlux(FuelSpec.MOX_HIGH, 100.0D), 1.0E-6F);
    }
}
