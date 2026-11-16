package dev.modmind.stellar_evolution.tank;

import org.jspecify.annotations.Nullable;

/**
 * 储罐物品 ↔ 储罐方块 的<strong>纯逻辑</strong>（不碰世界 / 不碰方块实体，便于单测）。
 *
 * <p>口径：一次性批量转移 —— 倒：把物品里能倒的全倒进方块（装满即止，剩余留在物品）；
 * 取：把方块里能取的尽量取进物品（以物品单格容量为上限）。不做「一次一桶」的零碎转移。
 */
final class TankItemTransfer {
    /** 本次最多能移动多少：min(可用的量, 对方剩余空间)；负数/零一律 0。 */
    static int amountToMove(int available, int freeCapacity) {
        int usable = Math.max(0, available);
        int room = Math.max(0, freeCapacity);
        return Math.min(usable, room);
    }

    /**
     * 流体类型校验：目标罐为空 / 无类型时接受任意流体；否则要求注册名完全一致。
     *
     * @param tankFluidId 目标罐里流体的注册名（空罐传 {@code null}）
     * @param heldFluidId 待倒入流体的注册名
     */
    static boolean typeMatches(@Nullable String tankFluidId, String heldFluidId) {
        return tankFluidId == null || tankFluidId.equals(heldFluidId);
    }

    private TankItemTransfer() {}
}
