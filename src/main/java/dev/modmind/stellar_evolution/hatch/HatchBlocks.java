package dev.modmind.stellar_evolution.hatch;

import dev.modmind.stellar_evolution.StellarEvolution;
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
 * 仓室与结构外壳注册。
 *
 * <p>只保留一个<strong>机械物流仓</strong>：本身没有界面、没有内置库存；
 * 只有成为已成型多方块结构的一部分时，才会被控制器认领并接入机械管网络。
 * 其余物品/流体/总成仓已删除。
 */
public final class HatchBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 机械物流仓：唯一保留的仓室方块。 */
    public static final DeferredBlock<HatchBlock> LOGISTICS =
            BLOCKS.register("machine_logistics_hatch", () -> new HatchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id("machine_logistics_hatch")))));
    public static final DeferredItem<BlockItem> LOGISTICS_ITEM =
            ITEMS.register("machine_logistics_hatch", key -> new BlockItem(LOGISTICS.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    /** 仓室基底方块（「高负荷机械方块」）：不锈钢色 connections 材质，客户端用 CTM 模型。 */
    public static final String CASING_ID = "high_load_machine_casing";
    public static final DeferredBlock<Block> HIGH_LOAD_CASING =
            BLOCKS.register(CASING_ID, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(CASING_ID)))));
    public static final DeferredItem<BlockItem> HIGH_LOAD_CASING_ITEM =
            ITEMS.register(CASING_ID, key -> new BlockItem(HIGH_LOAD_CASING.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    /** 第二种仓室基底（「坚固支撑机械方块」，钨色连接材质）。 */
    public static final String SUPPORT_CASING_ID = "rugged_support_machine_casing";
    public static final DeferredBlock<Block> RUGGED_SUPPORT_CASING =
            BLOCKS.register(SUPPORT_CASING_ID, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(SUPPORT_CASING_ID)))));
    public static final DeferredItem<BlockItem> RUGGED_SUPPORT_CASING_ITEM =
            ITEMS.register(SUPPORT_CASING_ID, key -> new BlockItem(RUGGED_SUPPORT_CASING.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    /** 超导线圈方块：下界合金色 + 连接材质 + 自发光 bloom 覆盖层。 */
    public static final String COIL_ID = "superconducting_coil_block";
    public static final DeferredBlock<Block> SUPERCONDUCTING_COIL =
            BLOCKS.register(COIL_ID, () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(COIL_ID)))));
    public static final DeferredItem<BlockItem> SUPERCONDUCTING_COIL_ITEM =
            ITEMS.register(COIL_ID, key -> new BlockItem(SUPERCONDUCTING_COIL.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HatchBlockEntity>> HATCH_ENTITY =
            BLOCK_ENTITIES.register("hatch", () -> new BlockEntityType<>(HatchBlockEntity::new, LOGISTICS.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private HatchBlocks() {}
}
