package dev.znecby.stellar_evolution.moon;

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
 * 月球矿石（2026-11-12）：
 * 钛铁矿 / 冰岩矿 / 辉钛晶骸；独居石沿用现有方块，只降低生成量。
 * 钛铁矿掉粗钛铁矿，冰岩矿掉斜长岩 + 冰，辉钛晶骸掉辉钛晶体（粗矿）。
 * y&lt;0 为深板岩层，钛铁矿 / 冰岩矿另有深板岩版本；辉钛晶骸只在石头层生成。
 */
public final class MoonOreBlocks {
    public static final String MOON_ILMENITE_ORE = "moon_ilmenite_ore";
    public static final String MOON_ICE_ROCK_ORE = "moon_ice_rock_ore";
    public static final String MOON_GLOW_TITANIUM_ORE = "moon_glow_titanium_ore";
    public static final String DEEPSLATE_MOON_ILMENITE_ORE = "deepslate_moon_ilmenite_ore";
    public static final String DEEPSLATE_MOON_ICE_ROCK_ORE = "deepslate_moon_ice_rock_ore";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredBlock<Block> MOON_ILMENITE_ORE_BLOCK = ore(MOON_ILMENITE_ORE, MapColor.STONE);
    public static final DeferredBlock<Block> MOON_ICE_ROCK_ORE_BLOCK = ore(MOON_ICE_ROCK_ORE, MapColor.ICE);
    public static final DeferredBlock<Block> MOON_GLOW_TITANIUM_ORE_BLOCK = ore(MOON_GLOW_TITANIUM_ORE, MapColor.COLOR_CYAN);
    public static final DeferredBlock<Block> DEEPSLATE_MOON_ILMENITE_ORE_BLOCK =
            ore(DEEPSLATE_MOON_ILMENITE_ORE, MapColor.DEEPSLATE, 4.5F);
    public static final DeferredBlock<Block> DEEPSLATE_MOON_ICE_ROCK_ORE_BLOCK =
            ore(DEEPSLATE_MOON_ICE_ROCK_ORE, MapColor.DEEPSLATE, 4.5F);

    public static final DeferredItem<BlockItem> MOON_ILMENITE_ORE_ITEM = item(MOON_ILMENITE_ORE, MOON_ILMENITE_ORE_BLOCK);
    public static final DeferredItem<BlockItem> MOON_ICE_ROCK_ORE_ITEM = item(MOON_ICE_ROCK_ORE, MOON_ICE_ROCK_ORE_BLOCK);
    public static final DeferredItem<BlockItem> MOON_GLOW_TITANIUM_ORE_ITEM =
            item(MOON_GLOW_TITANIUM_ORE, MOON_GLOW_TITANIUM_ORE_BLOCK);
    public static final DeferredItem<BlockItem> DEEPSLATE_MOON_ILMENITE_ORE_ITEM =
            item(DEEPSLATE_MOON_ILMENITE_ORE, DEEPSLATE_MOON_ILMENITE_ORE_BLOCK);
    public static final DeferredItem<BlockItem> DEEPSLATE_MOON_ICE_ROCK_ORE_ITEM =
            item(DEEPSLATE_MOON_ICE_ROCK_ORE, DEEPSLATE_MOON_ICE_ROCK_ORE_BLOCK);

    private static DeferredBlock<Block> ore(String name, MapColor color) {
        return ore(name, color, 3.0F);
    }

    private static DeferredBlock<Block> ore(String name, MapColor color, float hardness) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(hardness, 3.0F)
                .requiresCorrectToolForDrops()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name)))));
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

    private MoonOreBlocks() {}
}
