package dev.znecby.stellar_evolution.fission;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.large.LargeMachineStructure;
import dev.znecby.stellar_evolution.large.LargeMachineStructureOps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * 裂变线机器的<strong>2×2×2 结构公共层</strong>（照石化线 {@code PetroMachineBlocks} 口径）。
 *
 * <p>六台机器（酸浸槽 / 氟化炉 / 气体离心机群 / 燃料组装机 / 水冷反应堆 / 蒸汽轮机）共用：
 * <ul>
 *   <li><strong>占位</strong>：主方块在最小角，其余 7 格由不可见填充格 {@link FissionMachineShellBlock} 占位；</li>
 *   <li><strong>接管口</strong>：12 个独立状态（四侧各 2 + 底面 4），由服务端轮询铜管连接刷新；</li>
 *   <li><strong>可动部件规格</strong>：每个部件一个隐藏载体方块，渲染器按规格旋转；</li>
 *   <li><strong>能力转发</strong>：填充格的方块实体把物品 / 流体能力转给主方块。</li>
 * </ul>
 *
 * <p>只依赖原版类型，<strong>公共侧安全</strong>。
 */
public final class FissionMachineStructure {
    /** 一台机器占据的 8 格偏移（主方块在 (0,0,0)）。 */
    public static final int[][] CELL_OFFSETS = LargeMachineStructure.CELL_OFFSETS;

    // ------------------------------------------------------------------ 接管口（12 个，照石化线几何）

    /** 一个接点：状态属性 + 它贴着的那一格（相对主方块）+ 铜管指向机器的方向。 */
    public record Port(String name, BooleanProperty property, int dx, int dy, int dz, Direction towardMachine)
            implements LargeMachineStructureOps.BoundPort {}

    public static final List<Port> PORTS = LargeMachineStructure.PORTS.stream()
            .map(spec -> new Port(spec.name(), BooleanProperty.create(spec.name()),
                    spec.dx(), spec.dy(), spec.dz(), spec.towardMachine()))
            .toList();

    /** 名字 → 状态属性；模型 blockstate 按它切「开口 / 已接」两态。 */
    public static final Map<String, BooleanProperty> PORT_PROPERTIES = buildPortProperties();

    private static Map<String, BooleanProperty> buildPortProperties() {
        Map<String, BooleanProperty> map = new LinkedHashMap<>();
        for (Port port : PORTS) {
            map.put(port.name(), port.property());
        }
        return map;
    }

    // ------------------------------------------------------------------ 填充格 / 载体方块注册

    /** 填充格方块实体类型 id（方块本身按机器分别注册，见下面的 {@code SHELLS}）。 */
    public static final String SHELL_ENTITY_ID = "fission_machine_shell";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    // ------------------------------------------------------------------ 可动部件规格

    private static final Map<String, List<FissionMachinePart>> PARTS = new LinkedHashMap<>();
    private static final Map<String, Block> CARRIERS = new LinkedHashMap<>();

