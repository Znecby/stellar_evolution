package dev.modmind.stellar_evolution.fission;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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
 * 裂变线机器注册表（《核裂变线预规划蓝图》§6 / §13.2 F1）：<strong>2×2×2 燃料循环机器族</strong>。
 *
 * <p>每机种一个方块；燃料循环机器<strong>共用</strong> {@link FissionMachineBlock} 与
 * {@link FissionMachineBlockEntity}，缪子聚变仪用独立实体（{@link #MUON_ENTITY}）。
 * 对外能力 = <strong>大机器物流</strong>（机械管 → 铜箱子 / 储罐），不再自带内部槽 / 罐。贴图复用原版。
 */
public final class FissionMachines {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 方块 → 机种（注册时填写）。 */
    private static final Map<Block, FissionMachineKind> KINDS = new IdentityHashMap<>();
    /** 机种 → 方块。 */
    private static final Map<FissionMachineKind, DeferredBlock<? extends FissionMachineBlock>> BLOCKS_BY_KIND =
            new EnumMap<>(FissionMachineKind.class);

    /** 化学处理槽（id 仍为 ore_leacher）：酸浸 + 铀转化（氟化）合为一台。 */
    public static final DeferredBlock<FissionMachineBlock> ORE_LEACHER_BLOCK = machine(FissionMachineKind.ORE_LEACHER);
    /** 气体离心机群：UF6 → 富集铀 + 贫铀。 */
    public static final DeferredBlock<FissionMachineBlock> CENTRIFUGE_BLOCK = machine(FissionMachineKind.CENTRIFUGE);
    /** 燃料组装机：富集铀 / 钚 → 燃料棒。 */
    public static final DeferredBlock<FissionMachineBlock> CANNING_MACHINE_BLOCK = machine(FissionMachineKind.CANNING_MACHINE);
    /** 后处理厂：冷却乏燃料 → 钚 / U-233 / 回收铀。 */
    public static final DeferredBlock<FissionMachineBlock> REPROCESSOR_BLOCK = machine(FissionMachineKind.REPROCESSOR);
    /** 缪子聚变仪：展示机，只做外观与动画。 */
    public static final DeferredBlock<FissionMachineBlock> MUON_FUSION_BLOCK = machine(FissionMachineKind.MUON_FUSION);
    /** 回旋加速器（高能物理线第一期）。 */
    public static final DeferredBlock<FissionMachineBlock> CYCLOTRON_BLOCK = machine(FissionMachineKind.CYCLOTRON);
    /** 粒子源（高能物理线第二期）。 */
    public static final DeferredBlock<FissionMachineBlock> PARTICLE_SOURCE_BLOCK =
            machine(FissionMachineKind.PARTICLE_SOURCE);
    /** 辐照仓（高能物理线）：辐照 / 嬗变 / 对撞 / 离子注入 / 氚增殖合为一台，吃任意已接入的流。 */
    public static final DeferredBlock<FissionMachineBlock> IRRADIATION_CHAMBER_BLOCK =
            machine(FissionMachineKind.IRRADIATION_CHAMBER);
    /** 对撞机（高能物理线）：两路束流对撞 + 喷出 μ 子流。 */
    public static final DeferredBlock<FissionMachineBlock> COLLIDER_BLOCK =
            machine(FissionMachineKind.COLLIDER);
    /** ADS（二期 D1）：质子流驱动次临界嬗变，产物稳定核素 + 蒸汽。 */
    public static final DeferredBlock<FissionMachineBlock> ADS_BLOCK =
            machine(FissionMachineKind.ADS);
    /** 湮灭反应堆（二期 B2）：反物质 + 氢 → 直接产热压差。 */
    public static final DeferredBlock<FissionMachineBlock> ANNIHILATION_REACTOR_BLOCK =
            machine(FissionMachineKind.ANNIHILATION_REACTOR);
    /** 加速环段（三期 E4）：贴在对撞机旁，按数量解锁批量终局配方。 */
    public static final DeferredBlock<Block> ACCELERATOR_RING_BLOCK = BLOCKS.register("accelerator_ring",
            () -> new dev.modmind.stellar_evolution.particle.AcceleratorRingBlock(properties("accelerator_ring")));

    public static final DeferredItem<BlockItem> ORE_LEACHER_ITEM = item(FissionMachineKind.ORE_LEACHER);
    public static final DeferredItem<BlockItem> CENTRIFUGE_ITEM = item(FissionMachineKind.CENTRIFUGE);
    public static final DeferredItem<BlockItem> CANNING_MACHINE_ITEM = item(FissionMachineKind.CANNING_MACHINE);
    public static final DeferredItem<BlockItem> REPROCESSOR_ITEM = item(FissionMachineKind.REPROCESSOR);
    public static final DeferredItem<BlockItem> MUON_FUSION_ITEM = item(FissionMachineKind.MUON_FUSION);
    public static final DeferredItem<BlockItem> CYCLOTRON_ITEM = item(FissionMachineKind.CYCLOTRON);
    public static final DeferredItem<BlockItem> PARTICLE_SOURCE_ITEM = item(FissionMachineKind.PARTICLE_SOURCE);
    public static final DeferredItem<BlockItem> IRRADIATION_CHAMBER_ITEM = item(FissionMachineKind.IRRADIATION_CHAMBER);
    public static final DeferredItem<BlockItem> COLLIDER_ITEM = item(FissionMachineKind.COLLIDER);
    public static final DeferredItem<BlockItem> ADS_ITEM = item(FissionMachineKind.ADS);
    public static final DeferredItem<BlockItem> ANNIHILATION_REACTOR_ITEM =
            item(FissionMachineKind.ANNIHILATION_REACTOR);
    public static final DeferredItem<BlockItem> ACCELERATOR_RING_ITEM = ITEMS.register("accelerator_ring",
            key -> new BlockItem(ACCELERATOR_RING_BLOCK.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    // ---- 旧存档兼容：旧 id 仍注册，无物品，按新机种处理
    public static final DeferredBlock<FissionMachineBlock> LEGACY_GAS_CENTRIFUGE =
            legacyMachine("gas_centrifuge_bank", FissionMachineKind.CENTRIFUGE);
    public static final DeferredBlock<FissionMachineBlock> LEGACY_FUEL_ASSEMBLER =
            legacyMachine("fuel_assembler", FissionMachineKind.CANNING_MACHINE);
    /** 旧物品 id 也保留（旧存档背包能解码）。 */
    public static final DeferredItem<BlockItem> LEGACY_GAS_CENTRIFUGE_ITEM =
            legacyItem("gas_centrifuge_bank", LEGACY_GAS_CENTRIFUGE);
    public static final DeferredItem<BlockItem> LEGACY_FUEL_ASSEMBLER_ITEM =
            legacyItem("fuel_assembler", LEGACY_FUEL_ASSEMBLER);

    /** 四台燃料循环机器共用一个方块实体类型（缪子聚变仪有独立实体，见 {@link #MUON_ENTITY}）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FissionMachineBlockEntity>> MACHINE_ENTITY =
            BLOCK_ENTITIES.register("fission_machine", () -> new BlockEntityType<>(FissionMachineBlockEntity::new,
                    ORE_LEACHER_BLOCK.get(),
                    CENTRIFUGE_BLOCK.get(), CANNING_MACHINE_BLOCK.get(),
                    REPROCESSOR_BLOCK.get(),
                    LEGACY_GAS_CENTRIFUGE.get(), LEGACY_FUEL_ASSEMBLER.get()));

    /** 缪子聚变仪：独立方块实体（点火 / 温度 / 稳定值 / 粒子流 / 蒸汽）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.modmind.stellar_evolution.muon.MuonFusionBlockEntity>> MUON_ENTITY =
            BLOCK_ENTITIES.register("muon_fusion", () -> new BlockEntityType<>(
                    dev.modmind.stellar_evolution.muon.MuonFusionBlockEntity::new, MUON_FUSION_BLOCK.get()));

    /** 回旋加速器：独立方块实体（差速充能 / 配方结算）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.modmind.stellar_evolution.hep.CyclotronBlockEntity>> CYCLOTRON_ENTITY =
            BLOCK_ENTITIES.register("cyclotron", () -> new BlockEntityType<>(
                    dev.modmind.stellar_evolution.hep.CyclotronBlockEntity::new, CYCLOTRON_BLOCK.get()));

    /** 粒子源：独立方块实体（压力罐磨损 / 束流输出）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.modmind.stellar_evolution.particle.ParticleSourceBlockEntity>> PARTICLE_SOURCE_ENTITY =
            BLOCK_ENTITIES.register("particle_source", () -> new BlockEntityType<>(
                    dev.modmind.stellar_evolution.particle.ParticleSourceBlockEntity::new, PARTICLE_SOURCE_BLOCK.get()));

    /** 辐照仓：独立方块实体（多路束流的累计与配方分派）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.modmind.stellar_evolution.particle.BeamMachineBlockEntity>> IRRADIATION_CHAMBER_ENTITY =
            BLOCK_ENTITIES.register("irradiation_chamber", () -> new BlockEntityType<>(
                    dev.modmind.stellar_evolution.particle.BeamMachineBlockEntity::new,
                    IRRADIATION_CHAMBER_BLOCK.get()));

    /** 对撞机：同一个方块实体类，独立实体类型（双路进 / μ 子出）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.modmind.stellar_evolution.particle.BeamMachineBlockEntity>> COLLIDER_ENTITY =
            BLOCK_ENTITIES.register("collider", () -> new BlockEntityType<>(
                    dev.modmind.stellar_evolution.particle.BeamMachineBlockEntity::new,
                    COLLIDER_BLOCK.get()));

    /** ADS：束流机器子类（质子流 + 嬗变 + 蒸汽输出）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.modmind.stellar_evolution.particle.AdsBlockEntity>> ADS_ENTITY =
            BLOCK_ENTITIES.register("ads", () -> new BlockEntityType<>(
                    dev.modmind.stellar_evolution.particle.AdsBlockEntity::new, ADS_BLOCK.get()));

    /** 湮灭反应堆：独立实体（反物质燃烧 → 热压差生成端）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.modmind.stellar_evolution.particle.AnnihilationReactorBlockEntity>> ANNIHILATION_ENTITY =
            BLOCK_ENTITIES.register("annihilation_reactor", () -> new BlockEntityType<>(
                    dev.modmind.stellar_evolution.particle.AnnihilationReactorBlockEntity::new,
                    ANNIHILATION_REACTOR_BLOCK.get()));

    private static BlockBehaviour.Properties properties(String name) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                    .forceSolidOn()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name)));
    }

    private static DeferredBlock<FissionMachineBlock> machine(FissionMachineKind kind) {
        DeferredBlock<FissionMachineBlock> block = BLOCKS.register(kind.id(), () -> {
            BlockBehaviour.Properties props = properties(kind.id());
            if (kind == FissionMachineKind.MUON_FUSION) {
                // 缪子聚变仪内部有一颗"人造恒星"：整机自发光，夜里也能看见它在亮
                props = props.lightLevel(state -> 15);
            }
            FissionMachineBlock b = new FissionMachineBlock(props);
            KINDS.put(b, kind);
            return b;
        });
        BLOCKS_BY_KIND.put(kind, block);
        return block;
    }

    private static DeferredItem<BlockItem> item(FissionMachineKind kind) {
        DeferredBlock<? extends FissionMachineBlock> block = BLOCKS_BY_KIND.get(kind);
        return ITEMS.register(kind.id(), key -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .useBlockDescriptionPrefix()));
    }

    /** 旧存档兼容方块：只注册方块并映射到新机种，不产出物品。 */
    private static DeferredBlock<FissionMachineBlock> legacyMachine(String name, FissionMachineKind kind) {
        return BLOCKS.register(name, () -> {
            FissionMachineBlock block = new FissionMachineBlock(properties(name));
            KINDS.put(block, kind);
            return block;
        });
    }

    /** 旧物品 id 的方块物品（不进创造页）。 */
    private static DeferredItem<BlockItem> legacyItem(String name, DeferredBlock<FissionMachineBlock> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .useBlockDescriptionPrefix()));
    }

    /** 某方块属于哪个机种（未知方块按酸浸槽兜底）。 */
    public static FissionMachineKind kindOf(Block block) {
        return KINDS.getOrDefault(block, FissionMachineKind.ORE_LEACHER);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(FissionMachines::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, MACHINE_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MACHINE_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, MUON_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, MUON_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, CYCLOTRON_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, CYCLOTRON_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, PARTICLE_SOURCE_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, PARTICLE_SOURCE_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, IRRADIATION_CHAMBER_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, IRRADIATION_CHAMBER_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, COLLIDER_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, COLLIDER_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ADS_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ADS_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ANNIHILATION_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ANNIHILATION_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
    }

    private FissionMachines() {}
}
