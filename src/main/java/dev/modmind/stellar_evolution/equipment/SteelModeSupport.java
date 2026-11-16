package dev.modmind.stellar_evolution.equipment;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 钢制工具的<strong>提示文本</strong>：模式总览 + 每项功能的等级 / 规模。
 *
 * <p>时运 / 精准 / 横扫<strong>不写入真实附魔</strong>，而是由事件按功能实现，
 * 避免原版砂轮把模式附魔刷成经验。这里只负责显示。
 */
public final class SteelModeSupport {
    /** 物品介绍末尾：橙色模式行 + 每项功能的灰色等级行 + 操作提示。 */
    public static void appendTooltip(ItemStack stack, Consumer<Component> tooltip) {
        if (!(stack.getItem() instanceof ModeTool tool)) {
            return;
        }
        int features = tool.features(stack);
        tooltip.accept(Component.translatable("stellar_evolution.steel_tool.mode",
                        SteelToolFeature.names(features, tool.kind()))
                .withStyle(ChatFormatting.GOLD));
        for (SteelToolFeature feature : SteelToolFeature.fromMask(features, tool.kind())) {
            tooltip.accept(featureLabel(tool, stack, feature).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.accept(Component.translatable("stellar_evolution.steel_tool.mode.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 单个功能的等级 / 规模文本（UI 按钮也复用它）。 */
    public static net.minecraft.network.chat.MutableComponent featureLabel(
            ModeTool tool, ItemStack stack, SteelToolFeature feature) {
        int level = tool.steelLevel(stack);
        return switch (feature) {
            case AREA -> {
                int side = tool.areaRadius(stack) * 2 + 1;
                String key = tool.kind() == SteelToolKind.HOE
                        ? "stellar_evolution.steel_tool.feature.area_farm"
                        : "stellar_evolution.steel_tool.feature.area";
                yield Component.translatable(key, level, side, side);
            }
            case VEIN -> Component.translatable("stellar_evolution.steel_tool.feature.vein",
                    level, tool.veinLimit(stack));
            case FELL -> Component.translatable("stellar_evolution.steel_tool.feature.fell",
                    level, tool.veinLimit(stack));
            case BULK -> Component.translatable("stellar_evolution.steel_tool.feature.bulk",
                    level, tool.veinLimit(stack));
            case HARVEST -> Component.translatable("stellar_evolution.steel_tool.feature.harvest",
                    level, tool.veinLimit(stack));
            case FORTUNE -> Component.translatable("stellar_evolution.steel_tool.feature.fortune",
                    tool.fortuneLevel(stack));
            case SILK -> Component.translatable("stellar_evolution.steel_tool.feature.silk");
            case SWEEP -> Component.translatable("stellar_evolution.steel_tool.feature.sweep");
            case LOOTING -> Component.translatable("stellar_evolution.steel_tool.feature.looting");
        };
    }

    private SteelModeSupport() {}
}
