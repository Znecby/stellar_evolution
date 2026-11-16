package dev.znecby.stellar_evolution.farmland;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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
 * <strong>机械土壤</strong>注册表：四类「土壤设备」共用一套热压差消耗端逻辑（见 {@link MechanicalSoilBlockEntity}），
 * 只是材质与「能种什么」不同：
 * <ul>
 *   <li>{@link MechanicalFarmlandBlock 机械耕地} —— 铜框架 + 耕地，种原版作物（{@code supports_crops}）；</li>
 *   <li>{@link MechanicalSandBlock 机械沙地} —— 铜框架 + 沙子，种甘蔗 / 仙人掌（{@code supports_sugar_cane} /
 *       {@code supports_cactus}），水下可种海带；</li>
 *   <li>{@link MechanicalSoulSandBlock 机械灵魂沙地} —— 铜框架 + 灵魂沙，种下界疣（{@code supports_nether_wart}）。</li>
 * </ul>
 * 三者的挖掘 / 可种分别靠 {@code minecraft:mineable/shovel} 与各自的原版方块标签（见 {@code data/}）。
 */
public final class FarmlandBlocks {
    public static final String MECHANICAL_FARMLAND_NAME = "mechanical_farmland";
    public static final String MECHANICAL_SAND_NAME = "mechanical_sand";
    public static final String MECHANICAL_SOUL_SAND_NAME = "mechanical_soul_sand";
    public static final String MECHANICAL_GRASS_NAME = "mechanical_grass";

    /**
     * <strong>「右键收获」作物</strong>标签（本模组命名空间 {@code stellar_evolution:right_click_harvest}）：
     * 收录「靠右键摘取、摘完留在原地（年龄回退）」的作物，目前是甜浆果丛。
     * <ul>
     *   <li><strong>放置</strong>：机械耕地借 {@link MechanicalFarmlandBlock#canSustainPlant} 允许标签内的作物种上来
     *       （这类作物原版走 {@code minecraft:supports_vegetation}，机械耕地不在其中）；</li>
     *   <li><strong>收获</strong>：成熟时由 {@link MechanicalSoilBlockEntity} 走「右键收获」分支 ——
     *       产物进容器、作物保留并回退到 1 龄（与甜浆果丛右键一致）。</li>
     * </ul>
     * 约定：加入该标签的方块需具备 {@code age} 属性，且「摘完」后的年龄为 <strong>1</strong>。
     */
    public static final TagKey<Block> RIGHT_CLICK_HARVEST =
            TagKey.create(Registries.BLOCK, StellarEvolution.id("right_click_harvest"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    public static final DeferredBlock<MechanicalFarmlandBlock> MECHANICAL_FARMLAND =
            BLOCKS.register(MECHANICAL_FARMLAND_NAME, () -> new MechanicalFarmlandBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .randomTicks()
                    .strength(0.6F)
                    .sound(SoundType.GRAVEL)
                    .isViewBlocking((state, level, pos) -> true)
                    .isSuffocating((state, level, pos) -> true)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(MECHANICAL_FARMLAND_NAME)))));

    public static final DeferredBlock<MechanicalSandBlock> MECHANICAL_SAND =
            BLOCKS.register(MECHANICAL_SAND_NAME, () -> new MechanicalSandBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(0.6F)
                    .sound(SoundType.SAND)
                    .isViewBlocking((state, level, pos) -> true)
                    .isSuffocating((state, level, pos) -> true)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(MECHANICAL_SAND_NAME)))));

    public static final DeferredBlock<MechanicalSoulSandBlock> MECHANICAL_SOUL_SAND =
            BLOCKS.register(MECHANICAL_SOUL_SAND_NAME, () -> new MechanicalSoulSandBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.6F)
                    .sound(SoundType.SOUL_SAND)
                    .isViewBlocking((state, level, pos) -> true)
                    .isSuffocating((state, level, pos) -> true)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(MECHANICAL_SOUL_SAND_NAME)))));

    public static final DeferredBlock<MechanicalGrassBlock> MECHANICAL_GRASS =
            BLOCKS.register(MECHANICAL_GRASS_NAME, () -> new MechanicalGrassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GRASS)
                    .strength(0.6F)
                    .sound(SoundType.GRASS)
                    .isViewBlocking((state, level, pos) -> true)
                    .isSuffocating((state, level, pos) -> true)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(MECHANICAL_GRASS_NAME)))));

    public static final DeferredItem<BlockItem> MECHANICAL_FARMLAND_ITEM =
            ITEMS.register(MECHANICAL_FARMLAND_NAME, key -> new BlockItem(MECHANICAL_FARMLAND.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    public static final DeferredItem<BlockItem> MECHANICAL_SAND_ITEM =
            ITEMS.register(MECHANICAL_SAND_NAME, key -> new BlockItem(MECHANICAL_SAND.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    public static final DeferredItem<BlockItem> MECHANICAL_SOUL_SAND_ITEM =
            ITEMS.register(MECHANICAL_SOUL_SAND_NAME, key -> new BlockItem(MECHANICAL_SOUL_SAND.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    public static final DeferredItem<BlockItem> MECHANICAL_GRASS_ITEM =
            ITEMS.register(MECHANICAL_GRASS_NAME, key -> new BlockItem(MECHANICAL_GRASS.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /**
     * 三类机械土壤<strong>共用的方块实体类型</strong>：只放「催熟进度」，是热压差网络里那台消耗端的驱动器。
     * <p>
     * 注册名沿用 {@code mechanical_farmland}（不改成新的名字）—— 方块实体的类型 id 会写进存档，
     * 沿用旧 id 可以让已有世界里的机械耕地方块实体继续正常加载。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechanicalSoilBlockEntity>>
            MECHANICAL_SOIL_ENTITY = BLOCK_ENTITIES.register(MECHANICAL_FARMLAND_NAME,
                    () -> new BlockEntityType<>(MechanicalSoilBlockEntity::new,
                            MECHANICAL_FARMLAND.get(), MECHANICAL_SAND.get(), MECHANICAL_SOUL_SAND.get(),
                            MECHANICAL_GRASS.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(FarmlandBlocks::onRegisterCapabilities);
    }

    /**
     * 方块能力：把机械土壤的 1 格虚拟肥料口暴露成标准 {@code ResourceHandler<ItemResource>}
     * （26.x 物品能力），让其他模组的物品管道 / 物流模组能识别并接入。
     */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, MECHANICAL_SOIL_ENTITY.get(),
                (soil, side) -> soil.itemCapability());
    }

    private FarmlandBlocks() {}
}
