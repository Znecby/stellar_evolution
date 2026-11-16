package dev.modmind.stellar_evolution.equipment;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/** 钨钢护甲：合成自带耐久 III（沿用钢）+ 火焰保护 IV（仅护甲）。 */
public class TungstenSteelGearItem extends SteelGearItem {
    public TungstenSteelGearItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        super.onCraftedBy(stack, player);
        SteelCrafting.applyEnchantment(stack, player, Enchantments.FIRE_PROTECTION, 4);
    }
}
