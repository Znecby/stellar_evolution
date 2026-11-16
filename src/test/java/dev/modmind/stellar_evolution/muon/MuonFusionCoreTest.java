package dev.modmind.stellar_evolution.muon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.modmind.stellar_evolution.fission.SteamLoop;
import dev.modmind.stellar_evolution.steam.SteamState;
import dev.modmind.stellar_evolution.stream.StreamType;
import org.junit.jupiter.api.Test;

/** {@link MuonFusionCore} 的纯数值测试（点火 / 回退 / 稳定值 / 蒸汽等级 / 熔毁）。 */
class MuonFusionCoreTest {
    @Test
    void ignitionNeedsSupplyAndRetreatsWhenStarved() {
        MuonFusionCore core = new MuonFusionCore();
        for (int i = 0; i < 100; i++) {
            core.step(true, false, true, 1.0D);
        }
        assertFalse(core.ignited());
        assertEquals(0.0D, core.charge(), 1.0E-9D);
    }

    @Test
    void ignitionSucceedsAndSetsStabilityTo90() {
        MuonFusionCore core = new MuonFusionCore();
        for (int i = 0; i < 200 && !core.ignited(); i++) {
            core.step(true, true, true, 1.0D);
        }
        assertTrue(core.ignited());
        assertEquals(MuonFusionCore.STABILITY_START, core.stability(), 1.0E-6D);
        assertEquals(MuonFusionCore.IGNITION_TEMP_C, core.temperatureC(), 1.0E-6D);
    }

    @Test
    void cooledRunSlowlyRecoversStability() {
        MuonFusionCore core = new MuonFusionCore();
        for (int i = 0; i < 200 && !core.ignited(); i++) {
            core.step(true, true, true, 1.0D);
        }
        double before = core.stability();
        for (int i = 0; i < 100; i++) {
            core.step(true, true, true, 1.0D);
        }
        double gain = core.stability() - before;
        assertTrue(core.emitting());
        assertTrue(gain > 0.0D, "冷却水充足时稳定值应缓慢回升");
        assertTrue(gain < 1.0D, "只是缓慢回升，不应瞬间满值");
    }

    @Test
    void dryRunDrainsStabilityFastAndHalts() {
        MuonFusionCore core = new MuonFusionCore();
        for (int i = 0; i < 200 && !core.ignited(); i++) {
            core.step(true, true, true, 1.0D);
        }
        double before = core.stability();
        core.step(true, true, false, 1.0D);
        double drop = before - core.stability();
        assertTrue(drop > 0.1D, "缺水时稳定值应快速下跌");
        assertFalse(core.halted(), "单 tick 还不该直接停机");

        boolean halted = false;
        for (int i = 0; i < 1_000 && !halted; i++) {
            core.step(true, true, false, 1.0D);
            halted = core.halted();
        }
        assertTrue(halted, "持续缺水应在数十秒内停机");
    }

    @Test
    void neutronByproductAppearsOnlyAfterIgnition() {
        MuonFusionCore core = new MuonFusionCore();
        assertEquals(0.0D, core.neutronRate(), 1.0E-9D);
        for (int i = 0; i < 200 && !core.ignited(); i++) {
            core.step(true, true, true, true, 1.0D);
        }
        assertTrue(core.ignited());
        assertTrue(core.neutronRate() > 0.0D, "点火后应产生中子副产");
    }

    @Test
    void missingMuonDrainsStabilityFastAndHalts() {
        MuonFusionCore core = new MuonFusionCore();
        for (int i = 0; i < 200 && !core.ignited(); i++) {
            core.step(true, true, true, true, 1.0D);
        }
        assertTrue(core.ignited());
        double before = core.stability();
        core.step(true, true, true, false, 1.0D);
        double drop = before - core.stability();
        assertTrue(drop > 0.2D, "缺缪子时稳定值应快速下跌");

        boolean halted = false;
        for (int i = 0; i < 600 && !halted; i++) {
            core.step(true, true, true, false, 1.0D);
            halted = core.halted();
        }
        assertTrue(halted, "持续缺缪子应在十余秒内停机");
    }

    @Test
    void stabilityStopRequiresManualCoreReset() {
        MuonFusionCore core = new MuonFusionCore();
        core.restore(true, false, MuonFusionCore.IGNITION_STEPS, MuonFusionCore.IGNITION_TEMP_C,
                10.0D, 0.0D, 0.0D);
        core.step(true, true, true, 1.0D);
        assertTrue(core.halted());
        assertFalse(core.ignited());
        for (int i = 0; i < 300; i++) {
            core.step(true, true, true, 1.0D);
        }
        assertFalse(core.ignited(), "停机锁未解开前不应自动重新点火");
        core.clearHalt();
        for (int i = 0; i < 300 && !core.ignited(); i++) {
            core.step(true, true, true, 1.0D);
        }
        assertTrue(core.ignited(), "取出重放芯核后应能重新点火");
    }

    @Test
    void steamReachesUltraSupercritical() {
        MuonFusionCore core = new MuonFusionCore();
        for (int i = 0; i < 200 && !core.ignited(); i++) {
            core.step(true, true, true, 1.0D);
        }
        SteamState steam = new SteamState(core.steamAmount(), core.steamTemperatureC(), core.steamPressure());
        assertEquals(SteamLoop.SteamGrade.ULTRA_SUPERCRITICAL, steam.grade());
        assertTrue(core.streamRate() > 0.0D);
    }

    @Test
    void steamTemperatureStaysInRealisticRange() {
        MuonFusionCore core = new MuonFusionCore();
        for (int i = 0; i < 200 && !core.ignited(); i++) {
            core.step(true, true, true, 1.0D);
        }
        assertTrue(core.steamTemperatureC() > 600.0D, "点火后要能撑住过超临界的温度门槛");
        assertTrue(core.steamTemperatureC() <= MuonFusionCore.STEAM_MAX_C, "工质温度不得越过 700℃");

        // 等离子体烧到 6000℃ 也一样：蒸汽温度封顶，不会跟着变成几千度
        core.restore(true, MuonFusionCore.IGNITION_STEPS, MuonFusionCore.SELF_HEAT_MAX_C, 100.0D,
                core.damage(), 0.0D);
        assertEquals(MuonFusionCore.STEAM_MAX_C, core.steamTemperatureC(), 1.0E-9D);
        assertTrue(core.temperatureC() > 5000.0D);
    }

    @Test
    void overheatMeltsDownAndShutsDown() {
        MuonFusionCore core = new MuonFusionCore();
        boolean melted = false;
        for (int i = 0; i < 400 && !melted; i++) {
            core.restore(true, MuonFusionCore.IGNITION_STEPS,
                    MuonFusionCore.SAFE_TEMP_C + 500.0D, 100.0D, core.damage(), 0.0D);
            melted = core.step(true, true, true, 1.0D).meltedDown();
        }
        assertTrue(melted);
        assertFalse(core.ignited());
    }

    @Test
    void coreSpecConstrainsMuonStream() {
        assertEquals(StreamType.MUON, MuonCoreSpec.DEUTERIUM_TRITIUM.stream());
    }
}
