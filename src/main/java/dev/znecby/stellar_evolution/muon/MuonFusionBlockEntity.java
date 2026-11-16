package dev.znecby.stellar_evolution.muon;

import dev.znecby.stellar_evolution.fission.FissionMachineBlockEntity;
import dev.znecby.stellar_evolution.fission.FissionMachineKind;
import dev.znecby.stellar_evolution.fission.FissionMachines;
import dev.znecby.stellar_evolution.fission.ReactionChamberBlockEntity;
import dev.znecby.stellar_evolution.large.LargeMachines;
import dev.znecby.stellar_evolution.pipe.BeamPipeBlock;
import dev.znecby.stellar_evolution.steam.SteamEndpoint;
import dev.znecby.stellar_evolution.steam.SteamNetwork;
import dev.znecby.stellar_evolution.steam.SteamState;
import dev.znecby.stellar_evolution.stream.StreamConsumer;
import dev.znecby.stellar_evolution.stream.StreamEndpoint;
import dev.znecby.stellar_evolution.stream.StreamNetwork;
import dev.znecby.stellar_evolution.stream.StreamType;
import dev.znecby.stellar_evolution.thermal.CopperHeat;
import dev.znecby.stellar_evolution.thermal.ThermalLoad;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * <strong>缪子聚变仪</strong>方块实体：点火 → 自热 → 出蒸汽（压力管）+ 出缪子流（束流管）。
 *
 * <p>四个属性：
 * <ul>
 *   <li><strong>核心</strong>：读<strong>反应仓</strong>里的聚变芯核（每台最多 1 个反应仓），芯核决定核心名与约束；</li>
 *   <li><strong>温度</strong>：等离子体温度（℃）。点火前随点火进度爬升；点火后自热，压力随温度；</li>
 *   <li><strong>稳定值</strong>：点火成功 90 起，稳定运行微涨、出束微降；<strong>冷却水充足时缓慢回升</strong>，
 *       点火后缺水则迅速下跌。低于 20 自动停机，需重新点火；</li>
 *   <li><strong>约束</strong>：芯核对应的粒子流类型 + 累计输出量（如「缪子流 1.2M μ」）。</li>
 * </ul>
 *
 * <p>蒸汽按 1:1 消耗水（从物流网络的输入罐 / 铜格栅抽，抽到即销毁）；抽不到水就不出蒸汽。
 *
 * <p>热压差：点火是<strong>逐级升压</strong>（HP/t 与 DP 一起涨到峰值），供不上就回退不点火；
 * 点火成功后需求骤降到维持档，之后靠自热维持温度。超温会累积损毁，熔毁时芯核直接销毁。
 */
