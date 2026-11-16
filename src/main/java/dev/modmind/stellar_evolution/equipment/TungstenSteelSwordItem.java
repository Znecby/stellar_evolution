package dev.modmind.stellar_evolution.equipment;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 钨钢剑：继承钢剑的横扫 / 抢夺模式，等级 Lv.3。 */
public class TungstenSteelSwordItem extends SteelSwordItem implements TungstenSteelTool {
    public TungstenSteelSwordItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public int steelLevel(ItemStack stack) {
        return 3;
    }
}
