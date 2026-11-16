package dev.modmind.stellar_evolution.equipment;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

/** 钨钢锹：继承钢锹全部模式，等级 Lv.3。 */
public class TungstenSteelShovelItem extends SteelShovelItem implements TungstenSteelTool {
    public TungstenSteelShovelItem(ToolMaterial material, float attackDamage, float attackSpeed,
                                   Item.Properties properties) {
        super(material, attackDamage, attackSpeed, properties);
    }

    @Override
    public int steelLevel(ItemStack stack) {
        return 3;
    }
}
