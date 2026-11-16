package dev.znecby.stellar_evolution.material;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * <strong>合金配料元素粉</strong>（2026-11-01）：铬 / 镍 / 铌 / 钛 / 铝 / 钼 / 锆 / 石墨 / 铍。
 *
 * <p>只为热室合金配方提供「对应粉」这一层，<strong>不做</strong>锭 / 板 / 杆等后续型材；
 * 来源由 B0 两段浸出 / B2 独居石 / B3 铝土矿提供。
 * 配方里用到的钴粉沿用裂变线的 {@code FissionMaterials.COBALT_DUST}，钢粉在 {@link IntermediateItems}。
 * 锰粉已于 2026-11-03 删除（不锈钢简化后无用途）。
 *
 * <p>贴图由 {@code tools/gen_ore_materials.ps1} 生成；双语名在 lang 里。
 */
public final class ElementDusts {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 铬粉：不锈钢 / 高速钢 / 镍基高温合金的配料。 */
    public static final DeferredItem<Item> CHROMIUM_DUST = register("chromium_dust");
    /** 镍粉：不锈钢 / 镍基高温合金的配料。 */
    public static final DeferredItem<Item> NICKEL_DUST = register("nickel_dust");
    /** 铌粉：NbTi 超导合金的配料。 */
    public static final DeferredItem<Item> NIOBIUM_DUST = register("niobium_dust");
    /** 钛粉：钛合金 / NbTi 超导合金的配料。 */
    public static final DeferredItem<Item> TITANIUM_DUST = register("titanium_dust");
    /** 铝粉：钛合金 / 铝镁合金的配料。 */
    public static final DeferredItem<Item> ALUMINUM_DUST = register("aluminum_dust");
    /** 钼粉：镍基高温合金的配料。 */
    public static final DeferredItem<Item> MOLYBDENUM_DUST = register("molybdenum_dust");
    /** 核级锆粉：锆合金包壳的配料。 */
    public static final DeferredItem<Item> ZIRCONIUM_DUST = register("zirconium_dust");
    /** 石墨粉：钻石渣二段浸出的副产，可压石墨棒。 */
    public static final DeferredItem<Item> GRAPHITE_DUST = register("graphite_dust");
    /** 铍粉：绿宝石渣二段浸出的副产，可做中子压力罐。 */
    public static final DeferredItem<Item> BERYLLIUM_DUST = register("beryllium_dust");

    /** 全部元素粉（创造页「基础材料」按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            CHROMIUM_DUST, NICKEL_DUST, NIOBIUM_DUST,
            TITANIUM_DUST, ALUMINUM_DUST, MOLYBDENUM_DUST, ZIRCONIUM_DUST,
            GRAPHITE_DUST, BERYLLIUM_DUST);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ElementDusts() {}
}
