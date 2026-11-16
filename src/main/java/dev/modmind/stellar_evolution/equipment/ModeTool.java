package dev.modmind.stellar_evolution.equipment;

import net.minecraft.world.item.ItemStack;

/**
 * 带功能开关的钢制工具（镐 / 斧 / 锹 / 锄 / 剑）。
 *
 * <p>功能以位掩码存在数据组件里，可多选；可用集合与冲突由 {@link SteelToolFeature} 按
 * {@link #kind()} 裁决。
 */
public interface ModeTool {
    /** 工具种类，决定可用功能。 */
    SteelToolKind kind();

    /** 当前功能位掩码（自动清掉不支持与冲突位）。 */
    default int features(ItemStack stack) {
        return SteelToolFeature.sanitize(
                stack.getOrDefault(SteelToolComponents.FEATURES.get(), 0), kind());
    }

    /** 是否启用了某个功能。 */
    default boolean hasFeature(ItemStack stack, SteelToolFeature feature) {
        return (features(stack) & feature.mask()) != 0;
    }

    /** 写入功能掩码；0 时移除组件。 */
    default void setFeatures(ItemStack stack, int features) {
        int clean = SteelToolFeature.sanitize(features, kind());
        if (clean == 0) {
            stack.remove(SteelToolComponents.FEATURES.get());
        } else {
            stack.set(SteelToolComponents.FEATURES.get(), clean);
        }
    }

    /** 工具等级（缺省 1）。 */
    default int steelLevel(ItemStack stack) {
        return Math.max(1, stack.getOrDefault(SteelToolComponents.LEVEL.get(), 1));
    }

    /** 连锁最大块数：钢基础 16，每高一级 +8。 */
    default int veinLimit(ItemStack stack) {
        return 16 + 8 * (steelLevel(stack) - 1);
    }

    /** 范围半径：钢基础 1（3×3），每高一级 +1（5×5、7×7…）。 */
    default int areaRadius(ItemStack stack) {
        return 1 + (steelLevel(stack) - 1);
    }

    /** 时运等级：钢基础 3，每高一级 +2。 */
    default int fortuneLevel(ItemStack stack) {
        return 3 + 2 * (steelLevel(stack) - 1);
    }
}
