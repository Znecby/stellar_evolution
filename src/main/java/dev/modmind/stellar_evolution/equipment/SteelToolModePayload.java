package dev.modmind.stellar_evolution.equipment;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 钢制工具<strong>功能配置</strong>的客户端 → 服务端提交包。
 *
 * <p>服务端校验主手确实是带功能的钢制工具后写入位掩码、同步时运 / 精准附魔，
 * 播放原版「叮」并回一条动作栏提示。
 */
public final class SteelToolModePayload {

    /** 提交功能位掩码。 */
    public record Set(int features) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(StellarEvolution.id("steel_tool_mode_set"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Set> STREAM_CODEC =
                StreamCodec.of(Set::write, Set::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Set payload) {
            buffer.writeVarInt(payload.features);
        }

        private static Set read(RegistryFriendlyByteBuf buffer) {
            return new Set(buffer.readVarInt());
        }

        public static void handle(Set payload, IPayloadContext context) {
            if (context.flow() != PacketFlow.SERVERBOUND) {
                return;
            }
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof ModeTool tool)) {
                return;
            }
            int features = SteelToolFeature.sanitize(payload.features(), tool.kind());
            tool.setFeatures(stack, features);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
            player.sendOverlayMessage(Component.translatable(
                    "stellar_evolution.steel_tool.mode.set",
                    SteelToolFeature.names(features, tool.kind())));
        }
    }

    private SteelToolModePayload() {}
}
