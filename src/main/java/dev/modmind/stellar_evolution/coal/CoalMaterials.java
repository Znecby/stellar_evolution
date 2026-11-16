package dev.modmind.stellar_evolution.coal;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 煤化工线（C 线）的<strong>物品类物质</strong>。
 *
 * <p>既有两件（焦炭 / 尿素）+ 2026-11-11 批次 1 新增五件：
 * <ul>
 *   <li>{@code coke} 焦炭 —— 干馏产物，进焦炭气化 / 回炉气化；</li>
 *   <li>{@code urea} 尿素 —— 合成塔产物，进机械土壤肥料点数池；</li>
 *   <li>{@code calcium_carbide} 电石 —— 乙炔原料；</li>
 *   <li>{@code calcium_hydroxide} 氢氧化钙 —— 电石水解副产；</li>
 *   <li>{@code activated_carbon} 活性炭 —— 净化合成气；</li>
 *   <li>{@code needle_coke} 针状焦 —— 石墨电极前驱；</li>
 *   <li>{@code graphite_electrode} 石墨电极 —— 裂变堆石墨。</li>
 * </ul>
 *
 * <p>贴图由 {@code tools/gen_coal_materials.ps1} 生成（材料模板上色）；双语名在 lang 里。
 */
public final class CoalMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 焦炭：干馏产物，气化原料 / 碳素。 */
    public static final DeferredItem<Item> COKE = register("coke");
    /** 尿素：氨水 + 二氧化碳的产物，回田肥料。 */
    public static final DeferredItem<Item> UREA = register("urea");
    /** 电石：焦炭 + 方解石热室电炉，乙炔原料。 */
    public static final DeferredItem<Item> CALCIUM_CARBIDE = register("calcium_carbide");
    /** 氢氧化钙：电石水解副产（建材 / 中和剂）。 */
    public static final DeferredItem<Item> CALCIUM_HYDROXIDE = register("calcium_hydroxide");
    /** 活性炭：焦炭 / 木质素活化，净化合成气。 */
    public static final DeferredItem<Item> ACTIVATED_CARBON = register("activated_carbon");
    /** 针状焦：重油焦化，石墨电极前驱。 */
    public static final DeferredItem<Item> NEEDLE_COKE = register("needle_coke");
    /** 石墨电极：针状焦石墨化，裂变堆石墨。 */
    public static final DeferredItem<Item> GRAPHITE_ELECTRODE = register("graphite_electrode");
    /** 聚氯乙烯（PVC）：氯乙烯聚合，T2 塑料标签。 */
    public static final DeferredItem<Item> PVC = register("pvc");
    /** 煤炭粉：煤炭粉碎，煤水浆原料（煤液化前置）。 */
    public static final DeferredItem<Item> COAL_DUST = register("coal_dust");
    /** 木炭粉：木炭粉碎，活性炭 / 煤化工辅料。 */
    public static final DeferredItem<Item> CHARCOAL_DUST = register("charcoal_dust");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            COKE, UREA, CALCIUM_CARBIDE, CALCIUM_HYDROXIDE,
            ACTIVATED_CARBON, NEEDLE_COKE, GRAPHITE_ELECTRODE, PVC,
            COAL_DUST, CHARCOAL_DUST);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private CoalMaterials() {}
}
