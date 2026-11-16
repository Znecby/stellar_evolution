package dev.modmind.stellar_evolution.pipe;

import dev.modmind.stellar_evolution.thermal.ThermalGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * <strong>限流管</strong>的方块实体：热压差网络里的<strong>闸门</strong>（{@link ThermalGate}）——
 * 只记一个「下游热流率上限」，别的什么都不做。
 *
 * <p><strong>它不切网、不占 DP、不需要 tick</strong>：网络仍是一张（容量 / 占用 / 超载全网算一次），
 * 闸门只是在 {@code ThermalNetwork} 的 BFS 里，让「从 {@link #outputFace()} 走出去」的那一段网络
 * 看到被 {@link #flowLimit()} 压低的热流率 —— 这就是「同一个网络、不同的地方热流率不同」。
 *
 * <p>设定值：{@code 0} = <strong>不限</strong>（等价于一根普通铜管）；正数 = 下游热流率上限（HP/t）。
 * 由空手右键的输入界面改写（见 {@code network.FlowLimitPayload}）。
 */
public class FlowLimiterBlockEntity extends BlockEntity implements ThermalGate {
    /** 下游热流率上限（HP/t）：{@code 0} = 不限。 */
    private int limit;

    public FlowLimiterBlockEntity(BlockPos pos, BlockState state) {
        super(PipeBlocks.FLOW_LIMITER_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ ThermalGate

    @Override
    public Direction outputFace() {
        return getBlockState().getValue(FlowLimiterBlock.FACING);
    }

    /** 下游热流率上限：{@code 0} = 不限（闸门只封顶、不放大）。 */
    @Override
    public double flowLimit() {
        return limit;
    }

    // ------------------------------------------------------------------ 设定值

    /** 当前设定值（0 = 不限）。 */
    public int limit() {
        return limit;
    }

    /** 改设定值（越界钳到 0..1_000_000）。 */
    public void setLimit(int value) {
        int clamped = Math.max(0, Math.min(1_000_000, value));
        if (clamped == limit) {
            return;
        }
        limit = clamped;
        setChanged();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("limit", limit);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        limit = input.getIntOr("limit", 0);
    }

    /** 设定值要带到客户端（界面 / 提示都可能读它）。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
