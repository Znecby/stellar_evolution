package dev.znecby.stellar_evolution.equipment;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * 钢系列的工具 / 护甲材料（2026-11-01）。
 *
 * <p>数值口径：<strong>强于铁、接近钻石</strong>；耐久高于钻石、低于下界合金；
 * 工具等级 = 钻石（可挖黑曜石）。
 *
 * <ul>
 *   <li>耐久 1800（钻石 1561 / 下界合金 2031）；</li>
 *   <li>挖掘速度 7.5（铁 6 / 钻石 8）；</li>
 *   <li>攻击加成 2.5（铁 2 / 钻石 3）；</li>
 *   <li>附魔能力 12（铁 14 / 钻石 10）；</li>
 *   <li>错误方块标签用 {@link BlockTags#INCORRECT_FOR_DIAMOND_TOOL} ⇒ 钻石级；</li>
 *   <li>修复材料 = 钢锭（标签 {@code stellar_evolution:steel_repair}）。</li>
 * </ul>
 */
public final class SteelMaterials {
    /** 钢制装备的修复材料标签（内容只有钢锭）。 */
    public static final TagKey<Item> STEEL_REPAIR =
            TagKey.create(Registries.ITEM, StellarEvolution.id("steel_repair"));

    /** 钢制工具材料。 */
    public static final ToolMaterial TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1800,
            7.5F,
            2.5F,
            12,
            STEEL_REPAIR);

    /** 钢制护甲外观资源（贴图见 {@code assets/stellar_evolution/equipment/steel.json}）。 */
    public static final ResourceKey<EquipmentAsset> ARMOR_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, StellarEvolution.id("steel"));

    /** 钢制护甲材料：护甲值 3 / 7 / 6 / 3，韧性 1.5。 */
    public static final ArmorMaterial ARMOR = new ArmorMaterial(
            35,
            Map.of(
                    ArmorType.HELMET, 3,
                    ArmorType.CHESTPLATE, 7,
                    ArmorType.LEGGINGS, 6,
                    ArmorType.BOOTS, 3),
            12,
            SoundEvents.ARMOR_EQUIP_IRON,
            1.5F,
            0.0F,
            STEEL_REPAIR,
            ARMOR_ASSET);

    private SteelMaterials() {}
}
