package dev.znecby.stellar_evolution.multiblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 多方块控制器的<strong>区块索引</strong>：把控制器按图案包围盒覆盖的区块登记起来。
 *
 * <p>方块变化事件只按区块查控制器，避免全世界遍历；控制器自身再做一次包围盒判断。
 * 索引只在服务端存在；控制器在 {@code onLoad} 登记、{@code setRemoved} 注销。
 * 索引不存盘：世界加载后控制器按自己重新登记。
 */
public final class MultiblockManager {
    private static final Map<ResourceKey<Level>, MultiblockManager> MANAGERS = new HashMap<>();

    /** 控制器坐标 → 控制器实体（用于区块加载时直接通知）。 */
    private final Map<BlockPos, MultiblockControllerBlockEntity> controllers = new HashMap<>();
    /** 控制器坐标 → 它登记过的区块（注销时按这张表清）。 */
    private final Map<BlockPos, Set<ChunkPos>> controllerChunks = new HashMap<>();
    /** 区块 → 控制器坐标集合。 */
    private final Map<ChunkPos, Set<BlockPos>> chunks = new HashMap<>();

    private MultiblockManager() {}

    public static MultiblockManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level.dimension(), key -> new MultiblockManager());
    }

    public static void onBlockChanged(ServerLevel level, BlockPos pos) {
        get(level).notifyBlockChanged(level, pos);
    }

    /** 方块即将变化（破坏 / 放置事件触发时方块还没落位）。 */
    public static void onBlockWillChange(ServerLevel level, BlockPos pos) {
        get(level).notifyBlockWillChange(level, pos);
    }

    public static void onChunkLoad(ServerLevel level, ChunkPos chunkPos) {
        get(level).notifyChunkLoaded(level, chunkPos);
    }

    public static void clear(ServerLevel level) {
        MANAGERS.remove(level.dimension());
    }

    public static void clearAll() {
        MANAGERS.clear();
    }

    // ------------------------------------------------------------------ 登记 / 注销

    public void register(MultiblockControllerBlockEntity controller) {
        BlockPos pos = controller.getBlockPos().immutable();
        unregister(controller);

        controllers.put(pos, controller);
        Set<ChunkPos> covered = new HashSet<>(controller.boundsChunks());
        if (covered.isEmpty()) {
            covered.add(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        }
        controllerChunks.put(pos, covered);
        for (ChunkPos chunkPos : covered) {
            chunks.computeIfAbsent(chunkPos, key -> new HashSet<>()).add(pos);
        }
    }

    public void unregister(MultiblockControllerBlockEntity controller) {
        BlockPos pos = controller.getBlockPos();
        controllers.remove(pos);
        Set<ChunkPos> covered = controllerChunks.remove(pos);
        if (covered == null) {
            return;
        }
        for (ChunkPos chunkPos : covered) {
            Set<BlockPos> positions = chunks.get(chunkPos);
            if (positions != null) {
                positions.remove(pos);
                if (positions.isEmpty()) {
                    chunks.remove(chunkPos);
                }
            }
        }
    }

    private void unregisterAt(BlockPos pos) {
        controllers.remove(pos);
        Set<ChunkPos> covered = controllerChunks.remove(pos);
        if (covered == null) {
            return;
        }
        for (ChunkPos chunkPos : covered) {
            Set<BlockPos> positions = chunks.get(chunkPos);
            if (positions != null) {
                positions.remove(pos);
                if (positions.isEmpty()) {
                    chunks.remove(chunkPos);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 事件

    private void notifyBlockChanged(ServerLevel level, BlockPos changed) {
        forEachController(level, chunkOf(changed), controller -> controller.onExternalBlockChanged(changed));
    }

    private void notifyBlockWillChange(ServerLevel level, BlockPos changed) {
        forEachController(level, chunkOf(changed), controller -> controller.onExternalBlockWillChange(changed));
    }

    private void notifyChunkLoaded(ServerLevel level, ChunkPos chunkPos) {
        forEachController(level, chunkPos, MultiblockControllerBlockEntity::onChunkLoaded);
    }

    /** 遍历某区块登记的所有控制器；方块实体已经不在了就顺手清掉索引。 */
    private void forEachController(ServerLevel level, ChunkPos chunkPos,
                                   Consumer<MultiblockControllerBlockEntity> action) {
        Set<BlockPos> positions = chunks.get(chunkPos);
        if (positions == null || positions.isEmpty()) {
            return;
        }
        for (BlockPos controllerPos : new ArrayList<>(positions)) {
            BlockEntity blockEntity = level.getBlockEntity(controllerPos);
            if (blockEntity instanceof MultiblockControllerBlockEntity controller) {
                action.accept(controller);
            } else {
                unregisterAt(controllerPos);
            }
        }
    }

    private static ChunkPos chunkOf(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
