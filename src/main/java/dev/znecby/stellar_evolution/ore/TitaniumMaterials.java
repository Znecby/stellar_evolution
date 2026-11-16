package dev.znecby.stellar_evolution.ore;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 钛线（2026-11-12 从独居石线分离）。
 *
 * <p>入口：月球钛铁矿 / 深层钛铁矿 → 粗钛铁矿 → 钛铁矿砂；铝土矿红泥保留少量钛铁矿砂保底。
 * 主链：钛铁矿砂 + 煤 → 钛铁粉 →（硫酸）→ 金红石粉 + 铁粉 →（煤 + 氯气）→ 四氯化钛 → 钛粉。
 * 钛铁粉为纯中间体，不另做合金。
 */
public final class TitaniumMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 粗钛铁矿：月球钛铁矿 / 深层钛铁矿的直接掉落物。 */
    public static final DeferredItem<Item> RAW_ILMENITE = register("raw_ilmenite");
    /** 钛铁矿砂：粗钛铁矿粉碎产物，还原成钛铁粉。 */
    public static final DeferredItem<Item> ILMENITE_SAND = register("ilmenite_sand");
    /** 钛铁粉：钛铁矿砂 + 煤的还原产物，酸浸出金红石粉。 */
    public static final DeferredItem<Item> FERROTITANIUM_POWDER = register("ferrotitanium_powder");
    /** 金红石粉：高纯二氧化钛，氯化制四氯化钛。 */
    public static final DeferredItem<Item> RUTILE_POWDER = register("rutile_powder");

    public static final List<DeferredItem<Item>> ALL = List.of(
            RAW_ILMENITE, ILMENITE_SAND, FERROTITANIUM_POWDER, RUTILE_POWDER);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private TitaniumMaterials() {}
}
