package dev.znecby.stellar_evolution.equipment;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** 钢剑：横扫（等效原版横扫之刃 III，不写真实附魔）。 */
public class SteelSwordItem extends Item implements ModeTool {
    public SteelSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public SteelToolKind kind() {
        return SteelToolKind.SWORD;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        SteelModeSupport.appendTooltip(stack, tooltip);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        SteelCrafting.applyUnbreaking(stack, player);
    }
}
