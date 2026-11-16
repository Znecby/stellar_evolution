package dev.modmind.stellar_evolution.space;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 航天线专用材料（2026-11-03）。
 *
 * <p><strong>稀土合金</strong>：独居石（重砂 / 精矿）+ 石脑油 + 汞（流体）在生化反应釜汞齐化 → 稀土合金锭，
 * 定位类似 HBM 的 Desh，只做锭 / 板 / 杆三种形态，用于质量投射轨道炮的结构与线圈骨架。
 * <p><strong>NbTi 超导合金</strong>：铌粉 + 钛粉 → NbTi 线，用于超导线圈方块。
 */
public final class SpaceMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 稀土合金锭：独居石 + 石脑油 + 汞（流体），生化反应釜合成。 */
    public static final DeferredItem<Item> RARE_EARTH_ALLOY_INGOT = register("rare_earth_alloy_ingot");
    public static final DeferredItem<Item> RARE_EARTH_ALLOY_PLATE = register("rare_earth_alloy_plate");
    public static final DeferredItem<Item> RARE_EARTH_ALLOY_ROD = register("rare_earth_alloy_rod");

    /** NbTi 超导线：铌 + 钛。 */
    public static final DeferredItem<Item> NB_TI_WIRE = register("nb_ti_wire");

    public static final List<DeferredItem<Item>> ALL = List.of(
            RARE_EARTH_ALLOY_INGOT, RARE_EARTH_ALLOY_PLATE, RARE_EARTH_ALLOY_ROD, NB_TI_WIRE);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private SpaceMaterials() {}
}
