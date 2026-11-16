package dev.modmind.stellar_evolution.equipment;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * <strong>钨钢系列</strong>（2026-11-03）：5 件工具 / 武器 + 4 件护甲。
 *
 * <p>全部继承钢系列的模式与行为；工具等级固定 Lv.3。合成自带耐久 III，
 * 护甲额外火焰保护 IV。
 */
public final class TungstenSteelEquipment {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredItem<Item> TUNGSTEN_STEEL_PICKAXE =
            ITEMS.register("tungsten_steel_pickaxe", key -> new TungstenSteelPickaxeItem(properties(key)
                    .pickaxe(TungstenSteelMaterials.TOOL, 1.0F, -2.8F)));

    public static final DeferredItem<Item> TUNGSTEN_STEEL_AXE =
            ITEMS.register("tungsten_steel_axe", key -> new TungstenSteelAxeItem(
                    TungstenSteelMaterials.TOOL, 6.0F, -3.1F, properties(key)));

    public static final DeferredItem<Item> TUNGSTEN_STEEL_SHOVEL =
            ITEMS.register("tungsten_steel_shovel", key -> new TungstenSteelShovelItem(
                    TungstenSteelMaterials.TOOL, 1.5F, -3.0F, properties(key)));

    public static final DeferredItem<Item> TUNGSTEN_STEEL_HOE =
            ITEMS.register("tungsten_steel_hoe", key -> new TungstenSteelHoeItem(
                    TungstenSteelMaterials.TOOL, -3.0F, 0.0F, properties(key)));

    public static final DeferredItem<Item> TUNGSTEN_STEEL_SWORD =
            ITEMS.register("tungsten_steel_sword", key -> new TungstenSteelSwordItem(properties(key)
                    .sword(TungstenSteelMaterials.TOOL, 3.0F, -2.4F)));

    public static final DeferredItem<Item> TUNGSTEN_STEEL_HELMET =
            ITEMS.register("tungsten_steel_helmet", key -> new TungstenSteelGearItem(properties(key)
                    .humanoidArmor(TungstenSteelMaterials.ARMOR, ArmorType.HELMET)));

    public static final DeferredItem<Item> TUNGSTEN_STEEL_CHESTPLATE =
            ITEMS.register("tungsten_steel_chestplate", key -> new TungstenSteelGearItem(properties(key)
                    .humanoidArmor(TungstenSteelMaterials.ARMOR, ArmorType.CHESTPLATE)));

    public static final DeferredItem<Item> TUNGSTEN_STEEL_LEGGINGS =
            ITEMS.register("tungsten_steel_leggings", key -> new TungstenSteelGearItem(properties(key)
                    .humanoidArmor(TungstenSteelMaterials.ARMOR, ArmorType.LEGGINGS)));

    public static final DeferredItem<Item> TUNGSTEN_STEEL_BOOTS =
            ITEMS.register("tungsten_steel_boots", key -> new TungstenSteelGearItem(properties(key)
                    .humanoidArmor(TungstenSteelMaterials.ARMOR, ArmorType.BOOTS)));

    /** 创造页顺序：工具 → 武器 → 护甲。 */
    public static final List<DeferredItem<? extends Item>> ALL = List.of(
            TUNGSTEN_STEEL_PICKAXE, TUNGSTEN_STEEL_AXE, TUNGSTEN_STEEL_SHOVEL, TUNGSTEN_STEEL_HOE,
            TUNGSTEN_STEEL_SWORD,
            TUNGSTEN_STEEL_HELMET, TUNGSTEN_STEEL_CHESTPLATE, TUNGSTEN_STEEL_LEGGINGS, TUNGSTEN_STEEL_BOOTS);

    private static Item.Properties properties(net.minecraft.resources.Identifier key) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, key));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private TungstenSteelEquipment() {}
}
