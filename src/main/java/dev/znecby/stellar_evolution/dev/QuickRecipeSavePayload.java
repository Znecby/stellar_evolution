package dev.znecby.stellar_evolution.dev;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 开发用：客户端点击“保存”后，请求服务端把当前 3×3 输入 + 输出槽写成 docs 文本。 */
public record QuickRecipeSavePayload(int containerId) implements CustomPacketPayload {
    public static final Type<QuickRecipeSavePayload> TYPE =
            new Type<>(StellarEvolution.id("quick_recipe_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickRecipeSavePayload> STREAM_CODEC =
            StreamCodec.of(QuickRecipeSavePayload::write, QuickRecipeSavePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, QuickRecipeSavePayload payload) {
        buffer.writeVarInt(payload.containerId);
    }

    private static QuickRecipeSavePayload read(RegistryFriendlyByteBuf buffer) {
        return new QuickRecipeSavePayload(buffer.readVarInt());
    }

    public static void handle(QuickRecipeSavePayload payload, IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND) {
            return;
        }
        if (!DevConfig.developerMode()) {
            return;
        }
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof QuickRecipeMenu menu)) {
            return;
        }
        if (menu.containerId != payload.containerId) {
            return;
        }
        QuickRecipeWriter.save(player, menu);
    }
}
