package dev.znecby.stellar_evolution.wood;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 木化工线的<strong>物品类物质</strong>（2026-11-11 批次 1）。
 *
 * <p>贴图由 {@code tools/gen_wood_materials.ps1} 生成（材料模板上色）；双语名在 lang 里。
 */
public final class WoodMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 松香：松木蒸汽蒸馏副产，助焊剂原料。 */
    public static final DeferredItem<Item> ROSIN = register("rosin");
    /** 醋酸纤维：纤维素（木屑粉）+ 醋酸；预留（未来薄膜 / 塑料，当前无下游）。 */
    public static final DeferredItem<Item> CELLULOSE_ACETATE = register("cellulose_acetate");
    /** 硝化纤维：纤维素（木屑粉）+ 硝酸；预留（未来固体推进剂 / 发射药，当前无下游）。 */
    public static final DeferredItem<Item> NITROCELLULOSE = register("nitrocellulose");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            ROSIN, CELLULOSE_ACETATE, NITROCELLULOSE);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private WoodMaterials() {}
}
