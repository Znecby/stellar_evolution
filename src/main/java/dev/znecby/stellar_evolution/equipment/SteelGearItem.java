package dev.znecby.stellar_evolution.equipment;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 钢制护甲：合成自带耐久 III。 */
public class SteelGearItem extends Item {
    public SteelGearItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        SteelCrafting.applyUnbreaking(stack, player);
    }
}
