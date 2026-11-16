package dev.modmind.stellar_evolution.engine;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;

/**
 * 引擎固体燃料池的公共取料规则：<strong>只在「整份燃料都装得下」时才消耗</strong>。
 *
 * <p>旧逻辑是「池里只要缺 1 点就取一份燃料」，于是 799/800 时也会烧掉一整块煤，
 * 多出来的点数被上限截断 —— 极大浪费。现在改为：
 * <ul>
 *   <li>池里有剩余空间时，只接受<strong>完全装得下</strong>的燃料；装不下就等池子先烧掉一些；</li>
 *   <li>池空时允许单份燃料超过池上限（例如熔岩桶 1000 点 &gt; 800），避免大燃料被截断。</li>
 * </ul>
 */
final class SolidFuelPool {
    /**
     * 从容器里取一份燃料并入池。
     *
     * @param points            当前池点数
     * @param poolMax           池容量（点）
     * @param burnTicksPerPoint 折算分母：1 点 = 多少刻原版燃烧时长
     * @return 新的池点数（没取到就原样返回）
     */
    static int refill(Container container, FuelValues fuels, int points, int poolMax, int burnTicksPerPoint) {
        int slot = -1;
        int gain = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || !fuels.isFuel(stack)) {
                continue;
            }
            int burn = fuels.burnDuration(stack);
            if (burn <= 0) {
                continue;
            }
            int itemPoints = Math.max(1, burn / burnTicksPerPoint);
            if (points > 0 && points + itemPoints > poolMax) {
                continue;   // 装不下：等池子再烧掉一些，别把余量截断
            }
            slot = i;
            gain = itemPoints;
            break;
        }
        if (slot < 0) {
            return points;
        }
        container.removeItem(slot, 1);
        container.setChanged();
        return points + gain;
    }

    private SolidFuelPool() {}
}
