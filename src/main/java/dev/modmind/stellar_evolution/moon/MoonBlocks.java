package dev.modmind.stellar_evolution.moon;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 月球维度方块：目前只加「月壤」一种，其余地形复用原版方块。
 *
 * <p>月壤是泥土换色：挖掘手感、硬度、声音都照泥土，不能长草、不能耕地、不能放水；
 * 世界生成时作为月面表层，矿石直接替换原版石头。
 */
public final class MoonBlocks {
    public static final String MOON_REGOLITH = "moon_regolith";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredBlock<Block> MOON_REGOLITH_BLOCK =
            BLOCKS.register(MOON_REGOLITH, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(0.5F)
                    .sound(SoundType.GRAVEL)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(MOON_REGOLITH)))));

    public static final DeferredItem<BlockItem> MOON_REGOLITH_ITEM =
            ITEMS.register(MOON_REGOLITH, key -> new BlockItem(MOON_REGOLITH_BLOCK.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private MoonBlocks() {}
}
