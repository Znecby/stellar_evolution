package dev.modmind.stellar_evolution.massdriver;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.network.MassDriverDimensionPayload;
import dev.modmind.stellar_evolution.network.MassDriverPressurePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 质量投射轨道炮的两种<strong>蹲下右键</strong>交互：
 * <ul>
 *   <li><strong>剪刀</strong>右键控制器 → 打开「等压」输入界面（与热室同款；等压当前只存储）；</li>
 *   <li><strong>末影之眼</strong>右键控制器 → 打开维度选择界面，选中后把手里的一枚末影之眼
 *       转成「坍缩末影之眼」（扣对应势能）。</li>
 * </ul>
 * 未成型时这两种交互一律不处理，交还原版。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class MassDriverEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof MassDriverControllerBlock)) {
            return;
        }
        Player player = event.getEntity();
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        boolean shears = stack.is(Items.SHEARS);
        boolean enderEye = stack.is(Items.ENDER_EYE);
        if (!shears && !enderEye) {
            return;
        }
        // 两端都取消：客户端阻止末影之眼的原版行为，服务端负责发 payload
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof MassDriverControllerBlockEntity controller)
                || !controller.isFormed()) {
            return;
        }
        if (shears) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new MassDriverPressurePayload.Open(pos, controller.pressure()));
            level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        if (MassDriverEyes.count(player) > 0) {
            player.sendOverlayMessage(
                    Component.translatable("stellar_evolution.mass_driver.already_bound"));
            return;
        }
        PacketDistributor.sendToPlayer(serverPlayer,
                new MassDriverDimensionPayload.Open(pos, controller.potential(), dimensions(serverLevel)));
    }

    /** 可选维度：当前维度以外的所有已加载维度，按消耗从低到高（同消耗按 id 排序）。 */
    private static List<MassDriverDimensionPayload.Entry> dimensions(ServerLevel level) {
        List<MassDriverDimensionPayload.Entry> entries = new ArrayList<>();
        for (ServerLevel other : level.getServer().getAllLevels()) {
            if (other.dimension().equals(level.dimension())) {
                continue;
            }
            entries.add(new MassDriverDimensionPayload.Entry(
                    other.dimension().identifier(), MassDriverCosts.cost(other.dimension())));
        }
        entries.sort(Comparator.comparingInt(MassDriverDimensionPayload.Entry::cost)
                .thenComparing(entry -> entry.id().toString()));
        return entries;
    }

    private MassDriverEvents() {}
}
