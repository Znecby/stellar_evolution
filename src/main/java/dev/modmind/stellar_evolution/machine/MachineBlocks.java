package dev.modmind.stellar_evolution.machine;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
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
 * 材料加工机械的注册表：破碎机 / 压模机 / 线材轧机（各含方块 / 物品），
 * 外加它们的<strong>隐藏载体方块</strong> —— 只提供 blockstate→模型 的映射，让 {@code client.MachineRenderer}
 * 拿一个 {@code BlockState} 去 {@code submitMovingBlock} 画那些会动的部件（无物品、不进创造页）。
 *
 * <p>三台机器共用{@link MachineBlock}与{@link MachineBlockEntity}，机种由本表按方块实例区分
 * （照石化线 {@code PetroMachineBlocks} 口径）。贴图全部复用原版（铜块 / 铁块 / 黑石 / 金块），零新增贴图。
 */
public final class MachineBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, StellarEvolution.MOD_ID);

    /** 方块 → 机种（注册时填写）。 */
    private static final Map<Block, MachineKind> KINDS = new IdentityHashMap<>();
    /** 载体方块名 → 载体方块（渲染器按部件名取）。 */
    private static final Map<String, Block> CARRIERS = new LinkedHashMap<>();

    private static BlockBehaviour.Properties machineProperties(String name) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                // 非整方块造型：不让邻居消面、不挡光（否则贴着实心块会出「洞」）
                .noOcclusion()
                    .forceSolidOn()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name)));
    }

    private static DeferredBlock<MachineBlock> machine(MachineKind kind) {
        return BLOCKS.register(kind.id(), () -> {
            MachineBlock block = new MachineBlock(machineProperties(kind.id()));
            KINDS.put(block, kind);
            return block;
        });
    }

    private static DeferredItem<BlockItem> item(String name, DeferredBlock<MachineBlock> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .useBlockDescriptionPrefix()));
    }

    // ---- 四台机器（方块 + 物品） ----
    public static final DeferredBlock<MachineBlock> CRUSHER = machine(MachineKind.CRUSHER);
    public static final DeferredBlock<MachineBlock> DIE_PRESS = machine(MachineKind.DIE_PRESS);
    public static final DeferredBlock<MachineBlock> ROLLING_MILL = machine(MachineKind.ROLLING_MILL);
    public static final DeferredBlock<MachineBlock> STEEL_FURNACE = machine(MachineKind.STEEL_FURNACE);

    public static final DeferredItem<BlockItem> CRUSHER_ITEM = item(MachineKind.CRUSHER.id(), CRUSHER);
    public static final DeferredItem<BlockItem> DIE_PRESS_ITEM = item(MachineKind.DIE_PRESS.id(), DIE_PRESS);
    public static final DeferredItem<BlockItem> ROLLING_MILL_ITEM = item(MachineKind.ROLLING_MILL.id(), ROLLING_MILL);
    public static final DeferredItem<BlockItem> STEEL_FURNACE_ITEM =
            item(MachineKind.STEEL_FURNACE.id(), STEEL_FURNACE);

    /** 四台机器共用一个方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> MACHINE_ENTITY =
            BLOCK_ENTITIES.register("machine", () -> new BlockEntityType<>(MachineBlockEntity::new,
                    CRUSHER.get(), DIE_PRESS.get(), ROLLING_MILL.get(), STEEL_FURNACE.get()));

    /** 压模机专用界面（带模具槽）；其它三台继续用原版发射器界面。 */
    public static final DeferredHolder<MenuType<?>, MenuType<DiePressMenu>> DIE_PRESS_MENU =
            MENUS.register("die_press", () -> new MenuType<>(DiePressMenu::new, FeatureFlags.VANILLA_SET));

    // ---- 隐藏载体方块：按动画规格里声明的部件名逐个注册（去重） ----
    static {
        Set<String> names = new LinkedHashSet<>();
        for (MachineKind kind : MachineKind.values()) {
            for (MachinePart part : kind.parts()) {
                names.add(part.carrier());
            }
        }
        for (String name : names) {
            BLOCKS.register(name, () -> {
                Block block = new Block(machineProperties(name));
                CARRIERS.put(name, block);
                return block;
            });
        }
    }

    /** 方块 → 机种。 */
    public static MachineKind kindOf(Block block) {
        MachineKind kind = KINDS.get(block);
        return kind != null ? kind : MachineKind.CRUSHER;
    }

    /** 载体方块名 → 载体方块（渲染器用）。 */
    public static Block carrier(String name) {
        return CARRIERS.get(name);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener(MachineBlocks::onRegisterCapabilities);
    }

    /**
     * 把机内 9 格料仓暴露成标准<strong>物品能力</strong>：Pipez / 机械管等物流模组按
     * {@link Capabilities.Item#BLOCK} 插入；原版漏斗走 {@code Container} 本来就能进。
     */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, MACHINE_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
    }

    private MachineBlocks() {}
}
