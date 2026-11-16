package dev.znecby.stellar_evolution.hep;

import dev.znecby.stellar_evolution.fission.FissionFluids;
import dev.znecby.stellar_evolution.fission.FissionMaterials;
import dev.znecby.stellar_evolution.ore.OreMaterials;
import java.util.List;

/**
 * 回旋加速器的内置配方表（第一期只开 2 行；其余行随第二 / 三期解锁）。
 *
 * <p>行按<strong>差速需求从低到高</strong>排列：同一轮里先做便宜的行，剩余预算再给贵的行。
 * <p>时间口径：{@code 秒数 = 3.2 × 需求 ÷ 网络 HP}。以 2048 HP 为基准，115,200 ≈ 180 秒、57,600 ≈ 90 秒。
 */
public final class CyclotronRecipes {
    private static List<CyclotronRecipe> cache;

    /** 全部配方（懒加载：物品 / 流体必须在注册表可用之后才取）。 */
    public static List<CyclotronRecipe> all() {
        if (cache == null) {
            cache = List.of(
                    // 锂粉 → 氚：聚变燃料链的最小入口（2048 HP 下 180 秒）
                    new CyclotronRecipe("lithium_to_tritium",
                            FissionMaterials.LITHIUM_DUST.get(), 1, 115_200.0D,
                            List.of(),
                            List.of(new CyclotronRecipe.FluidOutput(
                                    FissionFluids.TRITIUM.source().get(), 1_000))),
                    // 铀粉 → 裂变碎片：高门槛的铀粉去路（2048 HP 下 90 秒，裂变后提速）
                    new CyclotronRecipe("uranium_to_fission_fragments",
                            OreMaterials.URANIUM_DUST.get(), 1, 57_600.0D,
                            List.of(
                                    new CyclotronRecipe.ItemOutput(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                                    new CyclotronRecipe.ItemOutput(FissionMaterials.LONG_LIVED_WASTE.get(), 1)),
                            List.of()),
                    // 铀粉 → 超重元素前体（第三期 E2：回旋最后一行，对撞机前体路线的来源）
                    new CyclotronRecipe("uranium_to_superheavy_precursor",
                            OreMaterials.URANIUM_DUST.get(), 1, 131_072.0D,
                            List.of(
                                    new CyclotronRecipe.ItemOutput(FissionMaterials.SUPERHEAVY_PRECURSOR.get(), 1)),
                            List.of())
            );
        }
        return cache;
    }

    private CyclotronRecipes() {}
}
