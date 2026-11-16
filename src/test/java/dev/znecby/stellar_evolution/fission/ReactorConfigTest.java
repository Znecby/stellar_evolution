package dev.znecby.stellar_evolution.fission;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「<strong>燃料 × 慢化剂 → 堆型</strong>」配置合成的口径测试（纯数值层，不需要 Minecraft 环境）。
 *
 * <p>覆盖《核裂变线预规划蓝图》§3.4 / §13.3 F3：铀棒吃慢化剂（PWR / RBMK / CANDU / 研究堆），
 * 快谱（MOX → FBR）与钍基不受慢化剂影响。
 */
class ReactorConfigTest {
    @Test
    @DisplayName("铀棒：无慢化剂 → PWR；石墨 → RBMK；重水 → CANDU；研究堆通道 → 研究堆")
    void uraniumResolvesByModerator() {
        assertSame(ReactorConfig.PWR,
                ReactorConfig.resolve(ReactorConfig.PWR, ReactorConfig.Moderator.NONE));
        assertSame(ReactorConfig.RBMK,
                ReactorConfig.resolve(ReactorConfig.PWR, ReactorConfig.Moderator.GRAPHITE));
        assertSame(ReactorConfig.CANDU,
                ReactorConfig.resolve(ReactorConfig.PWR, ReactorConfig.Moderator.HEAVY_WATER));
        assertSame(ReactorConfig.RESEARCH,
                ReactorConfig.resolve(ReactorConfig.PWR, ReactorConfig.Moderator.RESEARCH));
    }

    @Test
    @DisplayName("快谱 / 钍基不吃慢化剂：MOX 与钍基在任何慢化剂下都不变")
    void fastAndThoriumIgnoreModerator() {
        for (ReactorConfig.Moderator moderator : ReactorConfig.Moderator.values()) {
            assertSame(ReactorConfig.FBR, ReactorConfig.resolve(ReactorConfig.FBR, moderator));
            assertSame(ReactorConfig.THORIUM, ReactorConfig.resolve(ReactorConfig.THORIUM, moderator));
        }
    }

    @Test
    @DisplayName("非燃料（base = null）→ null")
    void nullBaseStaysNull() {
        assertNull(ReactorConfig.resolve(null, ReactorConfig.Moderator.GRAPHITE));
    }

    @Test
    @DisplayName("慢化剂优先级：研究堆 > 石墨 > 重水（声明序）")
    void moderatorPriority() {
        assertTrue(ReactorConfig.Moderator.RESEARCH.ordinal() > ReactorConfig.Moderator.GRAPHITE.ordinal());
        assertTrue(ReactorConfig.Moderator.GRAPHITE.ordinal() > ReactorConfig.Moderator.HEAVY_WATER.ordinal());
        assertTrue(ReactorConfig.Moderator.HEAVY_WATER.ordinal() > ReactorConfig.Moderator.NONE.ordinal());
    }

    @Test
    @DisplayName("byId 反查与预设 id 一致；未知 id → null")
    void byIdLookup() {
        assertSame(ReactorConfig.PWR, ReactorConfig.byId("pwr"));
        assertSame(ReactorConfig.RBMK, ReactorConfig.byId("rbmk"));
        assertSame(ReactorConfig.CANDU, ReactorConfig.byId("candu"));
        assertSame(ReactorConfig.RESEARCH, ReactorConfig.byId("research"));
        assertNull(ReactorConfig.byId("nope"));
    }
}
