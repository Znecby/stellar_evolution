package dev.znecby.stellar_evolution.ore;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 选冶线（G 线）的<strong>物品类物质</strong>：粗矿 / 粉 / 精砂 / 铀锭。
 *
 * <p>口径（2026-10-25 定稿）：
 * <ul>
 *   <li><strong>粗矿</strong>：只补 {@code raw_uranium}（粗铁换色）；铁 / 铜 / 金沿用原版粗矿，
 *       远古残骸、下界金矿与全部非金属矿<strong>没有粗矿形态</strong>，直接拿原矿进线；</li>
 *   <li><strong>粉</strong>：铜粉沿用 {@code material.MaterialItems.COPPER_DUST}，这里补铁 / 金 / 铀 / 残骸与
 *       钻石 / 绿宝石 / 青金石 / 石英粉（煤与红石不做粉，直接用原版物品作终端）；</li>
 *   <li><strong>精砂</strong>：11 种（5 金属 / 残骸 + 6 非金属），贴图为原版海晶砂换色；</li>
 *   <li><strong>铀锭</strong>：铀粉熔炼产物，也是酸浸槽的新进料（铀锭 ×2 → 黄饼）。</li>
 * </ul>
 * <p>贴图由 {@code tools/gen_ore_materials.ps1} 生成；双语名在 lang 里。
 */
public final class OreMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    // ---- 粗矿
    /** 粗铀：铀矿石 / 深板岩铀矿石的破碎产物。 */
    public static final DeferredItem<Item> RAW_URANIUM = register("raw_uranium");
    /** 硫磺晶体：下界硫磺矿直接掉落，破碎机 → 3 硫粉。 */
    public static final DeferredItem<Item> SULFUR_CRYSTAL = register("sulfur_crystal");

    // ---- 粉
    /** 铁粉：粗铁粉碎产物 / 酸浸产物 / 熔炼成铁锭。 */
    public static final DeferredItem<Item> IRON_DUST = register("iron_dust");
    /** 金粉：粗金 / 下界金矿 / 金精砂的产物。 */
    public static final DeferredItem<Item> GOLD_DUST = register("gold_dust");
    /** 铀粉：粗铀 / 铀精砂的产物。 */
    public static final DeferredItem<Item> URANIUM_DUST = register("uranium_dust");
    /** 残骸粉：远古残骸 / 残骸精砂的产物，熔炼成下界合金碎片。 */
    public static final DeferredItem<Item> DEBRIS_DUST = register("debris_dust");
    /** 钻石粉：热室压回钻石。 */
    public static final DeferredItem<Item> DIAMOND_DUST = register("diamond_dust");
    /** 绿宝石粉：热室压回绿宝石。 */
    public static final DeferredItem<Item> EMERALD_DUST = register("emerald_dust");
    /** 青金石粉：热室压回青金石。 */
    public static final DeferredItem<Item> LAPIS_DUST = register("lapis_dust");
    /** 石英粉：热室压回石英。 */
    public static final DeferredItem<Item> QUARTZ_DUST = register("quartz_dust");

    // ---- 精砂（11 种）
    public static final DeferredItem<Item> IRON_CONCENTRATE_SAND = register("iron_concentrate_sand");
    public static final DeferredItem<Item> COPPER_CONCENTRATE_SAND = register("copper_concentrate_sand");
    public static final DeferredItem<Item> GOLD_CONCENTRATE_SAND = register("gold_concentrate_sand");
    public static final DeferredItem<Item> URANIUM_CONCENTRATE_SAND = register("uranium_concentrate_sand");
    public static final DeferredItem<Item> DEBRIS_CONCENTRATE_SAND = register("debris_concentrate_sand");
    public static final DeferredItem<Item> COAL_CONCENTRATE_SAND = register("coal_concentrate_sand");
    public static final DeferredItem<Item> DIAMOND_CONCENTRATE_SAND = register("diamond_concentrate_sand");
    public static final DeferredItem<Item> EMERALD_CONCENTRATE_SAND = register("emerald_concentrate_sand");
    public static final DeferredItem<Item> LAPIS_CONCENTRATE_SAND = register("lapis_concentrate_sand");
    public static final DeferredItem<Item> REDSTONE_CONCENTRATE_SAND = register("redstone_concentrate_sand");
    public static final DeferredItem<Item> QUARTZ_CONCENTRATE_SAND = register("quartz_concentrate_sand");

    // ---- 锭
    /** 铀锭：铀粉熔炼产物；酸浸槽的新进料（2 锭 → 1 黄饼）。 */
    public static final DeferredItem<Item> URANIUM_INGOT = register("uranium_ingot");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            RAW_URANIUM, SULFUR_CRYSTAL,
            IRON_DUST, GOLD_DUST, URANIUM_DUST, DEBRIS_DUST,
            DIAMOND_DUST, EMERALD_DUST, LAPIS_DUST, QUARTZ_DUST,
            IRON_CONCENTRATE_SAND, COPPER_CONCENTRATE_SAND, GOLD_CONCENTRATE_SAND,
            URANIUM_CONCENTRATE_SAND, DEBRIS_CONCENTRATE_SAND,
            COAL_CONCENTRATE_SAND, DIAMOND_CONCENTRATE_SAND, EMERALD_CONCENTRATE_SAND,
            LAPIS_CONCENTRATE_SAND, REDSTONE_CONCENTRATE_SAND, QUARTZ_CONCENTRATE_SAND,
            URANIUM_INGOT);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private OreMaterials() {}
}
