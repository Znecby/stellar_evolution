package dev.znecby.stellar_evolution.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 基岩线纯数值账本测试（不需要 Minecraft 环境）。
 */
class BedrockBalanceTest {
    @Test
    @DisplayName("元素清单：44 稳定元素 + 4 锕系，且全部有产出表")
    void coversAllElements() {
        assertEquals(44, BedrockBalance.STABLE_ELEMENTS.size());
        assertEquals(4, BedrockBalance.ACTINIDES.size());
        assertEquals(48, BedrockBalance.allElements().size());
        assertTrue(BedrockBalance.coversAllElements());
    }

    @Test
    @DisplayName("段位映射：S1–S4 + S5 覆盖全部元素，无重复")
    void stageMapping() {
        int mapped = BedrockBalance.S5_ELEMENTS.size();
        for (var elements : BedrockBalance.STAGE_ELEMENTS.values()) {
            mapped += elements.size();
        }
        assertEquals(48, mapped);
    }

    @Test
    @DisplayName("高性能层每矿总件数在 100–200 之间（收益高但不失控）")
    void totalYield() {
        int total = BedrockBalance.totalItemsPerOre();
        assertTrue(total >= 100, "total=" + total);
        assertTrue(total <= 200, "total=" + total);
    }

    @Test
    @DisplayName("溶剂：1 组 1000 mB，每批 200 mB ⇒ 5 批")
    void solventBatch() {
        assertEquals(1_000, BedrockBalance.SOLVENT_YIELD);
        assertEquals(200, BedrockBalance.SOLVENT_PER_BATCH);
        assertEquals(5, BedrockBalance.SOLVENT_BATCHES);
    }
}
