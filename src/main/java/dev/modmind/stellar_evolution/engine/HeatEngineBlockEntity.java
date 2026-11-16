package dev.modmind.stellar_evolution.engine;

import dev.modmind.stellar_evolution.thermal.ThermalGenerator;
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
import org.jetbrains.annotations.Nullable;

/**
 * 热力引擎方块实体：维护一个「转子角度」，同时是热压差网络的<strong>生成端</strong>。
 *
 * <p><strong>只走燃烧引擎的固体模式</strong>（旧的「正下方有工作中的熔炉 / 烟熏炉 / 高炉就转」判据已删除）：
 * 从<strong>正下方物品容器</strong>里取任意熔炉燃料，按原版燃烧时长折算点数入池
 * （{@code 燃烧时长 / 20}，例：1 煤炭 = 80 点）；有点数就在跑，出力固定
 * {@value #CAPACITY} DP / {@value #FLOW_RATE} HP·t⁻¹。
 *
 * <p><strong>与燃烧引擎的差别</strong>：燃料池单位一致（但上限砍半，{@value #POOL_MAX} 点），
 * <strong>每 {@value #TICKS_PER_POINT} 刻扣 1 点</strong>（燃烧引擎是 20 刻）—— 同样一份燃料只能烧
 * <strong>一半时长</strong>，即燃料消耗速度 2 倍；出力 {@value #CAPACITY} DP / {@value #FLOW_RATE} HP·t⁻¹。
 * 取料只在「整份燃料都装得下」时进行，不会因为缺 1 点就烧掉一整块燃料（见 {@link SolidFuelPool}）。
 *
 * <p>角度<strong>不存盘、不联机同步</strong>：它只喂给客户端的渲染器做外观动画，重进世界从静止位开始即可；
 * 但「是否在跑」会存盘并同步（{@link #getUpdateTag}），客户端据此决定转子转不转。
 */
public class HeatEngineBlockEntity extends BlockEntity implements ThermalGenerator {
    /** 工作时的差压容量（DP），进网络累加。 */
    public static final double CAPACITY = 1024.0D;
    /** 工作时提供的热流率（HP/t），进网络取最高值。 */
    public static final double FLOW_RATE = 128.0D;

    /** 燃料池容量（点）：燃烧引擎的一半（{@code 800}）；1 点 = 20t 燃烧时长，与燃烧引擎同一套池单位。 */
    public static final int POOL_MAX = 800;
    /** 多少刻消耗 1 点：燃烧引擎为 20，这里 10 = 燃料消耗速度 2 倍。 */
    public static final int TICKS_PER_POINT = 10;
    /** 点火数的折算分母（1 点 = 20t 燃烧时长，与燃烧引擎一致）。 */
    private static final int BURN_TICKS_PER_POINT = 20;

    /** 静止位：铁桨叶相对铜盘错开 45°。 */
    public static final float REST_ANGLE = 45.0F;
    /** 工作时每 tick 转过的角度。 */
    private static final float SPIN_SPEED = 4.0F;
    /** 停热后回落速度（度/tick）。 */
    private static final float RETURN_SPEED = 4.0F;

    /** 角度不取模，便于线性插值（渲染时任何角度都合法）。 */
    private float angle = REST_ANGLE;
    private float lastAngle = REST_ANGLE;

    /** 固体模式：燃料池点数 0..{@value #POOL_MAX}。 */
    private int points;
    /** 固体模式：距下一次扣点的刻数。 */
    private int pointTimer;
    /** 当前是否在跑（服务端算 + 同步给客户端供动画）。 */
    private boolean running;

    public HeatEngineBlockEntity(BlockPos pos, BlockState state) {
        super(EngineBlocks.HEAT_ENGINE_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HeatEngineBlockEntity engine) {
        engine.lastAngle = engine.angle;

        if (level instanceof ServerLevel serverLevel) {
            boolean wasRunning = engine.running;
            engine.running = engine.serverTick(serverLevel, pos);
            if (wasRunning != engine.running) {
                engine.setChanged();
                // 把「是否在跑」同步给客户端 —— 客户端据此决定转子要不要转（只在变化时发包）
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }

        if (engine.running) {
            engine.angle += SPIN_SPEED;
        } else {
            // 回落到「离当前最近的 45° 位置」，避免绕远路
            float target = REST_ANGLE + 360.0F * Math.round((engine.angle - REST_ANGLE) / 360.0F);
            engine.angle = approach(engine.angle, target, RETURN_SPEED);
        }
    }

    /** 供渲染器按帧插值取角度。 */
    public float angle(float partialTick) {
        return Mth.rotLerp(partialTick, this.lastAngle, this.angle);
    }

    // ------------------------------------------------------------------ 服务端：固体模式结算

    /** 池未满就从下方容器取 1 个熔炉燃料折算点数；有点数就在跑，每 {@value #TICKS_PER_POINT} 刻扣 1 点。 */
    private boolean serverTick(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos.below()) instanceof Container container)) {
            pointTimer = 0;
            return false;
        }
        if (points < POOL_MAX) {
            points = SolidFuelPool.refill(container, level.fuelValues(), points, POOL_MAX, BURN_TICKS_PER_POINT);
        }
        if (points <= 0) {
            pointTimer = 0;
            return false;
        }
        pointTimer++;
        if (pointTimer >= TICKS_PER_POINT) {
            pointTimer -= TICKS_PER_POINT;
            points--;
        }
        return true;
    }

    /** 是否正在工作（有动画）。 */
    public boolean isRunning() {
        return running;
    }

    /** 剩余可运行时长（刻）＝ 点数 × {@value #TICKS_PER_POINT}（Jade 燃料条用）。 */
    public int remainingTicks() {
        return points * TICKS_PER_POINT;
    }

    // ------------------------------------------------------------------ ThermalGenerator

    /** 生成端容量：只在工作时供电。 */
    @Override
    public double capacity() {
        return running ? CAPACITY : 0.0D;
    }

    /** 生成端热流率：只在工作时供电。 */
    @Override
    public double flowRate() {
        return running ? FLOW_RATE : 0.0D;
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
        float diff = target - current;
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return current + Math.signum(diff) * maxStep;
    }
}
