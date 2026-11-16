package dev.znecby.stellar_evolution.engine;

import dev.znecby.stellar_evolution.large.LargeMachine;
import dev.znecby.stellar_evolution.large.LargeMachineFluidHandler;
import dev.znecby.stellar_evolution.large.LargeMachineIo;
import dev.znecby.stellar_evolution.thermal.ThermalGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
 * 航空航天火箭引擎方块实体：<strong>中高 DP / 中高 HP</strong>，燃料自带氧化剂，真空可用。
 *
 * <ul>
 *   <li><strong>燃料</strong>：机械管网「输入」储罐里的 ROCKET 级燃料（煤油氧 / 甲烷氧 / 自燃），4 mB/t；</li>
 *   <li><strong>氧化剂</strong>：已在燃料配方里预混，运行时不另抽液氧；</li>
 *   <li><strong>室压</strong>：0 → 1 约 1 秒，瞬时响应；HP/DP 正比于室压；</li>
 *   <li><strong>尾焰</strong>：8 格锥形，吹飞 + 点燃 + 破甲秒杀（见 {@link RocketPlume}）。</li>
 * </ul>
 */
public class RocketEngineBlockEntity extends BlockEntity
        implements ThermalGenerator, LargeMachine, EngineAnimated {
    /** 满室压时的差压容量（DP）。 */
    public static final double MAX_CAPACITY = 26_000.0D;
    /** 满室压时的热流率（HP/t）。 */
    public static final double MAX_FLOW_RATE = 7_000.0D;
    /** 每 tick 燃料消耗（mB）。 */
    public static final int FUEL_PER_TICK = 4;
    /** 室压升速（每 tick）。 */
    private static final double PRESSURE_UP = 0.05D;
    /** 室压降速（每 tick）。 */
    private static final double PRESSURE_DOWN = 0.10D;

    private double pressure;
    private boolean running;
    private double lastSyncedPressure = -1.0D;
    private int syncTicks;
    /** 机械管网里的「输入」储罐合并视图（燃料统一从这里取，与生化 / 石化机器同一口径）。 */
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);
    /** Jade 用的运行状态（每 tick 结算）。 */
    private EngineMachineSupport.FuelState fuelState = EngineMachineSupport.FuelState.IDLE;
    /** 可动部件的已运行时长（客户端推进，供渲染器插值）。 */
    private float age;
    private float lastAge;
    /** 客户端平滑转速 0..1（起转 / 停机缓入缓出）。 */
    private float spin;

    public RocketEngineBlockEntity(BlockPos pos, BlockState state) {
        super(EngineMachines.ROCKET_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RocketEngineBlockEntity engine) {
        if (level.isClientSide()) {
            // 客户端只推进涡轮泵叶轮时长：在转就加速，停机慢慢停住
            engine.lastAge = engine.age;
            boolean animating = engine.running || engine.pressure > 0.05D;
            engine.spin = net.minecraft.util.Mth.clamp(engine.spin + (animating ? 0.06F : -0.12F), 0.0F, 1.0F);
            engine.age += engine.spin;
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        boolean wasRunning = engine.running;
        engine.serverTick(serverLevel, pos, state);
        if (wasRunning != engine.running) {
            engine.setChanged();
            serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        if (++engine.syncTicks >= 10) {
            engine.syncTicks = 0;
            if (Math.abs(engine.pressure - engine.lastSyncedPressure) >= 0.05D) {
                engine.lastSyncedPressure = engine.pressure;
                engine.setChanged();
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // 机械管接点（铜垫）状态与大机器框架同一轮询口径
        EngineMachineSupport.refreshPorts(level, pos);

        Fluid fuel = EngineMachineSupport.findNetworkFluid(fluidHandler, this::isRocketFuel);
        boolean burning = fuel != null
                && EngineMachineSupport.drainNetwork(fluidHandler, fuel, FUEL_PER_TICK) > 0;
        fuelState = burning ? EngineMachineSupport.FuelState.RUNNING
                : EngineMachineSupport.FuelState.NO_FUEL;

        if (burning) {
            pressure = Math.min(1.0D, pressure + PRESSURE_UP);
        } else {
            pressure = Math.max(0.0D, pressure - PRESSURE_DOWN);
        }
        running = pressure > 0.05D;

        if (running) {
            Direction facing = state.hasProperty(RocketEngineBlock.FACING)
                    ? state.getValue(RocketEngineBlock.FACING)
                    : Direction.SOUTH;
            RocketPlume.tick(level, pos, facing);
        }
    }

    private boolean isRocketFuel(Fluid fluid) {
        EngineFuels.Fuel fuel = EngineFuels.of(fluid);
        return fuel != null && fuel.grade() == EngineFuelGrade.ROCKET;
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
        return running ? MAX_CAPACITY * pressure : 0.0D;
    }

    @Override
    public double flowRate() {
        return running ? MAX_FLOW_RATE * pressure : 0.0D;
    }

    public double pressure() {
        return pressure;
    }

    /** Jade 运行状态。 */
    public EngineMachineSupport.FuelState fuelState() {
        return fuelState;
    }

    /** 供渲染器取插值后的运行时长（涡轮泵叶轮转角 = age × 角速度）。 */
    @Override
    public float age(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, lastAge, age);
    }

    public boolean isRunning() {
        return running;
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("pressure", pressure);
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
        pressure = input.getDoubleOr("pressure", 0.0D);
        running = input.getBooleanOr("running", false);
        lastSyncedPressure = pressure;
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
