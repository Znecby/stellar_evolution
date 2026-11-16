package dev.modmind.stellar_evolution.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link ReactorCoreModel} 的配平口径（纯数值，不依赖 Minecraft 运行时）。 */
class ReactorCoreModelTest {

    @Test
    void halfFuelHalfGraphiteIsHalfFlux() {
        assertEquals(0.5D, ReactorCoreModel.loadRatio(1, 1), 1.0E-9D);
        assertEquals(50.0D, ReactorCoreModel.targetFlux(1, 1, 0), 1.0E-9D);
        assertEquals(65.0D, ReactorCoreModel.targetFlux(1, 1, 6), 1.0E-9D);
        assertEquals(100.0D, ReactorCoreModel.targetFlux(1, 0, 0), 1.0E-9D);
    }

    @Test
    void efficiencyPeaksAtFiftyToSixty() {
        assertEquals(0.5D, ReactorCoreModel.efficiency(25.0D), 1.0E-9D);
        assertEquals(1.0D, ReactorCoreModel.efficiency(50.0D), 1.0E-9D);
        assertEquals(1.0D, ReactorCoreModel.efficiency(60.0D), 1.0E-9D);
        assertEquals(0.6D, ReactorCoreModel.efficiency(80.0D), 1.0E-9D);
        assertEquals(0.2D, ReactorCoreModel.efficiency(100.0D), 1.0E-9D);
    }

    @Test
    void burnRisesAboveFiftyAndFasterAboveSixty() {
        assertEquals(1.0D, ReactorCoreModel.burnFactor(50.0D), 1.0E-9D);
        assertEquals(1.15D, ReactorCoreModel.burnFactor(60.0D), 1.0E-9D);
        assertEquals(1.95D, ReactorCoreModel.burnFactor(80.0D), 1.0E-9D);
        assertEquals(2.75D, ReactorCoreModel.burnFactor(100.0D), 1.0E-9D);
    }

    @Test
    void shellAndCoreTemperatures() {
        assertEquals(948.25D, ReactorCoreModel.shellTarget(848.25D), 1.0E-9D);
        double core = ReactorCoreModel.coreTemp(948.25D, 450.0D, 32.5D, 6, 2.0D);
        assertEquals(1143.25D, core, 1.0E-9D);
        assertEquals(1045.75D, ReactorCoreModel.claddingTemp(948.25D, core), 1.0E-9D);
    }

    @Test
    void steamTemperatureIsClampedButShellStaysReal() {
        assertEquals(500.0D, ReactorCoreModel.steamTemperature(500.0D), 1.0E-9D);
        assertEquals(ReactorCoreModel.STEAM_MAX_C, ReactorCoreModel.steamTemperature(1499.0D), 1.0E-9D);
        assertEquals(ReactorCoreModel.STEAM_MAX_C, ReactorCoreModel.steamTemperature(1800.0D), 1.0E-9D);
        // 堆壳温度本身不受影响：损毁 / 耗水仍读原值
        assertEquals(1500.0D, ReactorCoreModel.shellTarget(1400.0D), 1.0E-9D);
        assertTrue(ReactorCoreModel.damagePerTick(1600.0D) > 0.0D);
    }

    @Test
    void damageStartsAt1500AndIsInstantAt1800() {
        assertEquals(0.0D, ReactorCoreModel.damagePerTick(1500.0D), 1.0E-12D);
        assertEquals(0.005D, ReactorCoreModel.damagePerTick(1800.0D), 1.0E-12D);
        assertFalse(ReactorCoreModel.instantMeltdown(1799.0D));
        assertTrue(ReactorCoreModel.instantMeltdown(1800.0D));
    }

    @Test
    void pressureAndExtraWater() {
        assertEquals(226.0D, ReactorCoreModel.pressureBar(450.0D, 0), 1.0E-9D);
        assertEquals(326.0D, ReactorCoreModel.pressureBar(450.0D, 2), 1.0E-9D);
        assertEquals(0.0D, ReactorCoreModel.extraWaterFactor(400.0D), 1.0E-9D);
        assertTrue(ReactorCoreModel.extraWaterFactor(800.0D) > 0.0D);
    }

