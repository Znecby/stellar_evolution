package dev.znecby.stellar_evolution.equipment;

import java.util.Set;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 原版<strong>铜系列装备</strong>（工具 / 武器 / 护甲）的识别与热室修复参数。
 *
 * <p>26.1.2 原版已提供铜镐 / 铜斧 / 铜锹 / 铜锄 / 铜剑与铜护甲，本类只做识别，
 * 不新增铜装备。热室（铜箱子底部垫铜块、接热压差）在通电时自动修复箱内
 * 任一受损铜装备，每完成一个周期修复最大耐久的 10%（至少 1 点）。
 */
public final class CopperGear {
    /** 热室修复一个周期所需的基础时长（tick，1.0 倍速）。 */
    public static final double REPAIR_DURATION = 100.0D;

    /** 每周期修复最大耐久的比例。 */
    public static final double REPAIR_FRACTION = 0.10D;

    private static final Set<Item> ALL = Set.of(
            Items.COPPER_PICKAXE,
            Items.COPPER_AXE,
            Items.COPPER_SHOVEL,
            Items.COPPER_HOE,
            Items.COPPER_SWORD,
            Items.COPPER_HELMET,
            Items.COPPER_CHESTPLATE,
            Items.COPPER_LEGGINGS,
            Items.COPPER_BOOTS);

    /** 是不是可修复的铜装备（必须真的掉了耐久）。 */
    public static boolean isDamaged(ItemStack stack) {
        return !stack.isEmpty()
                && ALL.contains(stack.getItem())
                && stack.isDamageableItem()
                && stack.getDamageValue() > 0;
    }

    /** 找箱内第一个受损铜装备；没有返回 -1。 */
    public static int findDamaged(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (isDamaged(container.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** 一个周期修复的耐久点数（最大耐久的 10%，至少 1）。 */
    public static int repairAmount(ItemStack stack) {
        return Math.max(1, (int) Math.ceil(stack.getMaxDamage() * REPAIR_FRACTION));
    }

    private CopperGear() {}
}
