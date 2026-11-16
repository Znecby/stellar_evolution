package dev.znecby.stellar_evolution.equipment;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 钢制装备的<strong>合成自带附魔</strong>。
 *
 * <p>只加原版「耐久 III」；时运 / 精准 / 横扫属于模式功能，<strong>不</strong>写进附魔组件，
 * 避免砂轮刷经验等问题。
 */
public final class SteelCrafting {
    public static void applyUnbreaking(ItemStack stack, Player player) {
        applyEnchantment(stack, player, Enchantments.UNBREAKING, 3);
    }

    /** 给合成产物写入一条原版附魔；已有时只升不降。 */
    public static void applyEnchantment(ItemStack stack, Player player,
                                       ResourceKey<Enchantment> enchantment, int level) {
        if (player.level().isClientSide()) {
            return;
        }
        applyEnchantment(stack, player.registryAccess(), enchantment, level);
    }

    /** 服务端 / 机器产出用：直接给注册表访问权。 */
    public static void applyEnchantment(ItemStack stack, RegistryAccess registries,
                                       ResourceKey<Enchantment> enchantment, int level) {
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var holder = lookup.getOrThrow(enchantment);
        // 新合成物品通常没有 ENCHANTMENTS 组件，不能走 updateEnchantments（它遇到空组件会直接返回）
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
                stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        mutable.upgrade(holder, level);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    private SteelCrafting() {}
}
