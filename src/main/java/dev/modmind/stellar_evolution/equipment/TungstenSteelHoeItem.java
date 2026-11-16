package dev.modmind.stellar_evolution.equipment;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

/** 钨钢锄：继承钢锄全部模式，等级 Lv.3。 */
public class TungstenSteelHoeItem extends SteelHoeItem implements TungstenSteelTool {
    public TungstenSteelHoeItem(ToolMaterial material, float attackDamage, float attackSpeed,
                                Item.Properties properties) {
        super(material, attackDamage, attackSpeed, properties);
    }

    @Override
    public int steelLevel(ItemStack stack) {
        return 3;
    }
}
