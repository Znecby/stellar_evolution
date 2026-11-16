package dev.znecby.stellar_evolution.particle;

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
 * 粒子线的<strong>单方块</strong>：目前只有同位素电源（RTG）。
 *
 * <p>2×2×2 的粒子机器（粒子源 / 辐照仓 / 增殖包层）在 {@code fission.FissionMachines} 里统一注册。
 */
public final class ParticleBlocks {
    public static final String ISOTOPE_GENERATOR_NAME = "isotope_generator";
    public static final String HIGH_POWER_ISOTOPE_GENERATOR_NAME = "high_power_isotope_generator";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 同位素电源：锶-90 的长期低功率去向（恒 512 DP / 64 HP·t⁻¹）。 */
    public static final DeferredBlock<IsotopeGeneratorBlock> ISOTOPE_GENERATOR =
            BLOCKS.register(ISOTOPE_GENERATOR_NAME, () -> new IsotopeGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(ISOTOPE_GENERATOR_NAME)))));

    public static final DeferredItem<BlockItem> ISOTOPE_GENERATOR_ITEM =
            ITEMS.register(ISOTOPE_GENERATOR_NAME, key -> new BlockItem(ISOTOPE_GENERATOR.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /** 高功率同位素电源：锔-244 的短寿命高功率去向（恒 2 048 DP / 256 HP·t⁻¹）。 */
    public static final DeferredBlock<IsotopeGeneratorBlock> HIGH_POWER_ISOTOPE_GENERATOR =
            BLOCKS.register(HIGH_POWER_ISOTOPE_GENERATOR_NAME,
                    () -> new IsotopeGeneratorBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 10)
                            .setId(ResourceKey.create(Registries.BLOCK,
                                    StellarEvolution.id(HIGH_POWER_ISOTOPE_GENERATOR_NAME)))));

    public static final DeferredItem<BlockItem> HIGH_POWER_ISOTOPE_GENERATOR_ITEM =
            ITEMS.register(HIGH_POWER_ISOTOPE_GENERATOR_NAME,
                    key -> new BlockItem(HIGH_POWER_ISOTOPE_GENERATOR.get(),
                            new Item.Properties()
                                    .setId(ResourceKey.create(Registries.ITEM, key))
                                    .useBlockDescriptionPrefix()));

    /** 两台同位素电源共用一个方块实体类型：容量 / 热流率由「哪块方块」决定。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IsotopeGeneratorBlockEntity>>
            ISOTOPE_GENERATOR_ENTITY = BLOCK_ENTITIES.register(ISOTOPE_GENERATOR_NAME,
                    () -> new BlockEntityType<>(IsotopeGeneratorBlockEntity::new,
                            ISOTOPE_GENERATOR.get(), HIGH_POWER_ISOTOPE_GENERATOR.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private ParticleBlocks() {}
}
