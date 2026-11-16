package dev.modmind.stellar_evolution.bio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.modmind.stellar_evolution.bio.FermentationKinetics.Band;
import dev.modmind.stellar_evolution.bio.FermentationKinetics.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 发酵动力学账本的口径测试（纯数值层，不需要 Minecraft 环境）。
 * <p>
 * 逐条对齐《生化线》§5.2：温度带分档、速率在中温带中心最高且对称、周期随速率缩放、主产 / 副产收率。
 */
class FermentationKineticsTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("温度带分档：< 8 厌氧、< 20 中温、≥ 20 高温")
    void bandBoundaries() {
        assertEquals(Band.ANAEROBIC, FermentationKinetics.bandFor(7.99D));
        assertEquals(Band.MESOPHILIC, FermentationKinetics.bandFor(8.0D));
        assertEquals(Band.MESOPHILIC, FermentationKinetics.bandFor(19.99D));
        assertEquals(Band.THERMOPHILIC, FermentationKinetics.bandFor(20.0D));
    }

    @Test
    @DisplayName("热流率为 0（未通电）或负数，都算厌氧带")
    void zeroAndNegativeGoAnaerobic() {
        assertEquals(Band.ANAEROBIC, FermentationKinetics.bandFor(0.0D));
        assertEquals(Band.ANAEROBIC, FermentationKinetics.bandFor(-5.0D));
    }

    @Test
    @DisplayName("速率在中温带中心最高，恰好 1.0，且不超过 1.0")
    void ratePeaksAtCenter() {
        assertEquals(1.0D, FermentationKinetics.rateMultiplier(FermentationKinetics.MESOPHILIC_CENTER), EPSILON);
        assertTrue(FermentationKinetics.rateMultiplier(30.0D) <= 1.0D);
        assertTrue(FermentationKinetics.rateMultiplier(0.0D) <= 1.0D);
    }

    @Test
    @DisplayName("速率关于中心对称：偏离同样的量，倍率相同")
    void rateIsSymmetricAroundCenter() {
        assertEquals(FermentationKinetics.rateMultiplier(10.0D),
                FermentationKinetics.rateMultiplier(18.0D), EPSILON);
        assertEquals(FermentationKinetics.rateMultiplier(2.0D),
                FermentationKinetics.rateMultiplier(26.0D), EPSILON);
    }

    @Test
    @DisplayName("越偏离中心速率越低，但不会低于下限")
    void rateHasFloor() {
        double near = FermentationKinetics.rateMultiplier(13.0D);
        double far = FermentationKinetics.rateMultiplier(2.0D);
        assertTrue(far < near);
        assertEquals(FermentationKinetics.MIN_RATE, FermentationKinetics.rateMultiplier(100.0D), EPSILON);
    }

    @Test
    @DisplayName("周期：中心处 = 基准刻数，越偏离越慢（更长）")
    void cycleScalesWithRate() {
        assertEquals(200.0D, FermentationKinetics.cycleTicks(200.0D, FermentationKinetics.MESOPHILIC_CENTER), EPSILON);
        assertTrue(FermentationKinetics.cycleTicks(200.0D, 0.0D) > 200.0D);
        assertTrue(FermentationKinetics.cycleTicks(200.0D, 30.0D) > 200.0D);
    }

    @Test
    @DisplayName("周期与速率互为倒数关系")
    void cycleIsInverseOfRate() {
        double rate = FermentationKinetics.rateMultiplier(11.0D);
        assertEquals(200.0D / rate, FermentationKinetics.cycleTicks(200.0D, 11.0D), EPSILON);
    }

    @Test
    @DisplayName("主产物：厌氧→沼气、中温→乙醇、高温→醋酸")
    void primaryProducts() {
        assertEquals(Product.BIOGAS, FermentationKinetics.primaryProduct(Band.ANAEROBIC));
        assertEquals(Product.ETHANOL, FermentationKinetics.primaryProduct(Band.MESOPHILIC));
        assertEquals(Product.ACETIC_ACID, FermentationKinetics.primaryProduct(Band.THERMOPHILIC));
    }

    @Test
    @DisplayName("收率：主产 1.0、相邻带 0.35、最远带 0.1")
    void yieldsPerBand() {
        assertEquals(1.0D, FermentationKinetics.yieldOf(Band.ANAEROBIC, Product.BIOGAS), EPSILON);
        assertEquals(0.35D, FermentationKinetics.yieldOf(Band.ANAEROBIC, Product.ETHANOL), EPSILON);
        assertEquals(0.1D, FermentationKinetics.yieldOf(Band.ANAEROBIC, Product.ACETIC_ACID), EPSILON);

        assertEquals(1.0D, FermentationKinetics.yieldOf(Band.MESOPHILIC, Product.ETHANOL), EPSILON);
        assertEquals(0.35D, FermentationKinetics.yieldOf(Band.MESOPHILIC, Product.BIOGAS), EPSILON);
        assertEquals(0.35D, FermentationKinetics.yieldOf(Band.MESOPHILIC, Product.ACETIC_ACID), EPSILON);

        assertEquals(1.0D, FermentationKinetics.yieldOf(Band.THERMOPHILIC, Product.ACETIC_ACID), EPSILON);
        assertEquals(0.1D, FermentationKinetics.yieldOf(Band.THERMOPHILIC, Product.BIOGAS), EPSILON);
    }

    @Test
    @DisplayName("产量取整：主产恰好等于基准量，副产按收率缩放")
    void amounts() {
        assertEquals(1000, FermentationKinetics.amount(1000, Band.MESOPHILIC, Product.ETHANOL));
        assertEquals(350, FermentationKinetics.amount(1000, Band.MESOPHILIC, Product.BIOGAS));
        assertEquals(100, FermentationKinetics.amount(1000, Band.ANAEROBIC, Product.ACETIC_ACID));
    }
}
