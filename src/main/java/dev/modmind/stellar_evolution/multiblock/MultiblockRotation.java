package dev.modmind.stellar_evolution.multiblock;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * 固定图案的旋转 / 包围盒工具。
 *
 * <p>图案按<strong>正面朝北</strong>定义：局部 +X 是面朝正面时的右手边，局部 +Z 是“往机器内部”。
 * 控制器方块的 {@code FACING} 表示正面朝向；世界坐标 = 控制器 + X×右 + Y×上 + Z×后。
 *
 * <p>只支持 4 个水平朝向，不做上下翻转。
 */
public final class MultiblockRotation {
    private MultiblockRotation() {}

    /** 局部坐标 → 世界坐标。{@code controller} 是控制器方块自身的坐标。 */
    public static BlockPos localToWorld(BlockPos controller, Direction facing, BlockPos local) {
        Direction right = facing.getClockWise();
        Direction back = facing.getOpposite();
        int dx = right.getStepX() * local.getX() + back.getStepX() * local.getZ();
        int dz = right.getStepZ() * local.getX() + back.getStepZ() * local.getZ();
        return controller.offset(dx, local.getY(), dz);
    }

    /** 局部偏移 → 世界坐标（2×2×2 机器占格 / 端口用）。 */
    public static BlockPos offset(BlockPos controller, Direction facing, int dx, int dy, int dz) {
        return localToWorld(controller, facing, new BlockPos(dx, dy, dz));
    }

    /** 朝向 → blockstate / PoseStack 的 y 旋转角。 */
    public static float yaw(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    /** 世界坐标是否落在图案的局部包围盒内（只做 AABB，不逐格查）。 */
    public static boolean boundsContains(BlockPos controller, Direction facing,
                                         int minX, int minY, int minZ,
                                         int maxX, int maxY, int maxZ,
                                         BlockPos world) {
        int minWorldX = Integer.MAX_VALUE;
        int maxWorldX = Integer.MIN_VALUE;
        int minWorldZ = Integer.MAX_VALUE;
        int maxWorldZ = Integer.MIN_VALUE;
        for (int x : new int[] { minX, maxX }) {
            for (int z : new int[] { minZ, maxZ }) {
                BlockPos corner = localToWorld(controller, facing, new BlockPos(x, minY, z));
                minWorldX = Math.min(minWorldX, corner.getX());
                maxWorldX = Math.max(maxWorldX, corner.getX());
                minWorldZ = Math.min(minWorldZ, corner.getZ());
                maxWorldZ = Math.max(maxWorldZ, corner.getZ());
            }
        }
        int minWorldY = controller.getY() + minY;
        int maxWorldY = controller.getY() + maxY;
        return world.getX() >= minWorldX && world.getX() <= maxWorldX
                && world.getY() >= minWorldY && world.getY() <= maxWorldY
                && world.getZ() >= minWorldZ && world.getZ() <= maxWorldZ;
    }

    /** 图案包围盒覆盖的区块坐标范围：{@code [minChunkX, minChunkZ, maxChunkX, maxChunkZ]}。 */
    public static int[] chunkRange(BlockPos controller, Direction facing,
                                   int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ) {
        int minWorldX = Integer.MAX_VALUE;
        int maxWorldX = Integer.MIN_VALUE;
        int minWorldZ = Integer.MAX_VALUE;
        int maxWorldZ = Integer.MIN_VALUE;
        for (int x : new int[] { minX, maxX }) {
            for (int z : new int[] { minZ, maxZ }) {
                BlockPos corner = localToWorld(controller, facing, new BlockPos(x, minY, z));
                minWorldX = Math.min(minWorldX, corner.getX());
                maxWorldX = Math.max(maxWorldX, corner.getX());
                minWorldZ = Math.min(minWorldZ, corner.getZ());
                maxWorldZ = Math.max(maxWorldZ, corner.getZ());
            }
        }
        return new int[] {
                Math.floorDiv(minWorldX, 16), Math.floorDiv(minWorldZ, 16),
                Math.floorDiv(maxWorldX, 16), Math.floorDiv(maxWorldZ, 16)
        };
    }

    /** 图案包围盒覆盖的区块（用于按区块索引控制器）。 */
    public static Set<ChunkPos> boundsChunks(BlockPos controller, Direction facing,
                                             int minX, int minY, int minZ,
                                             int maxX, int maxY, int maxZ) {
        int[] range = chunkRange(controller, facing, minX, minY, minZ, maxX, maxY, maxZ);
        Set<ChunkPos> chunks = new HashSet<>();
        for (int cx = range[0]; cx <= range[2]; cx++) {
            for (int cz = range[1]; cz <= range[3]; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }
}
