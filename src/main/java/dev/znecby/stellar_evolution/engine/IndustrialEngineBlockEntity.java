package dev.znecby.stellar_evolution.engine;

import dev.znecby.stellar_evolution.large.LargeMachine;
import dev.znecby.stellar_evolution.large.LargeMachineFluidHandler;
import dev.znecby.stellar_evolution.large.LargeMachineIo;
import dev.znecby.stellar_evolution.thermal.ThermalGenerator;
import dev.znecby.stellar_evolution.welding.WeldingFluids;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * 工业内燃机组方块实体：<strong>中 HP / 高 DP</strong> 的热压差生成端。
 *
 * <ul>
 *   <li><strong>转速</strong>：0 → 1.0 约 10 秒升转；无燃料时按 0.02/t 降转；</li>
 *   <li><strong>燃料</strong>：六面相邻储罐里的 LOW / MEDIUM / HIGH 级燃料，1 mB/t；</li>
 *   <li><strong>助燃</strong>：另一面相邻储罐里的氧气，2 mB/t → 转速上限 1.25、HP +25%、DP +15%；</li>
 *   <li><strong>大气</strong>：月球等真空维度不能燃烧（转速归零）。</li>
 * </ul>
 *
 * <p>出力：DP = 32000 × (0.6 + 0.4×转速)，HP/t = 4000 × 转速（氧气再乘系数）。
 */
public class IndustrialEngineBlockEntity extends BlockEntity implements ThermalGenerator, LargeMachine, EngineAnimated {
    /** 满转时的差压容量（DP）。 */
    public static final double MAX_CAPACITY = 32_000.0D;
    /** 满转时的热流率（HP/t）。 */
    public static final double MAX_FLOW_RATE = 4_000.0D;
    /** 氧气助燃的出力倍率。 */
    public static final double OXYGEN_FLOW_BOOST = 1.25D;
    /** 氧气助燃的容量倍率。 */
    public static final double OXYGEN_CAPACITY_BOOST = 1.15D;
    /** 每 tick 燃料消耗（mB）。 */
    public static final int FUEL_PER_TICK = 1;
    /** 每 tick 氧气消耗（mB）。 */
    public static final int OXYGEN_PER_TICK = 2;
    /** 升转速度（每 tick 的转速增量，约 10 秒满转）。 */
    private static final double RPM_UP = 0.01D;
    /** 降转速度。 */
    private static final double RPM_DOWN = 0.02D;
    /** 无氧时的转速上限。 */
    private static final double RPM_MAX = 1.0D;
    /** 有氧时的转速上限。 */
    private static final double RPM_MAX_OXYGEN = 1.25D;

