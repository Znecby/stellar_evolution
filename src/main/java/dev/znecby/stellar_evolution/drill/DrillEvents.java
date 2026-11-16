package dev.znecby.stellar_evolution.drill;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.network.DrillRangePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 开采钻的剪刀交互（照 {@code thermal.HotChamberEvents} 的成功口径走
 * {@link PlayerInteractEvent.RightClickBlock}，而不是方块自己的 {@code useItemOn}）：
 * <ul>
 *   <li><strong>剪刀右键</strong>（不蹲下）＝切换模式，并从机器下方一格重新开始；</li>
 *   <li><strong>蹲下 + 剪刀右键</strong>＝打开范围输入界面。</li>
 * </ul>
 * 两端都取消事件：客户端阻止原版交互，服务端负责改状态 / 发 {@link DrillRangePayload.Open}。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class DrillEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !event.getItemStack().is(Items.SHEARS)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof DrillBlock)) {
            return;
        }
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        // 两端都取消：客户端阻止原版交互 / 方块放置，服务端负责真正的动作
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverLevel.getBlockEntity(pos) instanceof DrillBlockEntity drill)) {
            return;
        }
        if (drill.isBedrockMode()) {
            // 基岩模式是只读状态：不能切模式、不能改范围
            player.sendOverlayMessage(Component.translatable("stellar_evolution.drill.mode.bedrock"));
            serverLevel.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        if (player.isShiftKeyDown()) {
            // 蹲下：把当前模式 / 范围 / 上下限送到客户端，打开文本输入界面
            DrillBlockEntity.Mode mode = drill.mode();
            PacketDistributor.sendToPlayer(serverPlayer, new DrillRangePayload.Open(pos.immutable(),
                    mode.ordinal(), drill.rangeFor(mode),
                    DrillBlockEntity.minFor(mode), DrillBlockEntity.maxFor(mode)));
        } else {
            // 普通右键：切换模式（同时从机器下方重新开始）
            drill.toggleMode();
            DrillBlockEntity.Mode mode = drill.mode();
            Component modeName = Component.translatable(mode == DrillBlockEntity.Mode.AREA
                    ? "stellar_evolution.drill.mode.area" : "stellar_evolution.drill.mode.ore");
            int range = drill.rangeFor(mode);
            Component rangeText = Component.translatable(mode == DrillBlockEntity.Mode.AREA
                    ? "stellar_evolution.drill.range.area" : "stellar_evolution.drill.range.ore", range, range);
            player.sendOverlayMessage(Component.translatable("stellar_evolution.drill.mode.set", modeName, rangeText));
        }
        serverLevel.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private DrillEvents() {}
}
