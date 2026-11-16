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
 * 铅的方块（2026-10-26，照钨矿口径）：铅矿石 / 深板岩铅矿石（世界生成，两形态）+ 铅块（9 锭存储块）。
 *
 * <p>贴图由 {@code tools/gen_lead_materials.ps1} 生成：矿石照原版铁矿「石底 + 成簇矿斑」，
 * 只把矿斑换成铅的冷灰蓝；铅块复用金属块模板。双语名在 lang；挖掘工具归 {@code mineable/pickaxe}。
 */
public final class LeadBlocks {
    public static final String LEAD_ORE = "lead_ore";
    public static final String DEEPSLATE_LEAD_ORE = "deepslate_lead_ore";
    public static final String LEAD_BLOCK = "lead_block";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 铅矿石：浅层形态（世界生成）。 */
    public static final DeferredBlock<Block> LEAD_ORE_BLOCK =
            BLOCKS.register(LEAD_ORE, () -> new Block(oreProperties(LEAD_ORE, MapColor.STONE)));
    /** 深板岩铅矿石：深层形态（世界生成）。 */
    public static final DeferredBlock<Block> DEEPSLATE_LEAD_ORE_BLOCK =
            BLOCKS.register(DEEPSLATE_LEAD_ORE, () -> new Block(oreProperties(DEEPSLATE_LEAD_ORE, MapColor.DEEPSLATE)));
    /** 铅块：9 铅锭存储块。 */
    public static final DeferredBlock<Block> LEAD_BLOCK_BLOCK =
            BLOCKS.register(LEAD_BLOCK, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(LEAD_BLOCK)))));

    public static final DeferredItem<BlockItem> LEAD_ORE_ITEM = item(LEAD_ORE, LEAD_ORE_BLOCK);
    public static final DeferredItem<BlockItem> DEEPSLATE_LEAD_ORE_ITEM =
            item(DEEPSLATE_LEAD_ORE, DEEPSLATE_LEAD_ORE_BLOCK);
    public static final DeferredItem<BlockItem> LEAD_BLOCK_ITEM = item(LEAD_BLOCK, LEAD_BLOCK_BLOCK);

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

    private LeadBlocks() {}
}
