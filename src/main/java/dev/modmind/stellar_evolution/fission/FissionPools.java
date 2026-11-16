package dev.modmind.stellar_evolution.fission;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 乏燃料池注册表：单方块 + 9 格容器 + 方块实体。
 *
 * <p>不新增物流框架能力，只暴露物品能力（漏斗 / 机械管可插入乏燃料）；
 * 右键复用投掷器界面。
 */
public final class FissionPools {
    public static final String SPENT_FUEL_POOL = "spent_fuel_pool";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    public static final DeferredBlock<SpentFuelPoolBlock> SPENT_FUEL_POOL_BLOCK =
            BLOCKS.register(SPENT_FUEL_POOL, () -> new SpentFuelPoolBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(SPENT_FUEL_POOL)))));

    public static final DeferredItem<BlockItem> SPENT_FUEL_POOL_ITEM =
            ITEMS.register(SPENT_FUEL_POOL, key -> new BlockItem(SPENT_FUEL_POOL_BLOCK.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpentFuelPoolBlockEntity>> SPENT_FUEL_POOL_ENTITY =
            BLOCK_ENTITIES.register(SPENT_FUEL_POOL, () -> new BlockEntityType<>(
                    SpentFuelPoolBlockEntity::new, SPENT_FUEL_POOL_BLOCK.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(FissionPools::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, SPENT_FUEL_POOL_ENTITY.get(),
                (pool, side) -> pool.itemCapability());
    }

    private FissionPools() {}
}
