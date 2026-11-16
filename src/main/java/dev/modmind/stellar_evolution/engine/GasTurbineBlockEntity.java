package dev.modmind.stellar_evolution.engine;

import dev.modmind.stellar_evolution.large.LargeMachine;
import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.large.LargeMachineIo;
import dev.modmind.stellar_evolution.steam.SteamEndpoint;
import dev.modmind.stellar_evolution.steam.SteamState;
import dev.modmind.stellar_evolution.thermal.ThermalGenerator;
import dev.modmind.stellar_evolution.welding.WeldingFluids;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * 联合燃气机组方块实体：<strong>高 HP / 中 DP</strong> 的热压差生成端 + 蒸汽网络生成端。
 *
 * <ul>
 *   <li><strong>转速</strong>：约 33 秒惰转到 1.0，断燃料后缓慢掉转（惯性大）；</li>
 *   <li><strong>温度</strong>：燃料品质 + 氧气助燃决定；HP 随温度上升；</li>
 *   <li><strong>燃料</strong>：相邻储罐里的 GAS / AERO / MEDIUM 级燃料，2 mB/t；</li>
 *   <li><strong>助燃</strong>：相邻储罐里的氧气，4 mB/t → 温度 +0.2、HP +10%；</li>
 *   <li><strong>水</strong>：六面任一原版铜格栅，8000 mB/t 水 → 8000 mB/t、300℃、30 bar 蒸汽；</li>
 *   <li><strong>大气</strong>：真空维度不能燃烧。</li>
 * </ul>
 *
 * <p>出力：DP = 20000（与转速无关，稳定容量），HP/t = 8000 × 转速 × 温度系数。
 */
