package dev.znecby.stellar_evolution.bedrock;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 高振基岩晶态物：<strong>始终带附魔光效</strong>，不依赖附魔或数据组件。
 *
 * <p>{@link ItemStack#hasFoil()} 会先看 {@code enchantment_glint_override} 组件；
 * 没有该组件时才回落到 {@link Item#isFoil(ItemStack)}。这里直接返回 true，
 * 保证无论从创造页、/give 还是旧存档取出，都有光效。
 */
public class BedrockCrystalItem extends Item {
    public BedrockCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
