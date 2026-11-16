package dev.znecby.stellar_evolution.particle;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * <strong>束流增压模块</strong>（二期 D2）：放进粒子源输入箱后，束流强度 ×1.5、能耗门槛 ×1.5；
 * 只判定存在、<strong>不消耗</strong>（与压力罐共存于同一输入容器）。
 */
public class BoosterModuleItem extends Item {
    public BoosterModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.stellar_evolution.booster_module.tooltip")
                .withStyle(ChatFormatting.GOLD));
    }
}
