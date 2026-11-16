package dev.znecby.stellar_evolution.network;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.pipe.FlowLimiterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * <strong>限流管</strong>的数值输入往返（两条 payload）：
 * <ul>
 *   <li>{@link Open}（服务端 → 客户端）：空手右键时把「设定值 + 输入端网络当前热流率」送过去，
 *       客户端据此打开输入界面（{@code client.FlowLimiterScreen}）；</li>
 *   <li>{@link Set}（客户端 → 服务端）：玩家填完提交，服务端校验距离后写回设定值
 *       （{@code 0} = 跟随输入端）。</li>
 * </ul>
 * 两条都跑在主线程（见 {@code StellarEvolutionNetwork}）。
 */
public final class FlowLimitPayload {

    /** 服务端 → 客户端：打开输入界面。 */
    public record Open(BlockPos pos, int limit, double upstreamFlow) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(StellarEvolution.id("flow_limit_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.of(Open::write, Open::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Open payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeVarInt(payload.limit);
            buffer.writeDouble(payload.upstreamFlow);
        }

        private static Open read(RegistryFriendlyByteBuf buffer) {
            return new Open(buffer.readBlockPos(), buffer.readVarInt(), buffer.readDouble());
        }

        public static void handle(Open payload, IPayloadContext context) {
            if (context.flow() != PacketFlow.CLIENTBOUND) {
                return;
            }
            StellarEvolutionNetwork.openFlowLimiter(payload);
        }
    }

    /** 客户端 → 服务端：提交设定值（{@code 0} = 跟随输入端）。 */
    public record Set(BlockPos pos, int limit) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(StellarEvolution.id("flow_limit_set"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Set> STREAM_CODEC =
                StreamCodec.of(Set::write, Set::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Set payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeVarInt(payload.limit);
        }

        private static Set read(RegistryFriendlyByteBuf buffer) {
            return new Set(buffer.readBlockPos(), buffer.readVarInt());
        }

        public static void handle(Set payload, IPayloadContext context) {
            if (context.flow() != PacketFlow.SERVERBOUND) {
                return;
            }
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // 距离校验（同原版菜单的 8 格口径）：防远程改数值
            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof FlowLimiterBlockEntity limiter) {
                limiter.setLimit(payload.limit());
            }
        }
    }

    private FlowLimitPayload() {}
}
