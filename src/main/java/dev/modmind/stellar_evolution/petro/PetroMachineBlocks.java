package dev.modmind.stellar_evolution.petro;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.pipe.CopperPipeBlock;
import dev.modmind.stellar_evolution.pipe.MachinePipeBlock;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * 石化线单方块机器的注册表（<strong>2×2×2 整机</strong> + 多部件动画）。
 *
 * <p>照生化线 {@code BioMachineBlocks} 口径 —— 六台机器（分馏塔 / 裂解炉 / 重整器 / 聚合釜 +
 * 煤化工的气化炉 / 合成塔）都有<b>物品</b>、进创造页、可挖、掉自身；另有<b>隐藏载体方块</b>（每个可动部件一个，无物品、不进创造页），
 * 只提供 blockstate→模型 的映射，让 {@code client.PetroMachineRenderer} 拿一个 {@code BlockState}
 * 去 {@code submitMovingBlock} 画那件会动的部件。
 *
 * <p><strong>机制</strong>：六台机器<strong>全部</strong>接入「大机器框架」（热压差消耗端 + 铜箱子 / 储罐物流，
 * 见 {@link PetroMachineBlockEntity}），按机种结算 {@link PetroRecipes}。
 */
public final class PetroMachineBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 方块 → 机种（注册时填写）。 */
    private static final Map<Block, PetroMachineKind> KINDS = new IdentityHashMap<>();
    /** 载体方块名 → 载体方块（渲染器按部件名取）。 */
    private static final Map<String, Block> CARRIERS = new LinkedHashMap<>();
    /** 机器 → 它的填充格方块（每种机器一种，名字与其他机器一致便于 Jade 识别为整台机器）。 */
    private static final Map<PetroMachineKind, DeferredBlock<PetroMachineShellBlock>> SHELLS =
            new EnumMap<>(PetroMachineKind.class);

    /** 一台机器占据的 8 格偏移（主方块在 (0,0,0)，其余 7 格由填充格占位）。 */
    public static final int[][] CELL_OFFSETS = {
            {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
            {1, 1, 0}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}
    };

    // ------------------------------------------------------------------ 接管口（照生化线 BioMachineBlocks 口径）
    // 每个接点<b>独立</b>一个状态 —— 早先按「面」记（每侧 2 个口共用一个标志）时，接一个会让同侧另一个也变。

    /** 一个接点：状态属性 + 它贴着的那一格（相对主方块）+ 铜管指向机器的方向。 */
    public record Port(String name, BooleanProperty property, int dx, int dy, int dz, Direction towardMachine) {}

    /**
     * 12 个接点：四个水平侧面各 2 个（机器 2 格宽，底排每格一个）+ 底面 4 个。
     * <p>名字（{@code port_<面>_<格>}）同时也是 blockstate 里 {@code when} 条件的属性名、以及生成脚本
     * {@code gen_petro_machines.ps1} 里的接点名 —— <b>三处必须一致</b>。
     */
    public static final List<Port> PORTS = List.of(
            new Port("port_north_a", BooleanProperty.create("port_north_a"), 0, 0, -1, Direction.SOUTH),
            new Port("port_north_b", BooleanProperty.create("port_north_b"), 1, 0, -1, Direction.SOUTH),
            new Port("port_east_a", BooleanProperty.create("port_east_a"), 2, 0, 0, Direction.WEST),
            new Port("port_east_b", BooleanProperty.create("port_east_b"), 2, 0, 1, Direction.WEST),
            new Port("port_south_a", BooleanProperty.create("port_south_a"), 0, 0, 2, Direction.NORTH),
            new Port("port_south_b", BooleanProperty.create("port_south_b"), 1, 0, 2, Direction.NORTH),
            new Port("port_west_a", BooleanProperty.create("port_west_a"), -1, 0, 0, Direction.EAST),
            new Port("port_west_b", BooleanProperty.create("port_west_b"), -1, 0, 1, Direction.EAST),
            new Port("port_down_a", BooleanProperty.create("port_down_a"), 0, -1, 0, Direction.UP),
            new Port("port_down_b", BooleanProperty.create("port_down_b"), 1, -1, 0, Direction.UP),
            new Port("port_down_c", BooleanProperty.create("port_down_c"), 0, -1, 1, Direction.UP),
            new Port("port_down_d", BooleanProperty.create("port_down_d"), 1, -1, 1, Direction.UP));

    /** 12 个接点状态属性（名字 → 属性）。模型 blockstate 按它在该口切「开口 / 已接」两态。 */
    public static final Map<String, BooleanProperty> PORT_PROPERTIES = buildPortProperties();

    private static Map<String, BooleanProperty> buildPortProperties() {
        Map<String, BooleanProperty> map = new LinkedHashMap<>();
        for (Port port : PORTS) {
            map.put(port.name(), port.property());
        }
        return map;
    }

    /**
     * 扫一遍机器外沿的 12 个接管口格子，把「该面是否接着铜管」写回本方块的状态。
     * <p>为什么要轮询而不是靠 {@code updateShape}：机器占 2×2×2，而主方块只占其中一格 ——
     * 东/南面（+X/+Z）和底面另外 3 格外面贴的管子，主方块<b>根本收不到邻居更新</b>，
     * 所以由服务端每 tick 扫一圈（12 次查方块，代价可忽略），有变化才写状态。
     *
     * @return 是否真的改了状态
     */
    public static boolean refreshPorts(Level level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof PetroMachineBlock)) {
            return false;
        }
        BlockState updated = state;
        for (Port port : PORTS) {
            BlockPos portPos = origin.offset(port.dx(), port.dy(), port.dz());
            BlockState pipeState = level.getBlockState(portPos);
            boolean connected = (pipeState.getBlock() instanceof MachinePipeBlock
                    && pipeState.getValue(MachinePipeBlock.CONNECTIONS.get(port.towardMachine())))
                    || (pipeState.getBlock() instanceof CopperPipeBlock
                    && CopperPipeBlock.connectsToMachine(level, portPos,
                    portPos.relative(port.towardMachine()), port.towardMachine()));
            if (updated.getValue(port.property()) != connected) {
                updated = updated.setValue(port.property(), connected);
            }
        }
        if (updated == state) {
            return false;
        }
        level.setBlock(origin, updated, Block.UPDATE_ALL);
        return true;
    }

    private static BlockBehaviour.Properties machineProperties(String name) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                    .forceSolidOn()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name)));
    }

    private static DeferredBlock<PetroMachineBlock> machine(PetroMachineKind kind) {
        return BLOCKS.register(kind.id(), () -> {
            PetroMachineBlock block = new PetroMachineBlock(machineProperties(kind.id()));
            KINDS.put(block, kind);
            return block;
        });
    }

    private static DeferredBlock<PetroMachineShellBlock> registerShell(String machineName, PetroMachineKind kind) {
        String shellName = machineName + "_shell";
        DeferredBlock<PetroMachineShellBlock> block = BLOCKS.register(shellName, () -> new PetroMachineShellBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(3.5F, 6.0F)
                        .noOcclusion()
                    .forceSolidOn()
                        .noLootTable()
                        .randomTicks()   // 供「孤儿填充格」自检消失
                        .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(shellName)))));
        SHELLS.put(kind, block);
        return block;
    }

    private static DeferredItem<BlockItem> item(String name, DeferredBlock<PetroMachineBlock> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .useBlockDescriptionPrefix()));
    }

    // ---- 六台机器（主方块 + 填充格 + 物品） ----
    public static final DeferredBlock<PetroMachineBlock> DISTILLATION_TOWER = machine(PetroMachineKind.DISTILLATION_TOWER);
    public static final DeferredBlock<PetroMachineBlock> CRACKING_FURNACE = machine(PetroMachineKind.CRACKING_FURNACE);
    public static final DeferredBlock<PetroMachineBlock> REFORMER = machine(PetroMachineKind.REFORMER);
    public static final DeferredBlock<PetroMachineBlock> POLYMERIZER = machine(PetroMachineKind.POLYMERIZER);
    // 煤化工线（C 线）
    public static final DeferredBlock<PetroMachineBlock> GASIFIER = machine(PetroMachineKind.GASIFIER);
    public static final DeferredBlock<PetroMachineBlock> SYNTHESIS_TOWER = machine(PetroMachineKind.SYNTHESIS_TOWER);

    public static final DeferredBlock<PetroMachineShellBlock> DISTILLATION_TOWER_SHELL =
            registerShell(PetroMachineKind.DISTILLATION_TOWER.id(), PetroMachineKind.DISTILLATION_TOWER);
    public static final DeferredBlock<PetroMachineShellBlock> CRACKING_FURNACE_SHELL =
            registerShell(PetroMachineKind.CRACKING_FURNACE.id(), PetroMachineKind.CRACKING_FURNACE);
    public static final DeferredBlock<PetroMachineShellBlock> REFORMER_SHELL =
            registerShell(PetroMachineKind.REFORMER.id(), PetroMachineKind.REFORMER);
    public static final DeferredBlock<PetroMachineShellBlock> POLYMERIZER_SHELL =
            registerShell(PetroMachineKind.POLYMERIZER.id(), PetroMachineKind.POLYMERIZER);
    public static final DeferredBlock<PetroMachineShellBlock> GASIFIER_SHELL =
            registerShell(PetroMachineKind.GASIFIER.id(), PetroMachineKind.GASIFIER);
    public static final DeferredBlock<PetroMachineShellBlock> SYNTHESIS_TOWER_SHELL =
            registerShell(PetroMachineKind.SYNTHESIS_TOWER.id(), PetroMachineKind.SYNTHESIS_TOWER);

    public static final DeferredItem<BlockItem> DISTILLATION_TOWER_ITEM = item(PetroMachineKind.DISTILLATION_TOWER.id(), DISTILLATION_TOWER);
    public static final DeferredItem<BlockItem> CRACKING_FURNACE_ITEM = item(PetroMachineKind.CRACKING_FURNACE.id(), CRACKING_FURNACE);
    public static final DeferredItem<BlockItem> REFORMER_ITEM = item(PetroMachineKind.REFORMER.id(), REFORMER);
    public static final DeferredItem<BlockItem> POLYMERIZER_ITEM = item(PetroMachineKind.POLYMERIZER.id(), POLYMERIZER);
    public static final DeferredItem<BlockItem> GASIFIER_ITEM = item(PetroMachineKind.GASIFIER.id(), GASIFIER);
    public static final DeferredItem<BlockItem> SYNTHESIS_TOWER_ITEM =
            item(PetroMachineKind.SYNTHESIS_TOWER.id(), SYNTHESIS_TOWER);

    /** 全部机器的主方块物品（创造页按序陈列）。 */
    public static final java.util.List<DeferredItem<BlockItem>> MACHINE_ITEMS = java.util.List.of(
            DISTILLATION_TOWER_ITEM, CRACKING_FURNACE_ITEM, REFORMER_ITEM,
            POLYMERIZER_ITEM,
            GASIFIER_ITEM, SYNTHESIS_TOWER_ITEM);

    /** 六台机器共用一个方块实体类型。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PetroMachineBlockEntity>> PETRO_MACHINE_ENTITY =
            BLOCK_ENTITIES.register("petro_machine", () -> new BlockEntityType<>(PetroMachineBlockEntity::new,
                    DISTILLATION_TOWER.get(), CRACKING_FURNACE.get(), REFORMER.get(),
                    POLYMERIZER.get(),
                    GASIFIER.get(), SYNTHESIS_TOWER.get()));

    /** 填充格的方块实体类型（与主方块分开）：只把机器本体的物品 / 流体能力转发出去。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PetroMachineShellBlockEntity>> PETRO_MACHINE_SHELL_ENTITY =
            BLOCK_ENTITIES.register("petro_machine_shell", () -> new BlockEntityType<>(PetroMachineShellBlockEntity::new,
                    DISTILLATION_TOWER_SHELL.get(), CRACKING_FURNACE_SHELL.get(), REFORMER_SHELL.get(),
                    POLYMERIZER_SHELL.get(),
                    GASIFIER_SHELL.get(), SYNTHESIS_TOWER_SHELL.get()));

    // ---- 隐藏载体方块：按动画规格里声明的部件名逐个注册（每台 3 个） ----
    static {
        for (PetroMachineKind kind : PetroMachineKind.values()) {
            for (PetroMachinePart part : kind.parts()) {
                String name = part.carrier();
                BLOCKS.register(name, () -> {
                    Block block = new Block(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 6.0F)
                            .noOcclusion()
                    .forceSolidOn()
                            .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name))));
                    CARRIERS.put(name, block);
                    return block;
                });
            }
        }
    }

    /** 方块 → 机种。 */
    public static PetroMachineKind kindOf(Block block) {
        PetroMachineKind kind = KINDS.get(block);
        return kind != null ? kind : PetroMachineKind.DISTILLATION_TOWER;
    }

    /** 这个方块是不是石化机器（主方块）。 */
    public static boolean isMachine(Block block) {
        return KINDS.containsKey(block);
    }

    /** 载体方块名 → 载体方块（渲染器用）。 */
    public static Block carrier(String name) {
        return CARRIERS.get(name);
    }

    /** 8 格里除主方块外的 7 格是否都能被占用（放机器前的检查）。 */
    public static boolean canOccupy(BlockGetter level, BlockPos origin) {
        for (int[] offset : CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
            if (!level.getBlockState(pos).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** 把一台机器占的另外 7 格填成不可见填充格（凑齐完整 8 格碰撞）；返回本次补了几格。 */
    public static int fillShells(Level level, BlockPos origin, PetroMachineKind kind) {
        DeferredBlock<PetroMachineShellBlock> shell = SHELLS.get(kind);
        if (shell == null) {
            return 0;
        }
        int filled = 0;
        for (int[] offset : CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
            if (level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, shell.get().defaultBlockState(), 3);
                filled++;
            }
        }
        return filled;
    }

    /** 清掉一台机器占的另外 7 格填充格（不区分种类）。 */
    public static void clearShells(Level level, BlockPos origin) {
        for (int[] offset : CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
            if (level.getBlockState(pos).getBlock() instanceof PetroMachineShellBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** 旧存档 / 自愈调用：朝向缺失时按北处理。 */
    public static Direction facingOf(BlockGetter level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        return state.hasProperty(PetroMachineBlock.FACING) ? state.getValue(PetroMachineBlock.FACING) : Direction.NORTH;
    }

    /** 从某个填充格出发，找出它所属机器的主方块位置（破坏填充格时用）。 */
    @Nullable
    public static BlockPos findMachine(BlockGetter level, BlockPos shellPos) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos candidate = shellPos.offset(-dx, -dy, -dz);
                    if (level.getBlockState(candidate).getBlock() instanceof PetroMachineBlock) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** 强制重算这 8 格的光照（修复历史存档里「旧填充格挡光」留下的暗块）。 */
    public static void refreshLight(Level level, BlockPos origin) {
        for (int[] offset : CELL_OFFSETS) {
            level.getLightEngine().checkBlock(origin.offset(offset[0], offset[1], offset[2]));
        }
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(PetroMachineBlocks::onRegisterCapabilities);
    }

    /** 方块能力注册：大机器物流能力（物品 / 流体）+ 填充格的能力转发。 */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, PETRO_MACHINE_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, PETRO_MACHINE_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, PETRO_MACHINE_SHELL_ENTITY.get(),
                (shell, side) -> shell.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, PETRO_MACHINE_SHELL_ENTITY.get(),
                (shell, side) -> shell.fluidCapability());
    }

    private PetroMachineBlocks() {}
}
