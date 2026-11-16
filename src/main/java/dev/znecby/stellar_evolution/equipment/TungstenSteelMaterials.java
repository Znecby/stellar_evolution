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
 * <strong>钨钢系列</strong>（2026-11-03）：钨铜硬质合金装备。
 *
 * <p>定位：钢的严格上位，数值贴近下界合金。
 * <ul>
 *   <li>工具耐久 2000（钢 1800 / 下界合金 2031），挖速 9.0，攻击加成 3.0；</li>
 *   <li>工具等级用 {@link BlockTags#INCORRECT_FOR_NETHERITE_TOOL}；</li>
 *   <li>护甲值 3 / 8 / 6 / 3（等于下界合金），韧性 2.5（略低于下界合金 3.0），
 *       护甲耐久倍率 36（下界合金 37）；</li>
 *   <li>修复材料 = 钨铜硬质合金锭（标签 {@code stellar_evolution:tungsten_steel_repair}）。</li>
 * </ul>
 */
public final class TungstenSteelMaterials {
    /** 钨钢装备的修复材料标签（内容只有钨铜硬质合金锭）。 */
    public static final TagKey<Item> TUNGSTEN_STEEL_REPAIR =
            TagKey.create(Registries.ITEM, StellarEvolution.id("tungsten_steel_repair"));

    /** 钨钢工具材料：耐久 / 挖速 / 攻击全部贴近下界合金。 */
    public static final ToolMaterial TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2000,
            9.0F,
            3.0F,
            15,
            TUNGSTEN_STEEL_REPAIR);

    /** 钨钢护甲外观资源（贴图见 {@code assets/stellar_evolution/equipment/tungsten_steel.json}）。 */
    public static final ResourceKey<EquipmentAsset> ARMOR_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, StellarEvolution.id("tungsten_steel"));

    /** 钨钢护甲：护甲值等于下界合金，韧性 2.5（略低）。 */
    public static final ArmorMaterial ARMOR = new ArmorMaterial(
            36,
            Map.of(
                    ArmorType.HELMET, 3,
                    ArmorType.CHESTPLATE, 8,
                    ArmorType.LEGGINGS, 6,
                    ArmorType.BOOTS, 3),
            15,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            2.5F,
            0.1F,
            TUNGSTEN_STEEL_REPAIR,
            ARMOR_ASSET);

    private TungstenSteelMaterials() {}
}
