package dev.modmind.stellar_evolution.ore;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 铝土矿线（B3）的物品。
 *
 * <p>铝的成品只出 {@code ElementDusts.ALUMINUM_DUST}，不新增铝锭 / 铝板 / 铝合金；
 * 红泥回收铁 / 钛，镓 / 钪留到有 sink 再开。
 */
public final class BauxiteMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 粗铝土矿：铝土矿的破碎产物。 */
    public static final DeferredItem<Item> RAW_BAUXITE = register("raw_bauxite");
    /** 铝土矿粉：粗铝土矿粉碎产物。 */
    public static final DeferredItem<Item> BAUXITE_POWDER = register("bauxite_powder");
    /** 氧化铝：拜耳法产物，电解铝原料。 */
    public static final DeferredItem<Item> ALUMINA = register("alumina");
    /** 冰晶石：电解铝催化剂（少量消耗）。 */
    public static final DeferredItem<Item> CRYOLITE = register("cryolite");
    /** 红泥：拜耳法副产，回收铁 / 钛。 */
    public static final DeferredItem<Item> RED_MUD = register("red_mud");

    public static final List<DeferredItem<Item>> ALL = List.of(RAW_BAUXITE, BAUXITE_POWDER, ALUMINA, CRYOLITE, RED_MUD);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private BauxiteMaterials() {}
}
