package dev.znecby.stellar_evolution.coal;

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

/**
 * 焦化炉注册：<strong>只有控制器</strong>一个方块（3×3×4 炉体用原版红砖块 / 砖半砖自备）。
 *
 * <p>控制器对外提供大机器物流能力（机械物流仓连着的铜箱子 / 储罐），
 * 方块实体同时实现 {@code HatchOwner}，让仓室把热压差记账转发给它。
 */
public final class CokeOvenBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    public static final String CONTROLLER_ID = "coke_oven_controller";

    public static final DeferredBlock<CokeOvenControllerBlock> CONTROLLER =
            BLOCKS.register(CONTROLLER_ID, () -> new CokeOvenControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(CokeOvenControllerBlock.LIT) ? 10 : 0)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(CONTROLLER_ID)))));

    public static final DeferredItem<BlockItem> CONTROLLER_ITEM =
            ITEMS.register(CONTROLLER_ID, key -> new BlockItem(CONTROLLER.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CokeOvenControllerBlockEntity>>
            CONTROLLER_ENTITY = BLOCK_ENTITIES.register(CONTROLLER_ID,
                    () -> new BlockEntityType<>(CokeOvenControllerBlockEntity::new, CONTROLLER.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    /** 控制器对外能力：未成型时 handler 为 null，管道自然接不上。 */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, CONTROLLER_ENTITY.get(),
                (controller, side) -> controller.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, CONTROLLER_ENTITY.get(),
                (controller, side) -> controller.fluidCapability());
    }

    private CokeOvenBlocks() {}
}
