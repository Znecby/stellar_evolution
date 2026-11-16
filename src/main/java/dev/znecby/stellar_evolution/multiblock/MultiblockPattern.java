package dev.znecby.stellar_evolution.multiblock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * 不可变的固定多方块图案。
 *
 * <p>局部坐标以<strong>控制器为原点</strong>：控制器在 (0,0,0)，图案可以伸向负坐标。
 * 单元格、固定端口、包围盒在构造时一次算好；校验时只读。
 *
 * <p>图案由 {@link MultiblockPatternBuilder} 生成；具体机器在
 * {@link MultiblockControllerBlockEntity#createPattern()} 里返回自己的图案。
 */
public final class MultiblockPattern {
    private final String id;
    private final Map<BlockPos, MultiblockCell> cells;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    MultiblockPattern(String id, Map<BlockPos, MultiblockCell> cells) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("多方块图案不能为空: " + id);
        }
        this.id = id;
        this.cells = Collections.unmodifiableMap(new LinkedHashMap<>(cells));

        int loX = Integer.MAX_VALUE;
        int loY = Integer.MAX_VALUE;
        int loZ = Integer.MAX_VALUE;
        int hiX = Integer.MIN_VALUE;
        int hiY = Integer.MIN_VALUE;
        int hiZ = Integer.MIN_VALUE;
        for (BlockPos pos : cells.keySet()) {
            loX = Math.min(loX, pos.getX());
            loY = Math.min(loY, pos.getY());
            loZ = Math.min(loZ, pos.getZ());
            hiX = Math.max(hiX, pos.getX());
            hiY = Math.max(hiY, pos.getY());
            hiZ = Math.max(hiZ, pos.getZ());
        }
        this.minX = loX;
        this.minY = loY;
        this.minZ = loZ;
        this.maxX = hiX;
        this.maxY = hiY;
        this.maxZ = hiZ;
    }

    public String id() {
        return id;
    }

    /** 局部坐标 → 期望格；只读。 */
    public Map<BlockPos, MultiblockCell> cells() {
        return cells;
    }

    @Nullable
    public MultiblockCell cellAt(BlockPos local) {
        return cells.get(local);
    }

    public boolean containsLocal(BlockPos local) {
        return cells.containsKey(local);
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    /** 局部坐标 → 世界坐标。 */
    public BlockPos toWorld(BlockPos controller, Direction facing, BlockPos local) {
        return MultiblockRotation.localToWorld(controller, facing, local);
    }

    /** 世界坐标是否在图案包围盒内。 */
    public boolean boundsContains(BlockPos controller, Direction facing, BlockPos world) {
        return MultiblockRotation.boundsContains(controller, facing, minX, minY, minZ, maxX, maxY, maxZ, world);
    }

    /** 图案包围盒覆盖的区块（控制器按这些区块注册到索引）。 */
    public Set<ChunkPos> boundsChunks(BlockPos controller, Direction facing) {
        return MultiblockRotation.boundsChunks(controller, facing, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
