package dev.znecby.stellar_evolution.equipment;

import net.minecraft.world.item.ItemStack;

/**
 * 钨钢工具标记：模式等级固定为钢 +2（即 Lv.3）。
 *
 * <p>范围 7×7、连锁 32 块、时运 7 级都由此统一推出，不依赖物品组件。
 */
public interface TungstenSteelTool extends ModeTool {
    @Override
    default int steelLevel(ItemStack stack) {
        return 3;
    }
}
