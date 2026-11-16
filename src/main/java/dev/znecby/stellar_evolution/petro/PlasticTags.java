package dev.znecby.stellar_evolution.petro;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 塑料<strong>等级标签</strong>（2026-11-08）：只有一套，严格按等级命名，不掺功能名。
 *
 * <p>下游<strong>主要消耗</strong>一律用等级标签；<strong>特殊材料</strong>（放射性接枝塑料 /
 * 手性聚合物 / 离子径迹膜）不进标签，仍按具体物品指定。
 *
 * <p>标签文件在 {@code data/stellar_evolution/tags/item/plastic/t*.json}。
 *
 * <p><strong>分级口径</strong>（每级 2~3 种，一易一快；每级只需做一个成员，但四级最终都要做）：
 * <ul>
 *   <li>{@link #T1} 通用：生物塑料颗粒（易获取 / 难量产）+ 电木（难获取 / 易量产）；</li>
 *   <li>{@link #T2} 结构 / 密封：硅橡胶（易 / 难）+ 尼龙、有机玻璃（难 / 易）；</li>
 *   <li>{@link #T3} 橡胶：氯丁橡胶（易 / 难）+ 丁苯、丁腈（难 / 易）；</li>
 *   <li>{@link #T4} 特种：PTFE（难获取 / 易量产）+ 交联塑料（易获取 / 难量产，束流）。</li>
 * </ul>
 */
public final class PlasticTags {
    /** 一级塑料：生物塑料颗粒 / 电木。 */
    public static final TagKey<Item> T1 = tag("plastic/t1");
    /** 二级塑料：硅橡胶 / 尼龙 / 有机玻璃。 */
    public static final TagKey<Item> T2 = tag("plastic/t2");
    /** 三级塑料：氯丁橡胶 / 丁苯橡胶 / 丁腈橡胶。 */
    public static final TagKey<Item> T3 = tag("plastic/t3");
    /** 四级塑料：PTFE / 交联塑料。 */
    public static final TagKey<Item> T4 = tag("plastic/t4");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, StellarEvolution.id(path));
    }

    private PlasticTags() {}
}
