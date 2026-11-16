package dev.znecby.stellar_evolution.thermal;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.drill.DrillBlockEntity;
import dev.znecby.stellar_evolution.engine.EngineMachineSupport;
import dev.znecby.stellar_evolution.farmland.MechanicalSoil;
import dev.znecby.stellar_evolution.fission.SteamLoop;
import dev.znecby.stellar_evolution.machine.MachineBlockEntity;
import dev.znecby.stellar_evolution.muon.MuonFusionCore;
import dev.znecby.stellar_evolution.particle.ParticleSourceBlockEntity;
import dev.znecby.stellar_evolution.pipe.CopperPipeBlock;
import dev.znecby.stellar_evolution.steam.SteamEndpoint;
import dev.znecby.stellar_evolution.steam.SteamState;
import dev.znecby.stellar_evolution.stream.StreamType;
import dev.znecby.stellar_evolution.thermal.ThermalJadeData.Role;
import dev.znecby.stellar_evolution.welding.WeldingMachineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

/**
 * 热压差网络的 Jade <strong>客户端组件提供者</strong>：瞄整个网络里的<strong>任意一类方块</strong>
 * （铜管 / 热力引擎 / 燃烧引擎 / 蒸汽轮机 / 木桶 / 熔炉·烟熏炉·高炉 / 机械土壤 / 热室 / 铜块 / 大型机器）
 * 都画<strong>同一套三行 HUD</strong>，风格统一、信息简洁：
 * <ol>
 *   <li><strong>能量槽式进度条</strong>：与 Jade 的 FE 能量槽完全同款（外框 {@link BoxStyle#nestedBox()} +
 *       横向平铺的橙色渐变条），条上只写「占用 / 容量 DP」；</li>
 *   <li>热流率（HP/t）；</li>
 *   <li><strong>状态行</strong>（一行、按角色着色）：
 *       <ul>
 *         <li><strong>超载</strong>（红）——网络级告警，谁身上都先报这个；</li>
 *         <li>铜管 → <strong>正常</strong>（绿）；</li>
 *         <li>生成端（引擎 / 汽轮机）→ <strong>工作中</strong>（绿）/ <strong>停机</strong>（灰）——它自己的供电状态；</li>
 *         <li>消耗侧（铜块 / 木桶 / 炉子 / 机械土壤 / 热室 / 大型机器）→ <strong>热压中</strong>（橙）/ <strong>未热压</strong>（灰）。</li>
 *       </ul></li>
 * </ol>
 * 超载时世界里的<strong>铜管会发红闪</strong>（见 {@code CopperPipeBlock} 的 OVERLOADED/BLINKING 与
 * {@code client.ThermalPipeTintSource}）。
 *
 * <p><strong>贴着 Jade 的 FE 条照抄</strong>（反编译 {@code EnergyStorageProvider$Client} 得到的画法）：
 * 填充用 {@link JadeUI#horizontalTiledSprite} 把 16×16 的渐变条<strong>横向平铺</strong>，
 * 外框直接用 Jade 的 {@link BoxStyle#nestedBox()}，于是外形、边框、留白与 FE 条逐像素一致，
 * 只把「红渐变」换成「橙渐变」（贴图 {@code stellar_evolution:thermal_progress}）。
 *
 * <p><strong>sprite id 别写 {@code gui/sprites/} 前缀</strong>：GUI 图集（{@code minecraft:gui} 的
 * {@code atlases/gui.json}）以 {@code source=gui/sprites、prefix=""} 收图，所以
 * {@code textures/gui/sprites/thermal_progress.png} 的 sprite id 就是 {@code stellar_evolution:thermal_progress}；
 * 多写前缀会解析不到，条会变成品红的「缺失贴图」。
 *
 * <p>角色判断放在客户端（看方块类型即可），数值一律由 {@link ThermalJadeData} 在服务端算好送来；
 * 拿不到数据就不显示（网络跨区块，客户端算不准）。
 */
public final class ThermalJadeProvider implements IBlockComponentProvider {
    /** 单例（无状态）。 */
    public static final ThermalJadeProvider INSTANCE = new ThermalJadeProvider();

    /** 填充贴图的平铺单元边长（贴图本身 16×16）。 */
    private static final int BAR_TILE = 16;

    /** 填充元素：橙色的原版能量条渐变（横向平铺）。与 Jade 一样建一次、反复用。 */
    private static final Element BAR_FILL = JadeUI.horizontalTiledSprite(
            RenderPipelines.GUI_TEXTURED, StellarEvolution.id("thermal_progress"), BAR_TILE, BAR_TILE);

    /** 紫色「燃料 / 肥料」条填充（同款渐变，只换紫色相）。 */
    private static final Element FUEL_FILL = JadeUI.horizontalTiledSprite(
            RenderPipelines.GUI_TEXTURED, StellarEvolution.id("fuel_progress"), BAR_TILE, BAR_TILE);

    /** 绿色「配方」条填充（同款渐变，只换绿色相）——大机器工作时的剩余配方时长。 */
    private static final Element RECIPE_FILL = JadeUI.horizontalTiledSprite(
            RenderPipelines.GUI_TEXTURED, StellarEvolution.id("recipe_progress"), BAR_TILE, BAR_TILE);

    /** 「满条」当量：32000 刻 ≈ 26.7 分钟（= 固体燃料池 1600 点 × 20t，液体模式按同一尺度显示剩余时长）。 */
    private static final int FUEL_FULL_TICKS = 32000;

    /** 机械土壤肥料点数池容量（点）。 */
    private static final int FERT_FULL = 1600;

