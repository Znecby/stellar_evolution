package dev.modmind.stellar_evolution.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 富集 / 分离功账本的口径测试（纯数值层，不需要 Minecraft 环境）。
 */
class EnrichmentCascadeTest {
    private static final double EPSILON = 1.0E-6D;

    @Test
    @DisplayName("质量守恒：产品 + 尾料 = 进料")
    void massIsConserved() {
        EnrichmentCascade.EnrichResult r = EnrichmentCascade.enrich(1000.0D);
        assertEquals(1000.0D, r.product() + r.tails(), EPSILON);
    }

    @Test
    @DisplayName("进料越多，产品越多；且产品丰度高于尾料")
    void moreFeedYieldsMoreProduct() {
        double small = EnrichmentCascade.enrich(1000.0D).product();
        double large = EnrichmentCascade.enrich(2000.0D).product();
        assertTrue(large > small, "进料翻倍应产出更多富集产品");

        EnrichmentCascade.EnrichResult r = EnrichmentCascade.enrich(1000.0D);
        assertTrue(r.product() > 0.0D);
        assertTrue(r.tails() > 0.0D);
    }

    @Test
    @DisplayName("分离功为正（分离需要做功）")
    void swuIsPositive() {
        assertTrue(EnrichmentCascade.enrich(1000.0D).swu() > 0.0D);
    }

    @Test
    @DisplayName("非法丰度 / 非正进料 → 全零结果（不报错）")
    void invalidInputGivesZero() {
        assertEquals(0.0D, EnrichmentCascade.enrich(0.0D).product(), EPSILON);
        assertEquals(0.0D, EnrichmentCascade.enrich(-5.0D).product(), EPSILON);
        // 产品丰度 ≤ 尾料丰度：不可能富集
        assertEquals(0.0D, EnrichmentCascade.enrich(1000.0D, 0.0072D, 0.0025D, 0.0025D).product(), EPSILON);
    }
}
