package dev.modmind.stellar_evolution.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 选冶线倍率口径测试（纯数值层，不需要 Minecraft 环境）。
 */
class OreBalanceTest {
    @Test
    @DisplayName("有粗矿：粉线 6× / 直还原 6× / 酸浸 12×")
    void rawOreMultipliers() {
        assertEquals(6, OreBalance.crushedIngotsPerOre());
        assertEquals(6, OreBalance.directIngotsPerOre());
        assertEquals(12, OreBalance.acidIngotsPerOre());
    }

    @Test
    @DisplayName("无粗矿：2 砂 → 下界金矿 / 残骸 直出 2、酸浸 4；非金属酸浸 8")
    void orelessMultipliers() {
        assertEquals(2, OreBalance.sandPerOrelessOre());
        assertEquals(2, OreBalance.directProductsPerOrelessOre());
        assertEquals(4, OreBalance.acidProductsPerOrelessOre());
        assertEquals(8, OreBalance.acidProductsPerNonMetalOre());
    }

    @Test
    @DisplayName("铀：完整线 6 黄饼 / 基础线 3 黄饼")
    void uraniumYellowcake() {
        assertEquals(6, OreBalance.yellowcakePerUraniumOreFull());
        assertEquals(3, OreBalance.yellowcakePerUraniumOreBasic());
    }

    @Test
    @DisplayName("浆 / 砂配比：3000 浆 = 2 砂，1 粗矿 = 2 砂；每砂另耗 750 过氧化氢")
    void slurryAndSandRatio() {
        assertEquals(2, OreBalance.SLURRY_PER_RAW / OreBalance.SLURRY_PER_SAND);
        assertEquals(2, OreBalance.SAND_PER_RAW);
        assertEquals(750, OreBalance.HYDROGEN_PEROXIDE_PER_SAND);
        assertEquals(250, OreBalance.SUGAR_LIQUOR_PER_RAW);
        assertEquals(1000, OreBalance.WATER_PER_RAW);
        assertEquals(1, OreBalance.GRAVEL_PER_RAW);
    }

    @Test
    @DisplayName("B0 两段浸出：双副产 2 渣 → 1+1，单副产 1 渣 → 1")
    void secondStageLeaching() {
        assertEquals(2, OreBalance.SECOND_STAGE_RESIDUE_PER_PRODUCT);
        assertEquals(1, OreBalance.SECOND_STAGE_SINGLE_RESIDUE);
    }
}
