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
 * 铀燃料棒：<strong>堆叠 1 + 耐久制 + 中子通量</strong>（参考 HBM RBMK）。
 *
 * <p>燃料棒<strong>常驻反应堆输入箱</strong>，由反应堆原地修改：
 * 每 tick 增长中子通量（{@link FissionComponents#FUEL_FLUX}）、磨损耐久；
 * 通量越高功率越大，耐久归零后原地变成乏燃料。
 *
 * <p><strong>高温</strong>：通量 ≥ {@link #HOT_FLUX} 时物品提示变橙色，
 * 玩家把它拿进背包会持续着火并中凋零 —— 必须等它冷却后再拿。
 */
public class FuelRodItem extends Item {
    /** 进入「高温」状态的通量阈值（%）。 */
    public static final float HOT_FLUX = 30.0F;

    private final FuelSpec spec;

    public FuelRodItem(Properties properties, FuelSpec spec) {
        super(properties);
        this.spec = spec;
    }

    public FuelSpec spec() {
        return spec;
    }

    /** 当前中子通量（0–100）。 */
    public static float flux(ItemStack stack) {
        return stack.getOrDefault(FissionComponents.FUEL_FLUX, 0.0F);
    }

    public static void setFlux(ItemStack stack, float flux) {
        stack.set(FissionComponents.FUEL_FLUX, Math.max(0.0F, Math.min(100.0F, flux)));
    }

    /** 是否已经烫手（不能安全取出）。 */
    public static boolean isHot(ItemStack stack) {
        return flux(stack) >= HOT_FLUX;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.family."
                + spec.family().name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.spectrum."
                + spec.spectrum().name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.role."
                + spec.role().name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.void."
                + spec.voidReactivity().name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.form."
                + spec.form().name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.fissile",
                spec.fissilePercent()).withStyle(ChatFormatting.GRAY));
        if (spec.thermalPowerMw() > 0) {
            tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.power",
                    spec.thermalPowerMw()).withStyle(ChatFormatting.GRAY));
        }
        if (spec.u233Yield() > 0) {
            tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.u233",
                    spec.u233Yield()).withStyle(ChatFormatting.GREEN));
        }
        tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.life",
                spec.cycles()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.flux",
                Math.round(flux(stack))).withStyle(ChatFormatting.AQUA));
        if (isHot(stack)) {
            tooltip.accept(Component.translatable("item.stellar_evolution.fuel_rod.tooltip.hot")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** 高温棒被玩家拿走时：着火 + 凋零（服务端）。 */
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