    @Override
    public Identifier getUid() {
        return ThermalJadeData.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();

        // 束流管：粒子流网络汇总（无热压差数据，必须先处理）
        if (data.getBooleanOr(ThermalJadeData.STREAM_PIPE, false)) {
            appendStreamPipe(tooltip, data);
            return;
        }

        // 蒸汽网络：压力管画网络汇总；汽轮机先走通用热压差 HUD（DP 槽和其它机器一致），再追加转速等行；
        // 裂变堆在下方专属面板里补蒸汽行
        boolean turbine = data.getBooleanOr(ThermalJadeData.TURBINE, false);
        if (data.getBooleanOr(ThermalJadeData.STEAM_RELATED, false)
                && !turbine
                && !data.contains(ThermalJadeData.CAPACITY)
                && data.getBooleanOr(ThermalJadeData.STEAM_PIPE, false)) {
            appendSteamPipe(tooltip, data);
            return;
        }

        if (!data.contains(ThermalJadeData.CAPACITY)) {
            if (turbine) {
                appendTurbine(tooltip, data, false);
            }
            // 物流容器即使没接热压差，也要显示「所属机器 + 输入 / 输出」
            if (data.contains(ThermalJadeData.LARGE_IO)) {
                appendLargeName(tooltip, data);
                appendLargeIo(tooltip, data);
            }
            return;
        }

        double capacity = data.getDoubleOr(ThermalJadeData.CAPACITY, 0.0D);
        double flowRate = data.getDoubleOr(ThermalJadeData.FLOW_RATE, 0.0D);
        double occupancy = data.getDoubleOr(ThermalJadeData.OCCUPANCY, 0.0D);
        boolean overloaded = data.getBooleanOr(ThermalJadeData.OVERLOADED, false);
        boolean working = data.getBooleanOr(ThermalJadeData.WORKING, false);
        double output = data.getDoubleOr(ThermalJadeData.OUTPUT, 0.0D);
        double maxFlow = data.getDoubleOr(ThermalJadeData.MAX_FLOW, 0.0D);
        int recipeTicks = data.getIntOr(ThermalJadeData.RECIPE_TICKS, -1);
        double recipeProgress = data.getDoubleOr(ThermalJadeData.RECIPE_PROGRESS, 0.0D);
        boolean needContainer = data.getBooleanOr(ThermalJadeData.NEED_CONTAINER, false);
        int band = data.getIntOr(ThermalJadeData.BAND, -1);
        int fuelMode = data.getIntOr(ThermalJadeData.FUEL_MODE, -1);
        int fuelTicks = data.getIntOr(ThermalJadeData.FUEL_TICKS, 0);
        int fuelFull = data.getIntOr(ThermalJadeData.FUEL_FULL, FUEL_FULL_TICKS);
        int fert = data.getIntOr(ThermalJadeData.FERT, -1);
        int drillMode = data.getIntOr(ThermalJadeData.DRILL_MODE, -1);
        int drillRange = data.getIntOr(ThermalJadeData.DRILL_RANGE, 0);
        int drillState = data.getIntOr(ThermalJadeData.DRILL_STATE, -1);
        double drillSpeed = data.getDoubleOr(ThermalJadeData.DRILL_SPEED, 0.0D);
        boolean drillScanning = data.getBooleanOr(ThermalJadeData.DRILL_SCANNING, false);
        boolean drillBedrock = data.getBooleanOr(ThermalJadeData.DRILL_BEDROCK, false);

        Role role = roleOf(accessor, data);

        // 多方块结构：端口机械管额外报出它所属的大型机器名（如「生物反应釜」）
        String largeName = data.getStringOr(ThermalJadeData.LARGE_NAME, "");
        if (!largeName.isEmpty()) {
            tooltip.add(Component.translatable(largeName).withStyle(ChatFormatting.GOLD));
        }
        // 物流容器：所属机器下面紧跟「物流方向：输入 / 输出」
        appendLargeIo(tooltip, data);

        // 质量投射轨道炮：势能（上限 = 网络差压容量）/ 等压 / 本人倒计时 / 未接入提示
        if (data.getBooleanOr(ThermalJadeData.MASS_DRIVER, false)) {
            double driverCapacity = data.getDoubleOr(ThermalJadeData.CAPACITY, 0.0D);
            double potential = data.getDoubleOr(ThermalJadeData.MASS_DRIVER_POTENTIAL, 0.0D);
            tooltip.add(Component.translatable("stellar_evolution.jade.mass_driver.potential",
                    num(potential), num(driverCapacity)).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("stellar_evolution.jade.mass_driver.pressure",
                    data.getIntOr(ThermalJadeData.MASS_DRIVER_PRESSURE, 0)).withStyle(ChatFormatting.AQUA));
            int countdown = data.getIntOr(ThermalJadeData.MASS_DRIVER_COUNTDOWN, 0);
            if (countdown > 0) {
                tooltip.add(Component.translatable("stellar_evolution.jade.mass_driver.countdown", countdown)
                        .withStyle(ChatFormatting.RED));
            }
            if (driverCapacity <= 0.0D) {
                tooltip.add(Component.translatable("stellar_evolution.jade.mass_driver.no_network")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        // 格栅：水格栅（产水）/ 钢格栅（空气源）/ 泵模式 + 缓冲 —— 让玩家一眼看出机制在跑
        if (data.contains(ThermalJadeData.GRATE_MODE)) {
            int grateMode = data.getIntOr(ThermalJadeData.GRATE_MODE, 0);
            int grateSources = data.getIntOr(ThermalJadeData.GRATE_SOURCES, 0);
            long grateBuffer = data.getLongOr(ThermalJadeData.GRATE_BUFFER, 0L);
            double grateCapacity = data.getDoubleOr(ThermalJadeData.GRATE_CAPACITY, 16000.0D);
            double grateRate = data.getDoubleOr(ThermalJadeData.GRATE_RATE, 8.0D);
            if (grateMode == 2) {
                tooltip.add(Component.translatable("stellar_evolution.jade.grate.air",
                        grateSources, num(grateRate), num(grateBuffer), num(grateCapacity))
                        .withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(grateMode == 1
                        ? Component.translatable("stellar_evolution.jade.grate.water", grateSources,
                                num(grateRate), num(grateBuffer), num(grateCapacity)).withStyle(ChatFormatting.AQUA)
                        : Component.translatable("stellar_evolution.jade.grate.pump", 16)
                                .withStyle(ChatFormatting.GOLD));
            }
        }

        // 大机器：网络里接了含水铜格栅 → 显示「无限水供应 + 总速率」
        if (data.contains(ThermalJadeData.WATER_SUPPLY)) {
            double supply = data.getDoubleOr(ThermalJadeData.WATER_SUPPLY, 0.0D);
            int grates = data.getIntOr(ThermalJadeData.WATER_GRATES, 0);
            tooltip.add(Component.translatable("stellar_evolution.jade.water_supply",
                    num(supply), grates).withStyle(ChatFormatting.AQUA));
        }

        // 回旋加速器：差速 / 配方 / 状态专属面板（不画通用热压差 HUD）
        if (data.getBooleanOr(ThermalJadeData.CYCLOTRON, false)) {
            appendCyclotron(tooltip, data);
            return;
        }

        // 粒子源：粒子类型 / 罐耐久 / 输出 / 状态专属面板（不画通用热压差 HUD）
        if (data.getBooleanOr(ThermalJadeData.PARTICLE_SOURCE, false)) {
            appendParticleSource(tooltip, data);
            return;
        }

        // 辐照仓：DP 槽 / 热流率 + 中子流接收 / 进度 / 状态
        if (data.getBooleanOr(ThermalJadeData.IRRADIATION, false)) {
            appendStreamConsumerPower(tooltip, data);
            appendIrradiation(tooltip, data);
            return;
        }

        // 缪子聚变仪：核心 / 温度 / 稳定值 / 约束专属面板（不画通用热压差 HUD）
        if (data.getBooleanOr(ThermalJadeData.MUON, false)) {
            appendMuon(tooltip, data);
            return;
        }

        // 裂变堆：画专属面板（温度 / 通量 / 功率 / 启动门槛），不画通用热压差 HUD
        if (data.getBooleanOr(ThermalJadeData.REACTOR, false)) {
            appendReactor(tooltip, data);
            return;
        }

        // 第 1 行：能量槽（外框 + 橙色填充；条上只写数字 + 单位）
        ProgressView bar = new ProgressView(
                ProgressView.Part.of(ratio(capacity, occupancy), BAR_FILL),
                Component.translatable("stellar_evolution.jade.thermal.bar", num(occupancy), num(capacity)),
                JadeUI.progressStyle(),
                BoxStyle.nestedBox());
        tooltip.add(JadeUI.progress(bar));

        // 第 2 行：热流率 —— 按角色换名（连接件报网络值、生成端报自己的输出、消耗端报「有效值」）
        // 消耗端：显示 min(网络热流率, 该方块满速 1t 所需热流率) —— 超过满速阈值再涨也不会更快，就不往上涨。
        double effectiveFlow = maxFlow > 0.0D ? Math.min(flowRate, maxFlow) : flowRate;
        tooltip.add(switch (role) {
            case PIPE -> Component.translatable("stellar_evolution.jade.thermal.flow.network", num(flowRate));
            case GENERATOR -> Component.translatable("stellar_evolution.jade.thermal.flow.output", num(output));
            case CONSUMER -> Component.translatable("stellar_evolution.jade.thermal.flow.current", num(effectiveFlow));
        });

        // 第 3 行：状态（大型机器若带温度带条件，这里换成「厌氧 / 中温 / 高温 模式」；燃烧引擎换成「固体 / 液体模式」）
        tooltip.add(statusLine(role, overloaded, working, flowRate, band, fuelMode));

        // 湮灭反应堆：燃料构成 + 本次燃烧是否加过鎶-304（稳定岛点火）
        if (data.getBooleanOr(ThermalJadeData.ANNIHILATION, false)) {
            tooltip.add(Component.translatable("stellar_evolution.jade.annihilation.fuel")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (data.getBooleanOr(ThermalJadeData.ANNIHILATION_BOOST, false)) {
            tooltip.add(Component.translatable("stellar_evolution.jade.annihilation.boost")
                    .withStyle(ChatFormatting.GOLD));
        }

        // 开采钻：模式 + 范围（金）；速度封顶时提示「满速运行中」（绿）
        if (drillMode >= 0) {
            if (drillBedrock) {
                appendDrillBedrock(tooltip, drillState, drillSpeed);
            } else {
                appendDrillMode(tooltip, drillMode, drillRange, drillState, drillSpeed);
            }
        }

        // 大型机器（生化 / 石化）/ 热室：绿色「配方」槽 —— 与 DP 槽同款，条 = 当前周期进度，
        // 文字 = 剩余配方时长（分:秒）；每刻至少结算一条（服务端给 0）时显示「满速」。
        if (recipeTicks >= 0) {
            boolean drill = drillMode >= 0;
            Component recipeText;
            if (drill && drillScanning) {
                // 周期已满但还在补扫剩余方块（大范围 / 高倍速）
                recipeText = Component.translatable("stellar_evolution.jade.drill.scanning");
            } else if (recipeTicks == 0) {
                recipeText = Component.translatable(drill
                        ? "stellar_evolution.jade.drill.recipe_full"
                        : "stellar_evolution.jade.thermal.recipe_full");
            } else {
                recipeText = Component.translatable(drill
                        ? "stellar_evolution.jade.drill.recipe_bar"
                        : "stellar_evolution.jade.thermal.recipe_bar", duration(recipeTicks));
            }
            ProgressView recipeBar = new ProgressView(
                    ProgressView.Part.of((float) Math.min(1.0D, Math.max(0.0D, recipeProgress)), RECIPE_FILL),
                    recipeText,
                    JadeUI.progressStyle(),
                    BoxStyle.nestedBox());
            tooltip.add(JadeUI.progress(recipeBar));
        }

        // 引擎：紫色「燃料」条 —— 条 = 剩余可运行时长 / 满池当量（燃烧引擎 1600×20t、热力引擎 1600×10t）
        if (fuelMode >= 0) {
            float fuelRatio = (float) Math.min(1.0D, Math.max(0.0D, fuelFull <= 0 ? 0.0D : fuelTicks / (double) fuelFull));
            ProgressView fuelBar = new ProgressView(
                    ProgressView.Part.of(fuelRatio, FUEL_FILL),
                    Component.translatable("stellar_evolution.jade.thermal.fuel_bar", duration(fuelTicks)),
                    JadeUI.progressStyle(),
                    BoxStyle.nestedBox());
            tooltip.add(JadeUI.progress(fuelBar));
        }

        // 机械土壤：紫色「肥料」条 —— 条 = 肥料点数 / 1600
        if (fert >= 0) {
            float fertRatio = (float) Math.min(1.0D, Math.max(0.0D, fert / (double) FERT_FULL));
            ProgressView fertBar = new ProgressView(
                    ProgressView.Part.of(fertRatio, FUEL_FILL),
                    Component.translatable("stellar_evolution.jade.thermal.fert_bar", fert),
                    JadeUI.progressStyle(),
                    BoxStyle.nestedBox());
            tooltip.add(JadeUI.progress(fertBar));
        }

        // 机械土壤（耕地 / 沙地 / 灵魂沙地 / 草地）专有：底部缺容器（不工作）
        if (needContainer) {
            tooltip.add(Component.translatable("stellar_evolution.jade.thermal.status.need_container")
                    .withStyle(ChatFormatting.RED));
        }

        // 材料加工机械（破碎机 / 压模机 / 线材轧机）专有：顶部缺容器（红）/ 等待原料（灰）
        // ——加工中不额外加行，绿色「配方」槽 + 状态行「过热中」已经说明问题。
        int machineState = data.getIntOr(ThermalJadeData.MACHINE_STATE, -1);
        if (machineState == MachineBlockEntity.State.NO_CONTAINER.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.machine.no_container")
                    .withStyle(ChatFormatting.RED));
        } else if (machineState == MachineBlockEntity.State.WAITING.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.machine.waiting")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 焊接机专有：顶部缺容器（红）/ 底部缺储罐（红）/ 等待流体（灰）/ 等待原料（灰）
        // ——加工中不额外加行，绿色「配方」槽 + 状态行「热压中」已经说明问题。
        int weldState = data.getIntOr(ThermalJadeData.WELD_STATE, -1);
        if (weldState == WeldingMachineBlockEntity.State.NO_CONTAINER.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.machine.no_container")
                    .withStyle(ChatFormatting.RED));
        } else if (weldState == WeldingMachineBlockEntity.State.NO_TANK.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.welding.no_tank")
                    .withStyle(ChatFormatting.RED));
        } else if (weldState == WeldingMachineBlockEntity.State.NO_FLUID.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.welding.no_fluid")
                    .withStyle(ChatFormatting.GRAY));
        } else if (weldState == WeldingMachineBlockEntity.State.WAITING.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.machine.waiting")
                    .withStyle(ChatFormatting.GRAY));
        } else if (weldState == WeldingMachineBlockEntity.State.NO_HEAT.ordinal()) {
            double minHp = data.getDoubleOr(ThermalJadeData.WELD_MIN_HP, 0.0D);
            double baseHp = data.getDoubleOr(ThermalJadeData.WELD_BASE_HP, 0.0D);
            if (minHp > 0.0D) {
                tooltip.add(Component.translatable("stellar_evolution.jade.welding.no_heat",
                                (int) minHp, (int) baseHp)
                        .withStyle(ChatFormatting.RED));
            }
        }

        // 开采钻专有：缺失容器（红）/ 等待上方容器腾空间（灰）/ 已挖到世界底部（灰）
        if (drillState == DrillBlockEntity.State.NO_CONTAINER.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.machine.no_container")
                    .withStyle(ChatFormatting.RED));
        } else if (drillState == DrillBlockEntity.State.WAITING.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.drill.waiting")
                    .withStyle(ChatFormatting.GRAY));
        } else if (drillState == DrillBlockEntity.State.DONE.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.drill.done")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 限流管（闸门）专有：多画一行「限流」（表里读的是上游网络的账本 + 本机设定值 / 实际下放值）
        if (data.contains(ThermalJadeData.LIMITER_PASS)) {
            int limitValue = data.getIntOr(ThermalJadeData.LIMITER_LIMIT, 0);
            double pass = data.getDoubleOr(ThermalJadeData.LIMITER_PASS, 0.0D);
            tooltip.add(Component.translatable(limitValue > 0
                            ? "stellar_evolution.jade.limiter.set"
                            : "stellar_evolution.jade.limiter.auto",
                    limitValue > 0 ? new Object[] {num(limitValue), num(pass)} : new Object[] {num(pass)})
                    .withStyle(ChatFormatting.AQUA));
        }

        // 热室专有：结构缺失（大铜箱子只垫了一块铜块）—— 整箱不工作
        if (data.getBooleanOr(ThermalJadeData.HOT_CHAMBER_MISSING, false)) {
            tooltip.add(Component.translatable("stellar_evolution.jade.thermal.status.hot_chamber_missing")
                    .withStyle(ChatFormatting.RED));
        }

        // 热室专有：等压值（0 = 默认配方组；1/2… = 冲突配方组）
        if (data.contains(ThermalJadeData.HOT_CHAMBER_PRESSURE)) {
            tooltip.add(Component.translatable("stellar_evolution.jade.thermal.hot_chamber_pressure",
                            data.getIntOr(ThermalJadeData.HOT_CHAMBER_PRESSURE, 0))
                    .withStyle(ChatFormatting.AQUA));
        }

        // 汽轮机：通用热压差 HUD 之后追加转速 / 耗汽 / 出力 / 状态
        if (turbine) {
            appendTurbine(tooltip, data, true);
        }

        // 燃烧类发电机组（内燃 / 燃气 / 火箭）：同款口径追加转速（室压）/ 耗燃料 / 出力 / 产汽 / 状态
        if (data.getBooleanOr(ThermalJadeData.ENGINE, false)) {
            appendEngine(tooltip, data);
        }
    }

    /** 大机器物流容器的所属机器名（只在确实写了 LARGE_IO 时画）。 */
    private static void appendLargeName(ITooltip tooltip, CompoundTag data) {
        String largeName = data.getStringOr(ThermalJadeData.LARGE_NAME, "");
        if (!largeName.isEmpty()) {
            tooltip.add(Component.translatable(largeName).withStyle(ChatFormatting.GOLD));
        }
    }

    /** 大机器物流容器的物流方向：输入（蓝）/ 输出（金）。 */
    private static void appendLargeIo(ITooltip tooltip, CompoundTag data) {
        if (!data.contains(ThermalJadeData.LARGE_IO)) {
            return;
        }
        boolean output = data.getIntOr(ThermalJadeData.LARGE_IO, 0) == 1;
        tooltip.add(Component.translatable("stellar_evolution.large.io.set",
                        Component.translatable(output
                                ? "stellar_evolution.large.io.output"
                                : "stellar_evolution.large.io.input"))
                .withStyle(output ? ChatFormatting.GOLD : ChatFormatting.AQUA));
    }

    /** 燃烧类发电机组专属行：转速（室压）+ 耗燃料 + 出力 + 燃气轮机产汽 + 状态原因。 */
    private static void appendEngine(ITooltip tooltip, CompoundTag data) {
        int kind = data.getIntOr(ThermalJadeData.ENGINE_KIND, 0);
        double load = data.getDoubleOr(ThermalJadeData.ENGINE_LOAD, 0.0D);
        double dp = data.getDoubleOr(ThermalJadeData.ENGINE_DP, 0.0D);
        double hp = data.getDoubleOr(ThermalJadeData.OUTPUT, 0.0D);
        int fuelUse = data.getIntOr(ThermalJadeData.ENGINE_FUEL_USE, 0);
        boolean oxy = data.getBooleanOr(ThermalJadeData.ENGINE_OXY, false);
        double steam = data.getDoubleOr(ThermalJadeData.ENGINE_STEAM, 0.0D);
        int state = data.getIntOr(ThermalJadeData.ENGINE_STATE,
                EngineMachineSupport.FuelState.IDLE.ordinal());

        // 1) 转速 / 室压
        tooltip.add(Component.translatable(kind == 2
                        ? "stellar_evolution.jade.engine.pressure" : "stellar_evolution.jade.engine.rpm",
                percent(load)).withStyle(ChatFormatting.GOLD));

        // 2) 耗燃料（只有真正在烧的时候才报；内燃 / 燃气缺液氧照烧但出力受限，火箭不产生该状态）
        if (state == EngineMachineSupport.FuelState.RUNNING.ordinal()
                || state == EngineMachineSupport.FuelState.NO_OXYGEN.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.engine.fuel", fuelUse)
                    .withStyle(ChatFormatting.AQUA));
        }

        // 3) 出力
        tooltip.add(Component.translatable("stellar_evolution.jade.engine.output", num(dp), num(hp))
                .withStyle(ChatFormatting.GOLD));

        // 4) 燃气轮机联合循环：产汽 / 缺水
        if (kind == 1) {
            if (steam > 0.0D) {
                tooltip.add(Component.translatable("stellar_evolution.jade.engine.steam", num(steam))
                        .withStyle(ChatFormatting.AQUA));
            } else if (state == EngineMachineSupport.FuelState.RUNNING.ordinal()
                    || state == EngineMachineSupport.FuelState.NO_OXYGEN.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.engine.no_water")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        // 5) 状态原因（运转中 / 停机已由通用 HUD 的状态行说明，这里只补「为什么不动」）
        if (state == EngineMachineSupport.FuelState.VACUUM.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.engine.vacuum")
                    .withStyle(ChatFormatting.RED));
        } else if (state == EngineMachineSupport.FuelState.NO_FUEL.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.engine.no_fuel")
                    .withStyle(ChatFormatting.GRAY));
        } else if (state == EngineMachineSupport.FuelState.NO_OXYGEN.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.engine.no_oxygen")
                    .withStyle(ChatFormatting.GOLD));
        }

