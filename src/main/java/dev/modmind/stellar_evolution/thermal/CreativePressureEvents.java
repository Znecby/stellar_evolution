package dev.modmind.stellar_evolution.thermal;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 创造差压的<strong>左键</strong>交互：空手左键 -64 HP/t（蹲下 1024），并把这次破坏拦下来。
 *
 * <p>为什么走 NeoForge 事件而不是 {@code Block.attack}：{@code attack} 只在生存挖掘路径上被调，
 * <strong>创造模式左键是直接破坏方块</strong>，根本走不到；而且就算走到了，方块也已经被拆了。
 * 这里用 {@code LeftClickBlock} 既能在创造 / 生存都生效，又能 {@code setCanceled} 阻止真正的破坏。
 *
 * <p>手里<strong>拿着东西</strong>时左键放行 —— 保留一个「正常挖掉它」的口子。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class CreativePressureEvents {
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof CreativePressureBlock)) {
            return;
        }
        if (!event.getItemStack().isEmpty()) {
            return;   // 手里有物品：照常挖掘 / 破坏
        }
        event.setCanceled(true);   // 空手左键只调值，不破坏方块
        CreativePressureBlock.adjust(level, event.getPos(), event.getEntity(), -1L);
    }

    private CreativePressureEvents() {}
}
