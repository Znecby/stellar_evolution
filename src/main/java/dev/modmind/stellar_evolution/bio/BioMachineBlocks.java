package dev.modmind.stellar_evolution.bio;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.pipe.CopperPipeBlock;
import dev.modmind.stellar_evolution.pipe.MachinePipeBlock;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
 * 生化线单方块机器的注册表（2×2×2 放大版 + 多部件动画）。
 * <p>
 * 六台机器：调浆机 / 发酵罐 / 分解仪 / 灭菌罐 / 结晶机 / 生物反应器 —— 都有<b>物品</b>、进创造页、可挖、掉自身。
 * <p>
 * 另有<b>隐藏载体方块</b>（每个可动部件一个，名字见 {@link BioMachineKind} 里的 {@code carrier}）：
 * 无物品、不进创造页，只提供 blockstate→模型 的映射，让 {@code client.BioMachineRenderer} 拿一个
 * {@code BlockState} 去 {@code submitMovingBlock} 画那件会动的部件（照燃烧引擎的 {@code _rotor} 口径）。
 * <p>
 * <strong>机制</strong>：调浆机 / 发酵罐 / 分解仪 / 灭菌罐 / 结晶机目前仍只有模型 + 材质 + 动画（方块实体只维护运行时长）；
 * <strong>生物反应釜</strong>已接入<strong>大机器框架</strong>（热压差消耗端 + 物流能力，见 {@code large} 包）。
 */
public final class BioMachineBlocks {
    public static final String SLURRY_MIXER_NAME = "slurry_mixer";
    public static final String FERMENTER_NAME = "fermenter";
    public static final String DECOMPOSER_NAME = "decomposer";
    public static final String STERILIZER_NAME = "sterilizer";
    public static final String CRYSTALLIZER_NAME = "crystallizer";
    public static final String REACTOR_NAME = "reactor";

    /**
     * 「大型机器」名单：这些机器走大机器框架（只经任意一格任意一面贴铜块接入热压差、贴机器的机械管组成物流网络）。
     * <p><strong>6 台生化机器全部</strong>都按生物反应釜这套做（热阻 2、基准按机种 128–320 HP/t、8 并行、周期跟随配方）。
     */
    private static final Set<BioMachineKind> LARGE_MACHINES = EnumSet.allOf(BioMachineKind.class);


    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 方块 → 动画规格（注册时填写）。 */
    private static final Map<Block, BioMachineKind> KINDS = new IdentityHashMap<>();
    /** 载体方块名 → 载体方块（注册时填写，渲染器按部件名取）。 */
    private static final Map<String, Block> CARRIERS = new LinkedHashMap<>();
    /** 机器 → 它的填充格方块（每种机器一种，名字与机器一致，便于 Jade 识别为整台机器）。 */
    private static final Map<BioMachineKind, DeferredBlock<BioMachineShellBlock>> SHELLS = new EnumMap<>(BioMachineKind.class);

    /** 一台机器占据的 8 格偏移（主方块在 (0,0,0)，其余 7 格由填充格占位）。 */
    public static final int[][] CELL_OFFSETS = {
            {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
            {1, 1, 0}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}
    };

    /**
     * 一个接点：状态属性 + 它贴着的那一格（相对主方块）+ 铜管指向机器的方向。
     * <p>
     * 每个接点<b>独立</b>一个状态 —— 早先按「面」记（每侧 2 个口共用一个标志）时，接一个会让同侧另一个也变。
     */
    public record Port(String name, BooleanProperty property, int dx, int dy, int dz, Direction towardMachine) {}