        // 液氧助燃提示（有氧时轻描淡写一行，和转速行区分开）
        if (oxy && (state == EngineMachineSupport.FuelState.RUNNING.ordinal())) {
            tooltip.add(Component.translatable("stellar_evolution.jade.engine.oxygen")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** 0..1.25 → 百分比文案。 */
    private static String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", Math.max(0.0D, value) * 100.0D);
    }

    /** 压力管：蒸汽网络汇总（DP 槽式剩余蒸汽条 + 管道 / 生成端 / 消耗端 + 蒸汽指数；无消耗端时报末端排气）。 */
    private static void appendSteamPipe(ITooltip tooltip, CompoundTag data) {
        int pipes = data.getIntOr(ThermalJadeData.STEAM_PIPES, 0);
        int generators = data.getIntOr(ThermalJadeData.STEAM_GENS, 0);
        int consumers = data.getIntOr(ThermalJadeData.STEAM_CONS, 0);
        double supply = data.getDoubleOr(ThermalJadeData.STEAM_SUPPLY, 0.0D);
        double allocated = Math.min(supply, Math.max(0.0D,
                data.getDoubleOr(ThermalJadeData.STEAM_ALLOCATED, 0.0D)));
        double remaining = Math.max(0.0D, supply - allocated);
        tooltip.add(Component.translatable("stellar_evolution.jade.steam.network", pipes, generators, consumers)
                .withStyle(ChatFormatting.AQUA));
        if (supply > 0.0D) {
            // 与 DP 槽同款：外框 + 横向平铺填充，条上写「剩余 / 总量」
            ProgressView bar = new ProgressView(
                    ProgressView.Part.of(ratio(supply, remaining), BAR_FILL),
                    Component.translatable("stellar_evolution.jade.steam.bar", num(remaining), num(supply)),
                    JadeUI.progressStyle(),
                    BoxStyle.nestedBox());
            tooltip.add(JadeUI.progress(bar));
            tooltip.add(steamLine(data, supply));
        }
        if (data.getBooleanOr(ThermalJadeData.STEAM_VENTING, false)) {
            tooltip.add(Component.translatable("stellar_evolution.jade.steam.venting")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** 汽轮机专属行：转速（不带上限斜杠）+ 耗汽 + 出力 + 状态；{@code generic} = 上面已经画过通用 HUD。 */
    private static void appendTurbine(ITooltip tooltip, CompoundTag data, boolean generic) {
        double rpm = data.getDoubleOr(ThermalJadeData.TURBINE_RPM, 0.0D);
        double use = data.getDoubleOr(ThermalJadeData.TURBINE_STEAM_USE, 0.0D);
        double dp = data.getDoubleOr(ThermalJadeData.TURBINE_DP, 0.0D);
        double hp = data.getDoubleOr(ThermalJadeData.TURBINE_HP, 0.0D);
        int pipes = data.getIntOr(ThermalJadeData.STEAM_PIPES, 0);
        int status = data.getIntOr(ThermalJadeData.STEAM_STATUS, SteamEndpoint.SteamStatus.NO_STEAM.ordinal());

        tooltip.add(Component.translatable("stellar_evolution.jade.turbine.rpm", num(rpm))
                .withStyle(ChatFormatting.GOLD));

        if (use > 0.0D) {
            int grade = data.getIntOr(ThermalJadeData.STEAM_GRADE, 0);
            tooltip.add(Component.translatable("stellar_evolution.jade.turbine.steam", num(use),
                            gradeName(grade),
                            oneDecimal(data.getDoubleOr(ThermalJadeData.STEAM_TEMP, 20.0D)),
                            oneDecimal(data.getDoubleOr(ThermalJadeData.STEAM_PRESS, 0.0D)))
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("stellar_evolution.jade.turbine.output", num(dp), num(hp))
                .withStyle(ChatFormatting.GOLD));

        if (pipes == 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.turbine.no_pipe")
                    .withStyle(ChatFormatting.RED));
        } else if (status == SteamEndpoint.SteamStatus.NO_STEAM.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.turbine.no_steam")
                    .withStyle(ChatFormatting.GRAY));
        } else if (status == SteamEndpoint.SteamStatus.STARVED.ordinal()) {
            // 网络有汽，但被更近的机组先拿满了
            tooltip.add(Component.translatable("stellar_evolution.jade.turbine.starved")
                    .withStyle(ChatFormatting.RED));
        } else if (status == SteamEndpoint.SteamStatus.SPINNING_UP.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.turbine.spin_up")
                    .withStyle(ChatFormatting.GOLD));
        } else if (!generic) {
            // 通用 HUD 的「工作中」已经说明运转中，这里不重复
            tooltip.add(Component.translatable("stellar_evolution.jade.turbine.running")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** 开采钻：基岩模式固定行 + 门槛提示 + 速度封顶时的「满速运行中」行。 */
    private static void appendDrillBedrock(ITooltip tooltip, int state, double speed) {
        tooltip.add(Component.translatable("stellar_evolution.jade.drill.bedrock",
                        (int) DrillBlockEntity.BEDROCK_MIN_FLOW_RATE)
                .withStyle(ChatFormatting.GOLD));
        if (state == DrillBlockEntity.State.NO_HEAT.ordinal()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.drill.bedrock_low_flow",
                            (int) DrillBlockEntity.BEDROCK_MIN_FLOW_RATE)
                    .withStyle(ChatFormatting.RED));
        }
        double maxSpeed = DrillBlockEntity.BEDROCK_MAX_USEFUL_FLOW_RATE / DrillBlockEntity.BASE_FLOW_RATE;
        if (state == DrillBlockEntity.State.WORKING.ordinal() && speed >= maxSpeed - 1.0E-6D) {
            tooltip.add(Component.translatable("stellar_evolution.jade.drill.full_speed")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** 开采钻：模式行（含当前范围）+ 速度封顶时的「满速运行中」行。 */
    private static void appendDrillMode(ITooltip tooltip, int mode, int range, int state, double speed) {
        boolean ore = mode == DrillBlockEntity.Mode.ORE.ordinal();
        Component modeName = Component.translatable(ore
                ? "stellar_evolution.drill.mode.ore" : "stellar_evolution.drill.mode.area");
        Component rangeText = Component.translatable(ore
                ? "stellar_evolution.drill.range.ore" : "stellar_evolution.drill.range.area", range, range);
        tooltip.add(Component.translatable("stellar_evolution.jade.drill.mode", modeName, rangeText)
                .withStyle(ChatFormatting.GOLD));
        double maxSpeed = DrillBlockEntity.MAX_USEFUL_FLOW_RATE / DrillBlockEntity.BASE_FLOW_RATE;
        if (state == DrillBlockEntity.State.WORKING.ordinal() && speed >= maxSpeed - 1.0E-6D) {
            tooltip.add(Component.translatable("stellar_evolution.jade.drill.full_speed")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** 蒸汽数据行：等级 + 温度 + 压力 + 流量。 */
    private static Component steamLine(CompoundTag data, double supply) {
        return translatableSteam("stellar_evolution.jade.steam.data", data, supply);
    }

    private static Component translatableSteam(String key, CompoundTag data, double supply) {
        int grade = data.getIntOr(ThermalJadeData.STEAM_GRADE, 0);
        double temperature = data.getDoubleOr(ThermalJadeData.STEAM_TEMP, 20.0D);
        double pressure = data.getDoubleOr(ThermalJadeData.STEAM_PRESS, 0.0D);
        return Component.translatable(key, gradeName(grade), oneDecimal(temperature), oneDecimal(pressure), num(supply))
                .withStyle(ChatFormatting.AQUA);
    }

    /** 蒸汽等级名（序数与 {@link SteamLoop.SteamGrade} 一致）。 */
    private static Component gradeName(int ordinal) {
        String[] keys = {
                "stellar_evolution.jade.steam.grade.none",
                "stellar_evolution.jade.steam.grade.saturated",
                "stellar_evolution.jade.steam.grade.superheated",
                "stellar_evolution.jade.steam.grade.supercritical",
                "stellar_evolution.jade.steam.grade.ultra_supercritical"
        };
        return Component.translatable(keys[Math.max(0, Math.min(keys.length - 1, ordinal))]);
    }

    /** 束流管：粒子流网络汇总（类型 + 管道 / 源数量 + 瞬时速率）。 */
    private static void appendStreamPipe(ITooltip tooltip, CompoundTag data) {
        int pipes = data.getIntOr(ThermalJadeData.STREAM_PIPES, 0);
        int sources = data.getIntOr(ThermalJadeData.STREAM_SOURCES, 0);
        int streamOrdinal = data.getIntOr(ThermalJadeData.STREAM_TYPE, 0);
        double rate = data.getDoubleOr(ThermalJadeData.STREAM_RATE, 0.0D);
        tooltip.add(Component.translatable("stellar_evolution.jade.stream.network", pipes, sources)
                .withStyle(ChatFormatting.AQUA));
        if (streamOrdinal < 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.stream.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        StreamType type = StreamType.byOrdinal(streamOrdinal);
        tooltip.add(Component.translatable("stellar_evolution.jade.stream.data",
                        Component.translatable(type.nameKey()), num(rate), Component.translatable(type.unitKey()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** 回旋加速器专属面板：差速 / DP / 配方需求 / 本轮产出 / 状态。 */
    private static void appendCyclotron(ITooltip tooltip, CompoundTag data) {
        double charge = data.getDoubleOr(ThermalJadeData.CYCLOTRON_CHARGE, 0.0D);
        double cap = data.getDoubleOr(ThermalJadeData.CYCLOTRON_CAP, 0.0D);
        double demand = data.getDoubleOr(ThermalJadeData.CYCLOTRON_DEMAND, 0.0D);
        int parallel = data.getIntOr(ThermalJadeData.CYCLOTRON_PARALLEL, 0);
        int ops = data.getIntOr(ThermalJadeData.CYCLOTRON_OPS, 0);
        int state = data.getIntOr(ThermalJadeData.CYCLOTRON_STATE, 0);

        tooltip.add(Component.translatable("stellar_evolution.jade.cyclotron.charge", num(charge), num(cap))
                .withStyle(ChatFormatting.AQUA));
        if (demand > 0.0D) {
            tooltip.add(Component.translatable("stellar_evolution.jade.cyclotron.recipe", num(demand), parallel)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (ops > 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.cyclotron.ops", ops)
                    .withStyle(ChatFormatting.GREEN));
        }
        String stateKey = switch (state) {
            case 1 -> "charging";
            case 2 -> "running";
            case 3 -> "no_power";
            case 4 -> "output_blocked";
            default -> "idle";
        };
        tooltip.add(Component.translatable("stellar_evolution.jade.cyclotron.state",
                        Component.translatable("stellar_evolution.jade.cyclotron.state." + stateKey))
                .withStyle(state == 2 ? ChatFormatting.GREEN
                        : state == 4 ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    /** 粒子源专属面板：DP 槽 → 热流率 → 周期条 → 要求 DP/HP → 粒子/输出/缓冲 → 状态 → 压力罐。 */
    private static void appendParticleSource(ITooltip tooltip, CompoundTag data) {
        // 1) DP 槽（与普通机器同款）
        double occ = data.getDoubleOr(ThermalJadeData.OCCUPANCY, 0.0D);
        double cap = data.getDoubleOr(ThermalJadeData.CAPACITY, 0.0D);
        tooltip.add(JadeUI.progress(new ProgressView(
                ProgressView.Part.of(ratio(cap, occ), BAR_FILL),
                Component.translatable("stellar_evolution.jade.thermal.bar", num(occ), num(cap)),
                JadeUI.progressStyle(),
                BoxStyle.nestedBox())));

        // 2) 当前热流率
        tooltip.add(Component.translatable("stellar_evolution.jade.thermal.flow.current",
                        num(data.getDoubleOr(ThermalJadeData.FLOW_RATE, 0.0D)))
                .withStyle(ChatFormatting.GOLD));

        // 3) 20t 周期进度条
        double cycle = data.getDoubleOr(ThermalJadeData.PARTICLE_CYCLE, 0.0D);
        tooltip.add(JadeUI.progress(new ProgressView(
                ProgressView.Part.of((float) Math.min(1.0D, Math.max(0.0D, cycle)), RECIPE_FILL),
                Component.translatable("stellar_evolution.jade.particle.cycle",
                        (int) Math.round(cycle * ParticleSourceBlockEntity.CYCLE_TICKS)),
                JadeUI.progressStyle(),
                BoxStyle.nestedBox())));

        String typeKey = data.getStringOr(ThermalJadeData.PARTICLE_TYPE, "");
        boolean hasTank = !typeKey.isEmpty();
        // 4) 要求 DP / HP
        if (hasTank) {
            tooltip.add(Component.translatable("stellar_evolution.jade.particle.require",
                            num(data.getDoubleOr(ThermalJadeData.PARTICLE_REQUIRED_DP, 0.0D)),
                            num(data.getDoubleOr(ThermalJadeData.PARTICLE_REQUIRED_HP, 0.0D)))
                    .withStyle(ChatFormatting.GOLD));
        }
        // 5) 粒子 / 输出 / 缓冲
        if (hasTank) {
            tooltip.add(Component.translatable("stellar_evolution.jade.particle.type",
                            Component.translatable(typeKey))
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("stellar_evolution.jade.particle.output",
                            num(data.getDoubleOr(ThermalJadeData.PARTICLE_OUTPUT, 0.0D)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            if (data.getBooleanOr(ThermalJadeData.PARTICLE_BOOST, false)) {
                tooltip.add(Component.translatable("stellar_evolution.jade.particle.boost")
                        .withStyle(ChatFormatting.YELLOW));
            }
        } else {
            tooltip.add(Component.translatable("stellar_evolution.jade.particle.no_tank")
                    .withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("stellar_evolution.jade.particle.buffer",
                        num(data.getDoubleOr(ThermalJadeData.PARTICLE_BUFFER, 0.0D)),
                        num(ParticleSourceBlockEntity.BUFFER_MAX))
                .withStyle(ChatFormatting.GRAY));

        // 6) 状态 / 压力罐
        int state = data.getIntOr(ThermalJadeData.PARTICLE_STATE, 0);
        String stateKey = switch (state) {
            case 1 -> "no_tank";
            case 2 -> "no_power";
            case 3 -> "buffer_full";
            case 4 -> "running";
            case 5 -> "output_blocked";
            case 6 -> "no_output";
            default -> "idle";
        };
        tooltip.add(Component.translatable("stellar_evolution.jade.particle.state",
                        Component.translatable("stellar_evolution.jade.particle.state." + stateKey))
                .withStyle(state == 4 ? ChatFormatting.GREEN
                        : state == 5 || state == 6 || state == 2 ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (hasTank) {
            int damage = data.getIntOr(ThermalJadeData.PARTICLE_TANK, 0);
            int max = data.getIntOr(ThermalJadeData.PARTICLE_TANK_MAX, 0);
            tooltip.add(Component.translatable("stellar_evolution.jade.particle.tank", max - damage, max)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 束流消费者公用：DP 槽 + 当前热流率（辐照仓 / 增殖包层）。 */
    private static void appendStreamConsumerPower(ITooltip tooltip, CompoundTag data) {
        double occ = data.getDoubleOr(ThermalJadeData.OCCUPANCY, 0.0D);
        double cap = data.getDoubleOr(ThermalJadeData.CAPACITY, 0.0D);
        tooltip.add(JadeUI.progress(new ProgressView(
                ProgressView.Part.of(ratio(cap, occ), BAR_FILL),
                Component.translatable("stellar_evolution.jade.thermal.bar", num(occ), num(cap)),
                JadeUI.progressStyle(),
                BoxStyle.nestedBox())));
        tooltip.add(Component.translatable("stellar_evolution.jade.thermal.flow.current",
                        num(data.getDoubleOr(ThermalJadeData.FLOW_RATE, 0.0D)))
                .withStyle(ChatFormatting.GOLD));
    }

    /** 束流处理机专属面板：已接入的粒子流 / 当前吃哪一路 / 进度 / 状态。 */
    private static void appendIrradiation(ITooltip tooltip, CompoundTag data) {
        double share = data.getDoubleOr(ThermalJadeData.IRRADIATION_SHARE, 0.0D);
        double progress = data.getDoubleOr(ThermalJadeData.IRRADIATION_PROGRESS, 0.0D);
        double required = data.getDoubleOr(ThermalJadeData.IRRADIATION_REQUIRED, 0.0D);
        int state = data.getIntOr(ThermalJadeData.IRRADIATION_STATE, 0);
        int mask = data.getIntOr(ThermalJadeData.IRRADIATION_STREAMS, 0);
        int active = data.getIntOr(ThermalJadeData.IRRADIATION_ACTIVE, -1);
        if (mask != 0) {
            MutableComponent wired = Component.empty();
            boolean first = true;
            for (StreamType type : StreamType.values()) {
                if ((mask & (1 << type.ordinal())) == 0) {
                    continue;
                }
                if (!first) {
                    wired.append(Component.literal(" / "));
                }
                wired.append(Component.translatable(type.nameKey()));
                first = false;
            }
            tooltip.add(Component.translatable("stellar_evolution.jade.irradiation.particles", wired)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (active >= 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.irradiation.active",
                            Component.translatable(StreamType.byOrdinal(active).nameKey()))
                    .withStyle(ChatFormatting.BLUE));
        }
        int output = data.getIntOr(ThermalJadeData.IRRADIATION_OUTPUT, -1);
        if (output >= 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.irradiation.output",
                            Component.translatable(StreamType.byOrdinal(output).nameKey()),
                            num(data.getDoubleOr(ThermalJadeData.IRRADIATION_OUTPUT_RATE, 0.0D)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("stellar_evolution.jade.irradiation.stream", num(share))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        // 逐路显示各自的进度：两路对撞时一眼能看出是哪一路没吃满
        String demands = data.getStringOr(ThermalJadeData.IRRADIATION_DEMANDS, "");
        if (!demands.isEmpty()) {
            for (String part : demands.split(";")) {
                int eq = part.indexOf('=');
                int slash = part.indexOf('/');
                if (eq <= 0 || slash <= eq) {
                    continue;
                }
                StreamType type = StreamType.byId(part.substring(0, eq));
                tooltip.add(Component.translatable(type.nameKey())
                        .append(Component.literal(" " + part.substring(eq + 1, slash)
                                + " / " + part.substring(slash + 1)))
                        .withStyle(ChatFormatting.AQUA));
            }
        } else {
            tooltip.add(Component.translatable("stellar_evolution.jade.irradiation.progress",
                            num(progress), num(required))
                    .withStyle(ChatFormatting.AQUA));
        }
        String stateKey = switch (state) {
            case 1 -> "no_stream";
            case 2 -> "running";
            case 3 -> "output_blocked";
            default -> "idle";
        };
        tooltip.add(Component.translatable("stellar_evolution.jade.irradiation.state",
                        Component.translatable("stellar_evolution.jade.irradiation.state." + stateKey))
                .withStyle(state == 2 ? ChatFormatting.GREEN : ChatFormatting.GRAY));

        // 加速环段：有环段或当前配方要求门槛时才显示（0 级普通配方不占行）
        int rings = data.getIntOr(ThermalJadeData.RING_LEVEL, 0);
        int ringRequired = data.getIntOr(ThermalJadeData.RING_REQUIRED, 0);
        if (rings > 0 || ringRequired > 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.beam.ring", rings, ringRequired)
                    .withStyle(rings >= ringRequired ? ChatFormatting.GOLD : ChatFormatting.RED));
        }

        // ADS：运行时蒸汽输出 + 压力管状态（蒸汽只在运行时产生）
        double steamAmount = data.getDoubleOr(ThermalJadeData.ADS_STEAM_AMOUNT, 0.0D);
        if (steamAmount > 0.0D) {
            tooltip.add(Component.translatable("stellar_evolution.jade.ads.steam",
                            num(steamAmount),
                            oneDecimal(data.getDoubleOr(ThermalJadeData.ADS_STEAM_TEMP, 20.0D)),
                            oneDecimal(data.getDoubleOr(ThermalJadeData.ADS_STEAM_PRESS, 0.0D)))
                    .withStyle(ChatFormatting.AQUA));
            int steamStatus = data.getIntOr(ThermalJadeData.ADS_STEAM_STATUS,
                    SteamEndpoint.SteamStatus.IDLE.ordinal());
            if (steamStatus == SteamEndpoint.SteamStatus.NO_PIPE.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.ads.no_pipe")
                        .withStyle(ChatFormatting.RED));
            } else if (steamStatus == SteamEndpoint.SteamStatus.VENTING.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.ads.venting")
                        .withStyle(ChatFormatting.GOLD));
            } else if (steamStatus == SteamEndpoint.SteamStatus.DELIVERING.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.ads.delivering")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** 缪子聚变仪专属面板：温度条 + 核心 / 稳定值 / 约束 + 点火需求 + 芯核耐久 + 蒸汽 + 状态。 */
    private static void appendMuon(ITooltip tooltip, CompoundTag data) {
        int state = data.getIntOr(ThermalJadeData.MUON_STATE, 0);
        String coreKey = data.getStringOr(ThermalJadeData.MUON_CORE, "");
        double temperature = data.getDoubleOr(ThermalJadeData.MUON_TEMP, MuonFusionCore.AMBIENT_C);
        double stability = data.getDoubleOr(ThermalJadeData.MUON_STABILITY, 0.0D);
        double charge = data.getDoubleOr(ThermalJadeData.MUON_CHARGE, 0.0D);
        StreamType type = StreamType.byOrdinal(data.getIntOr(ThermalJadeData.MUON_STREAM_TYPE, 0));
        double streamRate = data.getDoubleOr(ThermalJadeData.MUON_STREAM_RATE, 0.0D);
        double streamTotal = data.getDoubleOr(ThermalJadeData.MUON_STREAM_TOTAL, 0.0D);
        boolean muOk = data.getBooleanOr(ThermalJadeData.MUON_MU_OK, false);
        double neutronRate = data.getDoubleOr(ThermalJadeData.MUON_NEUTRON_RATE, 0.0D);
        double neutronTotal = data.getDoubleOr(ThermalJadeData.MUON_NEUTRON_TOTAL, 0.0D);
        double requiredFlow = data.getDoubleOr(ThermalJadeData.MUON_REQ_FLOW, 0.0D);
        double requiredResistance = data.getDoubleOr(ThermalJadeData.MUON_REQ_RES, 0.0D);
        int chambers = data.getIntOr(ThermalJadeData.MUON_CHAMBERS, 0);
        boolean overflow = data.getBooleanOr(ThermalJadeData.MUON_CHAMBER_OVERFLOW, false);
        int coreDamage = data.getIntOr(ThermalJadeData.MUON_CORE_DAMAGE, 0);
        int coreMax = data.getIntOr(ThermalJadeData.MUON_CORE_MAX, 0);
        double damage = data.getDoubleOr(ThermalJadeData.MUON_DAMAGE, 0.0D);
        double steamAmount = data.getDoubleOr(ThermalJadeData.MUON_STEAM_AMOUNT, 0.0D);
        double steamTemp = data.getDoubleOr(ThermalJadeData.MUON_STEAM_TEMP, 20.0D);
        double steamPress = data.getDoubleOr(ThermalJadeData.MUON_STEAM_PRESS, 0.0D);

        float ratio = (float) Math.min(1.0D, Math.max(0.0D,
                temperature / MuonFusionCore.SELF_HEAT_MAX_C));
        ProgressView bar = new ProgressView(
                ProgressView.Part.of(ratio, RECIPE_FILL),
                Component.translatable("stellar_evolution.jade.muon.temp",
                        oneDecimal(temperature), Math.round(MuonFusionCore.SELF_HEAT_MAX_C)),
                JadeUI.progressStyle(),
                BoxStyle.nestedBox());
        tooltip.add(JadeUI.progress(bar));

        tooltip.add(coreKey.isEmpty()
                ? Component.translatable("stellar_evolution.jade.muon.core.none").withStyle(ChatFormatting.GRAY)
                : Component.translatable("stellar_evolution.jade.muon.core",
                        Component.translatable(coreKey)).withStyle(ChatFormatting.GOLD));

        ChatFormatting stabilityColor = stability >= 60.0D ? ChatFormatting.GREEN
                : stability >= MuonFusionCore.STABILITY_MIN ? ChatFormatting.GOLD : ChatFormatting.RED;
        tooltip.add(Component.translatable("stellar_evolution.jade.muon.stability",
                        oneDecimal(stability), Math.round(MuonFusionCore.STABILITY_MAX),
                        Math.round(MuonFusionCore.STABILITY_MIN))
                .withStyle(stabilityColor));

        if (coreKey.isEmpty()) {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.constraint.none")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.constraint",
                            Component.translatable(type.nameKey()), num(streamTotal),
                            Component.translatable(type.unitKey()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable(muOk
                            ? "stellar_evolution.jade.muon.mu_ok"
                            : "stellar_evolution.jade.muon.mu_missing")
                    .withStyle(muOk ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (streamRate > 0.0D) {
                tooltip.add(Component.translatable("stellar_evolution.jade.muon.stream_rate",
                                num(streamRate), Component.translatable(type.unitKey()))
                        .withStyle(ChatFormatting.AQUA));
            }
            if (neutronRate > 0.0D || neutronTotal > 0.0D) {
                tooltip.add(Component.translatable("stellar_evolution.jade.muon.neutron",
                                num(neutronRate), num(neutronTotal))
                        .withStyle(ChatFormatting.AQUA));
            }
        }

        if (state == 1) {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.igniting",
                            Math.round(charge * 100.0D), num(requiredFlow), num(requiredResistance))
                    .withStyle(ChatFormatting.GOLD));
        } else if (state == 2) {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.maintain",
                            num(requiredFlow), num(requiredResistance))
                    .withStyle(ChatFormatting.AQUA));
        }

        if (coreMax > 0) {
            int percent = (int) Math.round(100.0D * (coreMax - coreDamage) / coreMax);
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.core_durability", percent)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (damage > 0.0D) {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.damage",
                            Math.round(damage * 100.0D))
                    .withStyle(ChatFormatting.RED));
        }

        if (steamAmount > 0.0D) {
            int grade = new SteamState(steamAmount, steamTemp, steamPress).grade().ordinal();
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.steam",
                            gradeName(grade), num(steamAmount), oneDecimal(steamTemp), oneDecimal(steamPress))
                    .withStyle(ChatFormatting.AQUA));
            int steamStatus = data.getIntOr(ThermalJadeData.STEAM_STATUS,
                    SteamEndpoint.SteamStatus.IDLE.ordinal());
            if (steamStatus == SteamEndpoint.SteamStatus.NO_PIPE.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.muon.steam.no_pipe")
                        .withStyle(ChatFormatting.RED));
            } else if (steamStatus == SteamEndpoint.SteamStatus.VENTING.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.muon.steam.venting")
                        .withStyle(ChatFormatting.GOLD));
            } else if (steamStatus == SteamEndpoint.SteamStatus.DELIVERING.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.muon.steam.delivering")
                        .withStyle(ChatFormatting.GREEN));
            }
        }

        // 冷却水：点火后按蒸汽量 1:1 抽水，抽不到稳定值会迅速下跌
        double waterDemand = data.getDoubleOr(ThermalJadeData.MUON_WATER_DEMAND, 0.0D);
        if (waterDemand > 0.0D) {
            boolean waterOk = data.getBooleanOr(ThermalJadeData.MUON_WATER_OK, false);
            tooltip.add(waterOk
                    ? Component.translatable("stellar_evolution.jade.muon.water",
                            oneDecimal(waterDemand / 50.0D)).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("stellar_evolution.jade.muon.water.dry",
                            oneDecimal(waterDemand / 50.0D)).withStyle(ChatFormatting.RED));
        }

        if (overflow) {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.chamber_too_many", chambers)
                    .withStyle(ChatFormatting.RED));
        } else if (state == 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.no_core")
                    .withStyle(ChatFormatting.GRAY));
        } else if (state == 1) {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.igniting_state")
                    .withStyle(ChatFormatting.GOLD));
        } else if (state == 2) {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.running")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("stellar_evolution.jade.muon.stopped")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** 裂变堆专属面板：壳温条 + 通量 / 效率 / 三层温度 / 棒数 / 损毁 + 状态。 */
    private static void appendReactor(ITooltip tooltip, CompoundTag data) {
        double temperature = data.getDoubleOr(ThermalJadeData.REACTOR_TEMP, 20.0D);
        double power = data.getDoubleOr(ThermalJadeData.REACTOR_POWER, 0.0D);
        double flux = data.getDoubleOr(ThermalJadeData.REACTOR_FLUX, 0.0D);
        double efficiency = data.getDoubleOr(ThermalJadeData.REACTOR_EFFICIENCY, 0.0D);
        double damage = data.getDoubleOr(ThermalJadeData.REACTOR_DAMAGE, 0.0D);
        double coreAvg = data.getDoubleOr(ThermalJadeData.REACTOR_CORE_AVG, 20.0D);
        double coreMax = data.getDoubleOr(ThermalJadeData.REACTOR_CORE_MAX, 20.0D);
        double cladAvg = data.getDoubleOr(ThermalJadeData.REACTOR_CLAD_AVG, 20.0D);
        double pressure = data.getDoubleOr(ThermalJadeData.REACTOR_PRESSURE, 0.0D);
        int rods = data.getIntOr(ThermalJadeData.REACTOR_RODS, 0);
        int graphite = data.getIntOr(ThermalJadeData.REACTOR_GRAPHITE, 0);
        int reflectors = data.getIntOr(ThermalJadeData.REACTOR_REFLECTORS, 0);
        int chambers = data.getIntOr(ThermalJadeData.REACTOR_CHAMBERS, 0);
        boolean chamberOverflow = data.getBooleanOr(ThermalJadeData.REACTOR_CHAMBER_OVERFLOW, false);
        boolean running = data.getBooleanOr(ThermalJadeData.REACTOR_RUNNING, false);
        boolean coolant = data.getBooleanOr(ThermalJadeData.REACTOR_COOLANT, true);

        float ratio = (float) Math.min(1.0D, Math.max(0.0D, temperature / 1800.0D));
        ProgressView bar = new ProgressView(
                ProgressView.Part.of(ratio, RECIPE_FILL),
                Component.translatable("stellar_evolution.jade.reactor.shell",
                        oneDecimal(temperature), 1800, 1500),
                JadeUI.progressStyle(),
                BoxStyle.nestedBox());
        tooltip.add(JadeUI.progress(bar));

        tooltip.add(Component.translatable("stellar_evolution.jade.reactor.core",
                Math.round(flux), Math.round(efficiency * 100.0D), oneDecimal(power))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("stellar_evolution.jade.reactor.rods",
                rods, graphite, reflectors).withStyle(ChatFormatting.GRAY));

        ChatFormatting coreColor = coreMax >= 2500.0D ? ChatFormatting.RED
                : coreMax >= 2000.0D ? ChatFormatting.GOLD : ChatFormatting.AQUA;
        tooltip.add(Component.translatable("stellar_evolution.jade.reactor.core_temp",
                oneDecimal(coreAvg), oneDecimal(coreMax), 2800).withStyle(coreColor));
        tooltip.add(Component.translatable("stellar_evolution.jade.reactor.clad_temp",
                oneDecimal(cladAvg), 2000).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("stellar_evolution.jade.reactor.pressure",
                oneDecimal(pressure)).withStyle(ChatFormatting.GOLD));

        if (damage > 0.0D) {
            tooltip.add(Component.translatable("stellar_evolution.jade.reactor.damage",
                            Math.round(damage * 100.0D))
                    .withStyle(damage >= 0.5D ? ChatFormatting.RED : ChatFormatting.GOLD));
        }

        if (chamberOverflow) {
            tooltip.add(Component.translatable("stellar_evolution.jade.reactor.chamber_too_many", chambers)
                    .withStyle(ChatFormatting.RED));
        } else if (chambers == 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.reactor.chamber_missing")
                    .withStyle(ChatFormatting.RED));
        } else if (rods == 0) {
            tooltip.add(Component.translatable("stellar_evolution.jade.reactor.no_fuel")
                    .withStyle(ChatFormatting.GRAY));
        } else if (!coolant) {
            tooltip.add(Component.translatable("stellar_evolution.jade.reactor.blocked_coolant")
                    .withStyle(ChatFormatting.RED));
        } else if (running) {
            tooltip.add(Component.translatable("stellar_evolution.jade.reactor.running")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("stellar_evolution.jade.reactor.waiting")
                    .withStyle(ChatFormatting.GRAY));
        }

        // 蒸汽网络：输出指数 + 无法输出 / 末端排气 / 正常送出
        if (data.getBooleanOr(ThermalJadeData.STEAM_RELATED, false)) {
            double amount = data.getDoubleOr(ThermalJadeData.REACTOR_STEAM_AMOUNT, 0.0D);
            int status = data.getIntOr(ThermalJadeData.STEAM_STATUS, SteamEndpoint.SteamStatus.IDLE.ordinal());
            if (amount > 0.0D) {
                int grade = data.getIntOr(ThermalJadeData.STEAM_GRADE, 0);
                tooltip.add(Component.translatable("stellar_evolution.jade.reactor.steam",
                                gradeName(grade), num(amount),
                                oneDecimal(data.getDoubleOr(ThermalJadeData.REACTOR_STEAM_TEMP, 20.0D)),
                                oneDecimal(data.getDoubleOr(ThermalJadeData.REACTOR_STEAM_PRESS, 0.0D)))
                        .withStyle(ChatFormatting.AQUA));
            }
            if (status == SteamEndpoint.SteamStatus.NO_PIPE.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.reactor.steam.no_pipe")
                        .withStyle(ChatFormatting.RED));
            } else if (status == SteamEndpoint.SteamStatus.VENTING.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.reactor.steam.venting")
                        .withStyle(ChatFormatting.GOLD));
            } else if (status == SteamEndpoint.SteamStatus.DELIVERING.ordinal()) {
                tooltip.add(Component.translatable("stellar_evolution.jade.reactor.steam.delivering")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private static String oneDecimal(double value) {
        return String.format("%.1f", value);
    }

    /**
     * 角色：优先用服务端写进 NBT 的 {@link ThermalJadeData#ROLE}（最准，覆盖大型机器等自定义方块）；
     * 老数据 / 缺字段时退回「按方块类型」猜（先看消耗侧，含铜块与机械土壤，打蜡铜块是纯 Block、认不出类，得靠标签）。
     */
    private static Role roleOf(BlockAccessor accessor, CompoundTag data) {
        int ordinal = data.getIntOr(ThermalJadeData.ROLE, -1);
        if (ordinal >= 0 && ordinal < Role.values().length) {
            return Role.values()[ordinal];
        }
        BlockState state = accessor.getBlockState();
        if (state.is(CopperHeat.COPPER_BLOCKS) || state.is(CopperHeat.COPPER_CHESTS) || state.is(Blocks.BARREL)
                || state.getBlock() instanceof MechanicalSoil) {
            return Role.CONSUMER;
        }
        return accessor.getBlock() instanceof CopperPipeBlock ? Role.PIPE : Role.GENERATOR;
    }

    /** 温度带状态键：序数与 {@code FermentationKinetics.Band} 一致（厌氧 / 中温 / 高温）。 */
    private static final String[] BAND_KEYS = {
            "stellar_evolution.jade.bio.band.anaerobic",
            "stellar_evolution.jade.bio.band.mesophilic",
            "stellar_evolution.jade.bio.band.thermophilic"
    };

    /** 燃烧引擎模式键：序数与 {@code BurningEngineBlockEntity.Mode} 一致（停机 / 固体 / 液体）。 */
    private static final String[] BURNING_MODE_KEYS = {
            "stellar_evolution.jade.thermal.mode.off",
            "stellar_evolution.jade.thermal.mode.solid",
            "stellar_evolution.jade.thermal.mode.liquid"
    };

    /** 状态行：超载（网络级）优先，其次大型机器的温度带模式 / 燃烧引擎的固体·液体模式，否则按角色给一句。 */
    private static Component statusLine(Role role, boolean overloaded, boolean working, double networkFlowRate,
                                        int band, int fuelMode) {
        if (overloaded) {
            return Component.translatable("stellar_evolution.jade.thermal.status.overloaded")
                    .withStyle(ChatFormatting.RED);
        }
        if (role == Role.CONSUMER) {
            // 大型机器（带温度带条件的，如发酵罐）：整行换成「厌氧 / 中温 / 高温 模式」
            if (band >= 0 && band < BAND_KEYS.length) {
                return Component.translatable(BAND_KEYS[band]).withStyle(ChatFormatting.GOLD);
            }
            boolean heating = networkFlowRate > 0.0D;
            return Component.translatable(heating
                            ? "stellar_evolution.jade.thermal.status.heat_pressing"
                            : "stellar_evolution.jade.thermal.status.idle")
                    .withStyle(heating ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        }
        if (role == Role.PIPE) {
            return Component.translatable("stellar_evolution.jade.thermal.status.ok").withStyle(ChatFormatting.GREEN);
        }
        // 生成端（引擎）：燃烧引擎报「固体 / 液体模式」，热力引擎报它自己的供电状态
        if (fuelMode >= 0 && fuelMode < BURNING_MODE_KEYS.length) {
            return Component.translatable(BURNING_MODE_KEYS[fuelMode])
                    .withStyle(fuelMode == 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD);
        }
        return Component.translatable(working
                        ? "stellar_evolution.jade.thermal.status.working"
                        : "stellar_evolution.jade.thermal.status.stopped")
                .withStyle(working ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    /** 刻 → {@code 分:秒}（燃料剩余时长）。 */
    private static String duration(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** 条的填充比例：占用 / 容量；容量为 0 时，有占用即为满条（配合红色状态）。 */
    private static float ratio(double capacity, double occupancy) {
        if (capacity <= 0.0D) {
            return occupancy > 0.0D ? 1.0F : 0.0F;
        }
        return (float) Math.min(1.0D, Math.max(0.0D, occupancy / capacity));
    }

    /**
     * 数值文案：小数值交给 Jade 的 {@link IDisplayHelper#humanReadableNumber}（k / M / G…），
     * <strong>≥ 10⁹ 改用科学计数法</strong>（如 {@code 2.75E14}）—— 铜格栅的容量 / 产水会到这个量级，
     * 用 SI 后缀会变成「275T」这种不好读的形式。
     */
    private static String num(double value) {
        if (value == 0.0D) {
            return "0";
        }
        if (Math.abs(value) >= 1.0e9D) {
            return String.format(java.util.Locale.ROOT, "%.2E", value).replace("E+", "E");
        }
        return IDisplayHelper.get().humanReadableNumber(value, "", false, null);
    }

    private ThermalJadeProvider() {}
}
