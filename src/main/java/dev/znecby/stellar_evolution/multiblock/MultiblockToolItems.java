package dev.znecby.stellar_evolution.multiblock;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 多方块通用物品（一键搭建工具）。 */
public final class MultiblockToolItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredItem<MultiblockBuilderItem> BUILDER =
            ITEMS.register("multiblock_builder", key -> new MultiblockBuilderItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private MultiblockToolItems() {}
}
