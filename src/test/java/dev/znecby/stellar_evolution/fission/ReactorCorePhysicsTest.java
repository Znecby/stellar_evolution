package dev.znecby.stellar_evolution.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 堆芯数值内核的口径测试（纯数值层，不需要 Minecraft 环境）。
 *
 * <p>覆盖《核裂变线预规划蓝图》§13.1 要求的四类典型场景：<strong>自稳 / 失控 / 熄火 / 尖峰</strong>，
 * 外加同位素谱（材料轴）。
 */
class ReactorCorePhysicsTest {
    private static final double EPSILON = 1.0E-9D;

    private static double runFor(ReactorCorePhysics core, int steps) {
        double temp = core.coreTemp();
        for (int i = 0; i < steps; i++) {
            temp = core.step(1.0D).coreTemp();
        }
        return temp;
    }

    @Test
    @DisplayName("自稳（PWR 负空泡）：升温 → 反应性下降 → 温度收敛且不熔毁")
    void pwrIsSelfStabilising() {
        ReactorCorePhysics core = new ReactorCorePhysics(ReactorConfig.PWR);
        core.setControlRod(0.2D);
        double finalTemp = runFor(core, 400);

        assertFalse(core.meltedDown(), "PWR 不应熔毁");
        assertEquals(0.0D, core.damage(), EPSILON);
        assertTrue(finalTemp > ReactorCorePhysics.T_REF, "应高于冷态参考温");
        assertTrue(finalTemp < ReactorConfig.PWR.meltdownTemp(), "应稳定在熔毁阈值之下");
    }

    @Test
    @DisplayName("失控（RBMK 正空泡）：升温 → 反应性上升 → 正反馈 → 熔毁")
    void rbmkRunsAway() {
        ReactorCorePhysics core = new ReactorCorePhysics(ReactorConfig.RBMK);
        core.setControlRod(0.2D);
        runFor(core, 400);

        assertTrue(core.meltedDown(), "RBMK 正空泡应失控熔毁");
        assertTrue(core.coreTemp() > ReactorConfig.RBMK.meltdownTemp(), "堆温应超过熔毁阈值");
    }

    @Test
    @DisplayName("熄火：控制棒全插入 → 反应性 ≤ 0 → 功率 0，温度退回参考温")
    void fullInsertionShutsDown() {
        ReactorCorePhysics core = new ReactorCorePhysics(ReactorConfig.PWR);
        core.setControlRod(1.0D);

        ReactorCorePhysics.StepResult result = core.step(1.0D);
        assertTrue(result.reactivity() <= EPSILON, "全插入应无可产热反应性");
        assertEquals(0.0D, result.power(), EPSILON);

        double finalTemp = runFor(core, 200);
        assertEquals(ReactorCorePhysics.T_REF, finalTemp, EPSILON);
    }

    @Test
    @DisplayName("尖峰（RBMK 控制棒尖端效应）：深插入区抽棒的 Δ反应性 大于浅插入区")
    void rodTipEffectSpikesWhenWithdrawnFromDeep() {
        ReactorCorePhysics rbmk = new ReactorCorePhysics(ReactorConfig.RBMK);
        rbmk.setControlRod(0.98D);
        double deepLow = rbmk.reactivity();
        rbmk.setControlRod(0.90D);
        double deepHigh = rbmk.reactivity();
        double deepDelta = deepHigh - deepLow;

        rbmk.setControlRod(0.30D);
        double shallowLow = rbmk.reactivity();
        rbmk.setControlRod(0.22D);
        double shallowHigh = rbmk.reactivity();
        double shallowDelta = shallowHigh - shallowLow;

        assertTrue(deepDelta > shallowDelta + 0.01D,
                "深插入区抽棒的反应性增量应明显更大（尖端效应）");

        // 对照：普通堆（PWR）无尖端效应 → 抽棒增量与深度无关（线性）
        ReactorCorePhysics pwr = new ReactorCorePhysics(ReactorConfig.PWR);
        pwr.setControlRod(0.98D);
        double pwrDeepLow = pwr.reactivity();
        pwr.setControlRod(0.90D);
        double pwrDeepDelta = pwr.reactivity() - pwrDeepLow;

        assertTrue(deepDelta > pwrDeepDelta, "同一步抽棒，RBMK 比 PWR 更危险");
        assertEquals(0.08D, pwrDeepDelta, 1.0E-6D); // 0.10 − 0.02，线性
    }

    @Test
    @DisplayName("同位素谱（材料轴）：快谱比热谱产钚更多")
    void fastSpectrumBreedsMorePlutonium() {
        ReactorCorePhysics thermal = new ReactorCorePhysics(ReactorConfig.PWR);
        thermal.setControlRod(0.2D);
        runFor(thermal, 100);

        ReactorCorePhysics fast = new ReactorCorePhysics(ReactorConfig.FBR);
        fast.setControlRod(0.2D);
        runFor(fast, 100);

        double thermalRatio = thermal.isotopeProfile().plutonium() / thermal.isotopeProfile().fissionProducts();
        double fastRatio = fast.isotopeProfile().plutonium() / fast.isotopeProfile().fissionProducts();
        assertTrue(fastRatio > thermalRatio, "快谱应比热谱增殖出更多钚");
    }
}
