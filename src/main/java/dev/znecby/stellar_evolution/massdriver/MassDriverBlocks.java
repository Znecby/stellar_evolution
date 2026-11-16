package dev.znecby.stellar_evolution.massdriver;

import dev.znecby.stellar_evolution.StellarEvolution;
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

/** 质量投射轨道炮：控制器 + 固定图案。结构外壳直接复用已有的高负荷机械方块等。 */
public final class MassDriverBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    public static final DeferredBlock<MassDriverControllerBlock> CONTROLLER =
            BLOCKS.register("mass_driver_controller", () -> new MassDriverControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(6.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(MassDriverControllerBlock.FORMED) ? 8 : 0)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id("mass_driver_controller")))));

    public static final DeferredItem<BlockItem> CONTROLLER_ITEM =
            ITEMS.register("mass_driver_controller", key -> new BlockItem(CONTROLLER.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MassDriverControllerBlockEntity>>
            CONTROLLER_ENTITY = BLOCK_ENTITIES.register("mass_driver_controller",
                    () -> new BlockEntityType<>(MassDriverControllerBlockEntity::new, CONTROLLER.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    /** 控制器对外能力：未成型时 handler 为 null，管道自然接不上。 */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, CONTROLLER_ENTITY.get(),
                (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, CONTROLLER_ENTITY.get(),
                (be, side) -> be.fluidHandler());
    }

    private MassDriverBlocks() {}
}
