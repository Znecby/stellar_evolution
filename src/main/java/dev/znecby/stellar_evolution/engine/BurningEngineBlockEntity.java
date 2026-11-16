package dev.znecby.stellar_evolution.engine;

import dev.znecby.stellar_evolution.tank.TankBlockEntity;
import dev.znecby.stellar_evolution.thermal.ThermalGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * 燃烧引擎方块实体：只维护一根「曲轴角」，同时是热压差网络的<strong>生成端</strong>。
 *
 * <p><strong>双模</strong>（《生化线》§8.3，已<strong>去掉</strong>「下方熔炉亮着就转」的旧判据）——
 * 按<strong>正下方方块</strong>决定怎么烧：
 * <ul>
 *   <li><strong>固体模式</strong>：正下方是<strong>物品容器</strong> → 从中取<strong>任意熔炉燃料</strong>，
 *       按原版燃烧时长折算点数（{@code 燃烧时长 / 20}，例：1 煤炭 = 80 点）入池；出力固定 {@value #CAPACITY} DP / {@value #FLOW_RATE} HP·t⁻¹；</li>
 *   <li><strong>液体模式</strong>：正下方是<strong>储罐</strong> → 从罐里取<strong>流体燃料</strong>，每周期固定消耗 <b>1 mB</b>；
 *       运行时长与出力查 {@link EngineFuels}（整体强于固体）；</li>
 *   <li>其它 / 无 → 停机（输出 0）。</li>
 * </ul>
 *
 * <p><strong>燃料池</strong>：容量 {@value #POOL_MAX} 点，{@value #TICKS_PER_POINT} 刻消耗 1 点（1 点 = 20t 运行）。
 * 只存盘「池点数 / 是否在跑」，加工中的液体会话重进世界重新开始。
 * <p>「是否在跑」同步给客户端（{@link #getUpdateTag}），客户端据此决定曲轴要不要转。
 */
public class BurningEngineBlockEntity extends BlockEntity implements ThermalGenerator {
    /** 固体模式的差压容量（DP）。 */
    public static final double CAPACITY = 4096.0D;
    /** 固体模式的热流率（HP/t）。 */
    public static final double FLOW_RATE = 512.0D;

    /** 燃料池容量（点）。 */
    public static final int POOL_MAX = 1600;
    /** 多少刻消耗 1 点。 */
    public static final int TICKS_PER_POINT = 20;

    /** 静止位（曲轴角，单位度）。 */
    public static final float REST_ANGLE = 0.0F;
    /** 工作时的曲轴角速度（度/tick）。 */
    private static final float SPIN_SPEED = 6.0F;
    /** 停转后回落到静止位的角速度（度/tick）。 */
    private static final float RETURN_SPEED = 4.0F;

    /** 运行模式。 */
    public enum Mode {
        /** 停机（下方没有容器 / 储罐，或没燃料）。 */
        OFF,
        /** 固体模式（下方物品容器）。 */
        SOLID,
        /** 液体模式（下方储罐）。 */
        LIQUID
    }

    /** 本章节当前曲轴角（度）。 */
    private float angle;
    /** 上一 tick 的曲轴角，用于插值。 */
    private float lastAngle;

    /** 固体模式：燃料池点数 0..{@value #POOL_MAX}。 */
    private int points;
    /** 固体模式：距下一次扣点的刻数。 */
    private int pointTimer;
    /** 液体模式：本周期已跑刻数。 */
    private int liquidTimer;

    /** 当前是否在跑（服务端算 + 同步给客户端供动画）。 */
    private boolean running;
    /** 当前模式。 */
    private Mode mode = Mode.OFF;
    /** 当前出力（液体模式按流体查表；固体固定）。 */
    private double outCapacity;
    private double outFlowRate;

    public BurningEngineBlockEntity(BlockPos pos, BlockState state) {
        super(EngineBlocks.BURNING_ENGINE_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BurningEngineBlockEntity engine) {
        engine.lastAngle = engine.angle;

        if (level instanceof ServerLevel serverLevel) {
            boolean wasRunning = engine.running;
            engine.running = engine.serverTick(serverLevel, pos);
            if (wasRunning != engine.running) {
                engine.setChanged();
                // 把「是否在跑」同步给客户端 —— 客户端据此决定曲轴要不要转（只在变化时发包）
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }

        if (engine.running) {
            engine.angle += SPIN_SPEED;
        } else {
            // 回到最近的一圈整数位，避免停在一个「半程」的怪姿态。
            float target = REST_ANGLE + 360.0F * Math.round((engine.angle - REST_ANGLE) / 360.0F);
            engine.angle = approach(engine.angle, target, RETURN_SPEED);
        }
    }

    // ------------------------------------------------------------------ 服务端：双模结算

    /** 按正下方方块决定模式并推进燃料；返回这一 tick 是否在跑。 */
    private boolean serverTick(ServerLevel level, BlockPos pos) {
        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof TankBlockEntity tank) {
            mode = Mode.LIQUID;
            return liquidTick(level, tank);
        }
        if (below instanceof Container container) {
            mode = Mode.SOLID;
            return solidTick(level, container);
        }
        mode = Mode.OFF;
        pointTimer = 0;
        liquidTimer = 0;
        outCapacity = 0.0D;
        outFlowRate = 0.0D;
        return false;
    }

    /** 固体模式：池未满就从下方容器取 1 个熔炉燃料折算点数；有点数就在跑，每 {@value #TICKS_PER_POINT} 刻扣 1 点。 */
    private boolean solidTick(ServerLevel level, Container container) {
        if (points < POOL_MAX) {
            points = SolidFuelPool.refill(container, level.fuelValues(), points, POOL_MAX, TICKS_PER_POINT);
        }
        if (points <= 0) {
            pointTimer = 0;
            outCapacity = 0.0D;
            outFlowRate = 0.0D;
            return false;
        }
        outCapacity = CAPACITY;
        outFlowRate = FLOW_RATE;
        pointTimer++;
        if (pointTimer >= TICKS_PER_POINT) {
            pointTimer -= TICKS_PER_POINT;
            points--;
        }
        return true;
    }

    /** 液体模式：罐里是<strong>往复式内燃机燃料</strong>（LOW / MEDIUM / HIGH）就在跑；每个「每 mB 运行时长」过完扣 1 mB。 */
    private boolean liquidTick(ServerLevel level, TankBlockEntity tank) {
        FluidStack stack = tank.fluid();
        EngineFuels.Fuel fuel = stack.isEmpty() ? null : EngineFuels.of(stack.getFluid());
        if (!EngineFuels.pistonGrade(fuel)) {
            liquidTimer = 0;
            outCapacity = 0.0D;
            outFlowRate = 0.0D;
            return false;
        }
        outCapacity = fuel.capacity();
        outFlowRate = fuel.flowRate();
        liquidTimer++;
        if (liquidTimer >= fuel.ticksPerMb()) {
            liquidTimer -= fuel.ticksPerMb();
            ResourceHandler<FluidResource> tankView = tank.fluids();
            try (Transaction transaction = Transaction.openRoot()) {
                if (tankView.extract(0, FluidResource.of(stack.getFluid()), 1, transaction) > 0) {
                    transaction.commit();
                }
            }
        }
        return true;
    }

    /** 供渲染器取插值后的曲轴角。 */
    public float angle(float partialTick) {
        return Mth.rotLerp(partialTick, lastAngle, angle);
    }

    /** 是否正在工作（有动画）。 */
    public boolean isRunning() {
        return running;
    }

    /** 当前模式（Jade / 调试用）。 */
    public Mode mode() {
        return mode;
    }

    /** 固体模式的燃料池点数（Jade 用）。 */
    public int fuelPoints() {
        return points;
    }

    /** 剩余可运行时长（刻）：固体 = 点数 × {@value #TICKS_PER_POINT}；液体 = 罐内该流体量 × 每 mB 时长。 */
    public int remainingTicks() {
        if (mode == Mode.SOLID) {
            return points * TICKS_PER_POINT;
        }
        if (mode == Mode.LIQUID && level != null
                && level.getBlockEntity(worldPosition.below()) instanceof TankBlockEntity tank) {
            FluidStack stack = tank.fluid();
            EngineFuels.Fuel fuel = stack.isEmpty() ? null : EngineFuels.of(stack.getFluid());
            if (EngineFuels.pistonGrade(fuel)) {
                return stack.getAmount() * fuel.ticksPerMb();
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ ThermalGenerator

    /** 生成端容量：只在工作时供电（液体模式按流体查表）。 */
    @Override
    public double capacity() {
        return running ? outCapacity : 0.0D;
    }

    /** 生成端热流率：只在工作时供电（液体模式按流体查表）。 */
    @Override
    public double flowRate() {
        return running ? outFlowRate : 0.0D;
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("points", points);
        output.putBoolean("running", running);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        points = input.getIntOr("points", 0);
        running = input.getBooleanOr("running", false);
        pointTimer = 0;
        liquidTimer = 0;
    }

    /** 整份带去客户端：进世界 / 区块加载时同步「是否在跑」（供动画）。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) {
            return Math.min(current + maxStep, target);
        }
        return Math.max(current - maxStep, target);
    }
}