    /**
     * 12 个接点：四个水平侧面各 2 个（机器 2 格宽，底排每格一个）+ 底面 4 个。
     * <p>
     * 名字（{@code port_<面>_<格>}）同时也是 blockstate 里 {@code when} 条件的属性名，**两处必须一致**。
     */
    public static final java.util.List<Port> PORTS = java.util.List.of(
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

    private static DeferredBlock<BioMachineShellBlock> registerShell(String machineName, BioMachineKind kind) {
        String shellName = machineName + "_shell";
        DeferredBlock<BioMachineShellBlock> block = BLOCKS.register(shellName, () -> new BioMachineShellBlock(
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

    public static final DeferredBlock<BioMachineBlock> SLURRY_MIXER = machine(SLURRY_MIXER_NAME, BioMachineKind.SLURRY_MIXER);
    public static final DeferredBlock<BioMachineBlock> FERMENTER = machine(FERMENTER_NAME, BioMachineKind.FERMENTER);
    public static final DeferredBlock<BioMachineBlock> DECOMPOSER = machine(DECOMPOSER_NAME, BioMachineKind.DECOMPOSER);
    public static final DeferredBlock<BioMachineBlock> STERILIZER = machine(STERILIZER_NAME, BioMachineKind.STERILIZER);
    public static final DeferredBlock<BioMachineBlock> CRYSTALLIZER = machine(CRYSTALLIZER_NAME, BioMachineKind.CRYSTALLIZER);
    public static final DeferredBlock<BioMachineBlock> REACTOR = machine(REACTOR_NAME, BioMachineKind.REACTOR);

    // 6 种填充格（无物品、不可见、不掉落）：名字跟对应机器一致，Jade 会把整台机器认成一个方块名
    public static final DeferredBlock<BioMachineShellBlock> SLURRY_MIXER_SHELL = registerShell(SLURRY_MIXER_NAME, BioMachineKind.SLURRY_MIXER);
    public static final DeferredBlock<BioMachineShellBlock> FERMENTER_SHELL = registerShell(FERMENTER_NAME, BioMachineKind.FERMENTER);
    public static final DeferredBlock<BioMachineShellBlock> DECOMPOSER_SHELL = registerShell(DECOMPOSER_NAME, BioMachineKind.DECOMPOSER);
    public static final DeferredBlock<BioMachineShellBlock> STERILIZER_SHELL = registerShell(STERILIZER_NAME, BioMachineKind.STERILIZER);
    public static final DeferredBlock<BioMachineShellBlock> CRYSTALLIZER_SHELL = registerShell(CRYSTALLIZER_NAME, BioMachineKind.CRYSTALLIZER);
    public static final DeferredBlock<BioMachineShellBlock> REACTOR_SHELL = registerShell(REACTOR_NAME, BioMachineKind.REACTOR);

    public static final DeferredItem<BlockItem> SLURRY_MIXER_ITEM = item(SLURRY_MIXER_NAME, SLURRY_MIXER);
    public static final DeferredItem<BlockItem> FERMENTER_ITEM = item(FERMENTER_NAME, FERMENTER);
    public static final DeferredItem<BlockItem> DECOMPOSER_ITEM = item(DECOMPOSER_NAME, DECOMPOSER);
    public static final DeferredItem<BlockItem> STERILIZER_ITEM = item(STERILIZER_NAME, STERILIZER);
    public static final DeferredItem<BlockItem> CRYSTALLIZER_ITEM = item(CRYSTALLIZER_NAME, CRYSTALLIZER);
    public static final DeferredItem<BlockItem> REACTOR_ITEM = item(REACTOR_NAME, REACTOR);

    // ---- 旧存档兼容：旧的 bioreactor / bioreactor_shell 仍注册，但无物品、按反应器处理（不覆盖新 SHELLS 映射）
    public static final DeferredBlock<BioMachineBlock> LEGACY_BIOREACTOR =
            machine("bioreactor", BioMachineKind.REACTOR);
    public static final DeferredBlock<BioMachineShellBlock> LEGACY_BIOREACTOR_SHELL =
            legacyShell("bioreactor_shell");
    /** 旧物品 id 也保留（旧存档背包能解码；放出来仍是能用的反应器）。 */
    public static final DeferredItem<BlockItem> LEGACY_BIOREACTOR_ITEM = item("bioreactor", LEGACY_BIOREACTOR);

    /** 六台机器共用一个方块实体类型（渲染器 / ticker 都按它注册）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BioMachineBlockEntity>> BIO_MACHINE_ENTITY =
            BLOCK_ENTITIES.register("bio_machine", () -> new BlockEntityType<>(BioMachineBlockEntity::new,
                    SLURRY_MIXER.get(), FERMENTER.get(), DECOMPOSER.get(), STERILIZER.get(), CRYSTALLIZER.get(), REACTOR.get(),
                    LEGACY_BIOREACTOR.get()));

    /**
     * 填充格的方块实体类型（与主方块分开）：只把机器本体的物品 / 流体能力转发出去，
     * 让「所有部分都视作本体」—— 瞄任意一格都能看到同样的物品 / 流体存量。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BioMachineShellBlockEntity>> BIO_MACHINE_SHELL_ENTITY =
            BLOCK_ENTITIES.register("bio_machine_shell", () -> new BlockEntityType<>(BioMachineShellBlockEntity::new,
                    SLURRY_MIXER_SHELL.get(), FERMENTER_SHELL.get(), DECOMPOSER_SHELL.get(), STERILIZER_SHELL.get(),
                    CRYSTALLIZER_SHELL.get(), REACTOR_SHELL.get(), LEGACY_BIOREACTOR_SHELL.get()));

    // ---- 隐藏载体方块：按动画规格里声明的部件名逐个注册（12 个） ----
    static {
        for (BioMachineKind kind : BioMachineKind.values()) {
            for (BioMachinePart part : kind.parts()) {
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

    private static DeferredBlock<BioMachineBlock> machine(String name, BioMachineKind kind) {
        return BLOCKS.register(name, () -> {
            BioMachineBlock block = new BioMachineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(name))));
            KINDS.put(block, kind);
            return block;
        });
    }

    private static DeferredItem<BlockItem> item(String name, DeferredBlock<BioMachineBlock> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .useBlockDescriptionPrefix()));
    }

    /** 旧存档兼容填充格：只注册方块，不写 SHELLS（否则会把新机器的填充格映射顶掉）。 */
    private static DeferredBlock<BioMachineShellBlock> legacyShell(String shellName) {
        return BLOCKS.register(shellName, () -> new BioMachineShellBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(3.5F, 6.0F)
                        .noOcclusion()
                    .forceSolidOn()
                        .noLootTable()
                        .randomTicks()
                        .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(shellName)))));
    }

    /** 方块 → 动画规格。 */
    public static BioMachineKind kindOf(Block block) {
        BioMachineKind kind = KINDS.get(block);
        return kind != null ? kind : BioMachineKind.SLURRY_MIXER;
    }

    /** 这台机器是不是「大型机器」（走大机器框架）。 */
    public static boolean isLargeMachine(Block block) {
        return LARGE_MACHINES.contains(kindOf(block));
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
    public static int fillShells(Level level, BlockPos origin, BioMachineKind kind) {
        DeferredBlock<BioMachineShellBlock> shell = SHELLS.get(kind);
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
            if (level.getBlockState(pos).getBlock() instanceof BioMachineShellBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** 旧存档 / 自愈调用：朝向缺失时按北处理。 */
    public static Direction facingOf(BlockGetter level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        return state.hasProperty(BioMachineBlock.FACING) ? state.getValue(BioMachineBlock.FACING) : Direction.NORTH;
    }

    /**
     * 扫一遍机器外沿的 12 个接管口格子，把「该面是否接着铜管」写回本方块的状态。
     * <p>
     * 为什么要轮询而不是靠 {@code updateShape}：机器占 2×2×2，而主方块只占其中一格 ——
     * 东/南面（+X/+Z）和底面另外 3 格外面贴的管子，主方块**根本收不到邻居更新**，
     * 所以由服务端每 tick 扫一圈（12 次查方块，代价可忽略），有变化才写状态。
     *
     * @return 是否真的改了状态
     */
    public static boolean refreshPorts(Level level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof BioMachineBlock)) {
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

    /** 强制重算这 8 格的光照（修复历史存档里「旧填充格挡光」留下的暗块）。 */
    public static void refreshLight(Level level, BlockPos origin) {
        for (int[] offset : CELL_OFFSETS) {
            level.getLightEngine().checkBlock(origin.offset(offset[0], offset[1], offset[2]));
        }
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
                    if (level.getBlockState(candidate).getBlock() instanceof BioMachineBlock) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(BioMachineBlocks::onRegisterCapabilities);
    }

    /**
     * 方块能力注册：把「大型机器」的物流能力接进 NeoForge 物品 / 流体链。
     * <p>只有生物反应釜（大型机器）会返回非 null 的能力；其余机器返回 null ⇒ 不暴露能力。
     */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, BIO_MACHINE_ENTITY.get(),
                (machine, side) -> machine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BIO_MACHINE_ENTITY.get(),
                (machine, side) -> machine.fluidCapability());
        // 填充格：转发机器本体的能力，这样瞄任意一格都看得到同样的物品 / 流体
        event.registerBlockEntity(Capabilities.Item.BLOCK, BIO_MACHINE_SHELL_ENTITY.get(),
                (shell, side) -> shell.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BIO_MACHINE_SHELL_ENTITY.get(),
                (shell, side) -> shell.fluidCapability());
    }

    private BioMachineBlocks() {}
}
