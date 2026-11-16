package dev.znecby.stellar_evolution.network;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.massdriver.CollapsedEyeData;
import dev.znecby.stellar_evolution.massdriver.MassDriverControllerBlockEntity;
import dev.znecby.stellar_evolution.massdriver.MassDriverCosts;
import dev.znecby.stellar_evolution.massdriver.MassDriverEyes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 质量投射轨道炮<strong>维度选择</strong>的往返：
 * <ul>
 *   <li>{@link Open}（S2C）：蹲下 + 末影之眼右键控制器时，把可选维度、各自消耗与当前势能送到客户端；</li>
 *   <li>{@link Select}（C2S）：玩家选中一个维度后，扣势能把那枚末影之眼转成「坍缩末影之眼」。</li>
 * </ul>
 */
public final class MassDriverDimensionPayload {

    /** 一个可选维度 + 消耗的势能。 */
    public record Entry(Identifier id, int cost) {}

    /** 服务端 → 客户端：打开维度选择界面。 */
    public record Open(BlockPos pos, double potential, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(StellarEvolution.id("mass_driver_dimension_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.of(Open::write, Open::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Open payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeDouble(payload.potential);
            buffer.writeVarInt(payload.entries.size());
            for (Entry entry : payload.entries) {
                buffer.writeIdentifier(entry.id());
                buffer.writeVarInt(entry.cost());
            }
        }

        private static Open read(RegistryFriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            double potential = buffer.readDouble();
            int size = buffer.readVarInt();
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new Entry(buffer.readIdentifier(), buffer.readVarInt()));
            }
            return new Open(pos, potential, entries);
        }

        public static void handle(Open payload, IPayloadContext context) {
            if (context.flow() != PacketFlow.CLIENTBOUND) {
                return;
            }
            StellarEvolutionNetwork.openMassDriverDimension(payload);
        }
    }

    /** 客户端 → 服务端：选中目标维度。 */
    public record Select(BlockPos pos, Identifier dimension) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(StellarEvolution.id("mass_driver_dimension_select"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Select> STREAM_CODEC =
                StreamCodec.of(Select::write, Select::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buffer, Select payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeIdentifier(payload.dimension);
        }

        private static Select read(RegistryFriendlyByteBuf buffer) {
            return new Select(buffer.readBlockPos(), buffer.readIdentifier());
        }

        public static void handle(Select payload, IPayloadContext context) {
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
            if (!(level.getBlockEntity(payload.pos()) instanceof MassDriverControllerBlockEntity controller)
                    || !controller.isFormed()) {
                return;
            }
            ResourceKey<Level> target = ResourceKey.create(Registries.DIMENSION, payload.dimension());
            if (target.equals(level.dimension())) {
                player.sendOverlayMessage(
                        Component.translatable("stellar_evolution.mass_driver.target_same_dimension"));
                return;
            }
            if (level.getServer().getLevel(target) == null) {
                player.sendOverlayMessage(
                        Component.translatable("stellar_evolution.mass_driver.target_missing"));
                return;
            }
            if (MassDriverEyes.count(player) > 0) {
                player.sendOverlayMessage(
                        Component.translatable("stellar_evolution.mass_driver.already_bound"));
                return;
            }
            int slot = MassDriverEyes.findEnderEyeSlot(player);
            if (slot < 0) {
                player.sendOverlayMessage(
                        Component.translatable("stellar_evolution.mass_driver.no_ender_eye"));
                return;
            }
            int cost = MassDriverCosts.cost(target);
            if (!controller.spendPotential(cost)) {
                player.sendOverlayMessage(
                        Component.translatable("stellar_evolution.mass_driver.not_enough_potential", cost));
                return;
            }
            CollapsedEyeData data = new CollapsedEyeData(
                    player.getName().getString(),
                    player.getUUID().toString(),
                    player.blockPosition().immutable(),
                    level.dimension(),
                    target);
            MassDriverEyes.convert(player, slot, data);
            player.sendOverlayMessage(Component.translatable("stellar_evolution.mass_driver.bound",
                    MassDriverCosts.displayName(payload.dimension()),
                    (int) Math.floor(controller.potential())));
        }
    }

    private MassDriverDimensionPayload() {}
}
