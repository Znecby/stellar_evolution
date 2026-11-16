package dev.modmind.stellar_evolution.thermal;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 热压差网络的方块注册表 - 目前只有<strong>创造差压</strong>（调试用的无限电源）。
 * <p>
 * 方块 / 物品模型都直接复用原版<strong>铜块</strong>（{@code minecraft:block/copper_block}），零新增贴图。
 */
public final class ThermalBlocks {
    public static final String CREATIVE_PRESSURE_NAME = "creative_pressure";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 创造差压：热压差网络的调试电源（容量 long 上限，热流率可调）。 */
    public static final DeferredBlock<CreativePressureBlock> CREATIVE_PRESSURE =
            BLOCKS.register(CREATIVE_PRESSURE_NAME, () -> new CreativePressureBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(CREATIVE_PRESSURE_NAME)))));

    public static final DeferredItem<BlockItem> CREATIVE_PRESSURE_ITEM =
            ITEMS.register(CREATIVE_PRESSURE_NAME, key -> new BlockItem(CREATIVE_PRESSURE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativePressureBlockEntity>>
            CREATIVE_PRESSURE_ENTITY = BLOCK_ENTITIES.register(CREATIVE_PRESSURE_NAME,
                    () -> new BlockEntityType<>(CreativePressureBlockEntity::new, CREATIVE_PRESSURE.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private ThermalBlocks() {}
}
