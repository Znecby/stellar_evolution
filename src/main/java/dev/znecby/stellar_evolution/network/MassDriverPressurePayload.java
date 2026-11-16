package dev.znecby.stellar_evolution.network;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.massdriver.MassDriverControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 质量投射轨道炮<strong>等压值</strong>的输入往返（与热室同款交互）：
 * <ul>
 *   <li>{@link Open}（S2C）：蹲下 + 剪刀右键控制器时把当前值送到客户端；</li>
 *   <li>{@link Set}（C2S）：玩家提交后写回控制器的方块实体。</li>
 * </ul>
 * <p>等压值当前<strong>只存储、不参与结算</strong>（与需求一致，留给后续机制接入）。
 */
public final class MassDriverPressurePayload {

    /** 服务端 → 客户端：打开等压输入界面。 */
    public record Open(BlockPos pos, int pressure) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(StellarEvolution.id("mass_driver_pressure_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.of(Open::write, Open::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Open payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeVarInt(payload.pressure);
        }

        private static Open read(RegistryFriendlyByteBuf buffer) {
            return new Open(buffer.readBlockPos(), buffer.readVarInt());
        }

        public static void handle(Open payload, IPayloadContext context) {
            if (context.flow() != PacketFlow.CLIENTBOUND) {
                return;
            }
            StellarEvolutionNetwork.openMassDriverPressure(payload);
        }
    }

    /** 客户端 → 服务端：提交等压值。 */
    public record Set(BlockPos pos, int pressure) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(StellarEvolution.id("mass_driver_pressure_set"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Set> STREAM_CODEC =
                StreamCodec.of(Set::write, Set::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Set payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeVarInt(payload.pressure);
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
            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (level.getBlockEntity(payload.pos()) instanceof MassDriverControllerBlockEntity controller
                    && controller.isFormed()) {
                controller.setPressure(payload.pressure());
            }
        }
    }

    private MassDriverPressurePayload() {}
}
