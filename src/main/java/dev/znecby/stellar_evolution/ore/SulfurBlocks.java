package dev.znecby.stellar_evolution.ore;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 下界硫磺矿：单方块形态，挖掉直接掉硫磺晶体（类似粗矿），晶体进破碎机出 3 硫粉。
 */
public final class SulfurBlocks {
    public static final String SULFUR_ORE = "sulfur_ore";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredBlock<Block> SULFUR_ORE_BLOCK =
            BLOCKS.register(SULFUR_ORE, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .strength(3.0F, 3.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(SULFUR_ORE)))));

    public static final DeferredItem<BlockItem> SULFUR_ORE_ITEM =
            ITEMS.register(SULFUR_ORE, key -> new BlockItem(SULFUR_ORE_BLOCK.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private SulfurBlocks() {}
}
