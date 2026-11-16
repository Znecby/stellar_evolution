package dev.znecby.stellar_evolution.network;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 坍缩末影之眼<strong>右键回溯</strong>的第一人称动画触发（S2C）：
 * 用原版不死图腾那套「物品在眼前旋转」的屏幕特效，但显示的是<strong>坍缩末影之眼</strong>本身；
 * 粒子由服务端换成末影粒子（{@code ParticleTypes.PORTAL}）。
 *
 * <p>只发给使用的玩家；其他玩家看到的是末影粒子爆发（与原版图腾一样，物品旋转本来就只对本人可见）。
 */
public record CollapsedEyeAnimationPayload(ItemStack stack) implements CustomPacketPayload {
    public static final Type<CollapsedEyeAnimationPayload> TYPE =
            new Type<>(StellarEvolution.id("collapsed_eye_animation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CollapsedEyeAnimationPayload> STREAM_CODEC =
            StreamCodec.of(CollapsedEyeAnimationPayload::write, CollapsedEyeAnimationPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, CollapsedEyeAnimationPayload payload) {
        ItemStack.STREAM_CODEC.encode(buffer, payload.stack);
    }

    private static CollapsedEyeAnimationPayload read(RegistryFriendlyByteBuf buffer) {
        return new CollapsedEyeAnimationPayload(ItemStack.STREAM_CODEC.decode(buffer));
    }

    public static void handle(CollapsedEyeAnimationPayload payload, IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND) {
            return;
        }
        StellarEvolutionNetwork.playCollapsedEyeAnimation(payload);
    }
}
