package dev.znecby.stellar_evolution.thermal;

/**
 * 热室配方「具体度」比较：<strong>输入种类多的更具体</strong>；种类相同再比<strong>总件数</strong>。
 *
 * <p>单独成类是为了能在纯 JUnit 里回归：{@link HotChamberRecipes} 的静态初始化会构造物品栈，
 * 必须有启动好的注册表；排序规则本身不依赖任何 MC 类型。
 */
public final class HotChamberRanking {
    /**
     * {@code (types, items)} 是否比当前最优 {@code (bestTypes, bestItems)} 更具体。
     * <p>完全并列返回 {@code false} —— 调用方据此保留表序在前的配方。
     */
    public static boolean moreSpecific(int types, int items, int bestTypes, int bestItems) {
        if (types != bestTypes) {
            return types > bestTypes;
        }
        return items > bestItems;
    }

    private HotChamberRanking() {}
}
