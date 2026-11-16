package dev.modmind.stellar_evolution.equipment;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 钨钢镐：继承钢镐全部模式，等级 Lv.3。 */
public class TungstenSteelPickaxeItem extends SteelPickaxeItem implements TungstenSteelTool {
    public TungstenSteelPickaxeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public int steelLevel(ItemStack stack) {
        return 3;
    }
}
