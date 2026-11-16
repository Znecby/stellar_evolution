package dev.modmind.stellar_evolution.fission;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 浸出渣：用 {@link FissionComponents#LEACH_RESIDUE_TYPE} 区分来源，tooltip 直接写出是哪条矿线的渣。
 */
public class LeachResidueItem extends Item {
    public LeachResidueItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        LeachResidueType type = stack.get(FissionComponents.LEACH_RESIDUE_TYPE.get());
        if (type == null) {
            return;
        }
        tooltip.accept(Component.translatable("item.stellar_evolution.leach_residue.type",
                        Component.translatable("item.stellar_evolution.leach_residue.source."
                                + type.name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.GRAY));
    }
}