    @Test
    void moxOverdriveAndThoriumBreederCurves() {
        // MOX 60–80% 仍在超功率区，铀在 80% 已跌到 0.6
        assertEquals(1.2D, ReactorCoreModel.efficiency(FuelSpec.MOX_MID, 80.0D), 1.0E-9D);
        assertEquals(0.6D, ReactorCoreModel.efficiency(FuelSpec.U_HIGH, 80.0D), 1.0E-9D);
        assertEquals(0.2D, ReactorCoreModel.efficiency(FuelSpec.MOX_MID, 100.0D), 1.0E-9D);
        // 钍棒不发热
        assertEquals(0.0D, ReactorCoreModel.efficiency(FuelSpec.TH_MID, 60.0D), 1.0E-9D);
        assertEquals(0.0D, ReactorCoreModel.rodPower(FuelSpec.TH_MID, 60.0D, 4), 1.0E-9D);
        // MOX 80% 的燃耗比铀低（允许超功率）
        assertTrue(ReactorCoreModel.burnFactor(FuelSpec.MOX_MID, 80.0D)
                < ReactorCoreModel.burnFactor(FuelSpec.U_HIGH, 80.0D));
    }

    @Test
    void thoriumSinkLowersTargetFluxAndRequiresSeed() {
        assertEquals(100.0D, ReactorCoreModel.targetFlux(1, 0, 0, 0.0D), 1.0E-9D);
        assertEquals(50.0D, ReactorCoreModel.targetFlux(1, 0, 0, 1.0D), 1.0E-9D);
        assertEquals(0.0D, ReactorCoreModel.targetFlux(0, 0, 0, 4.0D), 1.0E-9D);
    }

    @Test
    void fuelFamilyCapsPressure() {
        // 铀 300 bar 硬上限：再多的功率也上不去
        assertEquals(300.0D, ReactorCoreModel.pressureBar(900.0D, 0, 300.0D), 1.0E-9D);
        // MOX 500 bar 上限
        assertEquals(451.0D, ReactorCoreModel.pressureBar(900.0D, 0, 500.0D), 1.0E-9D);
        // 不传族上限时退回旧口径（300 bar 基础硬顶）
        assertEquals(300.0D, ReactorCoreModel.pressureBar(900.0D, 0), 1.0E-9D);
    }

    @Test
    void seventeenFuelSpecsHaveDistinctRoles() {
        assertEquals(17, FuelSpec.values().length);
        assertEquals(1, FuelSpec.U_LOW.u235PerPellet());
        // 钍棒必须靠点火棒：0 MW + 吸收体
        assertTrue(FuelSpec.TH_HIGH.isBreeder());
        assertEquals(0, FuelSpec.TH_HIGH.thermalPowerMw());
        assertTrue(FuelSpec.TH_HIGH.breederSink() > FuelSpec.TH_LOW.breederSink());
        // MOX 压力档高于铀
        assertTrue(FuelSpec.MOX_HIGH.pressureCapBar() > FuelSpec.U_HIGH.pressureCapBar());
    }

    @Test
    void sameFamilyAndEnrichmentMixHasNoPenalty() {
        assertEquals(0, ReactorCoreModel.mixingPenalty(
                java.util.List.of(FuelSpec.U_LOW, FuelSpec.U_MID, FuelSpec.U_HIGH)).points());
        // 铀 + MOX 低（同热谱 / 同为 OXIDE）也不触发规则
        assertEquals(0, ReactorCoreModel.mixingPenalty(
                java.util.List.of(FuelSpec.U_HIGH, FuelSpec.MOX_LOW)).points());
    }

    @Test
    void incompatibleTraitsAddPoints() {
        // 热谱 + 快谱 = 2；发电 + 嬗变 = 2；POSITIVE + NEGATIVE = 1 → 共 5
        assertEquals(5, ReactorCoreModel.mixingPenalty(
                java.util.List.of(FuelSpec.U_MID, FuelSpec.MA_TRANSMUTE)).points());
        // 发电 + 嬗变 = 2；再叠 OXIDE + METAL = 1、POSITIVE + NEGATIVE = 1 → 共 6
        ReactorCoreModel.MixingPenalty penalty = ReactorCoreModel.mixingPenalty(
                java.util.List.of(FuelSpec.U_MID, FuelSpec.MA_TRANSMUTE, FuelSpec.UZR_METAL));
        assertEquals(6, penalty.points());
        assertEquals(1.6D, penalty.fluxFactor(), 1.0E-9D);
        assertEquals(0.7D, penalty.efficiencyFactor(), 1.0E-9D);
        assertEquals(120.0D, penalty.shellTempBonusC(), 1.0E-9D);
        assertFalse(penalty.autoShutdown());
    }

    @Test
    void heavyMixSurgesAndAutoShutsDown() {
        ReactorCoreModel.MixingPenalty six = ReactorCoreModel.ofPoints(6);
        assertEquals(5.0D, six.fluxSurgePoints(), 1.0E-9D);
        assertEquals(0.0D, ReactorCoreModel.ofPoints(5).fluxSurgePoints(), 1.0E-9D);
        assertFalse(six.autoShutdown());
        assertTrue(ReactorCoreModel.ofPoints(8).autoShutdown());
        assertEquals(0.0D, ReactorCoreModel.effectiveTargetFlux(80.0D, 0.0D, 0.0D,
                ReactorCoreModel.ofPoints(8)), 1.0E-9D);
    }

