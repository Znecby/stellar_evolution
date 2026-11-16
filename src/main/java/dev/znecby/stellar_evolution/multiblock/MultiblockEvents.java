package dev.znecby.stellar_evolution.multiblock;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * 多方块结构失效 / 区块加载事件入口。
 *
 * <p>只监听<strong>方块破坏、方块放置、区块加载、世界卸载、服务器停止</strong>；
 * 不监听邻居更新（红石等高频事件），避免校验风暴。活塞 / 爆炸等非玩家改动暂不覆盖，
 * 需要时再补 {@code NeighborNotifyEvent} / {@code ExplosionEvent}。
 *
 * <p>破坏事件在方块真正移除前触发、放置事件在方块落位前触发，因此都走
 * {@link MultiblockManager#onBlockWillChange}：控制器延迟 2 tick 再读一次真实状态。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class MultiblockEvents {
    private MultiblockEvents() {}

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MultiblockManager.onBlockWillChange(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MultiblockManager.onBlockWillChange(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos chunkPos = event.getChunk().getPos();
            MultiblockManager.onChunkLoad(level, chunkPos);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MultiblockManager.clear(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MultiblockManager.clearAll();
    }
}
