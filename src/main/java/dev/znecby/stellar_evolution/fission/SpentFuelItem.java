package dev.znecby.stellar_evolution.fission;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 乏燃料：<strong>堆叠 1 + 余热组件</strong>。
 *
 * <p>反应堆产出时写入 {@link FissionComponents#SPENT_HEAT}；只有乏燃料池能降温。
 * 温度高于 {@link SpentFuelHeat#HOT} 时显示「自燃」，拿进背包会着火 + 凋零；
 * 冷却到 0 后显示「已冷却」，才允许进入后处理厂。
 */
public class SpentFuelItem extends Item {
    private final FuelSpec sourceSpec;

    public SpentFuelItem(Properties properties, FuelSpec sourceSpec) {
        super(properties);
        this.sourceSpec = sourceSpec;
    }

    /** 这根乏燃料来自哪一档燃料（tooltip / 后续后处理配方用）。 */
    public FuelSpec sourceSpec() {
        return sourceSpec;
    }

    /** 当前余热（0–1000）。 */
    public static float heat(ItemStack stack) {
        return stack.getOrDefault(FissionComponents.SPENT_HEAT, 0.0F);
    }

    public static void setHeat(ItemStack stack, float heat) {
        float clamped = Math.max(0.0F, Math.min(SpentFuelHeat.MAX, heat));
        if (clamped <= 0.0F) {
            // 冷却完成：移除组件，让物品重新匹配「无余热」的后处理配方。
            stack.remove(FissionComponents.SPENT_HEAT);
        } else {
            stack.set(FissionComponents.SPENT_HEAT, clamped);
        }
    }

    /** 是否处于自燃状态。 */
    public static boolean isHot(ItemStack stack) {
        return SpentFuelHeat.isHot(heat(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        float heat = heat(stack);
        if (heat > 0.0F) {
            tooltip.accept(Component.translatable("item.stellar_evolution.spent_fuel.tooltip.heat",
                    Math.round(heat)).withStyle(ChatFormatting.RED));
        } else {
            tooltip.accept(Component.translatable("item.stellar_evolution.spent_fuel.tooltip.cooled")
                    .withStyle(ChatFormatting.GREEN));
        }
        if (SpentFuelHeat.isHot(heat)) {
            tooltip.accept(Component.translatable("item.stellar_evolution.spent_fuel.tooltip.hot")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** 热乏燃料被玩家拿进背包时：着火 + 凋零（服务端）。 */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
        if (!isHot(stack) || !(owner instanceof Player player)) {
            return;
        }
        player.setRemainingFireTicks(60);
        if (level.getGameTime() % 20L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), player);
        }
    }
}