    static {
        parts("ore_leacher",
                new FissionMachinePart("ore_leacher_agitator", Direction.Axis.Y, 4.0F, false, 0F, 16F, 16F, 16F));
        // 氟化炉已于 2026-11-11 并入 ore_leacher（化学处理槽），其风扇 / 压力表载体一并移除。
        parts("fission_centrifuge",
                new FissionMachinePart("gas_centrifuge_rotor_a", Direction.Axis.Y, 10.0F, false, 0F, 10F, 16F, 10F),
                new FissionMachinePart("gas_centrifuge_rotor_b", Direction.Axis.Y, 10.0F, false, 0F, 22F, 16F, 10F),
                new FissionMachinePart("gas_centrifuge_fan", Direction.Axis.Y, 14.0F, false, 0F, 16F, 16F, 16F));
        parts("canning_machine",
                new FissionMachinePart("fuel_assembler_flywheel", Direction.Axis.Y, 4.0F, false, 0F, 16F, 16F, 16F),
                new FissionMachinePart("fuel_assembler_dial", Direction.Axis.Z, 3.0F, true, 25.0F, 8F, 16F, 3.35F));
        // 后处理厂：先复用罐装机的飞轮 / 压力表外观，后续再换专属模型
        parts("reprocessor",
                new FissionMachinePart("fuel_assembler_flywheel", Direction.Axis.Y, 4.0F, false, 0F, 16F, 16F, 16F),
                new FissionMachinePart("fuel_assembler_dial", Direction.Axis.Z, 3.0F, true, 25.0F, 8F, 16F, 3.35F));
        // 缪子聚变仪：球体固定不动，18 颗插栓各自沿所在夹持板的法线反复"深入 / 抽出"
        parts("muon_fusion", muonBolts());
        parts("water_reactor",
                new FissionMachinePart("water_reactor_crd", Direction.Axis.Y, 0.8F, false, 0F, 16F, 16F, 16F),
                new FissionMachinePart("water_reactor_pump", Direction.Axis.Y, 12.0F, false, 0F, 25.6F, 10F, 25.6F),
                new FissionMachinePart("water_reactor_gauge", Direction.Axis.Z, 3.0F, true, 25.0F, 8F, 16F, 3.35F));
        // 裂变堆（原水冷反应堆）：沿用同一套可动部件
        parts("fission_reactor",
                new FissionMachinePart("water_reactor_crd", Direction.Axis.Y, 0.8F, false, 0F, 16F, 16F, 16F),
                new FissionMachinePart("water_reactor_pump", Direction.Axis.Y, 12.0F, false, 0F, 25.6F, 10F, 25.6F),
                new FissionMachinePart("water_reactor_gauge", Direction.Axis.Z, 3.0F, true, 25.0F, 8F, 16F, 3.35F));
        parts("steam_turbine_unit",
                new FissionMachinePart("steam_turbine_rotor", Direction.Axis.X, 18.0F, false, 0F, 16F, 16F, 16F),
                new FissionMachinePart("steam_turbine_governor", Direction.Axis.Y, 5.0F, false, 0F, 24F, 26F, 16F),
                new FissionMachinePart("steam_turbine_gauge", Direction.Axis.Z, 3.0F, true, 30.0F, 8F, 16F, 3.35F));
        // 回旋加速器：顶部抽风扇（复用离心机风扇载体）+ 控制箱顶面前缘的偏转磁铁（Z 小角度摆动）
        parts("cyclotron",
                new FissionMachinePart("gas_centrifuge_fan", Direction.Axis.Y, 14.0F, false, 0F, 16F, 27F, 16F),
                new FissionMachinePart("cyclotron_deflector", Direction.Axis.Z, 2.5F, true, 22.0F,
                        8.5F, 15.35F, 2.28F));
        // 粒子源：涡轮分子泵风扇（Y）+ 南端引出电极轮（Z）+ 东侧冷却风机（X）
        parts("particle_source",
                new FissionMachinePart("particle_source_turbofan", Direction.Axis.Y, 14.0F, false, 0F, 16F, 23.9F, 20.9F),
                new FissionMachinePart("particle_source_wheel", Direction.Axis.Z, 26.0F, false, 0F, 16F, 13F, 30.2F),
                new FissionMachinePart("particle_source_cooler", Direction.Axis.X, 12.0F, false, 0F, 21.2F, 13F, 24.6F),
                new FissionMachinePart("particle_source_cooler_w", Direction.Axis.X, 11.0F, false, 0F, 10.4F, 13F, 26F),
                new FissionMachinePart("particle_source_gasvalve", Direction.Axis.Z, 3.6F, false, 0F, 16F, 12F, 0.4F));
        // 辐照仓：顶部靶盘（Y 慢转）+ 烟囱风扇（Y）+ 前部剂量表针（Z 摆动）
        parts("irradiation_chamber",
                new FissionMachinePart("irradiation_chamber_carousel", Direction.Axis.Y, 7.0F, false, 0F, 16F, 22.4F, 16F),
                new FissionMachinePart("irradiation_chamber_fan", Direction.Axis.Y, 14.0F, false, 0F, 22.8F, 27.4F, 22.8F),
                new FissionMachinePart("irradiation_chamber_gauge", Direction.Axis.Z, 3.0F, true, 25.0F,
                        8.8F, 15.8F, 4.63F),
                new FissionMachinePart("irradiation_chamber_shutter", Direction.Axis.Z, 2.2F, true, 28.0F,
                        16F, 12.9F, 30.3F),
                new FissionMachinePart("irradiation_chamber_scanner", Direction.Axis.X, 10.0F, false, 0F,
                        27F, 13F, 10.5F));
        // 对撞机：两束束流脉冲沿 Z 相向而行 + 顶部低温风扇（Y）+ 电源柜电流表针（Z 摆动）
        parts("collider",
                new FissionMachinePart("collider_beam_a", Direction.Axis.Z, 6.0F, false, 6.0F,
                        16F, 13.5F, 10.2F, true, 0F),
                new FissionMachinePart("collider_beam_b", Direction.Axis.Z, 6.0F, false, 6.0F,
                        16F, 13.5F, 22.0F, true, 3.1416F),
                new FissionMachinePart("collider_cryofan", Direction.Axis.Y, 13.0F, false, 0F, 16F, 23.2F, 16F),
                new FissionMachinePart("collider_gauge", Direction.Axis.Z, 3.0F, true, 25.0F, 8.7F, 11.6F, 3.48F),
                new FissionMachinePart("collider_shutter_n", Direction.Axis.Z, 2.4F, true, 30.0F, 16F, 13.5F, 1.0F),
                new FissionMachinePart("collider_shutter_s", Direction.Axis.Z, 6.0F, false, 0F, 16F, 13.5F, 30.4F));
        // 湮灭反应堆：三期 E5 起换专属外观（中央约束核心 + 双环），不再沿用对撞机的可动束流脉冲 → 无部件
        // ADS：三期 E5 起换专属外观（屏蔽体 + 靶盘），原辐照仓的靶盘 / 风扇 / 剂量表随之移除 → 无部件
        // 仍要登记空列表：填充格（`*_shell`）按 PARTS 的 key 注册，缺了它们旧存档的填充格会变成未知方块
        parts("annihilation_reactor");
        parts("ads");

        // 每个可动部件一个隐藏载体方块（无物品、不进创造页），只提供 blockstate → 模型 的映射
        // 注意：同名载体只注册一次（裂变堆沿用旧水冷堆的部件名）
        java.util.Set<String> registered = new java.util.HashSet<>();
        for (List<FissionMachinePart> list : PARTS.values()) {
            for (FissionMachinePart part : list) {
                String name = part.carrier();
                if (!registered.add(name)) {
                    continue;
                }
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

    private static void parts(String machineId, FissionMachinePart... parts) {
        PARTS.put(machineId, List.of(parts));
    }

    /**
     * 缪子聚变仪 18 颗插栓：与 {@code muon_bolt_0..17} 载体一一对应
     * （+X -X +Y -Y +Z -Z，每个夹持板 3 颗，顺序与资源生成脚本一致）。
     *
     * <p>每颗的转速 / 初相都不同，于是看起来是错相、无规律地反复深入抽出；
     * {@code amplitude} 取负号是让负方向那三面同样"向外抽出 / 向内收入"。
     */
    private static FissionMachinePart[] muonBolts() {
        Direction.Axis[] axes = {
                Direction.Axis.X, Direction.Axis.X,
                Direction.Axis.Y, Direction.Axis.Y,
                Direction.Axis.Z, Direction.Axis.Z};
        FissionMachinePart[] bolts = new FissionMachinePart[18];
        for (int i = 0; i < bolts.length; i++) {
            boolean positive = (i / 3) % 2 == 0;
            float speed = 1.6F + (float) (i * 1.37 % 4.4);   // 1.6~6.0 度/tick → 周期约 3~11 秒
            float amplitude = positive ? 0.5F : -0.5F;       // 模型单位（1px = 1/16 格）：抽到头也不脱开夹持板
            bolts[i] = new FissionMachinePart("muon_bolt_" + i, axes[i / 3], speed, true, amplitude,
                    0F, 0F, 0F, true, i * 2.399F);
        }
        return bolts;
    }

    // ------------------------------------------------------------------ 每台机器一个填充格

    /**
     * 机器 id → 它自己的填充格方块。
     *
     * <p>照石化 / 生化线口径：填充格<strong>按机器分别注册</strong>（id = 机器 id + {@code _shell}），
     * 于是 Jade 瞄任意一格都会显示「机器名（结构）」，而不是统一的「裂变机器（结构）」。
     */
    private static final Map<String, DeferredBlock<FissionMachineShellBlock>> SHELLS = new LinkedHashMap<>();

    private static void registerShell(String machineId) {
        String shellId = machineId + "_shell";
        DeferredBlock<FissionMachineShellBlock> block = BLOCKS.register(shellId,
                () -> new FissionMachineShellBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(3.5F, 6.0F)
                        .noOcclusion()
                    .forceSolidOn()
                        .noLootTable()
                        .randomTicks()
                        .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(shellId)))));
        SHELLS.put(machineId, block);
    }