public class GasTurbineBlockEntity extends BlockEntity
        implements ThermalGenerator, SteamEndpoint, LargeMachine, EngineAnimated {
    /** 差压容量（DP）。 */
    public static final double CAPACITY = 20_000.0D;
    /** 满转满温时的热流率（HP/t）。 */
    public static final double MAX_FLOW_RATE = 8_000.0D;
    /** 氧气助燃的 HP 倍率。 */
    public static final double OXYGEN_FLOW_BOOST = 1.10D;
    /** 每 tick 燃料消耗（mB）。 */
    public static final int FUEL_PER_TICK = 2;
    /** 每 tick 氧气消耗（mB）。 */
    public static final int OXYGEN_PER_TICK = 4;
    /** 每 tick 水耗（mB）→ 同量蒸汽。 */
    public static final int WATER_PER_TICK = 8_000;
    /** 产出蒸汽的速率（mB/t）。 */
    public static final double STEAM_RATE = 8_000.0D;
    /** 产出蒸汽温度（℃）：过热蒸汽。 */
    public static final double STEAM_TEMPERATURE = 300.0D;
    /** 产出蒸汽压力（bar）。 */
    public static final double STEAM_PRESSURE = 30.0D;

    private static final double RPM_UP = 0.003D;
    private static final double RPM_DOWN = 0.01D;

    /** 当前转速 0..1。 */
    private double rpm;
    /** 当前透平进口温度系数 0..1。 */
    private double temperature;
    private boolean running;
    private boolean oxygenated;
    private boolean steaming;
    private SteamStatus status = SteamStatus.IDLE;
    private double lastSyncedRpm = -1.0D;
    private int syncTicks;
    /** 机械管网里的「输入」储罐合并视图（燃料 / 液氧 / 水统一从这里取，与生化 / 石化机器同一口径）。 */
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);
    /** Jade 用的运行状态（每 tick 结算）。 */
    private EngineMachineSupport.FuelState fuelState = EngineMachineSupport.FuelState.IDLE;
    /** 可动部件的已运行时长（客户端推进，供渲染器插值）。 */
    private float age;
    private float lastAge;
    /** 客户端平滑转速 0..1（起转 / 停机缓入缓出）。 */
    private float spin;

    public GasTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(EngineMachines.GAS_TURBINE_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GasTurbineBlockEntity turbine) {
        if (level.isClientSide()) {
            // 客户端只推进叶轮时长：在转就加速，停机慢慢停住
            turbine.lastAge = turbine.age;
            boolean animating = turbine.running || turbine.rpm > 0.02D;
            turbine.spin = net.minecraft.util.Mth.clamp(turbine.spin + (animating ? 0.06F : -0.12F), 0.0F, 1.0F);
            turbine.age += turbine.spin;
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        boolean wasRunning = turbine.running;
        boolean wasSteaming = turbine.steaming;
        turbine.serverTick(serverLevel, pos);
        if (wasRunning != turbine.running || wasSteaming != turbine.steaming) {
            turbine.setChanged();
            serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        if (++turbine.syncTicks >= 10) {
            turbine.syncTicks = 0;
            if (Math.abs(turbine.rpm - turbine.lastSyncedRpm) >= 0.05D) {
                turbine.lastSyncedRpm = turbine.rpm;
                turbine.setChanged();
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }
    }

    private void serverTick(ServerLevel level, BlockPos pos) {
        // 机械管接点（铜垫）状态与大机器框架同一轮询口径
        EngineMachineSupport.refreshPorts(level, pos);

        boolean hasAir = EngineMachineSupport.hasAtmosphere(level);
        boolean burning = false;
        double quality = 0.0D;

        if (hasAir) {
            Fluid fuelFluid = EngineMachineSupport.findNetworkFluid(fluidHandler, this::isTurbineFuel);
            EngineFuels.Fuel fuel = fuelFluid == null ? null : EngineFuels.of(fuelFluid);
            if (fuel != null && EngineMachineSupport.drainNetwork(fluidHandler, fuelFluid, FUEL_PER_TICK) > 0) {
                burning = true;
                quality = fuel.quality();
            }
        }

        oxygenated = false;
        if (burning) {
            oxygenated = EngineMachineSupport.drainNetwork(fluidHandler,
                    WeldingFluids.OXYGEN.source().get(), OXYGEN_PER_TICK) > 0;
        }

        fuelState = !hasAir ? EngineMachineSupport.FuelState.VACUUM
                : !burning ? EngineMachineSupport.FuelState.NO_FUEL
                : oxygenated ? EngineMachineSupport.FuelState.RUNNING
                : EngineMachineSupport.FuelState.NO_OXYGEN;

        // 温度：燃料品质 0.5~0.7，氧气 +0.2，转速拉满再补 0.1
        double targetTemperature = burning
                ? Math.min(1.0D, 0.45D + 0.25D * quality + (oxygenated ? 0.20D : 0.0D) + 0.10D * rpm)
                : 0.0D;
        temperature += (targetTemperature - temperature) * 0.01D;

        if (burning) {
            rpm = Math.min(1.0D, rpm + RPM_UP);
        } else {
            rpm = Math.max(0.0D, rpm - RPM_DOWN);
        }
        running = rpm > 0.02D;

        // 联合循环：管网里有铜格栅（虚拟水源）就进水产汽
        steaming = false;
        if (running) {
            steaming = EngineMachineSupport.drainNetwork(fluidHandler, Fluids.WATER, WATER_PER_TICK) >= WATER_PER_TICK;
        }
        status = steaming ? SteamStatus.DELIVERING : SteamStatus.IDLE;

        if (level.getGameTime() % 3L == 0L && running) {
            if (steaming) {
                // 余热锅炉顶部冒白汽（整机局部 0.5~1.5 x、1.88 y、0.7~1.3 z）
                level.sendParticles(ParticleTypes.CLOUD,
                        pos.getX() + 1.0D, pos.getY() + 1.88D, pos.getZ() + 1.0D,
                        2, 0.32D, 0.04D, 0.22D, 0.01D);
            } else {
                // 没产汽时从左侧排气段冒淡烟（整机局部 x≈0.06、y≈0.85、z≈1.0，朝 −X 外）
                level.sendParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.06D, pos.getY() + 0.85D, pos.getZ() + 1.0D,
                        1, 0.02D, 0.08D, 0.12D, 0.006D);
            }
        }
    }

    private boolean isTurbineFuel(Fluid fluid) {
        EngineFuels.Fuel fuel = EngineFuels.of(fluid);
        if (fuel == null) {
            return false;
        }
        return fuel.grade() == EngineFuelGrade.GAS
                || fuel.grade() == EngineFuelGrade.AERO;
    }

    // ------------------------------------------------------------------ LargeMachine（只借框架做「任意一格接入」）

    @Override
    public boolean isLargeMachine() {
        return true;
    }

    @Override
    public Level level() {
        return level;
    }

    @Override
    public BlockPos origin() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> occupiedCells() {
        return EngineMachineSupport.cells(worldPosition);
    }

    @Override
    public LargeMachineIo ioMode(BlockPos container) {
        return ioModes.getOrDefault(container, LargeMachineIo.INPUT);
    }

    /** 各物流容器的输入 / 输出方向（剪刀右键切换，按容器坐标记账）。 */
    private final java.util.Map<BlockPos, LargeMachineIo> ioModes = new java.util.HashMap<>();

    @Override
    public void setIoMode(BlockPos container, LargeMachineIo mode) {
        ioModes.put(container.immutable(), mode);
        setChanged();
    }

    // ------------------------------------------------------------------ ThermalGenerator

    @Override
    public double capacity() {
        return running ? CAPACITY : 0.0D;
    }

    @Override
    public double flowRate() {
        if (!running) {
            return 0.0D;
        }
        double temperatureFactor = 0.6D + 0.4D * temperature;
        return MAX_FLOW_RATE * rpm * temperatureFactor * (oxygenated ? OXYGEN_FLOW_BOOST : 1.0D);
    }

    /** 本 tick 是否吃到液氧（Jade）。 */
    public boolean isOxygenated() {
        return oxygenated;
    }

    /** Jade 运行状态。 */
    public EngineMachineSupport.FuelState fuelState() {
        return fuelState;
    }

    /** 供渲染器取插值后的运行时长（压气机叶轮转角 = age × 角速度）。 */
    @Override
    public float age(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, lastAge, age);
    }

    // ------------------------------------------------------------------ SteamEndpoint

    @Override
    public boolean steamGenerator() {
        return true;
    }

    @Override
    public SteamState steamOutput() {
        return steaming ? new SteamState(STEAM_RATE, STEAM_TEMPERATURE, STEAM_PRESSURE) : SteamState.NONE;
    }

    @Override
    public List<BlockPos> steamCells() {
        return EngineMachineSupport.cells(getBlockPos());
    }

    @Override
    public BlockPos steamOrigin() {
        return getBlockPos();
    }

    @Override
    public SteamStatus steamStatus() {
        return status;
    }

    public double rpm() {
        return rpm;
    }

    public double temperature() {
        return temperature;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isSteaming() {
        return steaming;
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("rpm", rpm);
        output.putDouble("temperature", temperature);
        output.putBoolean("running", running);
        output.putBoolean("steaming", steaming);
        // 只存「输出」容器（输入是缺省值），与生化 / 石化机器同一口径
        int count = 0;
        for (LargeMachineIo mode : ioModes.values()) {
            if (mode == LargeMachineIo.OUTPUT) {
                count++;
            }
        }
        int[] outs = new int[count * 3];
        int index = 0;
        for (java.util.Map.Entry<BlockPos, LargeMachineIo> entry : ioModes.entrySet()) {
            if (entry.getValue() != LargeMachineIo.OUTPUT) {
                continue;
            }
            BlockPos pos = entry.getKey();
            outs[index++] = pos.getX();
            outs[index++] = pos.getY();
            outs[index++] = pos.getZ();
        }
        output.putIntArray("large_io_output", outs);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        rpm = input.getDoubleOr("rpm", 0.0D);
        temperature = input.getDoubleOr("temperature", 0.0D);
        running = input.getBooleanOr("running", false);
        steaming = input.getBooleanOr("steaming", false);
        lastSyncedRpm = rpm;
        ioModes.clear();
        int[] outs = input.getIntArray("large_io_output").orElse(new int[0]);
        for (int i = 0; i + 2 < outs.length; i += 3) {
            ioModes.put(new BlockPos(outs[i], outs[i + 1], outs[i + 2]), LargeMachineIo.OUTPUT);
        }
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
