package dev.znecby.stellar_evolution.network;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 质量炮发射 / 抵达的<strong>屏幕震动 + FOV 冲击</strong>（S2C）：
 * 服务端按距离推给附近玩家；客户端在 {@code client.MassDriverCameraEffects} 里叠加视角抖动。
 *
 * @param ticks     持续刻数
 * @param intensity 强度（角度振幅与 FOV 增量都按它缩放）
 */
public record MassDriverShakePayload(int ticks, float intensity) implements CustomPacketPayload {
    public static final Type<MassDriverShakePayload> TYPE =
            new Type<>(StellarEvolution.id("mass_driver_shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MassDriverShakePayload> STREAM_CODEC =
            StreamCodec.of(MassDriverShakePayload::write, MassDriverShakePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, MassDriverShakePayload payload) {
        buffer.writeVarInt(payload.ticks);
        buffer.writeFloat(payload.intensity);
    }

    private static MassDriverShakePayload read(RegistryFriendlyByteBuf buffer) {
        return new MassDriverShakePayload(buffer.readVarInt(), buffer.readFloat());
    }

    public static void handle(MassDriverShakePayload payload, IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND) {
            return;
        }
        StellarEvolutionNetwork.shakeMassDriverScreen(payload);
    }
}
