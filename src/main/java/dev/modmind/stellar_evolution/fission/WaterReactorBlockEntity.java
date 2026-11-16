package dev.modmind.stellar_evolution.fission;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.large.LargeMachine;
import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.large.LargeMachineIo;
import dev.modmind.stellar_evolution.large.LargeMachineItemHandler;
import dev.modmind.stellar_evolution.large.LargeMachines;
import dev.modmind.stellar_evolution.ore.OreFluids;
import dev.modmind.stellar_evolution.steam.SteamEndpoint;
import dev.modmind.stellar_evolution.steam.SteamNetwork;
import dev.modmind.stellar_evolution.steam.SteamState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * 裂变堆方块实体（原水冷反应堆）——<strong>燃料棒 / 石墨棒 / 反射棒</strong>三件套驱动。
 *
 * <p><strong>反应仓</strong>里：燃料棒 F、石墨棒 G、反射棒 R。<strong>装载比 r=F/(F+G)</strong> 决定目标通量；
 * 石墨降低通量与增速，反射棒提高通量、单棒功率、蒸汽压力与压力上限；效率 / 燃耗随通量走。
 * 铜箱子里的燃料棒不再参与反应堆逻辑；每台反应堆最多识别 1 个反应仓（&gt;1 会停机并提醒）。
 * <p><strong>三层温度</strong>：堆壳（蒸汽温度）、包壳（&gt;2000℃ 破壳报废该棒）、燃料芯（&gt;2800℃ 整堆熔毁）。
 * 堆壳 &gt;1500℃ 累积损毁，≥1800℃ 瞬间熔毁；损毁 100% 或任意芯超温 → 整堆熔毁。
 *
 * <p><strong>耗水</strong>：基础水 = 功率 × `102400/60` mB/t（与蒸汽 1:1）；温度越高额外冷却水越多
 * （附加水不转蒸汽）。没有压力管 → 无法输出蒸汽；网络无消耗端 → 末端排气。
 */
