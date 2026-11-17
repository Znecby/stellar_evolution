package dev.znecby.stellar_evolution.thermal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 热室配方命中排序的纯逻辑回归测试：<strong>输入种类数 → 输入总件数 → 表序</strong>。
 *
 * <p>规则见 {@link HotChamberRecipes#find}：只比较「更具体」；完全并列返回 false，
 * 让表序在前的配方胜出。这里直接调用 {@link HotChamberRanking}，不加载配方表。
 */
class HotChamberRankingTest {

    @Test
    void moreTypesAlwaysWinsEvenWithFewerItems() {
        // 3 种 × 1 件 比 1 种 × 64 件更具体
        assertTrue(HotChamberRanking.moreSpecific(3, 3, 1, 64));
        assertFalse(HotChamberRanking.moreSpecific(1, 64, 3, 3));
    }

    @Test
    void sameTypesThenMoreItemsWins() {
        assertTrue(HotChamberRanking.moreSpecific(2, 8, 2, 4));
        assertFalse(HotChamberRanking.moreSpecific(2, 4, 2, 8));
    }

    @Test
    void exactTieKeepsEarlierRecipe() {
        assertFalse(HotChamberRanking.moreSpecific(2, 4, 2, 4));
        assertFalse(HotChamberRanking.moreSpecific(0, 0, 0, 0));
    }

    @Test
    void tableOrderWinsAmongEqualCandidates() {
        // 表序：A(2 种/4 件) → B(3 种/3 件) → C(2 种/9 件) → D(3 种/3 件)
        int[][] table = {
                {2, 4},
                {3, 3},
                {2, 9},
                {3, 3},
        };
        assertEquals(1, bestIndex(table), "B 与 D 完全并列，应保留表序更靠前的 B");
    }

    @Test
    void laterCandidateOnlyWinsWhenStrictlyMoreSpecific() {
        int[][] table = {
                {1, 16},
                {2, 1},
                {2, 2},
        };
        assertEquals(2, bestIndex(table), "同种类时件数更多的 C 胜出");
    }

    /** 按 {@link HotChamberRecipes#find} 的同一套比较循环，返回被选中配方的表序下标。 */
    private static int bestIndex(int[][] table) {
        int best = -1;
        int bestTypes = -1;
        int bestItems = -1;
        for (int i = 0; i < table.length; i++) {
            int types = table[i][0];
            int items = table[i][1];
            if (!HotChamberRanking.moreSpecific(types, items, bestTypes, bestItems)) {
                continue;
            }
            best = i;
            bestTypes = types;
            bestItems = items;
        }
        return best;
    }
}
