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
 * 裂变线<strong>裂变堆</strong>注册表（《核裂变线预规划蓝图》§3.3 / §13.3 F2）。
 *
 * <p>裂变堆 = 2×2×2 整机；对外能力 = <strong>大机器物流</strong>（机械管 → 铜箱子 / 储罐 / 反应仓）。
 * <strong>反应堆无界面</strong>；燃料棒 / 石墨棒 / 反射棒只从<strong>反应仓</strong>读取（每台反应堆最多 1 个）。
 */
public final class FissionReactors {
    public static final String FISSION_REACTOR = "fission_reactor";
    /** 旧 id（水冷反应堆），保留兼容旧存档。 */
    public static final String LEGACY_WATER_REACTOR = "water_reactor";
    /** 反应仓：反应堆专属的燃料 / 组件容器（大箱子界面）。 */
    public static final String REACTION_CHAMBER = "reaction_chamber";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 裂变堆（原水冷反应堆）。 */
    public static final DeferredBlock<WaterReactorBlock> FISSION_REACTOR_BLOCK =
            block(FISSION_REACTOR);
    public static final DeferredItem<BlockItem> FISSION_REACTOR_ITEM =
            item(FISSION_REACTOR, FISSION_REACTOR_BLOCK);

    // ---- 旧存档兼容：旧 id 仍注册（无创造页入口），按裂变堆处理
    public static final DeferredBlock<WaterReactorBlock> LEGACY_WATER_REACTOR_BLOCK =
            block(LEGACY_WATER_REACTOR);
    public static final DeferredItem<BlockItem> LEGACY_WATER_REACTOR_ITEM =
            item(LEGACY_WATER_REACTOR, LEGACY_WATER_REACTOR_BLOCK);

    /** 反应仓方块 / 物品 / 方块实体。 */
    public static final DeferredBlock<ReactionChamberBlock> REACTION_CHAMBER_BLOCK =
            BLOCKS.register(REACTION_CHAMBER, () -> new ReactionChamberBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(REACTION_CHAMBER)))));
    public static final DeferredItem<BlockItem> REACTION_CHAMBER_ITEM =
            ITEMS.register(REACTION_CHAMBER, key -> new BlockItem(REACTION_CHAMBER_BLOCK.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactionChamberBlockEntity>> REACTION_CHAMBER_ENTITY =
            BLOCK_ENTITIES.register(REACTION_CHAMBER, () -> new BlockEntityType<>(
                    ReactionChamberBlockEntity::new, REACTION_CHAMBER_BLOCK.get()));

    /** 反应堆方块实体。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterReactorBlockEntity>> REACTOR_ENTITY =
            BLOCK_ENTITIES.register("fission_reactor", () -> new BlockEntityType<>(WaterReactorBlockEntity::new,
                    FISSION_REACTOR_BLOCK.get(), LEGACY_WATER_REACTOR_BLOCK.get()));

    /** 旧 BE 类型：旧存档里存的 `stellar_evolution:water_reactor` 仍能加载（加载后按新逻辑跑）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterReactorBlockEntity>> LEGACY_REACTOR_ENTITY =
            BLOCK_ENTITIES.register(LEGACY_WATER_REACTOR, () -> new BlockEntityType<>(WaterReactorBlockEntity::new,
                    LEGACY_WATER_REACTOR_BLOCK.get()));

    private static DeferredBlock<WaterReactorBlock> block(String name) {
        return BLOCKS.register(name, () -> new WaterReactorBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(4.0F, 8.0F)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                    .forceSolidOn()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name)))));
    }

    private static DeferredItem<BlockItem> item(String name, DeferredBlock<WaterReactorBlock> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(FissionReactors::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, REACTOR_ENTITY.get(),
                (reactor, side) -> reactor.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, REACTOR_ENTITY.get(),
                (reactor, side) -> reactor.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, LEGACY_REACTOR_ENTITY.get(),
                (reactor, side) -> reactor.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, LEGACY_REACTOR_ENTITY.get(),
                (reactor, side) -> reactor.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, REACTION_CHAMBER_ENTITY.get(),
                (chamber, side) -> chamber.itemCapability());
    }

    private FissionReactors() {}
}
