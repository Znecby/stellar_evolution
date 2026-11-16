package dev.znecby.stellar_evolution.engine;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 三台燃烧类发电机组的注册表：
 * <ul>
 *   <li>{@code industrial_combustion_engine} 工业内燃机组（中 HP / 高 DP，转速 + 氧气助燃）；</li>
 *   <li>{@code combined_cycle_gas_turbine} 联合燃气机组（高 HP / 中 DP，温度 + 转速 + 铜格栅产汽）；</li>
 *   <li>{@code aerospace_rocket_engine} 航空航天火箭引擎（中高 DP/HQ，真空可用，8 格尾焰）。</li>
 * </ul>
 */
public final class EngineMachines {
    public static final String INDUSTRIAL_NAME = "industrial_combustion_engine";
    public static final String GAS_TURBINE_NAME = "combined_cycle_gas_turbine";
    public static final String ROCKET_NAME = "aerospace_rocket_engine";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    public static final DeferredBlock<IndustrialEngineBlock> INDUSTRIAL_ENGINE =
            BLOCKS.register(INDUSTRIAL_NAME, () -> new IndustrialEngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(INDUSTRIAL_NAME)))));

    /** 工业内燃机组的 7 个填充格（不可见、无物品、不掉落）。 */
    public static final DeferredBlock<EngineMachineShellBlock> INDUSTRIAL_SHELL =
            shell(INDUSTRIAL_NAME + "_shell");

    public static final DeferredItem<BlockItem> INDUSTRIAL_ENGINE_ITEM =
            ITEMS.register(INDUSTRIAL_NAME, key -> new BlockItem(INDUSTRIAL_ENGINE.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static final DeferredBlock<GasTurbineBlock> GAS_TURBINE =
            BLOCKS.register(GAS_TURBINE_NAME, () -> new GasTurbineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(GAS_TURBINE_NAME)))));

    /** 联合燃气机组的 7 个填充格。 */
    public static final DeferredBlock<EngineMachineShellBlock> GAS_TURBINE_SHELL =
            shell(GAS_TURBINE_NAME + "_shell");

    public static final DeferredItem<BlockItem> GAS_TURBINE_ITEM =
            ITEMS.register(GAS_TURBINE_NAME, key -> new BlockItem(GAS_TURBINE.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static final DeferredBlock<RocketEngineBlock> ROCKET_ENGINE =
            BLOCKS.register(ROCKET_NAME, () -> new RocketEngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.5F, 9.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(ROCKET_NAME)))));

    /** 航空航天火箭引擎的 7 个填充格。 */
    public static final DeferredBlock<EngineMachineShellBlock> ROCKET_SHELL =
            shell(ROCKET_NAME + "_shell");

    public static final DeferredItem<BlockItem> ROCKET_ENGINE_ITEM =
            ITEMS.register(ROCKET_NAME, key -> new BlockItem(ROCKET_ENGINE.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    /**
     * 三台机组各自的可动部件「载体方块」：从不放置、没有物品 —— 只提供 blockstate→模型 的映射，
     * 让 {@code client.EngineMachineRenderer} 借一个 {@code BlockState} 用 {@code submitMovingBlock} 画旋转件
     * （照热力引擎 / 燃烧引擎的 rotor 口径）。
     */
    public static final DeferredBlock<Block> INDUSTRIAL_FAN = carrier("industrial_engine_fan");
    /** 联合燃气机组的压气机叶轮（进气口内，绕 X 轴）。 */
    public static final DeferredBlock<Block> GAS_TURBINE_COMPRESSOR = carrier("gas_turbine_compressor");
    /** 火箭引擎的涡轮泵叶轮（泵顶面，绕 Y 轴）。 */
    public static final DeferredBlock<Block> ROCKET_TURBOPUMP = carrier("rocket_engine_turbopump");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialEngineBlockEntity>> INDUSTRIAL_ENTITY =
            BLOCK_ENTITIES.register(INDUSTRIAL_NAME, () -> new BlockEntityType<>(
                    IndustrialEngineBlockEntity::new, INDUSTRIAL_ENGINE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasTurbineBlockEntity>> GAS_TURBINE_ENTITY =
            BLOCK_ENTITIES.register(GAS_TURBINE_NAME, () -> new BlockEntityType<>(
                    GasTurbineBlockEntity::new, GAS_TURBINE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RocketEngineBlockEntity>> ROCKET_ENTITY =
            BLOCK_ENTITIES.register(ROCKET_NAME, () -> new BlockEntityType<>(
                    RocketEngineBlockEntity::new, ROCKET_ENGINE.get()));

    private static DeferredBlock<Block> carrier(String name) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .noOcclusion()
                .forceSolidOn()
                .noLootTable()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name)))));
    }

    private static DeferredBlock<EngineMachineShellBlock> shell(String name) {
        return BLOCKS.register(name, () -> new EngineMachineShellBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .noOcclusion()
                .forceSolidOn()
                .noLootTable()
                .randomTicks()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name)))));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private EngineMachines() {}
}
