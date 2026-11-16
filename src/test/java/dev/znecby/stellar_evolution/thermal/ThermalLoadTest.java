package dev.znecby.stellar_evolution.thermal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 热压差账本的口径测试（纯数值层，不需要 Minecraft 环境）。
 * <p>
 * 逐条对齐需求：容量累加、热流率取最高、占用 = 基础热阻 × 热流率、超载停转、倍速比值制。
 */
class ThermalLoadTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("容量相加、热流率取最高")
    void capacitySummedAndFlowRateIsMax() {
        ThermalLoad load = new ThermalLoad();
        load.addGenerator(80.0D, 3.0D);
        load.addGenerator(120.0D, 8.0D);
        load.addGenerator(40.0D, 5.0D);

        assertEquals(240.0D, load.capacity(), EPSILON);
        assertEquals(8.0D, load.flowRate(), EPSILON);
    }

    @Test
    @DisplayName("占用 = 各消耗端基础热阻之和 × 网络热流率")
    void occupancyIsResistanceSumTimesNetworkFlowRate() {
        ThermalLoad load = new ThermalLoad();
        load.addGenerator(1000.0D, 8.0D);
        load.addConsumer(5.0D);
        load.addConsumer(10.0D);

        // (5 + 10) × 8 = 120 DP
        assertEquals(120.0D, load.occupancy(), EPSILON);
    }

    @Test
    @DisplayName("登记顺序无关：先登记消耗端也一样")
    void orderDoesNotMatter() {
        ThermalLoad consumerFirst = new ThermalLoad();
        consumerFirst.addConsumer(10.0D);
        consumerFirst.addGenerator(1000.0D, 8.0D);

        ThermalLoad generatorFirst = new ThermalLoad();
        generatorFirst.addGenerator(1000.0D, 8.0D);
        generatorFirst.addConsumer(10.0D);

        assertEquals(generatorFirst.occupancy(), consumerFirst.occupancy(), EPSILON);
    }

    @Test
    @DisplayName("占用超过容量即超载；刚好相等不算")
    void overloadedWhenOccupancyExceedsCapacity() {
        ThermalLoad exact = new ThermalLoad();
        exact.addGenerator(120.0D, 8.0D);
        exact.addConsumer(15.0D); // 15 × 8 = 120 = 容量
        assertFalse(exact.overloaded());
        assertEquals(1.0D, exact.loadRatio(), EPSILON);

        ThermalLoad over = new ThermalLoad();
        over.addGenerator(120.0D, 8.0D);
        over.addConsumer(15.1D);
        assertTrue(over.overloaded());
    }

    @Test
    @DisplayName("倍速 = 网络热流率 / 基准热流率（高于基准更快、低于更慢）")
    void speedIsFlowRateOverBaseline() {
        ThermalLoad load = new ThermalLoad();
        load.addGenerator(1000.0D, 12.0D);

        assertEquals(1.0D, load.speedFor(12.0D), EPSILON);
        assertEquals(2.0D, load.speedFor(6.0D), EPSILON);
        assertEquals(0.5D, load.speedFor(24.0D), EPSILON);
    }

    @Test
    @DisplayName("超载 → 倍速 0（全部消耗端停转）")
    void overloadedStopsConsumers() {
        ThermalLoad load = new ThermalLoad();
        load.addGenerator(10.0D, 8.0D);
        load.addConsumer(5.0D); // 40 DP > 10 DP

        assertTrue(load.overloaded());
        assertEquals(0.0D, load.speedFor(8.0D), EPSILON);
    }

    @Test
    @DisplayName("没有生成端 → 热流率 0 → 停转")
    void noGeneratorStopsConsumers() {
        ThermalLoad load = new ThermalLoad();
        load.addConsumer(5.0D);

        assertEquals(0.0D, load.flowRate(), EPSILON);
        assertEquals(0.0D, load.occupancy(), EPSILON);
        assertEquals(0.0D, load.speedFor(8.0D), EPSILON);
        assertFalse(load.overloaded());
        assertEquals(0.0D, load.loadRatio(), EPSILON);
    }

    @Test
    @DisplayName("容量为 0 但有消耗端 → 负载率视为无穷（无法满足）")
    void zeroCapacityWithConsumersIsInfiniteLoad() {
        ThermalLoad load = new ThermalLoad();
        load.addGenerator(0.0D, 8.0D);
        load.addConsumer(1.0D);

        assertTrue(Double.isInfinite(load.loadRatio()));
        assertTrue(load.overloaded());
    }

    @Test
    @DisplayName("非正数输入被忽略：负容量 / 负热阻 / 负热流率都不进账")
    void nonPositiveInputIgnored() {
        ThermalLoad load = new ThermalLoad();
        load.addGenerator(-100.0D, -5.0D);
        load.addConsumer(-3.0D);

        assertEquals(0.0D, load.capacity(), EPSILON);
        assertEquals(0.0D, load.flowRate(), EPSILON);
        assertEquals(0.0D, load.occupancy(), EPSILON);
    }

    @Test
    @DisplayName("空网络：全 0，倍速 0，不超载")
    void emptyNetwork() {
        ThermalLoad load = new ThermalLoad();

        assertEquals(0.0D, load.capacity(), EPSILON);
        assertEquals(0.0D, load.flowRate(), EPSILON);
        assertEquals(0.0D, load.occupancy(), EPSILON);
        assertEquals(0.0D, load.speedFor(8.0D), EPSILON);
        assertFalse(load.overloaded());
    }
}