    static {
        for (String machineId : PARTS.keySet()) {
            registerShell(machineId);
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FissionMachineShellBlockEntity>> SHELL_ENTITY =
            BLOCK_ENTITIES.register(SHELL_ENTITY_ID, () -> new BlockEntityType<>(FissionMachineShellBlockEntity::new,
                    shellBlocks()));

    /** 所有填充格方块（每台机器一个），共用一个方块实体类型。 */
    private static Block[] shellBlocks() {
        List<Block> blocks = new java.util.ArrayList<>(SHELLS.size());
        for (DeferredBlock<FissionMachineShellBlock> shell : SHELLS.values()) {
            blocks.add(shell.get());
        }
        return blocks.toArray(new Block[0]);
    }

    // ------------------------------------------------------------------ 查询

    /** 这个方块是不是裂变机器的主方块。 */
    public static boolean isMachine(Block block) {
        var key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && PARTS.containsKey(key.getPath());
    }

    /** 方块的可动部件规格（不是裂变机器 → 空表）。 */
    public static List<FissionMachinePart> partsFor(Block block) {
        var key = BuiltInRegistries.BLOCK.getKey(block);
        return key == null ? List.of() : PARTS.getOrDefault(key.getPath(), List.of());
    }

    /** 载体方块名 → 载体方块（渲染器用）。 */
    @Nullable
    public static Block carrier(String name) {
        return CARRIERS.get(name);
    }

    // ------------------------------------------------------------------ 状态 / 占位

    /** 给主方块挂上 12 个接管口属性（值由 {@link #refreshPorts} 维护）。 */
    public static void addPortStates(StateDefinition.Builder<Block, BlockState> builder) {
        PORT_PROPERTIES.values().forEach(builder::add);
    }

    /** BooleanProperty 隐式默认是 true —— 必须显式全置 false，否则新放的机器一上来就是「已接」。 */
    public static BlockState withPortsOff(BlockState state) {
        BlockState result = state;
        for (BooleanProperty port : PORT_PROPERTIES.values()) {
            result = result.setValue(port, false);
        }
        return result;
    }

    /** 扫一遍机器外沿的 12 个接管口格子，把「该面是否接着铜管」写回本方块状态。 */
    public static boolean refreshPorts(Level level, BlockPos origin) {
        return LargeMachineStructureOps.refreshPorts(level, origin, FissionMachineStructure::isMachine, PORTS);
    }

    // ------------------------------------------------------------------ 占位格

    /** 8 格里除主方块外的 7 格是否都能被占用。 */
    public static boolean canOccupy(BlockGetter level, BlockPos origin) {
        return LargeMachineStructureOps.canOccupy(level, origin);
    }

    /** 填充格方块：按主方块的机器 id 选对应那一种（认不出 → null，不填）。 */
    @Nullable
    private static Block shellFor(Block machine) {
        var key = BuiltInRegistries.BLOCK.getKey(machine);
        DeferredBlock<FissionMachineShellBlock> shell = key == null ? null : SHELLS.get(key.getPath());
        return shell == null ? null : shell.get();
    }

    /** 把另外 7 格填成不可见填充格；返回本次补了几格。 */
    public static int fillShells(Level level, BlockPos origin) {
        Block shell = shellFor(level.getBlockState(origin).getBlock());
        return shell == null ? 0 : LargeMachineStructureOps.fillShells(level, origin, shell.defaultBlockState());
    }

    /** 清掉一台机器占的另外 7 格填充格。 */
    public static void clearShells(Level level, BlockPos origin) {
        LargeMachineStructureOps.clearShells(level, origin, FissionMachineShellBlock.class);
    }

    /** 旧存档 / 自愈调用：朝向缺失时按北处理。 */
    public static Direction facingOf(BlockGetter level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (state.hasProperty(WaterReactorBlock.FACING)) {
            return state.getValue(WaterReactorBlock.FACING);
        }
        if (state.hasProperty(SteamTurbineBlock.FACING)) {
            return state.getValue(SteamTurbineBlock.FACING);
        }
        if (state.hasProperty(FissionMachineBlock.FACING)) {
            return state.getValue(FissionMachineBlock.FACING);
        }
        return Direction.NORTH;
    }

    /** 从某个填充格出发，找出所属机器的主方块位置。 */
    @Nullable
    public static BlockPos findMachine(BlockGetter level, BlockPos shellPos) {
        return LargeMachineStructureOps.findMachine(level, shellPos, FissionMachineStructure::isMachine);
    }

    /** 强制重算这 8 格的光照（修复旧存档「填充格挡光」留下的暗块）。 */
    public static void refreshLight(Level level, BlockPos origin) {
        LargeMachineStructureOps.refreshLight(level, origin);
    }

    /** 机器 8 格的外沿位置（扫慢化剂 / 找相邻方块用）。 */
    public static List<BlockPos> outline(BlockPos origin) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        java.util.LinkedHashSet<BlockPos> cells = new java.util.LinkedHashSet<>();
        for (int[] offset : CELL_OFFSETS) {
            for (Direction direction : Direction.values()) {
                cursor.set(origin.getX() + offset[0], origin.getY() + offset[1], origin.getZ() + offset[2]);
                cells.add(cursor.relative(direction).immutable());
            }
        }
        return List.copyOf(cells);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(FissionMachineStructure::onRegisterCapabilities);
    }

    /** 填充格的能力转发（物品 / 流体）：瞄任意一格都是同一份存量。 */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, SHELL_ENTITY.get(),
                (shell, side) -> shell.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SHELL_ENTITY.get(),
                (shell, side) -> shell.fluidCapability());
    }

    private FissionMachineStructure() {}
}
