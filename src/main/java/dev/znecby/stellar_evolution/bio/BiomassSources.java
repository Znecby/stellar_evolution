package dev.znecby.stellar_evolution.bio;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * {@code biomass} 的<strong>作物粉碎映射</strong>（机械土壤收割不再额外产出生物质）。
 *
 * <p>口径：粉碎机把「植物类物品」粉碎成 {@code biomass}，不同物品的产量不同
 * （作物 / 树叶 / 蘑菇 / 海带…由物品标签 {@code stellar_evolution:biomass_sources} 标记，
 * 单件折算量见 {@link #YIELDS}，表里没列的一律 1）。小麦块（干草捆）单独按
 * {@link #HAY_BLOCK_BIOMASS} 计算，产量高于 9 个单小麦，鼓励压块。
 *
 * <p>接线在 {@code machine.MachineRecipes#find}：粉碎机每周期扣 1 个原料，
 * 因此这里返回的是<strong>单件</strong>产量。
 */
public final class BiomassSources {
    /** 「算生物质来源」的物品标签（JSON 在 {@code data/stellar_evolution/tags/item/biomass_sources.json}）。 */
    public static final TagKey<Item> SOURCES = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "biomass_sources"));

    /** 小麦块（干草捆）的粉碎产量：24，比 9 个单小麦折出来的 18 更高。 */
    public static final int HAY_BLOCK_BIOMASS = 24;

    /** 每 1 个来源物品粉碎出多少生物质（只列「不止 1」的；其余来源一律 1）。 */
    private static final Map<Item, Integer> YIELDS = new HashMap<>();

    static {
        YIELDS.put(Items.WHEAT, 2);
        YIELDS.put(Items.SUGAR_CANE, 3);
        YIELDS.put(Items.KELP, 2);
        YIELDS.put(Items.POTATO, 2);
        YIELDS.put(Items.CARROT, 2);
        YIELDS.put(Items.BEETROOT, 2);
        YIELDS.put(Items.PUMPKIN, 4);
        YIELDS.put(Items.MELON_SLICE, 1);
        YIELDS.put(Items.APPLE, 2);
        YIELDS.put(Items.BROWN_MUSHROOM, 2);
        YIELDS.put(Items.RED_MUSHROOM, 2);
        YIELDS.put(Items.CACTUS, 1);
        YIELDS.put(Items.BAMBOO, 2);
    }

    /**
     * 单个物品能粉碎出多少生物质。
     * <p><strong>调用方需先确认该物品在 {@link #SOURCES} 标签里</strong>，否则这里会按 1 返回。
     */
    public static int biomassPerItem(Item item) {
        return YIELDS.getOrDefault(item, 1);
    }

    private BiomassSources() {}
}