public class MuonFusionBlockEntity extends FissionMachineBlockEntity
        implements SteamEndpoint, StreamConsumer, StreamEndpoint {
    /** 本机机种。 */
    public static final FissionMachineKind KIND = FissionMachineKind.MUON_FUSION;

    /** 聚变维持所需的缪子流（单位/t）。 */
    public static final double MU_DEMAND = 2.0D;
    /** 没有缪子流时，点火阶段的 HP / 热阻倍率。 */
    public static final double MU_IGNITION_PENALTY = 2.0D;

    /** 聚变仪状态（Jade 用）。 */
    public enum MuonState {
        /** 没接反应仓 / 仓里没有聚变芯核。 */
        NO_CORE,
        /** 正在逐级升压点火。 */
        IGNITING,
        /** 已点火、稳定运行。 */
        RUNNING,
        /** 已停机（稳定值耗尽 / 供能中断），需要重新点火。 */
        STOPPED,
        /** 反应仓超过 1 个，强制停机。 */
        CHAMBER_OVERFLOW
    }

    private final MuonFusionCore core = new MuonFusionCore();

    /** 芯核磨损的小数余量（每 tick 0.2 耐久，攒够 1 才写一次 NBT）。 */
    private double wearBudget;

    /** 冷却水的小数余量（每 tick 需求量带小数，攒够 1 mB 再抽）。 */
    private double waterBudget;
    /** 本 tick 是否抽到了冷却水（Jade 显示用）。 */
    private boolean waterOk;
    /** 本 tick 的冷却水需求（mB/t）。 */
    private double waterDemand;

    private SteamState lastSteam = SteamState.NONE;
    private SteamStatus steamStatus = SteamStatus.IDLE;

    private int chamberCount;
    private boolean chamberOverflow;
    private boolean hasCore;
    /** 当前芯核规格（点火难度倍率用）；无芯核时为 null。 */
    private MuonCoreSpec coreSpec;
    private String coreNameKey = "";
    private int coreDamage;
    private int coreMaxDamage;
    /** 本 tick 实际接收到的缪子流（单位/t）。 */
    private double muReceived;
    /** 本 tick 缪子流是否达到维持需求。 */
    private boolean muOk;
    /** 累计消耗的缪子流总量（单位）。 */
    private double muConsumed;
    /** 累计输出的中子副产总量（单位）。 */
    private double neutronEmitted;

    public MuonFusionBlockEntity(BlockPos pos, BlockState state) {
        super(FissionMachines.MUON_ENTITY.get(), pos, state);
    }

    @Override
    public FissionMachineKind kind() {
        return KIND;
    }

    // ------------------------------------------------------------------ 服务端主逻辑

    @Override
    public void onThermalTick(ThermalLoad load) {
        // 缪子聚变仪不用配方结算；全部逻辑在 serverTick 里做（避免父类的 stall() 清状态）。
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state, ThermalLoad load) {
        List<ReactionChamberBlockEntity> chambers = findChambers(level);
        chamberCount = chambers.size();
        chamberOverflow = chambers.size() > 1;
        ReactionChamberBlockEntity chamber = chambers.size() == 1 ? chambers.get(0) : null;

        int coreSlot = findCoreSlot(chamber);
        hasCore = coreSlot >= 0 && !chamberOverflow;
        if (hasCore) {
            ItemStack stack = chamber.getItem(coreSlot);
            MuonCoreSpec spec = MuonCoreSpec.of(stack);
            coreSpec = spec;
            coreNameKey = spec == null ? "" : spec.displayKey();
            coreDamage = stack.getDamageValue();
            coreMaxDamage = stack.getMaxDamage();
        } else {
            coreSpec = null;
            coreNameKey = "";
            coreDamage = 0;
            coreMaxDamage = 0;
        }
        if (!hasCore) {
            core.clearHalt();   // 芯核被取走 / 销毁 → 解除稳定值停机锁
        }

        // 缪子流：聚变是消费者；没接 / 没抽够时点火需求翻倍，点火后稳定值快速下降
        muReceived = 0.0D;
        double muShare = shareOfMuon();
        if (muShare > 0.0D) {
            streamAccept(StreamType.MUON, muShare);
        }
        muOk = hasCore && !core.halted() && muReceived + 1.0E-6D >= MU_DEMAND;
        if (hasCore && !core.halted()) {
            muConsumed += muReceived;
        }

        boolean supplyOk = hasCore && !load.overloaded() && load.flowRate() >= requiredFlow();
        // 冷却水：按蒸汽量 1:1 扣（未点火时无需求）；抽不到就是缺水
        waterDemand = hasCore && core.ignited() ? core.steamAmount() : 0.0D;
        waterOk = supplyWater(waterDemand);
        MuonFusionCore.Snapshot snapshot = core.step(hasCore, supplyOk, waterOk, muOk, 1.0D);

        if (snapshot.meltedDown() && chamber != null && coreSlot >= 0) {
            chamber.setItem(coreSlot, ItemStack.EMPTY);
            chamber.setChanged();
            coreSlot = -1;
            hasCore = false;
            coreNameKey = "";
            coreDamage = 0;
            coreMaxDamage = 0;
        }
        if (hasCore && snapshot.ignited() && supplyOk && chamber != null && coreSlot >= 0) {
            wearCore(chamber, coreSlot);
        }

        boolean active = snapshot.ignited() && hasCore;

        // 蒸汽：点火后按温度出汽，压力随温度（沿用压力管 + 汽轮机）；没抽到冷却水就不出汽
        // 工质温度与等离子体温度解耦（封顶 700℃），避免出现「6000℃ 的水蒸气」
        if (active && waterOk && snapshot.steamAmount() > 0.0D) {
            lastSteam = new SteamState(snapshot.steamAmount(), core.steamTemperatureC(), snapshot.steamPressure());
            refreshSteamStatus(level);
        } else {
            lastSteam = SteamState.NONE;
            steamStatus = SteamStatus.IDLE;
        }
        boolean working = active;
        if (working) {
            neutronEmitted += streamRate();
        }
        if (working != this.working) {
            this.working = working;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        if (level.getGameTime() % 20L == 0L) {
            setChanged();   // 温度 / 稳定值 / 累计束流写入存档
        }
        // 点火中每 10 tick 同步一次：客户端靠 charge 让插栓 / 电弧提前动起来
        if (snapshot.igniting() && level.getGameTime() % 10L == 0L) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        if (working && level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 5L == 0L) {
            // 2×2×2 大机器：热尘撒在整机顶面（随机一格），不再固定在主方块
            CopperHeat.spawnHeatParticles(serverLevel, occupiedCells());
        }
    }

    /** 反应堆 / 聚变仪同口径：机械管网络里的反应仓，每台最多识别 1 个。 */
    private List<ReactionChamberBlockEntity> findChambers(Level level) {
        List<ReactionChamberBlockEntity> chambers = new ArrayList<>();
        for (LargeMachines.Connected connected : LargeMachines.connectedContainers(level, this)) {
            if (level.getBlockEntity(connected.pos()) instanceof ReactionChamberBlockEntity chamber) {
                chambers.add(chamber);
            }
        }
        return chambers;
    }

    /** 反应仓里第一个聚变芯核的槽位；没有则 -1。 */
    private static int findCoreSlot(ReactionChamberBlockEntity chamber) {
        if (chamber == null) {
            return -1;
        }
        for (int slot = 0; slot < chamber.getContainerSize(); slot++) {
            if (chamber.getItem(slot).getItem() instanceof MuonCoreItem) {
                return slot;
            }
        }
        return -1;
    }

    /** 点火运行时磨损芯核；耐久归零直接销毁（芯核「不可用」）。 */
    private void wearCore(ReactionChamberBlockEntity chamber, int slot) {
        ItemStack stack = chamber.getItem(slot);
        if (!(stack.getItem() instanceof MuonCoreItem item)) {
            return;
        }
        wearBudget += item.spec().wearPerTick();
        int wear = (int) Math.floor(wearBudget);
        if (wear <= 0) {
            return;
        }
        wearBudget -= wear;
        int newDamage = Math.min(stack.getMaxDamage(), stack.getDamageValue() + wear);
        if (newDamage >= stack.getMaxDamage()) {
            chamber.setItem(slot, ItemStack.EMPTY);
            core.shutdown();
            hasCore = false;
            coreNameKey = "";
            coreDamage = 0;
            coreMaxDamage = 0;
        } else {
            stack.setDamageValue(newDamage);
            coreDamage = newDamage;
        }
        chamber.setChanged();
    }

    /**
     * 抽冷却水：需求量与蒸汽量 1:1（mB/t），从物流网络里的输入罐 / 铜格栅取，取到即销毁（变成蒸汽）。
     *
     * @return 本 tick 的水是否够（需求为 0 时恒为 true）
     */
    private boolean supplyWater(double demandMbPerTick) {
        if (demandMbPerTick <= 0.0D) {
            waterBudget = 0.0D;
            return true;
        }
        waterBudget += demandMbPerTick;
        int request = (int) Math.floor(waterBudget);
        waterBudget -= request;
        if (request <= 0) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (fluids().extractFromInputs(water(request), request, transaction) < request) {
                return false;   // 没抽满：事务不提交，水自动回滚
            }
            transaction.commit();
        }
        return true;
    }

    private static FluidResource water(int amount) {
        return FluidResource.of(new FluidStack(Fluids.WATER, amount));
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

    @Override
    protected boolean animating() {
        return core.ignited() || core.charge() > 0.0D;
    }

    // ------------------------------------------------------------------ ThermalConsumer

    @Override
    public double thermalResistance() {
        return hasCore ? requiredResistance() : 0.0D;
    }

    @Override
    public double baseFlowRate() {
        return requiredFlow();
    }

    @Override
    public double maxUsefulFlowRate() {
        return hasCore ? requiredFlow() : 0.0D;
    }

    /** 当前档位 HP 需求；点火阶段按芯核难度 + 缪子缺失放大，已点火后的维持档不放大。 */
    private double requiredFlow() {
        double base = core.requiredFlow();
        if (!hasCore) {
            return base;
        }
        double coreScale = coreSpec == null ? 1.0D : coreSpec.ignitionScale();
        double muScale = core.ignited() || muOk ? 1.0D : MU_IGNITION_PENALTY;
        return base * coreScale * muScale;
    }

    /** 当前档位热阻；点火阶段按芯核难度 + 缪子缺失放大，已点火后的维持档不放大。 */
    private double requiredResistance() {
        double base = core.requiredResistance();
        if (!hasCore) {
            return base;
        }
        double coreScale = coreSpec == null ? 1.0D : coreSpec.ignitionScale();
        double muScale = core.ignited() || muOk ? 1.0D : MU_IGNITION_PENALTY;
        return base * coreScale * muScale;
    }

    // ------------------------------------------------------------------ SteamEndpoint

    @Override
    public boolean steamGenerator() {
        return true;
    }

    @Override
    public SteamState steamOutput() {
        return lastSteam;
    }

    @Override
    public List<BlockPos> steamCells() {
        return occupiedCells();
    }

    @Override
    public BlockPos steamOrigin() {
        return getBlockPos();
    }

    @Override
    public SteamStatus steamStatus() {
        return steamStatus;
    }

    // ------------------------------------------------------------------ StreamConsumer

    @Override
    public StreamType streamType() {
        return StreamType.MUON;
    }

    /** 有芯核、未停机时持续要缪子流；其余状态不参与分配。 */
    @Override
    public double streamDemand(StreamType type) {
        if (type != StreamType.MUON || !hasCore || core.halted()) {
            return 0.0D;
        }
        return MU_DEMAND;
    }

    @Override
    public void streamAccept(StreamType type, double amount) {
        if (type == StreamType.MUON && amount > 0.0D) {
            muReceived += amount;
        }
    }

    @Override
    public List<BlockPos> streamCells() {
        return occupiedCells();
    }

    @Override
    public BlockPos streamOrigin() {
        return getBlockPos();
    }

    /** 贴着的缪子管里，取供流最多的一段网络分给本机的份额（同类型多段不叠加）。 */
    private double shareOfMuon() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return 0.0D;
        }
        int wanted = StreamType.MUON.ordinal();
        double best = 0.0D;
        for (BlockPos cell : occupiedCells()) {
            for (Direction direction : Direction.values()) {
                BlockPos pipePos = cell.relative(direction);
                BlockState pipe = level.getBlockState(pipePos);
                if (!(pipe.getBlock() instanceof BeamPipeBlock)
                        || pipe.getValue(BeamPipeBlock.STREAM) != wanted
                        || !pipe.getValue(BeamPipeBlock.CONNECTIONS.get(direction.getOpposite()))) {
                    continue;
                }
                double share = StreamNetwork.stateAt(level, pipePos).shareOf(getBlockPos());
                if (share > best) {
                    best = share;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ StreamEndpoint（中子副产）

    /** 聚变向外输出的唯一流：中子。 */
    @Override
    public StreamType outputStreamType() {
        return StreamType.NEUTRON;
    }

    /** 只有点火且缪子充足时才向外喷中子；缺 μ 时反应无法维持，副产为 0。 */
    @Override
    public double streamRate() {
        return core.ignited() && muOk ? core.neutronRate() : 0.0D;
    }

    @Override
    public double streamEmitted() {
        return neutronEmitted;
    }

    /** 输入 MUON、输出 NEUTRON：两种管都能接。 */
    @Override
    public boolean acceptsStream(StreamType type) {
        return type == StreamType.MUON || type == StreamType.NEUTRON;
    }

    // ------------------------------------------------------------------ Jade 读取

    public MuonState muonState() {
        if (chamberOverflow) {
            return MuonState.CHAMBER_OVERFLOW;
        }
        if (!hasCore) {
            return MuonState.NO_CORE;
        }
        if (core.halted()) {
            return MuonState.STOPPED;
        }
        if (core.ignited()) {
            return MuonState.RUNNING;
        }
        return core.charge() > 0.0D ? MuonState.IGNITING : MuonState.STOPPED;
    }

    public String muonCoreNameKey() {
        return coreNameKey;
    }

    public double muonTemperatureC() {
        return core.temperatureC();
    }

    public double muonStability() {
        return core.stability();
    }

    public double muonChargeRatio() {
        return core.charge() / MuonFusionCore.IGNITION_STEPS;
    }

    public boolean muonIgnited() {
        return core.ignited();
    }

    public boolean muonEmitting() {
        return core.ignited() && muOk;
    }

    public double muonStreamRate() {
        return muReceived;
    }

    public double muonEmitted() {
        return muConsumed;
    }

    public boolean muonMuOk() {
        return muOk;
    }

    public double muonNeutronRate() {
        return streamRate();
    }

    public double muonNeutronEmitted() {
        return neutronEmitted;
    }

    public double muonRequiredFlow() {
        return requiredFlow();
    }

    public double muonRequiredResistance() {
        return requiredResistance();
    }

    public int muonChamberCount() {
        return chamberCount;
    }

    public boolean muonChamberOverflow() {
        return chamberOverflow;
    }

    public boolean muonHasCore() {
        return hasCore;
    }

    public int muonCoreDamage() {
        return coreDamage;
    }

    public int muonCoreMaxDamage() {
        return coreMaxDamage;
    }

    public double muonDamage() {
        return core.damage();
    }

    public SteamState muonSteamOutput() {
        return lastSteam;
    }

    /** 本 tick 冷却水是否够（Jade 用）。 */
    public boolean muonWaterOk() {
        return waterOk;
    }

    /** 本 tick 冷却水需求（mB/t，Jade 用）。 */
    public double muonWaterDemand() {
        return waterDemand;
    }

    // ------------------------------------------------------------------ 存盘

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("muon_ignited", core.ignited());
        output.putBoolean("muon_halted", core.halted());
        output.putDouble("muon_charge", core.charge());
        output.putDouble("muon_temp", core.temperatureC());
        output.putDouble("muon_stability", core.stability());
        output.putDouble("muon_damage", core.damage());
        output.putDouble("muon_emitted", core.emitted());
        output.putDouble("muon_mu_consumed", muConsumed);
        output.putDouble("muon_neutron_emitted", neutronEmitted);
        output.putDouble("muon_wear", wearBudget);
        output.putDouble("muon_water", waterBudget);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        core.restore(input.getBooleanOr("muon_ignited", false),
                input.getBooleanOr("muon_halted", false),
                input.getDoubleOr("muon_charge", 0.0D),
                input.getDoubleOr("muon_temp", MuonFusionCore.AMBIENT_C),
                input.getDoubleOr("muon_stability", 0.0D),
                input.getDoubleOr("muon_damage", 0.0D),
                input.getDoubleOr("muon_emitted", 0.0D));
        muConsumed = input.getDoubleOr("muon_mu_consumed", 0.0D);
        neutronEmitted = input.getDoubleOr("muon_neutron_emitted", 0.0D);
        wearBudget = input.getDoubleOr("muon_wear", 0.0D);
        waterBudget = input.getDoubleOr("muon_water", 0.0D);
    }
}
