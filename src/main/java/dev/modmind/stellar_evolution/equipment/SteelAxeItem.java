package dev.modmind.stellar_evolution.equipment;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** 钢斧：范围 / 伐木 / 时运 / 精准采集。 */
public class SteelAxeItem extends AxeItem implements ModeTool {
    public SteelAxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Properties properties) {
        super(material, attackDamage, attackSpeed, properties);
    }

    @Override
    public SteelToolKind kind() {
        return SteelToolKind.AXE;
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