    @Test
    void boronScalesTargetDownToShutdown() {
        assertEquals(1.0D, ReactorCoreModel.boronFactor(0.0D), 1.0E-9D);
        assertEquals(0.5D, ReactorCoreModel.boronFactor(1_500.0D), 1.0E-9D);
        assertEquals(0.0D, ReactorCoreModel.boronFactor(3_000.0D), 1.0E-9D);
        // 1 桶硼酸（1000 mB = 3000 ppm 当量）正好停堆
        assertEquals(0.0D, ReactorCoreModel.boronFactor(
                ReactorCoreModel.BORON_PER_BUCKET), 1.0E-9D);
        assertEquals(0.0D, ReactorCoreModel.effectiveTargetFlux(80.0D, 3_000.0D, 0.0D,
                ReactorCoreModel.MixingPenalty.NONE), 1.0E-9D);
    }

    @Test
    void xenonBuildsUpAndCreatesIodinePitAfterShutdown() {
        ReactorCoreModel.XenonState state = ReactorCoreModel.XenonState.EMPTY;
        for (int i = 0; i < 8_000; i++) {
            state = ReactorCoreModel.xenonStep(state, 60.0D, 1.0D);
        }
        double equilibrium = state.xenon();
        assertTrue(equilibrium > 10.0D);
        double factorRunning = ReactorCoreModel.xenonFactor(equilibrium);
        assertTrue(factorRunning < 0.9D && factorRunning > 0.5D);

        // 停堆后碘继续变氙：氙先升后降，出现碘坑
        double peak = equilibrium;
        for (int i = 0; i < 3_000; i++) {
            state = ReactorCoreModel.xenonStep(state, 0.0D, 1.0D);
            peak = Math.max(peak, state.xenon());
        }
        assertTrue(peak > equilibrium);
        assertTrue(ReactorCoreModel.xenonFactor(peak) < factorRunning);

        // 快谱产额低
        assertEquals(0.2D, ReactorCoreModel.iodineYield(FuelSpec.Spectrum.FAST), 1.0E-9D);
    }

    @Test
    void emberRampFallsWithDurabilityAndBurnRisesAtLowFlux() {
        // R = 1 + (3 − 1) × d
        assertEquals(3.0D, ReactorCoreModel.emberRampFactor(FuelSpec.UNSTABLE_EMBER, 1.0D), 1.0E-9D);
        assertEquals(2.0D, ReactorCoreModel.emberRampFactor(FuelSpec.UNSTABLE_EMBER, 0.5D), 1.0E-9D);
        assertEquals(1.0D, ReactorCoreModel.emberRampFactor(FuelSpec.UNSTABLE_EMBER, 0.0D), 1.0E-9D);
        assertEquals(1.0D, ReactorCoreModel.emberRampFactor(FuelSpec.U_MID, 1.0D), 1.0E-9D);
        // 燃烧倍率：低通量费燃料（3.0 封顶），高通量省燃料（0.5 下限）
        assertEquals(3.0D, ReactorCoreModel.emberBurnFactor(FuelSpec.UNSTABLE_EMBER, 10.0D), 1.0E-9D);
        assertEquals(1.0D, ReactorCoreModel.emberBurnFactor(FuelSpec.UNSTABLE_EMBER, 50.0D), 1.0E-9D);
        assertEquals(0.5D, ReactorCoreModel.emberBurnFactor(FuelSpec.UNSTABLE_EMBER, 100.0D), 1.0E-9D);
        // 稳定档不吃反向燃烧
        assertEquals(1.0D, ReactorCoreModel.emberBurnFactor(FuelSpec.STABLE_EMBER, 10.0D), 1.0E-9D);
        assertEquals(1.2D, ReactorCoreModel.emberRampFactor(FuelSpec.STABLE_EMBER, 1.0D), 1.0E-9D);
    }

    @Test
    void exoticMixedWithThermalIsPenalised() {
        // EXOTIC + THERMAL = 3，再叠 POSITIVE + NEGATIVE = 1
        assertEquals(4, ReactorCoreModel.mixingPenalty(
                java.util.List.of(FuelSpec.UNSTABLE_EMBER, FuelSpec.U_MID)).points());
        // 两根离火棒同谱同角色：0
        assertEquals(0, ReactorCoreModel.mixingPenalty(
                java.util.List.of(FuelSpec.UNSTABLE_EMBER, FuelSpec.STABLE_EMBER)).points());
    }
}
