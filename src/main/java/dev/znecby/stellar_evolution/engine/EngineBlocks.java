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
 * 引擎注册表：热力引擎 + 燃烧引擎（各含方块 / 物品 / 方块实体），外加两个<strong>纯模型载体方块</strong>。
 * <p>
 * {@code heat_engine_rotor} / {@code burning_engine_rotor} 从不放置、也没有物品：它们只提供 blockstate→模型 的映射，
 * 让 {@code client.HeatEngineRenderer} / {@code client.BurningEngineRenderer} 能拿一个 {@code BlockState}
 * 去 {@code submitMovingBlock} 画那根旋转的桨叶 / 曲轴。
 * 贴图全部复用原版（平滑石 / 铜块 / 铁块 / 岩浆块），零新增贴图。
 */
public final class EngineBlocks {
    public static final String HEAT_ENGINE_NAME = "heat_engine";
    public static final String HEAT_ENGINE_ROTOR_NAME = "heat_engine_rotor";
    public static final String BURNING_ENGINE_NAME = "burning_engine";
    public static final String BURNING_ENGINE_ROTOR_NAME = "burning_engine_rotor";
    public static final String BURNING_ENGINE_PISTON_NAME = "burning_engine_piston";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    public static final DeferredBlock<HeatEngineBlock> HEAT_ENGINE =
            BLOCKS.register(HEAT_ENGINE_NAME, () -> new HeatEngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    // 非整方块造型：不让邻居消面、不挡光（否则贴着实心块会出「洞」）
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(HEAT_ENGINE_NAME)))));

    public static final DeferredItem<BlockItem> HEAT_ENGINE_ITEM =
            ITEMS.register(HEAT_ENGINE_NAME, key -> new BlockItem(HEAT_ENGINE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /**
     * 转子模型载体：只为了让渲染器能借一个方块状态拿到「铁桨叶」的烘焙模型。
     * 无物品、不进创造页、不进挖掘标签，正常游戏里拿不到也放不下。
     */
    public static final DeferredBlock<Block> HEAT_ENGINE_ROTOR =
            BLOCKS.register(HEAT_ENGINE_ROTOR_NAME, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(HEAT_ENGINE_ROTOR_NAME)))));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeatEngineBlockEntity>> HEAT_ENGINE_ENTITY =
            BLOCK_ENTITIES.register(HEAT_ENGINE_NAME,
                    () -> new BlockEntityType<>(HeatEngineBlockEntity::new, HEAT_ENGINE.get()));

    // ---- 燃烧引擎：同款版式的纯外观动画方块（无功能机制，曲轴常转） ----

    public static final DeferredBlock<BurningEngineBlock> BURNING_ENGINE =
            BLOCKS.register(BURNING_ENGINE_NAME, () -> new BurningEngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(BURNING_ENGINE_NAME)))));

    public static final DeferredItem<BlockItem> BURNING_ENGINE_ITEM =
            ITEMS.register(BURNING_ENGINE_NAME, key -> new BlockItem(BURNING_ENGINE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /** 曲轴模型载体：只为了让渲染器借一个方块状态拿到「铁曲轴」的烘焙模型。 */
    public static final DeferredBlock<Block> BURNING_ENGINE_ROTOR =
            BLOCKS.register(BURNING_ENGINE_ROTOR_NAME, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(BURNING_ENGINE_ROTOR_NAME)))));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurningEngineBlockEntity>> BURNING_ENGINE_ENTITY =
            BLOCK_ENTITIES.register(BURNING_ENGINE_NAME,
                    () -> new BlockEntityType<>(BurningEngineBlockEntity::new, BURNING_ENGINE.get()));

    /** 活塞模型载体：渲染器把它的烘焙模型画 6 次（左右各 3 缸），每次沿各自缸轴偏移。 */
    public static final DeferredBlock<Block> BURNING_ENGINE_PISTON =
            BLOCKS.register(BURNING_ENGINE_PISTON_NAME, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(BURNING_ENGINE_PISTON_NAME)))));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private EngineBlocks() {}
}
