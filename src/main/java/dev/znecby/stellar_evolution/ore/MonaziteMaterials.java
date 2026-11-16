package dev.znecby.stellar_evolution.ore;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 独居石线（B2）的物品。
 *
 * <p>一支矿三条线：锆英砂 → 锆 / 铪；独居石精矿 → 稀土 / 钍；磷酸盐 → 骨粉。
 * 钛线已迁出到 {@link TitaniumMaterials}；核级锆粉复用 {@code ElementDusts.ZIRCONIUM_DUST}，不另开 id。
 */
public final class MonaziteMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 粗独居石：独居石矿的破碎产物。 */
    public static final DeferredItem<Item> RAW_MONAZITE = register("raw_monazite");
    /** 独居石重砂：粗独居石粉碎产物。 */
    public static final DeferredItem<Item> MONAZITE_SAND = register("monazite_sand");
    /** 锆英砂：锆 / 铪分支。 */
    public static final DeferredItem<Item> ZIRCON_SAND = register("zircon_sand");
    /** 独居石精矿：稀土 / 钍分支。 */
    public static final DeferredItem<Item> MONAZITE_CONCENTRATE = register("monazite_concentrate");
    /** 斜长岩：冰岩矿的伴生产物；预留（后续用于铝 / 硅线，当前无下游）。 */
    public static final DeferredItem<Item> ANORTHOSITE = register("anorthosite");
    /** 辉钛晶体：辉钛矿的直接掉落物；预留（后续走高能物理线，当前无下游）；带原版附魔光效。 */
    public static final DeferredItem<Item> GLOW_TITANIUM_CRYSTAL = ITEMS.register("glow_titanium_crystal",
            key -> new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    /** 磷酸盐：骨粉 / 生化营养。 */
    public static final DeferredItem<Item> PHOSPHATE = register("phosphate");
    /** 钍精矿：钍粉原料。 */
    public static final DeferredItem<Item> THORIUM_CONCENTRATE = register("thorium_concentrate");
    /** 稀土氯化物：稀土分离原料。 */
    public static final DeferredItem<Item> RARE_EARTH_CHLORIDE = register("rare_earth_chloride");
    /** 粗锆粉（含铪）：除铪前体。 */
    public static final DeferredItem<Item> CRUDE_ZIRCONIUM_POWDER = register("crude_zirconium_powder");
    /** 铪粉：除铪副产。 */
    public static final DeferredItem<Item> HAFNIUM_POWDER = register("hafnium_powder");
    /** 锆合金粉：核级锆粉 + 锡 / 铁 / 铬。 */
    public static final DeferredItem<Item> ZIRCALOY_POWDER = register("zircaloy_powder");
    /** 锆合金锭：锆合金粉压制。 */
    public static final DeferredItem<Item> ZIRCALOY_INGOT = register("zircaloy_ingot");
    /** 锡粉：钨渣副产；锆合金 / 铌。 */
    public static final DeferredItem<Item> TIN_DUST = register("tin_dust");
    /** 钕粉：稀土磁体。 */
    public static final DeferredItem<Item> NEODYMIUM_DUST = register("neodymium_dust");
    /** 钐粉：中子辐照 → 钐-149。 */
    public static final DeferredItem<Item> SAMARIUM_DUST = register("samarium_dust");
    /** 钆粉：辐射屏蔽。 */
    public static final DeferredItem<Item> GADOLINIUM_DUST = register("gadolinium_dust");
    /** 铈粉：玻璃 / 抛光。 */
    public static final DeferredItem<Item> CERIUM_DUST = register("cerium_dust");

    public static final List<DeferredItem<Item>> ALL = List.of(
            RAW_MONAZITE,
            MONAZITE_SAND, ZIRCON_SAND, MONAZITE_CONCENTRATE,
            ANORTHOSITE, GLOW_TITANIUM_CRYSTAL,
            PHOSPHATE, THORIUM_CONCENTRATE, RARE_EARTH_CHLORIDE,
            CRUDE_ZIRCONIUM_POWDER, HAFNIUM_POWDER, ZIRCALOY_POWDER, ZIRCALOY_INGOT,
            TIN_DUST, NEODYMIUM_DUST, SAMARIUM_DUST, GADOLINIUM_DUST, CERIUM_DUST);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private MonaziteMaterials() {}
}
