package dev.znecby.stellar_evolution.fission;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 反射棒：昂贵的反应堆增益件（下界合金块为核心材料）。
 *
 * <p>正常使用<strong>不磨损</strong>；只有反应堆在累积损毁时才会掉耐久。
 * 耐久耗尽后物品<strong>不消失</strong>，只是暂时失去增益；在铁砧上用 1 个下界合金锭可回满
 * （见 {@code StellarEvolution#onAnvilUpdate}）。
 */
public class ReflectorRodItem extends Item {
    /** 反射棒耐久。 */
    public static final int DURABILITY = 1_000;

    public ReflectorRodItem(Properties properties) {
        super(properties);
    }

    /** 只能拿耐久值判断；耐久为 0 时效果关闭但物品保留。 */
    public static boolean isActive(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage();
    }
}
