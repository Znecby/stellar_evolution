package dev.znecby.stellar_evolution.machine;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** 材料加工机械用到的物品标签。 */
public final class MachineTags {
    /** 压模机模具：板模具 / 杆模具 / 齿轮模具。 */
    public static final TagKey<Item> DIE_PRESS_MOLDS =
            TagKey.create(Registries.ITEM, StellarEvolution.id("die_press_molds"));

    private MachineTags() {}
}
