package dev.modmind.stellar_evolution.ore;

import dev.modmind.stellar_evolution.StellarEvolution;
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
 * 独居石矿（B2）：矿石 / 深板岩矿石两形态，世界生成。
 */
public final class MonaziteBlocks {
    public static final String MONAZITE_ORE = "monazite_ore";
    public static final String DEEPSLATE_MONAZITE_ORE = "deepslate_monazite_ore";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredBlock<Block> MONAZITE_ORE_BLOCK =
            BLOCKS.register(MONAZITE_ORE, () -> new Block(oreProperties(MONAZITE_ORE, MapColor.STONE)));
    public static final DeferredBlock<Block> DEEPSLATE_MONAZITE_ORE_BLOCK =
            BLOCKS.register(DEEPSLATE_MONAZITE_ORE, () -> new Block(oreProperties(DEEPSLATE_MONAZITE_ORE, MapColor.DEEPSLATE)));

    public static final DeferredItem<BlockItem> MONAZITE_ORE_ITEM = item(MONAZITE_ORE, MONAZITE_ORE_BLOCK);
    public static final DeferredItem<BlockItem> DEEPSLATE_MONAZITE_ORE_ITEM =
            item(DEEPSLATE_MONAZITE_ORE, DEEPSLATE_MONAZITE_ORE_BLOCK);

    private static BlockBehaviour.Properties oreProperties(String name, MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(3.0F, 3.0F)
                .requiresCorrectToolForDrops()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name)));
    }

    private static DeferredItem<BlockItem> item(String name, DeferredBlock<? extends Block> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private MonaziteBlocks() {}
}
