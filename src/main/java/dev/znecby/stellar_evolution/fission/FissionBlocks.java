package dev.znecby.stellar_evolution.fission;

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
 * 核裂变线的<strong>方块注册表</strong>（《核裂变线预规划蓝图》§5.1 / §12.3；F1 起步：矿石方块）。
 *
 * <p>本批只放<strong>矿石方块</strong>（铀矿石 + 深板岩铀矿石）—— 燃料循环的<strong>总原料</strong>。
 * 世界生成（矿脉）留到世界层批次（对应规划 S8），本批先给方块便于测试（照石化线油砂岩的口径）。
 *
 * <p>贴图由 {@code tools/gen_fission_blocks.ps1} 生成；双语名在 lang；挖掘工具归 {@code mineable/pickaxe}。
 */
public final class FissionBlocks {
    public static final String URANIUM_ORE = "uranium_ore";
    public static final String DEEPSLATE_URANIUM_ORE = "deepslate_uranium_ore";
    public static final String HLW_GLASS_BLOCK = "hlw_glass_block";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 铀矿石：裂变线总原料（浅层外观）。 */
    public static final DeferredBlock<Block> URANIUM_ORE_BLOCK =
            BLOCKS.register(URANIUM_ORE, () -> new Block(oreProperties(URANIUM_ORE, MapColor.STONE)));
    /** 深板岩铀矿石：深层变体（外观随深板岩）。 */
    public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE_BLOCK =
            BLOCKS.register(DEEPSLATE_URANIUM_ORE, () -> new Block(oreProperties(DEEPSLATE_URANIUM_ORE, MapColor.DEEPSLATE)));
    /** 高放玻璃屏蔽块：高放玻璃固化体压制的屏蔽建材。 */
    public static final DeferredBlock<Block> HLW_GLASS_BLOCK_BLOCK =
            BLOCKS.register(HLW_GLASS_BLOCK, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(3.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(HLW_GLASS_BLOCK)))));

    public static final DeferredItem<BlockItem> URANIUM_ORE_ITEM = item(URANIUM_ORE, URANIUM_ORE_BLOCK);
    public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE_ITEM = item(DEEPSLATE_URANIUM_ORE, DEEPSLATE_URANIUM_ORE_BLOCK);
    public static final DeferredItem<BlockItem> HLW_GLASS_BLOCK_ITEM = item(HLW_GLASS_BLOCK, HLW_GLASS_BLOCK_BLOCK);

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

    private FissionBlocks() {}
}
