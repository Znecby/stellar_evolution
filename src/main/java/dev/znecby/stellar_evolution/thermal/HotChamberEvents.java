package dev.znecby.stellar_evolution.thermal;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.network.HotChamberPressurePayload;
import net.minecraft.core.BlockPos;
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
 * 热室等压交互：<strong>蹲下 + 剪刀右键</strong>结构完整的热室 → 打开等压输入界面。
 * <p>普通剪刀右键（未蹲下）仍交给 {@code large.LargeMachineEvents} 做大机器物流的进出切换。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class HotChamberEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!event.getItemStack().is(Items.SHEARS)) {
            return;
        }
        Player player = event.getEntity();
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (HotChamber.stateAt(level, pos) != HotChamber.State.COMPLETE) {
            return;
        }
        BlockPos controller = HotChamber.controller(level, pos);
        if (controller == null) {
            return;
        }
        // 两端都取消：客户端阻止原版开箱界面，服务端负责发 payload
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int pressure = HotChamberPressureData.get(serverLevel).get(controller);
        PacketDistributor.sendToPlayer(serverPlayer,
                new HotChamberPressurePayload.Open(controller, pressure));
        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private HotChamberEvents() {}
}
