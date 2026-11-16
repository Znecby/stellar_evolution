package dev.modmind.stellar_evolution.muon;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 聚变芯核：<strong>堆叠 1 + 耐久制</strong>，放进反应仓由缪子聚变仪原地磨损。
 *
 * <p>核心名与「约束」都来自 {@link MuonCoreSpec}：氘氚 / 氘氘 / 氘-氦3 / 质子-硼11 芯核。
 */
public class MuonCoreItem extends Item {
    private final MuonCoreSpec spec;

    public MuonCoreItem(Properties properties, MuonCoreSpec spec) {
        super(properties);
        this.spec = spec;
    }

    public MuonCoreSpec spec() {
        return spec;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.stellar_evolution.fusion_core.tooltip.core",
                Component.translatable(spec.displayKey())).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("item.stellar_evolution.fusion_core.tooltip.constraint",
                Component.translatable(spec.stream().nameKey())).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.translatable("item.stellar_evolution.fusion_core.tooltip.usage")
                .withStyle(ChatFormatting.GRAY));
    }
}
