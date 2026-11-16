package dev.znecby.stellar_evolution.network;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.drill.DrillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * <strong>开采钻</strong>的范围输入往返（照限流管 / 热室等压那套）：
 * <ul>
 *   <li>{@link Open}（服务端 → 客户端）：蹲下 + 剪刀右键时把「当前模式 / 范围 / 上下限」送过去，
 *       客户端据此打开 {@code client.DrillRangeScreen}；</li>
 *   <li>{@link Set}（客户端 → 服务端）：玩家提交后写回方块实体（值变了才从机器下方重新开始）。</li>
 * </ul>
 * 两条都跑在主线程（见 {@code StellarEvolutionNetwork}）。
 */
public final class DrillRangePayload {

    /** 服务端 → 客户端：打开范围输入界面。 */
    public record Open(BlockPos pos, int mode, int value, int min, int max) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(StellarEvolution.id("drill_range_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.of(Open::write, Open::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Open payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeVarInt(payload.mode);
            buffer.writeVarInt(payload.value);
            buffer.writeVarInt(payload.min);
            buffer.writeVarInt(payload.max);
        }

        private static Open read(RegistryFriendlyByteBuf buffer) {
            return new Open(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }

        public static void handle(Open payload, IPayloadContext context) {
            if (context.flow() != PacketFlow.CLIENTBOUND) {
                return;
            }
            StellarEvolutionNetwork.openDrillRange(payload);
        }
    }

    /** 客户端 → 服务端：提交范围值（服务端会再做一次奇数化 / 夹取）。 */
    public record Set(BlockPos pos, int mode, int value) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(StellarEvolution.id("drill_range_set"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Set> STREAM_CODEC =
                StreamCodec.of(Set::write, Set::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Set payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeVarInt(payload.mode);
            buffer.writeVarInt(payload.value);
        }

        private static Set read(RegistryFriendlyByteBuf buffer) {
            return new Set(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
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
            if (!(player.level().getBlockEntity(payload.pos()) instanceof DrillBlockEntity drill)) {
                return;
            }
            DrillBlockEntity.Mode mode = payload.mode() == DrillBlockEntity.Mode.ORE.ordinal()
                    ? DrillBlockEntity.Mode.ORE : DrillBlockEntity.Mode.AREA;
            drill.setRange(mode, payload.value());
        }
    }

    private DrillRangePayload() {}
}
