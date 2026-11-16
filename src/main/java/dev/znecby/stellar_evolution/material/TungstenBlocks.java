package dev.znecby.stellar_evolution.material;

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
 * 钨的方块：钨矿石 / 深板岩钨矿石（世界生成，两形态）+ 钨块（9 锭存储块）。
 *
 * <p>贴图由 {@code tools/gen_tungsten_materials.ps1} 生成：矿石照原版铁矿「石底 + 成簇矿斑」，
 * 只把矿斑换成钨的冷灰蓝；钨块复用金属块模板。双语名在 lang；挖掘工具归 {@code mineable/pickaxe}。
 */
public final class TungstenBlocks {
    public static final String TUNGSTEN_ORE = "tungsten_ore";
    public static final String DEEPSLATE_TUNGSTEN_ORE = "deepslate_tungsten_ore";
    public static final String TUNGSTEN_BLOCK = "tungsten_block";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 钨矿石：浅层形态（世界生成）。 */
    public static final DeferredBlock<Block> TUNGSTEN_ORE_BLOCK =
            BLOCKS.register(TUNGSTEN_ORE, () -> new Block(oreProperties(TUNGSTEN_ORE, MapColor.STONE)));
    /** 深板岩钨矿石：深层形态（世界生成）。 */
    public static final DeferredBlock<Block> DEEPSLATE_TUNGSTEN_ORE_BLOCK =
            BLOCKS.register(DEEPSLATE_TUNGSTEN_ORE, () -> new Block(oreProperties(DEEPSLATE_TUNGSTEN_ORE, MapColor.DEEPSLATE)));
    /** 钨块：9 钨锭存储块。 */
    public static final DeferredBlock<Block> TUNGSTEN_BLOCK_BLOCK =
            BLOCKS.register(TUNGSTEN_BLOCK, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(TUNGSTEN_BLOCK)))));

    public static final DeferredItem<BlockItem> TUNGSTEN_ORE_ITEM = item(TUNGSTEN_ORE, TUNGSTEN_ORE_BLOCK);
    public static final DeferredItem<BlockItem> DEEPSLATE_TUNGSTEN_ORE_ITEM =
            item(DEEPSLATE_TUNGSTEN_ORE, DEEPSLATE_TUNGSTEN_ORE_BLOCK);
    public static final DeferredItem<BlockItem> TUNGSTEN_BLOCK_ITEM = item(TUNGSTEN_BLOCK, TUNGSTEN_BLOCK_BLOCK);

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

    private TungstenBlocks() {}
}