    /** 当前转速 0..1.25。 */
    private double rpm;
    /** 是否在转（同步给客户端做烟）。 */
    private boolean running;
    /** 本 tick 是否吃到氧气。 */
    private boolean oxygenated;
    /** 上次同步的转速（减少发包）。 */
    private double lastSyncedRpm = -1.0D;
    private int syncTicks;
    /** 机械管网里的「输入」储罐合并视图（燃料 / 液氧统一从这里取，与生化 / 石化机器同一口径）。 */
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);
    /** Jade 用的运行状态（每 tick 结算）。 */
    private EngineMachineSupport.FuelState fuelState = EngineMachineSupport.FuelState.IDLE;
    /** 可动部件的已运行时长（客户端推进，供渲染器插值）。 */
    private float age;
    private float lastAge;
    /** 客户端平滑转速 0..1（起转 / 停机缓入缓出，风扇不会瞬间跳变）。 */
    private float spin;

    public IndustrialEngineBlockEntity(BlockPos pos, BlockState state) {
        super(EngineMachines.INDUSTRIAL_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, IndustrialEngineBlockEntity engine) {
        if (level.isClientSide()) {
            // 客户端只推进风扇时长：在转就加速，停机慢慢停住
            engine.lastAge = engine.age;
            boolean animating = engine.running || engine.rpm > 0.02D;
            engine.spin = net.minecraft.util.Mth.clamp(engine.spin + (animating ? 0.06F : -0.12F), 0.0F, 1.0F);
            engine.age += engine.spin;
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        boolean wasRunning = engine.running;
        engine.serverTick(serverLevel, pos);
        if (wasRunning != engine.running) {
            engine.setChanged();
            serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        if (++engine.syncTicks >= 10) {
            engine.syncTicks = 0;
            if (Math.abs(engine.rpm - engine.lastSyncedRpm) >= 0.05D) {
                engine.lastSyncedRpm = engine.rpm;
                engine.setChanged();
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }
    }

    private void serverTick(ServerLevel level, BlockPos pos) {
        // 机械管接点（铜垫）状态与大机器框架同一轮询口径
        EngineMachineSupport.refreshPorts(level, pos);

        boolean hasAir = EngineMachineSupport.hasAtmosphere(level);
        boolean burning = false;

        if (hasAir) {
            Fluid fuel = EngineMachineSupport.findNetworkFluid(fluidHandler, this::isCombustionFuel);
            if (fuel != null && EngineMachineSupport.drainNetwork(fluidHandler, fuel, FUEL_PER_TICK) > 0) {
                burning = true;
            }
        }

        // 氧气助燃：燃料在烧时才吃氧
        oxygenated = false;
        if (burning) {
            oxygenated = EngineMachineSupport.drainNetwork(fluidHandler,
                    WeldingFluids.OXYGEN.source().get(), OXYGEN_PER_TICK) > 0;
        }

        fuelState = !hasAir ? EngineMachineSupport.FuelState.VACUUM
                : !burning ? EngineMachineSupport.FuelState.NO_FUEL
                : oxygenated ? EngineMachineSupport.FuelState.RUNNING
                : EngineMachineSupport.FuelState.NO_OXYGEN;

        double maxRpm = oxygenated ? RPM_MAX_OXYGEN : RPM_MAX;
        if (burning) {
            rpm = Math.min(maxRpm, rpm + RPM_UP);
        } else {
            rpm = Math.max(0.0D, rpm - RPM_DOWN);
        }
        if (rpm > maxRpm) {
            rpm = Math.max(maxRpm, rpm - RPM_DOWN);
        }
        running = rpm > 0.02D;

        if (level.getGameTime() % 4L == 0L && running) {
            // 顶部两根立式排气管（整机局部坐标 0.5~1.5 x、1.9 y、1.1 z）：两根管口各自冒烟，
            // 不再固定在主方块（min 角）上方、也不会冒在机器内部
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 0.6D, pos.getY() + 1.92D, pos.getZ() + 1.12D,
                    1, 0.07D, 0.02D, 0.07D, 0.012D);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 1.4D, pos.getY() + 1.92D, pos.getZ() + 1.12D,
                    1, 0.07D, 0.02D, 0.07D, 0.012D);
        }
    }

    private boolean isCombustionFuel(Fluid fluid) {
        EngineFuels.Fuel fuel = EngineFuels.of(fluid);
        if (fuel == null) {
            return false;
        }
        // 工业内燃机组是不锈钢阶段的机器：只接 MEDIUM / HIGH；LOW（丙酮）留给早期燃烧引擎。
        return fuel.grade() == EngineFuelGrade.MEDIUM
                || fuel.grade() == EngineFuelGrade.HIGH;
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

    /** 各物流容器的输入 / 输出方向（剪刀右键切换，按容器坐标记账）。 */
    private final Map<BlockPos, LargeMachineIo> ioModes = new HashMap<>();

    @Override
    public LargeMachineIo ioMode(BlockPos container) {
        return ioModes.getOrDefault(container, LargeMachineIo.INPUT);
    }

    @Override
    public void setIoMode(BlockPos container, LargeMachineIo mode) {
        ioModes.put(container.immutable(), mode);
        setChanged();
    }

    // ------------------------------------------------------------------ ThermalGenerator

    @Override
    public double capacity() {
        if (!running) {
            return 0.0D;
        }
        double load = 0.6D + 0.4D * Math.min(1.0D, rpm);
        return MAX_CAPACITY * load * (oxygenated ? OXYGEN_CAPACITY_BOOST : 1.0D);
    }

    @Override
    public double flowRate() {
        if (!running) {
            return 0.0D;
        }
        return MAX_FLOW_RATE * rpm * (oxygenated ? OXYGEN_FLOW_BOOST : 1.0D);
    }

    /** 当前转速 0..1.25（Jade / 调试）。 */
    public double rpm() {
        return rpm;
    }

    /** 是否在转。 */
    public boolean isRunning() {
        return running;
    }

    /** 本 tick 是否吃到液氧（Jade）。 */
    public boolean isOxygenated() {
        return oxygenated;
    }

    /** Jade 运行状态。 */
    public EngineMachineSupport.FuelState fuelState() {
        return fuelState;
    }

    /** 供渲染器取插值后的运行时长（风扇转角 = age × 角速度）。 */
    @Override
    public float age(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, lastAge, age);
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("rpm", rpm);
        output.putBoolean("running", running);
        // 只存「输出」容器（输入是缺省值），与生化 / 石化机器同一口径
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        rpm = input.getDoubleOr("rpm", 0.0D);
        running = input.getBooleanOr("running", false);
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
