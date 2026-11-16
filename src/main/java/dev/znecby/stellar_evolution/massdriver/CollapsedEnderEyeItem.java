package dev.znecby.stellar_evolution.massdriver;

import dev.znecby.stellar_evolution.network.CollapsedEyeAnimationPayload;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「坍缩末影之眼」：保存了玩家、回溯点与目标维度的一次性回程票。
 *
 * <p><strong>右键使用</strong>：播放<strong>不死图腾同款的「物品在眼前旋转」屏幕动画</strong>，
 * 但动画里的物品换成坍缩末影之眼本身；世界粒子换成末影粒子（{@code ParticleTypes.PORTAL}），
 * 然后传送回保存的回溯点，提示「回溯成功」，并消耗 1 枚。
 *
 * <p><strong>注意</strong>：质量炮发射只要求背包里有这枚眼睛，发射本身<strong>不消耗</strong>它；
 * 消耗只发生在右键回溯时。
 */
public class CollapsedEnderEyeItem extends Item {
    public CollapsedEnderEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return activate(level, player, player.getItemInHand(hand));
    }

    /** 对空气右键走 {@link #use}，对方块右键走这里 —— 两条路径都触发回溯。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return activate(context.getLevel(), player, context.getItemInHand());
    }

    private InteractionResult activate(Level level, Player player, ItemStack stack) {
        CollapsedEyeData data = stack.get(MassDriverComponents.COLLAPSED_EYE.get());
        if (data == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ServerLevel target = serverLevel.getServer().getLevel(data.origin());
        if (target == null) {
            serverPlayer.sendOverlayMessage(
                    Component.translatable("item.stellar_evolution.collapsed_ender_eye.missing_dimension"));
            return InteractionResult.SUCCESS;
        }
        BlockPos point = data.point();
        double x = point.getX() + 0.5D;
        double y = point.getY();
        double z = point.getZ() + 0.5D;
        ItemStack animationStack = stack.copy();

        // 旧位置的末影粒子 → 传送 → 新位置的不死图腾同款屏幕动画（物品换成坍缩末影之眼）+ 末影粒子
        serverLevel.sendParticles(ParticleTypes.PORTAL, serverPlayer.getX(), serverPlayer.getY() + 1.0D,
                serverPlayer.getZ(), 60, 0.5D, 0.8D, 0.5D, 0.25D);
        serverPlayer.teleportTo(target, x, y, z, Set.of(), serverPlayer.getYRot(), serverPlayer.getXRot(), true);
        serverPlayer.resetFallDistance();
        target.playSound(null, x, y, z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        target.sendParticles(ParticleTypes.PORTAL, x, y + 1.0D, z, 80, 0.55D, 0.9D, 0.55D, 0.3D);
        PacketDistributor.sendToPlayer(serverPlayer, new CollapsedEyeAnimationPayload(animationStack));
        serverPlayer.sendOverlayMessage(
                Component.translatable("item.stellar_evolution.collapsed_ender_eye.backtracked"));
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        CollapsedEyeData data = stack.get(MassDriverComponents.COLLAPSED_EYE.get());
        if (data == null) {
            builder.accept(Component.translatable("item.stellar_evolution.collapsed_ender_eye.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        builder.accept(Component.translatable("item.stellar_evolution.collapsed_ender_eye.owner", data.ownerName())
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.stellar_evolution.collapsed_ender_eye.point",
                data.point().getX(), data.point().getY(), data.point().getZ()).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.stellar_evolution.collapsed_ender_eye.target",
                MassDriverCosts.displayName(data.target().identifier())).withStyle(ChatFormatting.DARK_PURPLE));
        builder.accept(Component.translatable("item.stellar_evolution.collapsed_ender_eye.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