public class WaterReactorBlockEntity extends BlockEntity
        implements LargeMachine, FissionAnimated, FissionMachineHost, SteamEndpoint {
    /** 本机机种。 */
    public static final FissionMachineKind KIND = FissionMachineKind.FISSION_REACTOR;
    /** 800 MW 特殊成就（单台裂变堆输出 800 MW 热功率）。 */
    private static final Identifier HIGH_POWER_ADVANCEMENT = StellarEvolution.id("fission/800mw");

    private static final double AMBIENT = 20.0D;
    private static final double BOIL = 100.0D;
    /** 基础冷却水：与一根「额外 8 水源」格栅 / 60 MW 额定棒对齐。 */
    private static final double COOLANT_PER_MW = 102_400.0D / 60.0D;
    private static final double TEMP_LERP = 0.002D;
    /** 石墨棒按运行时间磨损：1 点 / 200t。 */
    private static final double GRAPHITE_WEAR_PER_TICK = 0.005D;
    /** 反射棒在损毁累积时每 tick 磨损。 */
    private static final double REFLECTOR_WEAR_PER_TICK = 1.0D;
    /** 破壳后的蒸汽污染时长（t）。 */
    private static final int CONTAMINATION_TICKS = 1_200;
    /** 破壳对蒸汽量的折扣。 */
    private static final double CONTAMINATION_FACTOR = 0.8D;

    private final LargeMachineItemHandler itemHandler = new LargeMachineItemHandler(this);
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);
    private final Map<BlockPos, LargeMachineIo> ioModes = new HashMap<>();
    /** 每件物品的磨损小数余量（燃料棒 / 石墨棒共用；同种棒保持同步）。 */
    private final Map<Item, Double> wearBudgets = new HashMap<>();

    /** 堆壳温度（℃）——蒸汽温度。 */
    private double temperature = AMBIENT;
    /** 中子通量（0–100%）。 */
    private double flux;
    /** 堆壳损毁（0–1）。 */
    private double damage;
    /** 破壳污染剩余时间。 */
    private int contaminationTicks;
    /** 冷却水取整余量。 */
    private double coolantBudget;
    /** 是否已熔毁（防止重复触发）。 */
    private boolean melted;
    /** 硼酸浓度（ppm）：≥ {@link ReactorCoreModel#BORON_SHUTDOWN_PPM} 时目标通量归零。 */
    private double boronPpm;
    /** 硼酸注入武装标志：同一超温事件只消耗一次，壳温回落到 {@link ReactorCoreModel#BORON_ACID_RESET_C} 以下再武装。 */
    private boolean boricAcidArmed = true;
    /** 氙毒：碘 / 氙两个标量（配置关闭时恒为 0）。 */
    private double iodine;
    private double xenon;

    private float age;
    private float lastAge;
    private float spin;
    private boolean working;
    private boolean structureChecked;
    private int jadeRecipeTicks = -1;
    private double jadeRecipeProgress;
    private int idleLogTicks;
    private boolean firstRunLogged;

    // ---- Jade 快照
    private double lastPower;
    private double lastFlux;
    private double lastEfficiency;
    private double lastAvgCore;
    private double lastMaxCore;
    private double lastAvgClad;
    private double lastPressure;
    private int lastFuelCount;
    private int lastSeedCount;
    private int lastBreederCount;
    private int lastGraphiteCount;
    private int lastReflectorCount;
    private int lastChamberCount;
    private boolean chamberOverflow;
    private boolean lastCoolantOk = true;
    private SteamState lastSteam = SteamState.NONE;
    private SteamStatus steamStatus = SteamStatus.IDLE;

    public WaterReactorBlockEntity(BlockPos pos, BlockState state) {
        super(FissionReactors.REACTOR_ENTITY.get(), pos, state);
    }

    public FissionMachineKind kind() {
        return KIND;
    }

    @Override
    public ResourceHandler<ItemResource> itemCapability() {
        return itemHandler;
    }

    @Override
    public ResourceHandler<FluidResource> fluidCapability() {
        return fluidHandler;
    }

    @Override
    public float age(float partialTick) {
        return Mth.lerp(partialTick, lastAge, age);
    }

    // ---- Jade / 命令读取

    public double temperature() {
        return temperature;
    }

    public boolean isRunning() {
        return working;
    }

    public double powerMw() {
        return lastPower;
    }

    public double totalFlux() {
        return lastFlux;
    }

    public double efficiency() {
        return lastEfficiency;
    }

    public double damage() {
        return damage;
    }

    /** 当前硼酸浓度（ppm）。 */
    public double boronPpm() {
        return boronPpm;
    }

    /** 氙毒标量（配置关闭时恒为 0）。 */
    public double xenon() {
        return xenon;
    }

    /** 注入硼酸：浓度封顶 6000 ppm；超温时由 {@link #injectBoricAcidIfOverheated} 调用。 */
    private void addBoron(int ppm) {
        boronPpm = Math.min(6_000.0D, Math.max(0.0D, boronPpm + Math.max(0, ppm)));
        setChanged();
    }

    /**
     * 壳温超过 1500℃ 时从机械网络（输入储罐）抽取 1000 mB 硼酸 → 直接到停堆线。
     *
     * <p>同一超温事件只消耗一次：注入后解除武装，壳温回落到 1400℃ 以下才重新武装。
     * 因此玩家重启后不会立刻再消耗，必须等壳温<strong>再次</strong>越过 1500℃。
     */
    private void injectBoricAcidIfOverheated() {
        if (temperature < ReactorCoreModel.BORON_ACID_RESET_C) {
            boricAcidArmed = true;
            return;
        }
        if (!boricAcidArmed || temperature < ReactorCoreModel.BORON_ACID_TRIGGER_C) {
            return;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            FluidResource acid = FluidResource.of(
                    new FluidStack(OreFluids.BORIC_ACID.source().get(), ReactorCoreModel.BORON_ACID_PER_INJECTION_MB));
            if (fluidHandler.extractFromInputs(acid, ReactorCoreModel.BORON_ACID_PER_INJECTION_MB, transaction)
                    < ReactorCoreModel.BORON_ACID_PER_INJECTION_MB) {
                return;
            }
            transaction.commit();
        }
        addBoron(ReactorCoreModel.BORON_PER_BUCKET);
        boricAcidArmed = false;
        StellarEvolution.LOGGER.info("[裂变堆] {} 壳温 {}℃ 超 1500℃：抽取硼酸 {} mB 注入，化学停堆",
                worldPosition, String.format("%.1f", temperature), ReactorCoreModel.BORON_ACID_PER_INJECTION_MB);
    }

    public double avgCoreTemp() {
        return lastAvgCore;
    }

    public double maxCoreTemp() {
        return lastMaxCore;
    }

    public double avgCladdingTemp() {
        return lastAvgClad;
    }

    public double pressureBar() {
        return lastPressure;
    }

    public int rodCount() {
        return lastFuelCount;
    }

    public int seedCount() {
        return lastSeedCount;
    }

    public int breederCount() {
        return lastBreederCount;
    }

    public int graphiteCount() {
        return lastGraphiteCount;
    }

    public int reflectorCount() {
        return lastReflectorCount;
    }

    /** 当前连接的反应仓数量（每台反应堆最多 1 个）。 */
    public int chamberCount() {
        return lastChamberCount;
    }

    /** 反应仓 >1：反应堆被强制停机。 */
    public boolean chamberOverflow() {
        return chamberOverflow;
    }

    public boolean coolantOk() {
        return lastCoolantOk;
    }

    public SteamState steamOutput() {
        return lastSteam;
    }

    @Override
    public SteamStatus steamStatus() {
        return steamStatus;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WaterReactorBlockEntity reactor) {
        if (level.isClientSide()) {
            reactor.lastAge = reactor.age;
            reactor.spin = Mth.clamp(reactor.spin + (reactor.working ? 0.06F : -0.12F), 0.0F, 1.0F);
            reactor.age += reactor.spin;
            return;
        }
        if (!reactor.structureChecked) {
            reactor.structureChecked = true;
            FissionMachineStructure.refreshLight(level, pos);
            FissionMachineStructure.fillShells(level, pos);
            FissionMachineStructure.refreshLight(level, pos);
        }
        FissionMachineStructure.refreshPorts(level, pos);
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 40L) == 0L) {
            int filled = FissionMachineStructure.fillShells(level, pos);
            if (filled > 0) {
                FissionMachineStructure.refreshLight(level, pos);
            }
        }
        reactor.serverTick(level, state);
    }

    private void serverTick(Level level, BlockState state) {
        boolean running = reactorTick(level);
        if (melted) {
            return;   // 方块已被拆除，不再同步
        }
        jadeRecipeProgress = Mth.clamp(temperature / ReactorCoreModel.SHELL_MAX_C, 0.0D, 1.0D);
        jadeRecipeTicks = running ? 0 : -1;
        if (level.getGameTime() % 20L == 0L) {
            setChanged();   // 温度 / 通量 / 损毁写入存档
        }
        if (running != working) {
            working = running;
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    /** 单台反应堆输出达到 800 MW 时，给附近玩家授予特殊成就（每 tick 判定，已获得则跳过）。 */
    private void awardHighPower(Level level, double totalPower) {
        if (totalPower < 800.0D || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var holder = serverLevel.getServer().getAdvancements().get(HIGH_POWER_ADVANCEMENT);
        if (holder == null) {
            return;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.blockPosition().closerThan(this.worldPosition, 64.0D)
                    && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
                player.getAdvancements().award(holder, "impossible");
            }
        }
    }

    private boolean reactorTick(Level level) {
        if (melted) {
            return false;
        }
        // 硼酸自然稀释（换水 / 冲洗的简化实现）：停堆后等浓度降下来就能重启
        if (boronPpm > 0.0D) {
            boronPpm = Math.max(0.0D, boronPpm - ReactorCoreModel.BORON_DECAY_PER_TICK);
        }
        List<ReactionChamberBlockEntity> chambers = findChambers(level);
        lastChamberCount = chambers.size();
        chamberOverflow = chambers.size() > 1;
        if (chamberOverflow) {
            // 每台反应堆最多 1 个反应仓：多了直接停机并提醒
            lastFuelCount = 0;
            lastSeedCount = 0;
            lastBreederCount = 0;
            lastGraphiteCount = 0;
            lastReflectorCount = 0;
            lastPower = 0.0D;
            lastEfficiency = 0.0D;
            lastAvgCore = temperature;
            lastMaxCore = temperature;
            lastAvgClad = temperature;
            lastPressure = 0.0D;
            flux = Math.max(0.0D, flux - 0.02D);
            lastFlux = flux;
            advanceXenon(List.of(), flux);
            lastSteam = SteamState.NONE;
            steamStatus = SteamStatus.IDLE;
            warnChamberOverflow(level, chambers.size());
            return false;
        }

        ReactionChamberBlockEntity chamber = chambers.isEmpty() ? null : chambers.get(0);
        List<Integer> seedSlots = new ArrayList<>();
        List<Integer> breederSlots = new ArrayList<>();
        List<Integer> fuelSlots = new ArrayList<>();
        List<Integer> graphiteSlots = new ArrayList<>();
        List<Integer> reflectorSlots = new ArrayList<>();
        List<FuelSpec> loadedSpecs = new ArrayList<>();
        if (chamber != null) {
            for (int slot = 0; slot < chamber.getContainerSize(); slot++) {
                ItemStack stack = chamber.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                Item item = stack.getItem();
                if (item instanceof FuelRodItem rod) {
                    fuelSlots.add(slot);
                    loadedSpecs.add(rod.spec());
                    if (rod.spec().isBreeder()) {
                        breederSlots.add(slot);
                    } else {
                        seedSlots.add(slot);
                    }
                } else if (FissionMaterials.isGraphiteRod(item)) {
                    graphiteSlots.add(slot);
                } else if (FissionMaterials.isReflectorRod(item) && ReflectorRodItem.isActive(stack)) {
                    reflectorSlots.add(slot);
                }
            }
        }
        int graphite = graphiteSlots.size();
        int reflectors = 0;
        for (int slot : reflectorSlots) {
            // 超重反射棒每根按 2 根计（通量 / 功率 / 压力一并受益）
            reflectors += chamber == null ? 1 : FissionMaterials.reflectorWeight(chamber.getItem(slot).getItem());
        }
        double breederSink = 0.0D;
        for (int slot : breederSlots) {
            if (chamber != null && chamber.getItem(slot).getItem() instanceof FuelRodItem rod) {
                breederSink += rod.spec().breederSink();
            }
        }
        lastFuelCount = fuelSlots.size();
        lastSeedCount = seedSlots.size();
        lastBreederCount = breederSlots.size();
        lastGraphiteCount = graphite;
        lastReflectorCount = reflectorSlots.size();

        // ---- 通量：基础目标 × 混装惩罚 × 硼浓度 × 氙毒；到 100% 整堆熔毁
        ReactorCoreModel.MixingPenalty penalty = ReactorCoreModel.mixingPenalty(loadedSpecs);
        if (penalty.autoShutdown() && level.getGameTime() % 200L == 0L) {
            StellarEvolution.LOGGER.info("[裂变堆] {} 混装点数 P={}：自动停堆", worldPosition, penalty.points());
        }
        double baseTarget = ReactorCoreModel.targetFlux(seedSlots.size(), graphite, reflectors, breederSink);
        double target = ReactorCoreModel.effectiveTargetFlux(baseTarget, boronPpm, xenon, penalty);
        double ramp = ReactorCoreModel.fluxRampPerTick(seedSlots.size(), breederSink, graphite, reflectors);
        // 离火棒：新棒起步猛冲（R0 × 剩余耐久），随燃烧回落
        double emberRamp = 1.0D;
        for (int slot : seedSlots) {
            ItemStack stack = chamber.getItem(slot);
            if (stack.getItem() instanceof FuelRodItem rod && rod.spec().rampR0() > 1.0D) {
                double durability = 1.0D - (double) stack.getDamageValue() / Math.max(1, stack.getMaxDamage());
                emberRamp = Math.max(emberRamp, ReactorCoreModel.emberRampFactor(rod.spec(), durability));
            }
        }
        ramp *= emberRamp;
        flux = flux < target ? Math.min(target, flux + ramp) : Math.max(target, flux - ramp);
        flux = Mth.clamp(flux, 0.0D, 100.0D);
        lastFlux = flux;
        lastEfficiency = 0.0D;
        advanceXenon(loadedSpecs, flux);
        if (flux >= 100.0D - 1.0E-6D) {
            meltdown(level, "通量达到 100%");
            return false;
        }

        // ---- 无点火棒 / 无反应仓：降温 + 损毁回退
        if (seedSlots.isEmpty()) {
            temperature += (AMBIENT - temperature) * TEMP_LERP;
            coolantBudget = 0.0D;
            lastPower = 0.0D;
            lastEfficiency = 0.0D;
            lastAvgCore = temperature;
            lastMaxCore = temperature;
            lastAvgClad = temperature;
            lastPressure = 0.0D;
            lastSteam = SteamState.NONE;
            steamStatus = SteamStatus.IDLE;
            damage = Math.max(0.0D, damage - ReactorCoreModel.DAMAGE_RECOVERY_PER_SECOND / 20.0D);
            if (contaminationTicks > 0) {
                contaminationTicks--;
            }
            idleLogTicks = 0;
            return false;
        }

        // ---- 功率：每根点火棒独立；钍棒只增殖不发热
        double totalPower = 0.0D;
        double pressureCap = Double.MAX_VALUE;
        for (int slot : seedSlots) {
            ItemStack stack = chamber.getItem(slot);
            if (stack.getItem() instanceof FuelRodItem rod) {
                FuelSpec spec = rod.spec();
                totalPower += ReactorCoreModel.rodPower(spec, flux, reflectors) * penalty.efficiencyFactor();
                lastEfficiency = Math.max(lastEfficiency,
                        ReactorCoreModel.efficiency(spec, flux) * penalty.efficiencyFactor());
                pressureCap = Math.min(pressureCap, spec.pressureCapBar());
            }
        }
        if (pressureCap == Double.MAX_VALUE) {
            pressureCap = 0.0D;
        }
        lastPower = totalPower;
        awardHighPower(level, totalPower);

        // ---- 冷却水：基础 + 温度附加；附加只冷却，不转蒸汽
        double baseWater = totalPower * COOLANT_PER_MW;
        double extraFactor = ReactorCoreModel.extraWaterFactor(temperature);
        double demand = baseWater * (1.0D + extraFactor);
        coolantBudget += demand;
        int request = (int) Math.floor(coolantBudget);
        coolantBudget -= request;
        boolean hadCoolant = true;
        if (request > 0) {
            hadCoolant = transferCoolant(request);
        }
        lastCoolantOk = hadCoolant;

        // ---- 堆壳温度（混装惩罚额外升温）
        double targetTemp = ReactorCoreModel.shellTarget(totalPower) + penalty.shellTempBonusC();
        if (hadCoolant) {
            temperature += (targetTemp - temperature) * TEMP_LERP;
        } else {
            temperature += (ReactorCoreModel.SHELL_MAX_C - temperature) * 0.02D;
        }
        temperature = Mth.clamp(temperature, AMBIENT, ReactorCoreModel.SHELL_MAX_C);
        injectBoricAcidIfOverheated();

        // ---- 损毁
        if (ReactorCoreModel.instantMeltdown(temperature)) {
            meltdown(level, "堆壳达到 1800℃");
            return false;
        }
        double damageBefore = damage;
        double damagePerTick = ReactorCoreModel.damagePerTick(temperature) + penalty.damagePerTickBonus();
        if (damagePerTick > 0.0D) {
            damage = Math.min(1.0D, damage + damagePerTick);
        } else if (temperature < ReactorCoreModel.DAMAGE_RECOVERY_BELOW_C) {
            damage = Math.max(0.0D, damage - ReactorCoreModel.DAMAGE_RECOVERY_PER_SECOND / 20.0D);
        }
        if (damage >= 1.0D) {
            meltdown(level, "堆壳损毁 100%");
            return false;
        }
        boolean damagedThisTick = damage > damageBefore;

        // ---- 核心 / 包壳温度；芯超温整堆熔毁，包壳超温破壳报废
        double coolingMargin = hadCoolant ? 2.0D : 0.5D;
        double sumCore = 0.0D;
        double maxCore = 0.0D;
        double sumClad = 0.0D;
        int coreSamples = 0;
        List<Integer> breachedSlots = new ArrayList<>();
        for (int slot : fuelSlots) {
            ItemStack stack = chamber.getItem(slot);
            if (!(stack.getItem() instanceof FuelRodItem rod)) {
                continue;
            }
            FuelSpec spec = rod.spec();
            double rated = spec.thermalPowerMw();
            double core = ReactorCoreModel.coreTemp(temperature, rated, flux, reflectors, coolingMargin);
            double clad = ReactorCoreModel.claddingTemp(temperature, core);
            sumCore += core;
            maxCore = Math.max(maxCore, core);
            sumClad += clad;
            coreSamples++;
            if (core > spec.coreLimitC()) {
                meltdown(level, "燃料芯超温");
                return false;
            }
            if (clad > ReactorCoreModel.CLADDING_MAX_C) {
                breachedSlots.add(slot);
            }
        }
        lastAvgCore = coreSamples == 0 ? temperature : sumCore / coreSamples;
        lastMaxCore = maxCore;
        lastAvgClad = coreSamples == 0 ? temperature : sumClad / coreSamples;
        // 先结算燃料磨损，再处理破壳，避免刚换掉的棒又被写回
        updateFuelRods(chamber, fuelSlots);
        for (int slot : breachedSlots) {
            breachRod(chamber, slot);
        }
        if (damage >= 1.0D) {
            meltdown(level, "破壳损毁叠加至 100%");
            return false;
        }

        // ---- 蒸汽数据（对外温度钳到 600℃，堆壳温度本身仍按原值参与损毁 / 耗水）
        lastPressure = ReactorCoreModel.pressureBar(totalPower, reflectors, pressureCap);
        if (hadCoolant && temperature >= BOIL && totalPower > 0.0D) {
            double steamAmount = baseWater * (contaminationTicks > 0 ? CONTAMINATION_FACTOR : 1.0D);
            lastSteam = new SteamState(steamAmount, ReactorCoreModel.steamTemperature(temperature), lastPressure);
            refreshSteamStatus(level);
        } else {
            lastSteam = SteamState.NONE;
            steamStatus = SteamStatus.IDLE;
        }

        // ---- 石墨磨损 / 反射棒磨损
        updateGraphite(chamber, graphiteSlots);
        if (damagedThisTick) {
            updateReflectors(chamber, reflectorSlots);
        }
        if (contaminationTicks > 0) {
            contaminationTicks--;
        }

        // ---- 诊断
        if (hadCoolant) {
            idleLogTicks = 0;
            if (!firstRunLogged) {
                firstRunLogged = true;
                StellarEvolution.LOGGER.info("[裂变堆] {} 启动：燃料 {} / 石墨 {} / 反射 {}，通量 {}%，功率 {} MW",
                        worldPosition, lastFuelCount, lastGraphiteCount, lastReflectorCount,
                        Math.round(flux), String.format("%.1f", totalPower));
            }
        } else {
            logIdle(level, fuelSlots.size(), "冷却水不足");
        }
        return true;
    }

    /** 推进碘 / 氙；配置关闭时清零。产额取装载棒里最高的谱产额。 */
    private void advanceXenon(List<FuelSpec> specs, double flux) {
        if (!FissionConfig.XENON_POISONING.get()) {
            iodine = 0.0D;
            xenon = 0.0D;
            return;
        }
        double yield = 0.0D;
        for (FuelSpec spec : specs) {
            yield = Math.max(yield, ReactorCoreModel.iodineYield(spec.spectrum()));
        }
        ReactorCoreModel.XenonState state = ReactorCoreModel.xenonStep(
                new ReactorCoreModel.XenonState(iodine, xenon), flux, yield);
        iodine = state.iodine();
        xenon = state.xenon();
    }

    private boolean transferCoolant(int request) {
        try (Transaction transaction = Transaction.openRoot()) {
            if (fluidHandler.extractFromInputs(water(request), request, transaction) < request) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /** 反应堆网络里的反应仓（每台最多识别 1 个）。 */
    private List<ReactionChamberBlockEntity> findChambers(Level level) {
        List<ReactionChamberBlockEntity> chambers = new ArrayList<>();
        for (LargeMachines.Connected connected : LargeMachines.connectedContainers(level, this)) {
            if (level.getBlockEntity(connected.pos()) instanceof ReactionChamberBlockEntity chamber) {
                chambers.add(chamber);
            }
        }
        return chambers;
    }

    /** 反应仓过多：停机 + 行为栏 / 日志提醒（每 5 秒一次，不刷屏）。 */
    private void warnChamberOverflow(Level level, int count) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 100L != 0L) {
            return;
        }
        Component message = Component.translatable("message.stellar_evolution.reaction_chamber.too_many", count);
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D) <= 256.0D) {
                player.sendSystemMessage(message, true);
            }
        }
        StellarEvolution.LOGGER.warn("[裂变堆] {} 反应仓过多（{} 个，每台最多 1 个），已停机",
                worldPosition, count);
    }

    /** 生成一根带初始余热的乏燃料；燃尽与破壳共用。 */
    private ItemStack makeSpent(FuelSpec spec) {
        ItemStack stack = new ItemStack(FissionMaterials.spentItem(spec));
        SpentFuelItem.setHeat(stack, SpentFuelHeat.initialWithFlux(spec, flux));
        return stack;
    }

    /** 燃料棒磨损 + 通量写回；耐久归零变乏燃料。 */
    private void updateFuelRods(ReactionChamberBlockEntity chamber, List<Integer> fuelSlots) {
        if (chamber == null || fuelSlots.isEmpty()) {
            return;
        }
        double fluxFactor = flux / 100.0D;
        Map<Item, Integer> wearByItem = new HashMap<>();
        for (int slot : fuelSlots) {
            ItemStack stack = chamber.getItem(slot);
            if (!(stack.getItem() instanceof FuelRodItem rod)) {
                continue;
            }
            Item item = stack.getItem();
            if (wearByItem.containsKey(item)) {
                continue;
            }
            double burn = ReactorCoreModel.burnFactor(rod.spec(), flux)
                    * ReactorCoreModel.emberBurnFactor(rod.spec(), flux);
            double budget = wearBudgets.getOrDefault(item, 0.0D)
                    + rod.spec().wearPerCycle() / 600.0D * fluxFactor * burn;
            int wear = (int) Math.floor(budget);
            wearBudgets.put(item, budget - wear);
            wearByItem.put(item, wear);
        }
        float fluxInt = (float) Math.round(flux);
        for (int slot : fuelSlots) {
            ItemStack stack = chamber.getItem(slot);
            if (!(stack.getItem() instanceof FuelRodItem item)) {
                continue;
            }
            int wear = wearByItem.getOrDefault(item, 0);
            int newDamage = Math.min(stack.getMaxDamage(), stack.getDamageValue() + wear);
            boolean depleted = newDamage >= stack.getMaxDamage();
            boolean fluxChanged = FuelRodItem.flux(stack) != fluxInt;
            if (depleted) {
                chamber.setItem(slot, makeSpent(item.spec()));
            } else if (wear > 0 || fluxChanged) {
                FuelRodItem.setFlux(stack, fluxInt);
                stack.setDamageValue(newDamage);
                chamber.setItem(slot, stack);
            }
        }
        chamber.setChanged();
    }

    /** 石墨棒按运行时间磨损；耗尽变废石墨棒。 */
    private void updateGraphite(ReactionChamberBlockEntity chamber, List<Integer> graphiteSlots) {
        if (chamber == null || graphiteSlots.isEmpty() || flux <= 0.0D) {
            return;
        }
        Item item = FissionMaterials.GRAPHITE_ROD.get();
        double budget = wearBudgets.getOrDefault(item, 0.0D) + GRAPHITE_WEAR_PER_TICK;
        int wear = (int) Math.floor(budget);
        wearBudgets.put(item, budget - wear);
        if (wear <= 0) {
            return;
        }
        for (int slot : graphiteSlots) {
            ItemStack stack = chamber.getItem(slot);
            if (!FissionMaterials.isGraphiteRod(stack.getItem())) {
                continue;
            }
            int newDamage = Math.min(stack.getMaxDamage(), stack.getDamageValue() + wear);
            if (newDamage >= stack.getMaxDamage()) {
                chamber.setItem(slot, new ItemStack(FissionMaterials.SPENT_GRAPHITE_ROD.get()));
            } else {
                stack.setDamageValue(newDamage);
                chamber.setItem(slot, stack);
            }
        }
        chamber.setChanged();
    }

    /** 损毁累积时反射棒掉耐久；耐久耗尽不消失，只是失去增益。 */
    private void updateReflectors(ReactionChamberBlockEntity chamber, List<Integer> reflectorSlots) {
        if (chamber == null) {
            return;
        }
        for (int slot : reflectorSlots) {
            ItemStack stack = chamber.getItem(slot);
            if (!FissionMaterials.isReflectorRod(stack.getItem()) || !ReflectorRodItem.isActive(stack)) {
                continue;
            }
            int newDamage = Math.min(stack.getMaxDamage(),
                    stack.getDamageValue() + (int) REFLECTOR_WEAR_PER_TICK);
            stack.setDamageValue(newDamage);
            chamber.setItem(slot, stack);
        }
        chamber.setChanged();
    }

    /** 包壳破裂：该棒报废为乏燃料，蒸汽污染一段时间，损毁 +10%。 */
    private void breachRod(ReactionChamberBlockEntity chamber, int slot) {
        if (chamber == null || !(chamber.getItem(slot).getItem() instanceof FuelRodItem rod)) {
            return;
        }
        chamber.setItem(slot, makeSpent(rod.spec()));
        chamber.setChanged();
        damage = Math.min(1.0D, damage + 0.10D);
        contaminationTicks = CONTAMINATION_TICKS;
        StellarEvolution.LOGGER.info("[裂变堆] {} 包壳破裂：一根燃料棒报废，蒸汽污染 {}t",
                worldPosition, CONTAMINATION_TICKS);
    }

    /** 整堆熔毁：每根装载燃料棒掉 1 个放射性熔渣，拆掉反应堆，按配置决定是否爆炸。 */
    private void meltdown(Level level, String reason) {
        if (melted) {
            return;
        }
        melted = true;
        lastSteam = SteamState.NONE;
        steamStatus = SteamStatus.IDLE;
        damage = 1.0D;
        int slag = 0;
        for (ReactionChamberBlockEntity chamber : findChambers(level)) {
            for (int slot = 0; slot < chamber.getContainerSize(); slot++) {
                if (chamber.getItem(slot).getItem() instanceof FuelRodItem) {
                    chamber.setItem(slot, ItemStack.EMPTY);
                    slag++;
                }
            }
            chamber.setChanged();
        }
        for (int i = 0; i < slag; i++) {
            Block.popResource(level, worldPosition, new ItemStack(FissionMaterials.RADIOACTIVE_SLAG.get()));
        }
        StellarEvolution.LOGGER.info("[裂变堆] {} 熔毁：{}（燃料 {} / 石墨 {} / 反射 {}，通量 {}%，功率 {} MW，"
                        + "壳温 {}℃，芯 {}℃，冷却水 {}，损毁 {}%，掉落放射性熔渣 {} 个）",
                worldPosition, reason, lastFuelCount, lastGraphiteCount, lastReflectorCount,
                Math.round(flux), String.format("%.1f", lastPower), String.format("%.1f", temperature),
                String.format("%.1f", lastMaxCore), lastCoolantOk ? "正常" : "不足",
                Math.round(damage * 100.0D), slag);
        if (level instanceof ServerLevel serverLevel && FissionConfig.MELTDOWN_EXPLOSION.get()) {
            serverLevel.explode(null, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D, FissionConfig.MELTDOWN_EXPLOSION_RADIUS.get(),
                    Level.ExplosionInteraction.BLOCK);
        }
        level.destroyBlock(worldPosition, false);
    }

    private void refreshSteamStatus(Level level) {
        if (!SteamNetwork.hasAdjacentPipe(level, this)) {
            steamStatus = SteamStatus.NO_PIPE;
            return;
        }
        SteamNetwork.State state = SteamNetwork.stateAt(level, worldPosition);
        if (state.consumers().isEmpty()) {
            steamStatus = SteamStatus.VENTING;
            if (level instanceof ServerLevel serverLevel && Math.floorMod(level.getGameTime(), 4L) == 0L) {
                SteamNetwork.spawnVentParticles(serverLevel, state);
            }
        } else {
            steamStatus = SteamStatus.DELIVERING;
        }
    }

    private void logIdle(Level level, int rods, String reason) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (idleLogTicks++ % 200 != 0) {
            return;
        }
        StellarEvolution.LOGGER.info("[裂变堆] {} 未运行：{}（燃料 {}，壳温 {}℃，通量 {}%，损毁 {}%）",
                worldPosition, reason, rods, String.format("%.1f", temperature),
                Math.round(flux), Math.round(damage * 100.0D));
    }

    private static FluidResource water(int amount) {
        return FluidResource.of(new FluidStack(Fluids.WATER, amount));
    }

    // ------------------------------------------------------------------ SteamEndpoint

    @Override
    public boolean steamGenerator() {
        return true;
    }

    @Override
    public List<BlockPos> steamCells() {
        return occupiedCells();
    }

    @Override
    public BlockPos steamOrigin() {
        return getBlockPos();
    }

    // ------------------------------------------------------------------ LargeMachine

    @Override
    public boolean isLargeMachine() {
        return true;
    }

    @Override
    public Level level() {
        return getLevel();
    }

    @Override
    public BlockPos origin() {
        return getBlockPos();
    }

    @Override
    public List<BlockPos> occupiedCells() {
        BlockPos origin = getBlockPos();
        List<BlockPos> cells = new ArrayList<>(FissionMachineStructure.CELL_OFFSETS.length);
        for (int[] offset : FissionMachineStructure.CELL_OFFSETS) {
            cells.add(origin.offset(offset[0], offset[1], offset[2]));
        }
        return cells;
    }

    @Override
    public LargeMachineIo ioMode(BlockPos container) {
        return ioModes.getOrDefault(container, LargeMachineIo.INPUT);
    }

    @Override
    public void setIoMode(BlockPos container, LargeMachineIo mode) {
        ioModes.put(container.immutable(), mode);
        setChanged();
    }

    @Override
    public int remainingRecipeTicks() {
        return working ? jadeRecipeTicks : -1;
    }

    @Override
    public double recipeProgress() {
        return jadeRecipeProgress;
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        int count = 0;
        for (LargeMachineIo mode : ioModes.values()) {
            if (mode == LargeMachineIo.OUTPUT) {
                count++;
            }
        }
        int[] outs = new int[count * 3];
        int index = 0;
        for (Map.Entry<BlockPos, LargeMachineIo> entry : ioModes.entrySet()) {
            if (entry.getValue() != LargeMachineIo.OUTPUT) {
                continue;
            }
            BlockPos pos = entry.getKey();
            outs[index++] = pos.getX();
            outs[index++] = pos.getY();
            outs[index++] = pos.getZ();
        }
        output.putIntArray("large_io_output", outs);
        output.putBoolean("working", working);
        output.putDouble("temperature", temperature);
        output.putDouble("flux", flux);
        output.putDouble("damage", damage);
        output.putInt("contamination", contaminationTicks);
        output.putDouble("coolant_budget", coolantBudget);
        output.putBoolean("melted", melted);
        output.putDouble("boron_ppm", boronPpm);
        output.putBoolean("boric_acid_armed", boricAcidArmed);
        output.putDouble("iodine", iodine);
        output.putDouble("xenon", xenon);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ioModes.clear();
        int[] outs = input.getIntArray("large_io_output").orElse(new int[0]);
        for (int i = 0; i + 2 < outs.length; i += 3) {
            ioModes.put(new BlockPos(outs[i], outs[i + 1], outs[i + 2]), LargeMachineIo.OUTPUT);
        }
        working = input.getBooleanOr("working", false);
        temperature = input.getDoubleOr("temperature", AMBIENT);
        flux = input.getDoubleOr("flux", 0.0D);
        damage = input.getDoubleOr("damage", 0.0D);
        contaminationTicks = input.getIntOr("contamination", 0);
        coolantBudget = input.getDoubleOr("coolant_budget", 0.0D);
        melted = input.getBooleanOr("melted", false);
        boronPpm = input.getDoubleOr("boron_ppm", 0.0D);
        boricAcidArmed = input.getBooleanOr("boric_acid_armed", true);
        iodine = input.getDoubleOr("iodine", 0.0D);
        xenon = input.getDoubleOr("xenon", 0.0D);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
