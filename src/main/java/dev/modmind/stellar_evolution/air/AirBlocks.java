package dev.modmind.stellar_evolution.air;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 空气分馏线的<strong>方块</strong>：只注册不锈钢格栅（虚拟液态空气源）。
 *
 * <p>格栅没有方块实体；接入机械管网络后由
 * {@link dev.modmind.stellar_evolution.tank.GrateSource} 提供液态空气。
 */
public final class AirBlocks {
    public static final String STEEL_GRATE_NAME = "steel_grate";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredBlock<SteelGrateBlock> STEEL_GRATE =
            BLOCKS.register(STEEL_GRATE_NAME, () -> new SteelGrateBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(STEEL_GRATE_NAME)))));

    public static final DeferredItem<BlockItem> STEEL_GRATE_ITEM =
            ITEMS.register(STEEL_GRATE_NAME, key -> new BlockItem(STEEL_GRATE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private AirBlocks() {}
}
