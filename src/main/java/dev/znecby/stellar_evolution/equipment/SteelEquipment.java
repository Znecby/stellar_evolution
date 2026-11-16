package dev.znecby.stellar_evolution.equipment;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * <strong>钢系列装备</strong>（2026-11-01）：5 件工具 / 武器 + 4 件护甲。
 *
 * <p>工具等级 = 钻石（错误方块标签用 {@code INCORRECT_FOR_DIAMOND_TOOL}），
 * 数值见 {@link SteelMaterials}。镐 / 斧 / 锹 / 锄带模式；剑不带模式。
 */
public final class SteelEquipment {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredItem<Item> STEEL_PICKAXE =
            ITEMS.register("steel_pickaxe", key -> new SteelPickaxeItem(properties(key)
                    .pickaxe(SteelMaterials.TOOL, 1.0F, -2.8F)));

    public static final DeferredItem<Item> STEEL_AXE =
            ITEMS.register("steel_axe", key -> new SteelAxeItem(SteelMaterials.TOOL, 6.0F, -3.1F, properties(key)));

    public static final DeferredItem<Item> STEEL_SHOVEL =
            ITEMS.register("steel_shovel", key -> new SteelShovelItem(SteelMaterials.TOOL, 1.5F, -3.0F, properties(key)));

    public static final DeferredItem<Item> STEEL_HOE =
            ITEMS.register("steel_hoe", key -> new SteelHoeItem(SteelMaterials.TOOL, -3.0F, 0.0F, properties(key)));

    public static final DeferredItem<Item> STEEL_SWORD =
            ITEMS.register("steel_sword", key -> new SteelSwordItem(properties(key)
                    .sword(SteelMaterials.TOOL, 3.0F, -2.4F)));

    public static final DeferredItem<Item> STEEL_HELMET =
            ITEMS.register("steel_helmet", key -> new SteelGearItem(properties(key)
                    .humanoidArmor(SteelMaterials.ARMOR, ArmorType.HELMET)));

    public static final DeferredItem<Item> STEEL_CHESTPLATE =
            ITEMS.register("steel_chestplate", key -> new SteelGearItem(properties(key)
                    .humanoidArmor(SteelMaterials.ARMOR, ArmorType.CHESTPLATE)));

    public static final DeferredItem<Item> STEEL_LEGGINGS =
            ITEMS.register("steel_leggings", key -> new SteelGearItem(properties(key)
                    .humanoidArmor(SteelMaterials.ARMOR, ArmorType.LEGGINGS)));

    public static final DeferredItem<Item> STEEL_BOOTS =
            ITEMS.register("steel_boots", key -> new SteelGearItem(properties(key)
                    .humanoidArmor(SteelMaterials.ARMOR, ArmorType.BOOTS)));

    /** 创造页顺序：工具 → 武器 → 护甲。 */
    public static final List<DeferredItem<? extends Item>> ALL = List.of(
            STEEL_PICKAXE, STEEL_AXE, STEEL_SHOVEL, STEEL_HOE, STEEL_SWORD,
            STEEL_HELMET, STEEL_CHESTPLATE, STEEL_LEGGINGS, STEEL_BOOTS);

    private static Item.Properties properties(net.minecraft.resources.Identifier key) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, key));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private SteelEquipment() {}
}
