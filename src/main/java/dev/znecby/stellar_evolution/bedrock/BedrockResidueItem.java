package dev.znecby.stellar_evolution.bedrock;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 基岩浸出渣：用 {@link BedrockComponents#BEDROCK_STAGE} 区分 S1–S4，tooltip 直接写出段位。
 */
public class BedrockResidueItem extends Item {
    public BedrockResidueItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        BedrockStage stage = stack.get(BedrockComponents.BEDROCK_STAGE.get());
        if (stage == null) {
            return;
        }
        tooltip.accept(Component.translatable("item.stellar_evolution.bedrock_residue.stage",
                        Component.translatable("item.stellar_evolution.bedrock_residue.stage."
                                + stage.name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.GRAY));
    }
}
