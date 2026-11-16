package dev.modmind.stellar_evolution.large;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.tank.TankBlock;
import dev.modmind.stellar_evolution.thermal.CopperHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 大机器物流的交互：<strong>手持剪刀右键物流容器（铜箱子 / 储罐）</strong>即切换它的
 * <strong>输入 / 输出</strong>方向（见 {@link LargeMachineIo}）。
 *
 * <p>只在「该容器确实接在某台大型机器的物流网络上」时才拦截这次右键 —— 孤立的铜箱子 / 储罐
 * 照原版行为走（开箱 / 倒流体），不受影响。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class LargeMachineEvents {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) {
            return;   // 已被热室等压交互拦截
        }
        Level level = event.getLevel();
        if (level.isClientSide() || !event.getItemStack().is(Items.SHEARS)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean container = state.is(CopperHeat.COPPER_CHESTS)
                || state.getBlock() instanceof TankBlock
                || LargeMachines.isItemContainer(level, pos);
        if (!container) {
            return;
        }
        LargeMachine machine = LargeMachines.containerMachine(level, pos);
        if (machine == null) {
            return;   // 没接在大机器上：不拦截，交还原版
        }
        // 合并大罐的输入 / 输出按「控制器那一格」记账 —— 部件格右键也改到同一个锚点上
        BlockPos anchor = LargeMachines.containerAnchor(level, pos);
        LargeMachineIo next = machine.ioMode(anchor).toggle();
        machine.setIoMode(anchor, next);

        Player player = event.getEntity();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("stellar_evolution.large.io.set",
                    Component.translatable(next == LargeMachineIo.INPUT
                            ? "stellar_evolution.large.io.input"
                            : "stellar_evolution.large.io.output")));
        }
        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private LargeMachineEvents() {}
}
