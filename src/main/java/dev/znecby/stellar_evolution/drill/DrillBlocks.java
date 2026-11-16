package dev.znecby.stellar_evolution.drill;

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
 * 开采钻的注册表：主方块 / 物品 / 方块实体 + <strong>钻头物品</strong> + 两个隐藏载体方块。
 *
 * <p>{@code mining_drill_shaft} / {@code mining_drill_winch} 从不放置、也没有物品：它们只提供
 * blockstate→模型 的映射，让 {@code client.DrillRenderer} 拿一个 {@code BlockState} 去
 * {@code submitMovingBlock} 画「旋转的钻杆」与「旋转的绞盘」（照 {@code engine.EngineBlocks} 口径）。
 * 贴图全部复用原版（铜块 / 铁块 / 金块 / 黑石），零新增贴图。
 */
public final class DrillBlocks {
    public static final String DRILL_NAME = "mining_drill";
    public static final String SHAFT_NAME = "mining_drill_shaft";
    public static final String WINCH_NAME = "mining_drill_winch";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 主方块：一格大小 + 一圈 2px 铜框架。 */
    public static final DeferredBlock<DrillBlock> MINING_DRILL =
            BLOCKS.register(DRILL_NAME, () -> new DrillBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    // 非整方块造型：不让邻居消面、不挡光（否则贴着实心块会出「洞」）
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(DRILL_NAME)))));

    public static final DeferredItem<BlockItem> MINING_DRILL_ITEM =
            ITEMS.register(DRILL_NAME, key -> new BlockItem(MINING_DRILL.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    /** 钻头：开采钻的耗材 / 合成件（本轮无耐久机制，只作合成材料）。 */
    public static final DeferredItem<Item> DRILL_BIT =
            ITEMS.register("drill_bit", key -> new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))));

    /** 钻杆模型载体：只为了让渲染器借一个方块状态拿到「旋转钻杆」的烘焙模型。 */
    public static final DeferredBlock<Block> SHAFT =
            BLOCKS.register(SHAFT_NAME, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(SHAFT_NAME)))));

    /** 绞盘模型载体：同上，供「正面绞盘」自转。 */
    public static final DeferredBlock<Block> WINCH =
            BLOCKS.register(WINCH_NAME, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(WINCH_NAME)))));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrillBlockEntity>> DRILL_ENTITY =
            BLOCK_ENTITIES.register(DRILL_NAME,
                    () -> new BlockEntityType<>(DrillBlockEntity::new, MINING_DRILL.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private DrillBlocks() {}
}
