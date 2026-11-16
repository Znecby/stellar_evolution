package dev.znecby.stellar_evolution.thermal;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.bio.BioMachineBlockEntity;
import dev.znecby.stellar_evolution.coal.CokeOvenControllerBlockEntity;
import dev.znecby.stellar_evolution.drill.DrillBlockEntity;
import dev.znecby.stellar_evolution.engine.BurningEngineBlockEntity;
import dev.znecby.stellar_evolution.engine.GasTurbineBlockEntity;
import dev.znecby.stellar_evolution.engine.HeatEngineBlockEntity;
import dev.znecby.stellar_evolution.engine.IndustrialEngineBlockEntity;
import dev.znecby.stellar_evolution.engine.RocketEngineBlockEntity;
import dev.znecby.stellar_evolution.farmland.MechanicalSoil;
import dev.znecby.stellar_evolution.farmland.MechanicalSoilBlockEntity;
import dev.znecby.stellar_evolution.fission.FissionMachineBlockEntity;
import dev.znecby.stellar_evolution.fission.FissionMachineKind;
import dev.znecby.stellar_evolution.fission.ReactionChamberBlock;
import dev.znecby.stellar_evolution.fission.SteamTurbineBlockEntity;
import dev.znecby.stellar_evolution.fission.WaterReactorBlockEntity;
import dev.znecby.stellar_evolution.hatch.HatchBlockEntity;
import dev.znecby.stellar_evolution.hep.CyclotronBlockEntity;
import dev.znecby.stellar_evolution.large.LargeMachine;
import dev.znecby.stellar_evolution.large.LargeMachines;
import dev.znecby.stellar_evolution.machine.MachineBlockEntity;
import dev.znecby.stellar_evolution.massdriver.MassDriverControllerBlockEntity;
import dev.znecby.stellar_evolution.muon.MuonFusionBlockEntity;
import dev.znecby.stellar_evolution.pipe.BeamPipeBlock;
import dev.znecby.stellar_evolution.pipe.CopperPipeBlock;
import dev.znecby.stellar_evolution.pipe.FlowLimiterBlock;
import dev.znecby.stellar_evolution.pipe.FlowLimiterBlockEntity;
import dev.znecby.stellar_evolution.pipe.MachinePipeBlock;
import dev.znecby.stellar_evolution.pipe.PressurePipeBlock;
import dev.znecby.stellar_evolution.particle.AdsBlockEntity;
import dev.znecby.stellar_evolution.particle.AnnihilationReactorBlockEntity;
import dev.znecby.stellar_evolution.particle.BeamMachineBlockEntity;
import dev.znecby.stellar_evolution.particle.ParticleSourceBlockEntity;
import dev.znecby.stellar_evolution.particle.ParticleType;
import dev.znecby.stellar_evolution.petro.PetroMachineKind;
import dev.znecby.stellar_evolution.steam.SteamEndpoint;
import dev.znecby.stellar_evolution.steam.SteamNetwork;
import dev.znecby.stellar_evolution.steam.SteamState;
import dev.znecby.stellar_evolution.stream.StreamNode;
import dev.znecby.stellar_evolution.stream.StreamNetwork;
import dev.znecby.stellar_evolution.stream.StreamType;
import dev.znecby.stellar_evolution.tank.GrateSource;
import dev.znecby.stellar_evolution.tank.TankBlock;
import dev.znecby.stellar_evolution.welding.WeldingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 热压差网络的 Jade <strong>服务端数据提供者</strong>（公共侧）：把这一格的网络账本算好、写进发往客户端的 NBT。
 *
 * <p>挂在整个网络的<strong>多数方块</strong>上（铜管 / 热力引擎 / 燃烧引擎 / 木桶 / 熔炉·烟熏炉·高炉 / 铜块 /
 * <strong>热室</strong>（铜箱子垫铜块）/ 机械土壤 / 大型机器），算出同一套数：容量 / 热流率 / 占用 / 是否超载，
 * 外加角色与「本机是否在供电」。画的部分在 {@link ThermalJadeProvider}（客户端）。
 *
 * <p><strong>为什么注册在 {@code Block.class} 上</strong>：Jade 只按<strong>方块类</strong>注册，而原版铜块里
 * 「打蜡」那 4 种是纯 {@code Block}（另外 4 种是 {@code WeatheringCopperFullBlock}），没有一个共同子类能涵盖全 8 种，
 * 所以只能挂到根类 {@code Block} 上、再按<strong>标签 / 类型</strong>过滤（见下面的 {@link #anchorFor}）。
 * 非热压差方块直接不写数据，客户端也就不显示（开销可忽略）。
 *
 * <p><strong>取哪一格做锚点</strong>（{@link #anchorFor}）：
 * <ul>
 *   <li>消耗端（木桶 / 熔炉等 / 机械土壤）<strong>贴着铜块</strong>接入，自己不是网络成员，所以读它相邻的那格铜块；</li>
 *   <li>铜块本身就是网络里的中间导体，直接读它这格；</li>
 *   <li>以上两者还要求<strong>真的接入了网络</strong>（见 {@link #isNetworked}：网络里有铜管或热源）——</li>
 *   <li><strong>大型机器端口上的机械管</strong>：锚到该机器，并额外带上机器名（客户端多画一行「生物反应釜」）；</li>
 *   <li><strong>大型机器本身</strong>：消耗端只经端口铜块接入，没铜块就不显示；生成端（蒸汽轮机）贴铜管或铜块都显示；
 *       裂变堆 / 缪子聚变仪另有专属面板。</li>
 * </ul>
 *
 * <p>为什么坚持在服务端算：热压差网络是<strong>跨区块</strong>的，客户端手上只有已加载区块的方块数据，
 * 只有服务端算得准。客户端拿不到数据时 {@link ThermalJadeProvider} 会直接不显示，不做本地兜底。
 */
public final class ThermalJadeData implements IServerDataProvider<BlockAccessor> {
    /** 单例（无状态）。 */
    public static final ThermalJadeData INSTANCE = new ThermalJadeData();

    /** Jade 里这条信息的 ID；Jade 配置界面用的语言键是 {@code config.jade.plugin_stellar_evolution.thermal_network}。 */
    public static final Identifier UID = StellarEvolution.id("thermal_network");

    // 服务端 → 客户端的 NBT 键（客户端读同一套）
    public static final String CAPACITY = "Cap";
    public static final String FLOW_RATE = "Flow";
    public static final String OCCUPANCY = "Occ";
    public static final String OVERLOADED = "Over";
    /** 本机是不是「正在供电」的生成端（自己输出热流率 &gt; 0）；非生成端恒 false。 */
    public static final String WORKING = "Work";
    /** 生成端<strong>自身</strong>的输出热流率（HP/t）——不是网络取最大后的值；非生成端恒 0。 */
    public static final String OUTPUT = "Out";
    /** 消耗端「满速（每刻一次）所需热流率」（HP/t）——客户端用它给「当前热流率」封顶；0 表示不封顶。 */
    public static final String MAX_FLOW = "MaxFlow";
    /** 大型机器 / 热室：工作中的「剩余配方时长」（刻）——不写 = 没在工作（客户端不画绿色「配方」槽）；0 = 已饱和。 */
    public static final String RECIPE_TICKS = "RTick";
    /** 大型机器 / 热室：当前周期进度 0..1（绿色「配方」槽的填充比例）。 */
    public static final String RECIPE_PROGRESS = "RProg";
    /** 机械土壤（耕地 / 沙地 / 灵魂沙地 / 草地）专有：底部缺容器（工作必须先有容器）→ 客户端显示「底部缺失容器」提示。 */
    public static final String NEED_CONTAINER = "NeedC";
    /** 角色（{@link Role} 的序数）：决定第 2 行热流率的名字与第 3 行的状态措辞。 */
    public static final String ROLE = "Role";
    /** 多方块结构显示名（翻译键）：非空时客户端额外画一行（如端口机械管上的「生物反应釜」）。 */
    public static final String LARGE_NAME = "LargeName";
    /** 大机器物流容器：当前物流方向（{@code LargeMachineIo} 序数：0 输入 / 1 输出）。 */
    public static final String LARGE_IO = "LargeIo";
    /** 大型机器（生化）当前温度带序数（{@code FermentationKinetics.Band}）；不带温度带条件的机器不写。 */
    public static final String BAND = "Band";
    /** 燃烧引擎：模式序数（{@code BurningEngineBlockEntity.Mode}：0 停机 / 1 固体 / 2 液体）。 */
    public static final String FUEL_MODE = "FMode";
    /** 燃烧引擎：剩余可运行时长（刻）。 */
    public static final String FUEL_TICKS = "FTick";
    /** 燃料条满格对应的刻数（燃烧引擎 1600 点 × 20t；热力引擎 1600 点 × 10t）。 */
    public static final String FUEL_FULL = "FFull";
    /** 机械土壤：肥料点数（0..1600）。 */
    public static final String FERT = "Fert";
    /** 瞄的这格是<strong>热室</strong>（底部垫铜块的铜箱子）—— 客户端据此把 Jade 的对象名行改成「热室」。 */
    public static final String HOT_CHAMBER = "HC";
    /** 热室<strong>结构缺失</strong>（如大铜箱子只垫了一格铜块）—— 客户端多画一行红字提示。 */
    public static final String HOT_CHAMBER_MISSING = "HCM";
    /** 热室等压值（0 = 默认配方组）。 */
    public static final String HOT_CHAMBER_PRESSURE = "HCP";
    /** 瞄的是质量投射轨道炮（控制器 / 机械物流仓）—— 客户端额外画势能 / 等压 / 倒计时。 */
    public static final String MASS_DRIVER = "MD";
    /** 质量炮当前势能（上限 = 下方 CAPACITY 的差压容量）。 */
    public static final String MASS_DRIVER_POTENTIAL = "MDPot";
    /** 质量炮等压值（当前只存储）。 */
    public static final String MASS_DRIVER_PRESSURE = "MDPres";
    /** 质量炮发射倒计时（秒）；不带 = 没有倒计时。 */
    public static final String MASS_DRIVER_COUNTDOWN = "MDCd";
    /** 材料加工机械（破碎机 / 压模机 / 线材轧机）的状态序数（{@code MachineBlockEntity.State}）—— 客户端画「顶部缺失容器 / 等待原料」。 */
    public static final String MACHINE_STATE = "Mach";
    /** 焊接机的状态序数（{@code WeldingMachineBlockEntity.State}）—— 客户端画「顶部缺失容器 / 底部缺失储罐 / 等待流体 / 等待原料」。 */
    public static final String WELD_STATE = "Weld";
    /** 焊接机当前配方的最低 HP/t。 */
    public static final String WELD_MIN_HP = "WeldMinHp";
    /** 焊接机当前配方的 1× 基准 HP/t。 */
    public static final String WELD_BASE_HP = "WeldBaseHp";
    /** 开采钻：模式序数（{@code DrillBlockEntity.Mode}：0 范围破坏 / 1 精准采集）。 */
    public static final String DRILL_MODE = "DrMode";
    /** 开采钻：当前模式的范围值（范围破坏 = 方块边长；精准采集 = 区块边长）。 */
    public static final String DRILL_RANGE = "DrRange";
    /** 开采钻：状态序数（{@code DrillBlockEntity.State}）—— 客户端画「缺失容器 / 等待空间 / 已到底」。 */
    public static final String DRILL_STATE = "DrState";
    /** 开采钻：当前 HP 倍速（0 = 停机；1.0 = 基准 256 HP/t）。 */
    public static final String DRILL_SPEED = "DrSpeed";
    /** 开采钻：本层周期已满、但还在补扫剩余方块（大范围 / 高倍速时出现）。 */
    public static final String DRILL_SCANNING = "DrScan";
    /** 开采钻：正下方是基岩，处于固定「基岩矿」模式（范围 / 模式不可调）。 */
    public static final String DRILL_BEDROCK = "DrBed";
    /** 限流管（闸门）：实际放给下游的热流率（HP/t）—— 客户端多画一行「限流」。 */
    public static final String LIMITER_PASS = "LimPass";
    /** 限流管（闸门）：设定值（{@code 0} = 跟随输入端）。 */
    public static final String LIMITER_LIMIT = "LimSet";
    /** 铜格栅：模式（0 = 干（泵），1 = 含水（产水））。 */
    public static final String GRATE_MODE = "GMode";
    /** 铜格栅：3×3×3 内的水源数量（产水倍率基数）。 */
    public static final String GRATE_SOURCES = "GSrc";
    /** 铜格栅：当前虚拟缓冲（mB）。 */
    public static final String GRATE_BUFFER = "GBuf";
    /** 铜格栅：当前容量（mB，随水源倍率放大）。 */
    public static final String GRATE_CAPACITY = "GCap";
    /** 铜格栅：当前补水速率（B/s，随水源倍率放大）。 */
    public static final String GRATE_RATE = "GRate";
    /** 大机器：网络里含水铜格栅的总供水速率（B/s）——客户端画「无限水供应」。 */
    public static final String WATER_SUPPLY = "WSup";
    /** 大机器：网络里参与供水的含水铜格栅数量。 */
    public static final String WATER_GRATES = "WGrt";

    // ---- 蒸汽网络（压力管 / 裂变堆 / 汽轮机）
    /** 这一格与蒸汽网络有关（压力管 / 端点机器）。 */
    public static final String STEAM_RELATED = "StRel";
    /** 瞄的是压力管。 */
    public static final String STEAM_PIPE = "StPipe";
    /** 网络里的压力管数量。 */
    public static final String STEAM_PIPES = "StPipes";
    /** 网络里的生成端数量。 */
    public static final String STEAM_GENS = "StGen";
    /** 网络里的消耗端数量。 */
    public static final String STEAM_CONS = "StCon";
    /** 网络蒸汽量（mB/t）。 */
    public static final String STEAM_SUPPLY = "StSup";
    /** 本 tick 已分配给消耗端的蒸汽合计（mB/t）；剩余 = 蒸汽量 − 已分配。 */
    public static final String STEAM_ALLOCATED = "StAl";
    /** 网络蒸汽温度（℃）。 */
    public static final String STEAM_TEMP = "StT";
    /** 网络蒸汽压力（bar）。 */
    public static final String STEAM_PRESS = "StP";
    /** 网络蒸汽等级（{@code SteamLoop.SteamGrade} 序数）。 */
    public static final String STEAM_GRADE = "StG";
    /** 网络没有消耗端 → 末端排气。 */
    public static final String STEAM_VENTING = "StVent";
    /** 端点状态（{@code SteamEndpoint.SteamStatus} 序数）。 */
    public static final String STEAM_STATUS = "StSt";
    /** 裂变堆：本 tick 输出的蒸汽量（mB/t）。 */
    public static final String REACTOR_STEAM_AMOUNT = "RxSa";
    /** 裂变堆：输出蒸汽温度（℃）。 */
    public static final String REACTOR_STEAM_TEMP = "RxSt";
    /** 裂变堆：输出蒸汽压力（bar）。 */
    public static final String REACTOR_STEAM_PRESS = "RxSp";
    /** 汽轮机：本机存在（客户端画汽轮机面板）。 */
    public static final String TURBINE = "Tb";
    /** 汽轮机：当前转速（RPM）。 */
    public static final String TURBINE_RPM = "TbR";
    /** 汽轮机：本 tick 消耗的蒸汽（mB/t）。 */
    public static final String TURBINE_STEAM_USE = "TbU";
    /** 汽轮机：输出 DP。 */
    public static final String TURBINE_DP = "TbD";
    /** 汽轮机：输出 HP/t。 */
    public static final String TURBINE_HP = "TbH";

    // ---- 燃烧类发电机组（内燃机组 / 联合燃气机组 / 火箭引擎）
    /** 本机是燃烧类发电机组（客户端画机组面板）。 */
    public static final String ENGINE = "En";
    /** 机组类型：0 内燃 / 1 燃气 / 2 火箭。 */
    public static final String ENGINE_KIND = "EnK";
    /** 负荷：内燃 / 燃气的转速 0..1.25，火箭的室压 0..1。 */
    public static final String ENGINE_LOAD = "EnL";
    /** 满负荷耗燃料（mB/t）。 */
    public static final String ENGINE_FUEL_USE = "EnF";
    /** 是否吃到液氧。 */
    public static final String ENGINE_OXY = "EnO";
    /** 机组状态（{@code EngineMachineSupport.FuelState} 序数）。 */
    public static final String ENGINE_STATE = "EnS";
    /** 燃气轮机：本 tick 产汽（mB/t，0 = 没产汽）。 */
    public static final String ENGINE_STEAM = "EnSt";
    /** 本机自己的 DP 容量（与网络容量区分）。 */
    public static final String ENGINE_DP = "EnDp";

    // ---- 裂变堆专属（有这些键时客户端只画反应堆面板，不画通用热压差 HUD）
    public static final String REACTOR = "RxOn";
    public static final String REACTOR_TEMP = "RxTemp";
    public static final String REACTOR_POWER = "RxPower";
    public static final String REACTOR_FLUX = "RxFlux";
    public static final String REACTOR_RODS = "RxRods";
    public static final String REACTOR_GRAPHITE = "RxGraphite";
    public static final String REACTOR_REFLECTORS = "RxRefl";
    public static final String REACTOR_CHAMBERS = "RxChambers";
    public static final String REACTOR_CHAMBER_OVERFLOW = "RxChOver";
    public static final String REACTOR_EFFICIENCY = "RxEff";
    public static final String REACTOR_DAMAGE = "RxDmg";
    public static final String REACTOR_CORE_AVG = "RxCoreA";
    public static final String REACTOR_CORE_MAX = "RxCoreM";
    public static final String REACTOR_CLAD_AVG = "RxClad";
    public static final String REACTOR_PRESSURE = "RxPress";
    public static final String REACTOR_RUNNING = "RxRun";
    public static final String REACTOR_COOLANT = "RxCool";

    // ---- 缪子聚变仪专属（有 MUON 键时客户端只画聚变仪面板）
    public static final String MUON = "MuOn";
    public static final String MUON_STATE = "MuState";
    public static final String MUON_CORE = "MuCore";
    public static final String MUON_TEMP = "MuT";
    public static final String MUON_STABILITY = "MuS";
    public static final String MUON_CHARGE = "MuCh";
    public static final String MUON_STREAM_TYPE = "MuST";
    public static final String MUON_STREAM_RATE = "MuSR";
    public static final String MUON_STREAM_TOTAL = "MuSTot";
    public static final String MUON_REQ_FLOW = "MuRF";
    public static final String MUON_REQ_RES = "MuRR";
    public static final String MUON_CHAMBERS = "MuCham";
    public static final String MUON_CHAMBER_OVERFLOW = "MuChOver";
    public static final String MUON_CORE_DAMAGE = "MuCd";
    public static final String MUON_CORE_MAX = "MuCm";
    public static final String MUON_DAMAGE = "MuDmg";
    public static final String MUON_STEAM_AMOUNT = "MuSa";
    public static final String MUON_STEAM_TEMP = "MuSt";
    public static final String MUON_STEAM_PRESS = "MuSp";
    public static final String MUON_WATER_OK = "MuWat";
    public static final String MUON_WATER_DEMAND = "MuWatD";
    public static final String MUON_MU_OK = "MuMuOk";
    public static final String MUON_NEUTRON_RATE = "MuNR";
    public static final String MUON_NEUTRON_TOTAL = "MuNTot";
    // ---- 回旋加速器专属（有 CYCLOTRON 键时客户端只画回旋加速器面板）
    public static final String CYCLOTRON = "Cyc";
    public static final String CYCLOTRON_CHARGE = "CycCh";
    public static final String CYCLOTRON_CAP = "CycCap";
    public static final String CYCLOTRON_DEMAND = "CycDem";
    public static final String CYCLOTRON_PARALLEL = "CycPar";
    public static final String CYCLOTRON_OPS = "CycOps";
    public static final String CYCLOTRON_STATE = "CycSt";
    // ---- 粒子源专属（有 PARTICLE_SOURCE 键时客户端只画粒子源面板）
    public static final String PARTICLE_SOURCE = "PaSrc";
    public static final String PARTICLE_TYPE = "PaType";
    public static final String PARTICLE_TANK = "PaTank";
    public static final String PARTICLE_TANK_MAX = "PaTankM";
    public static final String PARTICLE_BUFFER = "PaBuf";
    public static final String PARTICLE_REQUIRED_DP = "PaDp";
    public static final String PARTICLE_REQUIRED_HP = "PaHp";
    public static final String PARTICLE_OUTPUT = "PaOut";
    public static final String PARTICLE_BOOST = "PaBst";
    public static final String PARTICLE_STATE = "PaSt";
    public static final String PARTICLE_TIER = "PaTier";
    public static final String PARTICLE_CYCLE = "PaCyc";
    // ---- 束流处理机专属
    public static final String IRRADIATION = "IrCh";
    public static final String IRRADIATION_SHARE = "IrSh";
    public static final String IRRADIATION_PROGRESS = "IrPg";
    public static final String IRRADIATION_REQUIRED = "IrRq";
    public static final String IRRADIATION_STATE = "IrSt";
    /** 已接入的粒子流（位掩码，按 {@code StreamType.ordinal()}）。 */
    public static final String IRRADIATION_STREAMS = "IrSm";
    /** 当前在吃的那一路（{@code StreamType.ordinal()}；-1 = 空闲）。 */
    public static final String IRRADIATION_ACTIVE = "IrAc";
    /** 正在喷出的流类型（-1 = 不喷）。 */
    public static final String IRRADIATION_OUTPUT = "IrOut";
    public static final String IRRADIATION_OUTPUT_RATE = "IrORt";
    /** 每一路需求的进度摘要（`uranium_ion=4200/6000;lead_ion=0/3000`）。 */
    public static final String IRRADIATION_DEMANDS = "IrDm";
    /** 贴在机器旁的加速环段数量（0–16）。 */
    public static final String RING_LEVEL = "RingLv";
    /** 当前配方要求的加速环段门槛（0 = 不要求）。 */
    public static final String RING_REQUIRED = "RingRq";
    /** 湮灭反应堆：本次燃烧是否加了鎶-304（稳定岛点火）。 */
    public static final String ANNIHILATION_BOOST = "AnBoost";
    /** 湮灭反应堆：标记（客户端据此多画一行燃料说明）。 */
    public static final String ANNIHILATION = "AnOn";
    /** ADS：运行时蒸汽输出（mB/t · ℃ · bar）与状态。 */
    public static final String ADS_STEAM_AMOUNT = "AdSa";
    public static final String ADS_STEAM_TEMP = "AdSt";
    public static final String ADS_STEAM_PRESS = "AdSp";
    public static final String ADS_STEAM_STATUS = "AdSs";

    // ---- 粒子流网络（束流管 / 缪子聚变仪）
    public static final String STREAM_RELATED = "SmRel";
    public static final String STREAM_PIPE = "SmPipe";
    public static final String STREAM_PIPES = "SmPipes";
    public static final String STREAM_SOURCES = "SmSrc";
    public static final String STREAM_TYPE = "SmType";
    public static final String STREAM_RATE = "SmRate";

    /** 方块在热压差网络里的角色。 */
    public enum Role {
        /** 连接件（铜管）。 */
        PIPE,
        /** 生成端（引擎）。 */
        GENERATOR,
        /** 消耗侧（铜块 / 木桶 / 熔炉等 / 机械土壤 / 大型机器）。 */
        CONSUMER
    }

    /** 锚点：实际账本所在格 + 角色 + 可选的「多方块」显示名。 */
    private record Anchor(BlockPos pos, Role role, @Nullable String largeName) {}

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();

        Anchor anchor = anchorFor(level, pos);

        // 粒子流网络（束流管 / 缪子聚变仪）：先写自己的数据
        appendStreamData(data, level, pos);

        // 蒸汽网络（压力管 / 裂变堆 / 汽轮机）：先写自己的数据，再按需叠加通用热压差 HUD
        // （大机器的机械管 / 容器要借锚点才能反查到反应堆 / 汽轮机端点）
        appendSteamData(data, level, pos, anchor);

        // 大机器物流容器：机器名 + 输入 / 输出方向（与热压差是否接入无关，物流本身也要看得出来）
        appendLargeContainerData(data, level, pos);

        if (anchor == null) {
            return;   // 不是热压差方块：不写数据，客户端也就不显示
        }

        ThermalLoad load = ThermalNetwork.loadAt(level, anchor.pos());
        data.putDouble(CAPACITY, load.capacity());
        data.putDouble(FLOW_RATE, load.flowRate());
        data.putDouble(OCCUPANCY, load.occupancy());
        data.putBoolean(OVERLOADED, load.overloaded());
        data.putInt(ROLE, anchor.role().ordinal());
        if (anchor.largeName() != null) {
            data.putString(LARGE_NAME, anchor.largeName());
        }

        // 质量投射轨道炮：追加势能 / 等压 / 本人倒计时（锚点可能是物流仓，所以按机器原点反查控制器）
        BlockPos driverOrigin = LargeMachines.originOf(level, anchor.pos());
        if (driverOrigin != null
                && level.getBlockEntity(driverOrigin) instanceof MassDriverControllerBlockEntity driver) {
            data.putBoolean(MASS_DRIVER, true);
            data.putDouble(MASS_DRIVER_POTENTIAL, driver.potential());
            data.putInt(MASS_DRIVER_PRESSURE, driver.pressure());
            if (accessor.getPlayer() != null) {
                int countdown = driver.countdownSeconds(accessor.getPlayer().getUUID());
                if (countdown > 0) {
                    data.putInt(MASS_DRIVER_COUNTDOWN, countdown);
                }
            }
        }

        // 格栅：写「含水（水格栅）/ 暴露空气（钢格栅）/ 泵」模式 + 缓冲，客户端多画一行
        if (GrateSource.isGrate(level.getBlockState(pos))) {
            GrateSource.Kind grateKind = GrateSource.kindOf(level.getBlockState(pos));
            data.putInt(GRATE_MODE, grateKind == GrateSource.Kind.AIR ? 2
                    : GrateSource.isWaterlogged(level, pos) ? 1 : 0);
            // 水格栅：额外水源 = 3×3×3 内水源数 − 1；钢格栅：暴露空气面数（每面 ×2）
            data.putInt(GRATE_SOURCES, grateKind == GrateSource.Kind.AIR
                    ? GrateSource.exposedAirFaces(level, pos)
                    : Math.max(0, GrateSource.countWaterSources(level, pos) - 1));
            data.putLong(GRATE_BUFFER, GrateSource.bufferAmount(level, pos));
            data.putDouble(GRATE_CAPACITY, GrateSource.capacityAt(level, pos));
            data.putDouble(GRATE_RATE, GrateSource.ratePerSecond(level, pos));
        }

        // 大机器：网络里若有「含水铜格栅」，汇总它们的产水速率 → 客户端显示「无限水供应」
        if (level.getBlockEntity(anchor.pos()) instanceof LargeMachine machine) {
            double supply = 0.0D;
            int grates = 0;
            for (LargeMachines.Connected connected : LargeMachines.connectedContainers(level, machine)) {
                if (connected.fluid() && GrateSource.isGrate(level.getBlockState(connected.pos()))
                        && GrateSource.isWaterlogged(level, connected.pos())) {
                    supply += GrateSource.ratePerSecond(level, connected.pos());
                    grates++;
                }
            }
            if (supply > 0.0D) {
                data.putDouble(WATER_SUPPLY, supply);
                data.putInt(WATER_GRATES, grates);
            }
        }

        // 裂变堆：写自己的运行快照（壳温 / 通量 / 效率 / 功率 / 三层温度 / 损毁 / 棒数）
        if (level.getBlockEntity(anchor.pos()) instanceof WaterReactorBlockEntity reactor) {
            data.putBoolean(REACTOR, true);
            data.putDouble(REACTOR_TEMP, reactor.temperature());
            data.putDouble(REACTOR_POWER, reactor.powerMw());
            data.putDouble(REACTOR_FLUX, reactor.totalFlux());
            data.putDouble(REACTOR_EFFICIENCY, reactor.efficiency());
            data.putDouble(REACTOR_DAMAGE, reactor.damage());
            data.putDouble(REACTOR_CORE_AVG, reactor.avgCoreTemp());
            data.putDouble(REACTOR_CORE_MAX, reactor.maxCoreTemp());
            data.putDouble(REACTOR_CLAD_AVG, reactor.avgCladdingTemp());
            data.putDouble(REACTOR_PRESSURE, reactor.pressureBar());
            data.putInt(REACTOR_RODS, reactor.rodCount());
            data.putInt(REACTOR_GRAPHITE, reactor.graphiteCount());
            data.putInt(REACTOR_REFLECTORS, reactor.reflectorCount());
            data.putInt(REACTOR_CHAMBERS, reactor.chamberCount());
            data.putBoolean(REACTOR_CHAMBER_OVERFLOW, reactor.chamberOverflow());
            data.putBoolean(REACTOR_RUNNING, reactor.isRunning());
            data.putBoolean(REACTOR_COOLANT, reactor.coolantOk());
        }

        // 罐装机：当前温度带（配方按它选档）
        if (level.getBlockEntity(anchor.pos()) instanceof FissionMachineBlockEntity machine
                && machine.kind() == FissionMachineKind.CANNING_MACHINE) {
            data.putInt(BAND, machine.currentBand().ordinal());
        }

        // 回旋加速器：差速 / DP / 配方需求 / 本轮产出 / 状态
        if (level.getBlockEntity(anchor.pos()) instanceof CyclotronBlockEntity cyclotron) {
            data.putBoolean(CYCLOTRON, true);
            data.putDouble(CYCLOTRON_CHARGE, cyclotron.cyclotronCharge());
            data.putDouble(CYCLOTRON_CAP, cyclotron.cyclotronCap());
            data.putDouble(CYCLOTRON_DEMAND, cyclotron.cyclotronDemand());
            data.putInt(CYCLOTRON_PARALLEL, cyclotron.cyclotronParallel());
            data.putInt(CYCLOTRON_OPS, cyclotron.cyclotronOps());
            data.putInt(CYCLOTRON_STATE, cyclotron.cyclotronState().ordinal());
        }

        // 粒子源：粒子类型 / 罐耐久 / 输出速率 / 缓冲 / 门槛 / 状态
        if (level.getBlockEntity(anchor.pos()) instanceof ParticleSourceBlockEntity source) {
            data.putBoolean(PARTICLE_SOURCE, true);
            ParticleType type = source.particleType();
            if (type != null) {
                data.putString(PARTICLE_TYPE, type.nameKey());
                data.putDouble(PARTICLE_REQUIRED_DP, type.requiredDp());
                data.putDouble(PARTICLE_REQUIRED_HP, type.requiredHp());
                data.putDouble(PARTICLE_OUTPUT, source.particleOutputRate());
                data.putBoolean(PARTICLE_BOOST, source.boosterActive());
            }
            data.putInt(PARTICLE_TANK, source.tankDamage());
            data.putInt(PARTICLE_TANK_MAX, source.tankMaxDamage());
            data.putDouble(PARTICLE_BUFFER, source.buffer());
            data.putInt(PARTICLE_STATE, source.particleState().ordinal());
            data.putDouble(PARTICLE_CYCLE, source.particleCycleProgress());
        }

        // 束流处理机：已接入哪几路流 / 当前吃哪一路 / 进度 / 状态
        if (level.getBlockEntity(anchor.pos()) instanceof BeamMachineBlockEntity beam) {
            data.putBoolean(IRRADIATION, true);
            data.putDouble(IRRADIATION_SHARE, beam.beamShare());
            data.putDouble(IRRADIATION_PROGRESS, beam.beamProgress());
            data.putDouble(IRRADIATION_REQUIRED, beam.beamRequired());
            data.putInt(IRRADIATION_STATE, beam.beamState().ordinal());
            int mask = 0;
            for (StreamType type : beam.wiredTypes()) {
                mask |= 1 << type.ordinal();
            }
            data.putInt(IRRADIATION_STREAMS, mask);
            data.putInt(IRRADIATION_ACTIVE,
                    beam.beamRecipe() == null ? -1 : beam.bottleneckType().ordinal());
            StreamType output = beam.beamOutputStream();
            data.putInt(IRRADIATION_OUTPUT, output == null ? -1 : output.ordinal());
            data.putDouble(IRRADIATION_OUTPUT_RATE, beam.beamOutputRate());
            data.putString(IRRADIATION_DEMANDS, beam.beamDemandSummary());
            data.putInt(RING_LEVEL, beam.ringLevel());
            data.putInt(RING_REQUIRED, beam.beamRecipe() == null ? 0 : beam.beamRecipe().minRingLevel());
            if (beam instanceof AdsBlockEntity ads) {
                SteamState steam = ads.steamOutput();
                data.putDouble(ADS_STEAM_AMOUNT, steam.amount());
                data.putDouble(ADS_STEAM_TEMP, steam.temperatureC());
                data.putDouble(ADS_STEAM_PRESS, steam.pressureBar());
                data.putInt(ADS_STEAM_STATUS, ads.steamStatus().ordinal());
            }
        }

        // 湮灭反应堆：稳定岛点火（鎶-304 添加剂）
        if (level.getBlockEntity(anchor.pos()) instanceof AnnihilationReactorBlockEntity annihilation) {
            data.putBoolean(ANNIHILATION, true);
            data.putBoolean(ANNIHILATION_BOOST, annihilation.stableIslandBoost());
        }

        // 缪子聚变仪：核心 / 温度 / 稳定值 / 约束 + 点火需求 + 芯核耐久 + 蒸汽
        if (level.getBlockEntity(anchor.pos()) instanceof MuonFusionBlockEntity fusion) {
            data.putBoolean(MUON, true);
            data.putInt(MUON_STATE, fusion.muonState().ordinal());
            if (!fusion.muonCoreNameKey().isEmpty()) {
                data.putString(MUON_CORE, fusion.muonCoreNameKey());
            }
            data.putDouble(MUON_TEMP, fusion.muonTemperatureC());
            data.putDouble(MUON_STABILITY, fusion.muonStability());
            data.putDouble(MUON_CHARGE, fusion.muonChargeRatio());
            data.putInt(MUON_STREAM_TYPE, fusion.streamType().ordinal());
            data.putDouble(MUON_STREAM_RATE, fusion.muonStreamRate());
            data.putDouble(MUON_STREAM_TOTAL, fusion.muonEmitted());
            data.putDouble(MUON_REQ_FLOW, fusion.muonRequiredFlow());
            data.putDouble(MUON_REQ_RES, fusion.muonRequiredResistance());
            data.putInt(MUON_CHAMBERS, fusion.muonChamberCount());
            data.putBoolean(MUON_CHAMBER_OVERFLOW, fusion.muonChamberOverflow());
            data.putInt(MUON_CORE_DAMAGE, fusion.muonCoreDamage());
            data.putInt(MUON_CORE_MAX, fusion.muonCoreMaxDamage());
            data.putDouble(MUON_DAMAGE, fusion.muonDamage());
            SteamState output = fusion.muonSteamOutput();
            data.putDouble(MUON_STEAM_AMOUNT, output.amount());
            data.putDouble(MUON_STEAM_TEMP, output.temperatureC());
            data.putDouble(MUON_STEAM_PRESS, output.pressureBar());
            data.putBoolean(MUON_WATER_OK, fusion.muonWaterOk());
            data.putDouble(MUON_WATER_DEMAND, fusion.muonWaterDemand());
            data.putBoolean(MUON_MU_OK, fusion.muonMuOk());
            data.putDouble(MUON_NEUTRON_RATE, fusion.muonNeutronRate());
            data.putDouble(MUON_NEUTRON_TOTAL, fusion.muonNeutronEmitted());
        }

        // 生成端自身的「输出热流率」（就是它自己那台的值，不是网络取最大后的值）；非生成端恒 0。
        BlockEntity blockEntity = level.getBlockEntity(pos);
        double output = blockEntity instanceof ThermalGenerator generator
                ? generator.flowRate()
                : level.getBlockEntity(anchor.pos()) instanceof ThermalGenerator anchored ? anchored.flowRate() : 0.0D;
        data.putDouble(OUTPUT, output);
        data.putBoolean(WORKING, output > 0.0D);   // 有输出即在供电（判据与动画同源）

        // 消耗端「满速（1t）所需热流率」——Jade 用它把「当前热流率」封顶（0 = 不封顶，如铜块导体）。
        // 设备型消耗端（机械土壤 / 木桶 / 熔炉）按玩家瞄的那格算；机器型消耗端（大机器）按锚点那格的方块实体算
        // （瞄端口机械管时玩家那格没有方块实体，必须回落到锚点）。
        double maxFlow = level instanceof ServerLevel serverLevel ? CopperHeat.maxUsefulFlowRate(serverLevel, pos) : 0.0D;
        if (maxFlow <= 0.0D && level.getBlockEntity(anchor.pos()) instanceof ThermalConsumer consumer) {
            maxFlow = consumer.maxUsefulFlowRate();
        }
        data.putDouble(MAX_FLOW, maxFlow);

        // 大型机器：工作中的「剩余配方时长」——客户端画绿色「配方」槽（DP 槽同款，只换绿色）；
        // 机器停机 / 没接入热压差时不写，客户端也就不画。
        // 锚点是物流仓时（质量炮 / 焦化炉）回落到认领它的控制器。
        LargeMachine recipeMachine = level.getBlockEntity(anchor.pos()) instanceof LargeMachine anchored
                ? anchored : machineOfHatch(level, anchor.pos());
        if (!(recipeMachine instanceof WaterReactorBlockEntity) && recipeMachine != null) {
            int recipeTicks = recipeMachine.remainingRecipeTicks();
            if (recipeTicks >= 0) {
                data.putInt(RECIPE_TICKS, recipeTicks);
                data.putDouble(RECIPE_PROGRESS, recipeMachine.recipeProgress());
            }
        }

        // 机械土壤（耕地 / 沙地 / 灵魂沙地 / 草地）：底部没有容器 → 不工作，客户端显示「底部缺失容器」。
        boolean needContainer = level.getBlockState(pos).getBlock() instanceof MechanicalSoil
                && !MechanicalSoilBlockEntity.hasOutputContainer(level, pos);
        data.putBoolean(NEED_CONTAINER, needContainer);

        // 材料加工机械（破碎机 / 压模机 / 线材轧机）：状态（顶部缺容器 / 等待原料）+ 加工中的配方进度
        // （绿色「配方」槽 —— 口径与大机器一致：停机 / 没料时不写，客户端也就不画）。
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            data.putInt(MACHINE_STATE, machine.state().ordinal());
            if (machine.state() == MachineBlockEntity.State.WORKING && machine.jadeRemainingTicks() >= 0) {
                data.putInt(RECIPE_TICKS, machine.jadeRemainingTicks());
                data.putDouble(RECIPE_PROGRESS, machine.jadeProgress());
            }
        }

        // 焊接机：状态（顶部缺容器 / 底部缺储罐 / 缺流体 / 等待原料）+ 加工中的绿色「配方」进度条
        // （口径与材料加工机械一致）。
        if (level.getBlockEntity(pos) instanceof WeldingMachineBlockEntity welding) {
            data.putInt(WELD_STATE, welding.state().ordinal());
            data.putDouble(WELD_MIN_HP, welding.recipeMinFlowRate());
            data.putDouble(WELD_BASE_HP, welding.recipeBaseFlowRate());
            if (welding.state() == WeldingMachineBlockEntity.State.WORKING
                    && welding.jadeRemainingTicks() >= 0) {
                data.putInt(RECIPE_TICKS, welding.jadeRemainingTicks());
                data.putDouble(RECIPE_PROGRESS, welding.jadeProgress());
            }
        }

        // 开采钻：模式 / 范围 / 状态 / HP 倍速 + 工作时的绿色「配方」进度（口径与材料加工机械一致）
        if (level.getBlockEntity(pos) instanceof DrillBlockEntity drill) {
            data.putInt(DRILL_MODE, drill.mode().ordinal());
            data.putInt(DRILL_RANGE, drill.rangeFor(drill.mode()));
            data.putInt(DRILL_STATE, drill.state().ordinal());
            data.putDouble(DRILL_SPEED, drill.speedMultiplier());
            data.putBoolean(DRILL_BEDROCK, drill.isBedrockMode());
            if (drill.state() == DrillBlockEntity.State.WORKING && drill.jadeRemainingTicks() >= 0) {
                if (drill.isScanning()) {
                    // 周期已满但还在补扫：进度条改用「扫描进度」，文字由客户端换成「扫描中」
                    data.putBoolean(DRILL_SCANNING, true);
                    data.putInt(RECIPE_TICKS, 0);
                    data.putDouble(RECIPE_PROGRESS, drill.scanProgress());
                } else {
                    data.putInt(RECIPE_TICKS, drill.jadeRemainingTicks());
                    data.putDouble(RECIPE_PROGRESS, drill.jadeProgress());
                }
            }
        }

        // 限流管（闸门）：本机设定值 + 实际下放热流率 —— 客户端在第 4 行后多画一行「限流」。
        // 「本机看到的热流率」= loadAt 按位置给的值（同一张账本，只是它下游那段被压低）；
        // 下放值 = min(本机看到的, 设定值) —— 也正是它 outputFace 那一侧看到的网络热流率。
        if (level.getBlockEntity(pos) instanceof FlowLimiterBlockEntity limiter) {
            double here = ThermalNetwork.loadAt(level, pos).flowRate();
            double pass = limiter.limit() > 0 ? Math.min(here, limiter.limit()) : here;
            data.putDouble(LIMITER_PASS, pass);
            data.putInt(LIMITER_LIMIT, limiter.limit());
        }

        // 大型机器（生化）：当前温度带（按贴在机器上的供能铜块数量）—— 客户端把状态行换成「XX 模式」；
        // 不带温度带条件的机器（调浆 / 离心 / 结晶 / 生物反应）不写，客户端照旧显示通用状态行。
        if (level.getBlockEntity(anchor.pos()) instanceof BioMachineBlockEntity bio) {
            int band = bio.jadeBandOrdinal();
            if (band >= 0) {
                data.putInt(BAND, band);
            }
        }

        // 燃烧引擎（双模）：模式 + 剩余可运行时长 —— 客户端把状态行换成「固体 / 液体模式」并画紫色条
        if (level.getBlockEntity(pos) instanceof BurningEngineBlockEntity engine) {
            data.putInt(FUEL_MODE, engine.mode().ordinal());
            data.putInt(FUEL_TICKS, engine.remainingTicks());
        }

        // 热力引擎（固体模式）：只有停机 / 固体两态 —— 同样用燃料条显示剩余时长（满格按它自己的消耗速度折算）
        if (level.getBlockEntity(pos) instanceof HeatEngineBlockEntity engine) {
            data.putInt(FUEL_MODE, engine.isRunning() ? 1 : 0);
            data.putInt(FUEL_TICKS, engine.remainingTicks());
            data.putInt(FUEL_FULL, HeatEngineBlockEntity.POOL_MAX * HeatEngineBlockEntity.TICKS_PER_POINT);
        }

        // 燃烧类发电机组（内燃 / 燃气 / 火箭）：转速或室压 / 耗燃料 / 液氧 / 状态 / 产汽
        // —— 客户端在通用热压差 HUD 之后追加机组面板（照汽轮机的口径：转速 + 耗量 + 出力 + 状态）。
        if (level.getBlockEntity(anchor.pos()) instanceof IndustrialEngineBlockEntity engine) {
            data.putBoolean(ENGINE, true);
            data.putInt(ENGINE_KIND, 0);
            data.putDouble(ENGINE_LOAD, engine.rpm());
            data.putDouble(ENGINE_DP, engine.capacity());
            data.putInt(ENGINE_FUEL_USE, IndustrialEngineBlockEntity.FUEL_PER_TICK);
            data.putBoolean(ENGINE_OXY, engine.isOxygenated());
            data.putInt(ENGINE_STATE, engine.fuelState().ordinal());
        } else if (level.getBlockEntity(anchor.pos()) instanceof GasTurbineBlockEntity turbine) {
            data.putBoolean(ENGINE, true);
            data.putInt(ENGINE_KIND, 1);
            data.putDouble(ENGINE_LOAD, turbine.rpm());
            data.putDouble(ENGINE_DP, turbine.capacity());
            data.putInt(ENGINE_FUEL_USE, GasTurbineBlockEntity.FUEL_PER_TICK);
            data.putBoolean(ENGINE_OXY, turbine.isOxygenated());
            data.putInt(ENGINE_STATE, turbine.fuelState().ordinal());
            data.putDouble(ENGINE_STEAM, turbine.isSteaming() ? GasTurbineBlockEntity.STEAM_RATE : 0.0D);
        } else if (level.getBlockEntity(anchor.pos()) instanceof RocketEngineBlockEntity engine) {
            data.putBoolean(ENGINE, true);
            data.putInt(ENGINE_KIND, 2);
            data.putDouble(ENGINE_LOAD, engine.pressure());
            data.putDouble(ENGINE_DP, engine.capacity());
            data.putInt(ENGINE_FUEL_USE, RocketEngineBlockEntity.FUEL_PER_TICK);
            data.putInt(ENGINE_STATE, engine.fuelState().ordinal());
        }

        // 机械土壤：肥料点数 —— 客户端画紫色「肥料」条
        if (level.getBlockEntity(pos) instanceof MechanicalSoilBlockEntity soil) {
            data.putInt(FERT, soil.fertilizerPoints());
        }

        // 热室：瞄到的是铜箱子且底部垫了铜块 → 客户端把对象名改成「热室」；结构缺失（大铜箱子只垫一格）另给提示
        HotChamber.State chamber = HotChamber.stateAt(level, pos);
        data.putBoolean(HOT_CHAMBER, chamber != HotChamber.State.NONE);
        data.putBoolean(HOT_CHAMBER_MISSING, chamber == HotChamber.State.MISSING);

        // 热室：配方进度（绿色「配方」条，口径与大机器一致）—— 进度只活在驱动内存里，向它要快照；
        // 结构不完整 / 没在做 / 不是代表格时拿不到快照，客户端就不画那条。
        if (chamber == HotChamber.State.COMPLETE && level instanceof ServerLevel serverLevel) {
            BlockPos controller = HotChamber.controller(level, pos);
            if (controller != null) {
                data.putInt(HOT_CHAMBER_PRESSURE, HotChamberPressureData.get(serverLevel).get(controller));
            }
            CopperHeatDriver.Progress progress =
                    controller == null ? null : CopperHeatDriver.progressAt(serverLevel, controller);
            if (progress != null) {
                data.putInt(RECIPE_TICKS, progress.remainingTicks());
                data.putDouble(RECIPE_PROGRESS, progress.ratio());
            }
        }
    }

    /** 蒸汽网络数据：压力管写网络汇总；裂变堆 / 汽轮机写各自的蒸汽快照。 */
    private static void appendSteamData(CompoundTag data, Level level, BlockPos pos, @Nullable Anchor anchor) {
        boolean pipe = level.getBlockState(pos).getBlock() instanceof PressurePipeBlock;
        // 机器本体直接取原点；机械管 / 容器这类「端口」借热压差锚点反查所属机器
        BlockPos origin = LargeMachines.originOf(level, pos);
        if (origin == null && anchor != null) {
            origin = anchor.pos();
        }
        SteamEndpoint endpoint = null;
        if (origin != null && level.getBlockEntity(origin) instanceof SteamEndpoint found) {
            origin = origin.immutable();
            endpoint = found;
        }
        if (!pipe && endpoint == null) {
            return;
        }

        // 端点的网络要从机器原点算：机械管本身不是蒸汽网络载体，stateAt(管) 会得到空网络
        SteamNetwork.State network = SteamNetwork.stateAt(level, endpoint != null ? origin : pos);
        SteamState steam = network.steam();
        data.putBoolean(STEAM_RELATED, true);
        data.putInt(STEAM_PIPES, network.pipes().size());
        data.putInt(STEAM_GENS, network.generators().size());
        data.putInt(STEAM_CONS, network.consumers().size());
        data.putDouble(STEAM_SUPPLY, steam.amount());
        data.putDouble(STEAM_ALLOCATED, network.allocated());
        data.putDouble(STEAM_TEMP, steam.temperatureC());
        data.putDouble(STEAM_PRESS, steam.pressureBar());
        data.putInt(STEAM_GRADE, steam.grade().ordinal());
        data.putBoolean(STEAM_VENTING, network.venting());
        if (pipe) {
            data.putBoolean(STEAM_PIPE, true);
        }
        if (endpoint != null) {
            data.putInt(STEAM_STATUS, endpoint.steamStatus().ordinal());
            if (endpoint instanceof WaterReactorBlockEntity reactor) {
                SteamState output = reactor.steamOutput();
                data.putDouble(REACTOR_STEAM_AMOUNT, output.amount());
                data.putDouble(REACTOR_STEAM_TEMP, output.temperatureC());
                data.putDouble(REACTOR_STEAM_PRESS, output.pressureBar());
            } else if (endpoint instanceof SteamTurbineBlockEntity turbine) {
                data.putBoolean(TURBINE, true);
                data.putDouble(TURBINE_RPM, turbine.rpm());
                data.putDouble(TURBINE_STEAM_USE, turbine.steamUse());
                data.putDouble(TURBINE_DP, turbine.dpOutput());
                data.putDouble(TURBINE_HP, turbine.hpOutput());
            }
        }
    }

    /** 粒子流网络数据：束流管写网络汇总；缪子聚变仪写自身的束流类型 / 速率 / 累计量。 */
    private static void appendStreamData(CompoundTag data, Level level, BlockPos pos) {
        boolean pipe = level.getBlockState(pos).getBlock() instanceof BeamPipeBlock;
        BlockPos origin = LargeMachines.originOf(level, pos);
        StreamNode endpoint = null;
        if (origin != null && level.getBlockEntity(origin) instanceof StreamNode found) {
            origin = origin.immutable();
            endpoint = found;
        }
        if (!pipe && endpoint == null) {
            return;
        }
        StreamNetwork.State network = StreamNetwork.stateAt(level, endpoint != null ? origin : pos);
        data.putBoolean(STREAM_RELATED, true);
        if (pipe) {
            data.putBoolean(STREAM_PIPE, true);
        }
        data.putInt(STREAM_PIPES, network.pipes().size());
        data.putInt(STREAM_SOURCES, network.sources().size());
        int streamOrdinal = network.type().ordinal();
        if (pipe) {
            BlockState pipeState = level.getBlockState(pos);
            if (pipeState.getBlock() instanceof BeamPipeBlock
                    && pipeState.getValue(BeamPipeBlock.STREAM) == BeamPipeBlock.EMPTY) {
                streamOrdinal = -1;   // 空管：还没接入任何流
            }
        }
        data.putInt(STREAM_TYPE, streamOrdinal);
        data.putDouble(STREAM_RATE, network.rate());
    }

    /** 这一格要不要显示热压差 HUD；要的话给出「记账锚点 + 角色 + 多方块名」，不要则 null。 */
    @Nullable
    private static Anchor anchorFor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // 1) 消耗端设备（木桶 / 熔炉系列 / 机械土壤）：必须贴着铜块，且该网络里另有铜管或热源才算「接入」
        if (CopperHeat.isConsumerDevice(level, pos)) {
            BlockPos copperPos = CopperHeat.adjacentCopperBlock(level, pos);
            return copperPos != null && isNetworked(level, copperPos)
                    ? new Anchor(copperPos, Role.CONSUMER, null) : null;
        }

        // 1b) 热室（铜箱子底部垫铜块）：锚到底部那格铜块 —— 结构缺失（大铜箱子只垫一格）也照样显示，另给「缺失」提示
        if (HotChamber.isPart(level, pos)) {
            BlockPos copperPos = HotChamber.anchorCopper(level, pos);
            if (copperPos != null) {
                return new Anchor(copperPos, Role.CONSUMER, null);
            }
        }

        // 2) 大型机器本体（主方块或任意填充格都等价）：消耗端 / 生成端都可贴铜块或底排铜管，账本锚到机器原点
        BlockPos largeOrigin = LargeMachines.originOf(level, pos);
        if (largeOrigin != null && level.getBlockEntity(largeOrigin) instanceof LargeMachine machine
                && machine.isLargeMachine()) {
            // 裂变堆：即使没接铜块也要显示（要看温度 / 通量 / 功率 / 启动门槛），客户端画专属面板
            if (level.getBlockEntity(largeOrigin) instanceof WaterReactorBlockEntity) {
                return new Anchor(largeOrigin, Role.CONSUMER, machineName(level, largeOrigin));
            }
            // 缪子聚变仪同理：核心 / 温度 / 稳定值 / 约束必须能直接看到
            if (level.getBlockEntity(largeOrigin) instanceof MuonFusionBlockEntity) {
                return new Anchor(largeOrigin, Role.CONSUMER, machineName(level, largeOrigin));
            }
            // 质量投射轨道炮：锚到真的接了铜块的那个物流仓，才能读到它所在的热压差网络
            if (machine instanceof MassDriverControllerBlockEntity driver) {
                return new Anchor(driver.thermalAnchor(), Role.CONSUMER, machineName(level, largeOrigin));
            }
            // 焦化炉：同上（控制器本身不在热网里，账要记在物流仓上）
            if (machine instanceof CokeOvenControllerBlockEntity oven) {
                return new Anchor(oven.thermalAnchor(), Role.CONSUMER, machineName(level, largeOrigin));
            }
            // 生成端大机器（蒸汽轮机）：经铜管或铜块都能接入；角色是生成端，不要求贴铜块
            if (machine instanceof ThermalGenerator) {
                return new Anchor(largeOrigin, Role.GENERATOR, null);
            }
            return LargeMachines.touchesCopperHeat(level, machine)
                    ? new Anchor(largeOrigin, Role.CONSUMER, null) : null;
        }

        // 3) 大机器物流网络成员（**机械管** / 铜箱子 / 储罐）：与「贴机器的机械管」连成一片的管系、以及挂在上面的容器，
        //    显示「机器名 + 机器账本」，并与机器保持一致（第 2 行「当前热流率」、状态行「过热中 / 未过热」）。
        //    刻意**不含铜块**：机器上贴的铜块是**供能入口**（它和从它引出的供能管属于热压差网络），不该被标成机器。
        //    也**不含细铜管**：细铜管只服务热压差网络（第 4 类）。
        boolean pipe = state.getBlock() instanceof MachinePipeBlock;
        boolean container = state.is(CopperHeat.COPPER_CHESTS) || state.getBlock() instanceof TankBlock
                || state.getBlock() instanceof ReactionChamberBlock || GrateSource.isGrate(state);
        if (pipe || container) {
            // 机械管**不能**只看「贴着机器」就算接入：必须管臂真的接着机器（剪刀切断后不再挂机器名）。
            // 容器沿用「贴着机器 / 贴着机器管」两种口径。
            LargeMachine machine = pipe
                    ? LargeMachines.pipeComponentMachine(level, pos)
                    : LargeMachines.machineTouching(level, pos);
            if (machine == null && !pipe) {
                machine = LargeMachines.containerMachine(level, pos);
            }
            if (machine != null && (machine instanceof WaterReactorBlockEntity
                    || machine instanceof ThermalGenerator
                    || LargeMachines.touchesCopperHeat(level, machine))) {
                Role role = machine instanceof ThermalGenerator ? Role.GENERATOR : Role.CONSUMER;
                return new Anchor(machine.origin(), role, machineName(level, machine.origin()));
            }
        }

        // 限流管（闸门）：与铜管一样锚在**它自己这格** —— 它所在的网络还是同一张（只是它下游那段热流率更低）；
        // 「本机看到的热流率」由 loadAt 按位置返回（可能已被更上游的闸门压低），另写一条「下放值」。
        if (level.getBlockState(pos).getBlock() instanceof FlowLimiterBlock) {
            return hasRealMember(level, pos) ? new Anchor(pos, Role.PIPE, null) : null;
        }

        // 4) 普通热压差方块：铜块（中间导体）与铜管（传递件）—— 只有「真的组成网络」（网络里有生成端或消耗端）
        //    才显示；空连的一串铜管 / 铜块（没有任何机器接进来）跟原版方块一样不给任何额外信息
        if (CopperHeat.isCopperBlock(level, pos)) {
            return hasRealMember(level, pos) ? new Anchor(pos, Role.CONSUMER, null) : null;
        }
        if (state.getBlock() instanceof CopperPipeBlock) {
            return hasRealMember(level, pos) ? new Anchor(pos, Role.PIPE, null) : null;
        }

        // 5) 其余机器：引擎是生成端；同用一个方块实体的其它生化机器不显示
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof LargeMachine) {
            return null;
        }
        if (blockEntity instanceof ThermalGenerator) {
            return new Anchor(pos, Role.GENERATOR, null);
        }
        return blockEntity instanceof ThermalConsumer ? new Anchor(pos, Role.CONSUMER, null) : null;
    }

    /** 机器方块的描述键（客户端 {@code Component.translatable} 后即机器名）。 */
    private static String machineName(Level level, BlockPos origin) {
        BlockEntity blockEntity = level.getBlockEntity(origin);
        return blockEntity == null ? "" : blockEntity.getBlockState().getBlock().getDescriptionId();
    }

    /** 物流仓 → 认领它的控制器（大机器）；不是仓室 / 未成型返回 null。 */
    @Nullable
    private static LargeMachine machineOfHatch(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof HatchBlockEntity)) {
            return null;
        }
        BlockPos origin = LargeMachines.originOf(level, pos);
        return origin != null && level.getBlockEntity(origin) instanceof LargeMachine machine
                && machine.isLargeMachine() ? machine : null;
    }

    /**
     * 大机器物流容器：写机器名与<strong>该容器的物流方向</strong>。
     * <p>只有真正接在机械管网上的容器才算（{@link LargeMachines#containerMachine}）；
     * 合并大罐的方向按控制器格记账（{@link LargeMachines#containerAnchor}），Jade 瞄任意一格都显示同一个方向。
     */
    private static void appendLargeContainerData(CompoundTag data, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean container = state.is(CopperHeat.COPPER_CHESTS)
                || state.getBlock() instanceof TankBlock
                || state.getBlock() instanceof ReactionChamberBlock
                || GrateSource.isGrate(state);
        if (!container) {
            return;
        }
        LargeMachine machine = LargeMachines.containerMachine(level, pos);
        if (machine == null) {
            return;
        }
        BlockPos anchor = LargeMachines.containerAnchor(level, pos);
        data.putInt(LARGE_IO, machine.ioMode(anchor).ordinal());
        data.putString(LARGE_NAME, machineName(level, machine.origin()));
    }

    /**
     * 这一格（铜块，或贴着它的消耗端）是否<strong>真的接入了网络</strong> ——
     * 网络里得有铜管（传递件）或热源（生成端）。只是把铜块与容器摆在一起的孤立组合，
     * <strong>跟原版方块一样不给任何额外信息</strong>。
     */
    private static boolean isNetworked(Level level, BlockPos pos) {
        ThermalNetwork.Scan scan = ThermalNetwork.scan(level, pos);
        return scan.transmitterCount() > 0 || scan.generatorCount() > 0;
    }

    /**
     * 网络里除「传递件 / 铜块」之外还有成员（<strong>生成端或消耗端</strong>）——
     * 孤立的<strong>空连</strong>铜管 / 铜块（只跟自己人挨着、没有任何机器接进来）不算「组成网络」，
     * 不该显示热压差 HUD。
     */
    private static boolean hasRealMember(Level level, BlockPos pos) {
        ThermalNetwork.Scan scan = ThermalNetwork.scan(level, pos);
        return scan.generatorCount() > 0 || scan.consumerCount() > 0;
    }

    private ThermalJadeData() {}
}
