package dev.znecby.stellar_evolution.equipment;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** 钢镐：范围 / 连锁挖矿 / 时运 / 精准采集。 */
public class SteelPickaxeItem extends Item implements ModeTool {
    public SteelPickaxeItem(Properties properties) {
        super(properties);
    }

    @Override
    public SteelToolKind kind() {
        return SteelToolKind.PICKAXE;
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
