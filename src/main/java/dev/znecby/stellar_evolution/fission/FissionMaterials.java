package dev.znecby.stellar_evolution.fission;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 核裂变线的<strong>物品类物质</strong>（《核裂变线预规划蓝图》§5 / §13.1 F0；清单见 §12.2）。
 *
 * <p>只放<strong>已加工物品</strong>：铀精矿 → 富集 / 贫铀 → 燃料棒 → 乏燃料 → 后处理产物。
 * 铀矿<strong>方块</strong>属 F1（燃料循环），不在此类。
 * <p>贴图由 {@code tools/gen_fission_materials.ps1} 生成（16×16 程序化图标，零外部素材）；双语名在 lang 里。
 */
public final class FissionMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 铀精矿（黄饼）：酸浸产物，氟化前体。 */
    public static final DeferredItem<Item> YELLOWCAKE = register("yellowcake");
    /** 铀-235 粒：离心分离出的裂变同位素，燃料芯块的原料。 */
    public static final DeferredItem<Item> U235 = register("u235");
    /** 铀-238 粒：离心分离出的贫铀同位素（配重 / 装甲 / 高级材料）。 */
    public static final DeferredItem<Item> U238 = register("u238");
    /** 燃料棒（三档富集度）：<strong>堆叠 1 + 耐久制</strong>，耐久归零才出乏燃料。 */
    public static final DeferredItem<FuelRodItem> ROD_LEU = rod(FuelSpec.U_LOW);
    public static final DeferredItem<FuelRodItem> ROD_MEU = rod(FuelSpec.U_MID);
    public static final DeferredItem<FuelRodItem> ROD_HEU = rod(FuelSpec.U_HIGH);
    /** 锆合金管：燃料棒包壳（罐装机 / 离心机的耐蚀件）。 */
    public static final DeferredItem<Item> ZIRCALOY_TUBE = register("zircaloy_tube");
    /** 碳化硼控制棒：水冷反应堆的控制棒束。 */
    public static final DeferredItem<Item> BORON_CARBIDE_ROD = register("boron_carbide_rod");
    /** 石墨棒：放进裂变堆输入箱，<strong>只降低通量增长速度</strong>，会随时间磨耐久。 */
    public static final DeferredItem<Item> GRAPHITE_ROD = graphiteRod();
    /** 废石墨棒：石墨棒耐久耗尽后的残骸。 */
    public static final DeferredItem<Item> SPENT_GRAPHITE_ROD = register("spent_graphite_rod");
    /** 反射棒：提高通量、单棒功率与蒸汽压力；正常不磨损，只有反应堆损毁时才掉耐久。 */
    public static final DeferredItem<ReflectorRodItem> REFLECTOR_ROD = ITEMS.register("reflector_rod",
            key -> new ReflectorRodItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .durability(ReflectorRodItem.DURABILITY)));
    /** 放射性熔渣：整堆熔毁时每根装载燃料棒掉 1 个。 */
    public static final DeferredItem<Item> RADIOACTIVE_SLAG = register("radioactive_slag");
    /** MOX 燃料棒（钚系）：低/中/高三档。 */
    public static final DeferredItem<FuelRodItem> MOX_FUEL_ROD = rod(FuelSpec.MOX_LOW);
    public static final DeferredItem<FuelRodItem> MOX_ROD_MID = rod(FuelSpec.MOX_MID);
    public static final DeferredItem<FuelRodItem> MOX_ROD_HIGH = rod(FuelSpec.MOX_HIGH);
    /** 钍燃料棒：0 MW 增殖棒，必须与铀/MOX 点火棒混装。 */
    public static final DeferredItem<FuelRodItem> THORIUM_FUEL_ROD = rod(FuelSpec.TH_LOW);
    public static final DeferredItem<FuelRodItem> THORIUM_ROD_MID = rod(FuelSpec.TH_MID);
    public static final DeferredItem<FuelRodItem> THORIUM_ROD_HIGH = rod(FuelSpec.TH_HIGH);
    /** §7.8 拓展棒：U-233 种子 / MA 嬗变 / Pu 惰性基体 / 可燃毒物 / UC 高功率 / U-Zr 金属。 */
    public static final DeferredItem<FuelRodItem> U233_SEED_ROD = rod(FuelSpec.U233_SEED);
    public static final DeferredItem<FuelRodItem> MA_TRANSMUTATION_ROD = rod(FuelSpec.MA_TRANSMUTE);
    public static final DeferredItem<FuelRodItem> PU_INERT_MATRIX_ROD = rod(FuelSpec.PU_INERT);
    public static final DeferredItem<FuelRodItem> BURNABLE_POISON_ROD = rod(FuelSpec.BURNABLE_POISON);
    public static final DeferredItem<FuelRodItem> UC_HIGH_POWER_ROD = rod(FuelSpec.UC_HIGH_POWER);
    public static final DeferredItem<FuelRodItem> UZR_METAL_ROD = rod(FuelSpec.UZR_METAL);

    // ---- §7.12 残骸线（星核粒 / 星骸粉 / 离火素 / 寂灰 / 不熄合金 / 离火棒） --------
    /** 星核粒：残骸精砂分离产物；离火棒外壳与星核聚变芯核的原料。 */
    public static final DeferredItem<Item> STAR_CORE_PELLET = register("star_core_pellet");
    /** 星骸粉：残骸渣二段浸出的轻相，进辐照仓用强中子流转化。 */
    public static final DeferredItem<Item> STAR_ASH = register("star_ash");
    /** 星核渣：残骸渣二段浸出的重相，再酸浸 / 离心才得星核粒。 */
    public static final DeferredItem<Item> STAR_CORE_SLAG = register("star_core_slag");
    /** 粗离火素：强中子辐照产物，需要酸洗提纯。 */
    public static final DeferredItem<Item> RAW_EMBER_ESSENCE = register("raw_ember_essence");
    /** 离火素：粗离火素提纯后的离火棒核心。 */
    public static final DeferredItem<Item> EMBER_ESSENCE = register("ember_essence");
    /** 寂灰：辐照 / 燃尽的副产，可少量回收。 */
    public static final DeferredItem<Item> SILENT_ASH = register("silent_ash");
    /** 离火燃料棒外壳：星核粒 + 不熄合金。 */
    public static final DeferredItem<Item> EMBER_FUEL_ROD_SHELL = register("ember_fuel_rod_shell");
    /** 不熄合金锭：钨 + 锆合金 + 锝钢，热室熔炼。 */
    public static final DeferredItem<Item> UNQUENCHABLE_ALLOY = register("unquenchable_alloy");
    /** 不稳定离火燃料棒：起步通量 ×3.0。 */
    public static final DeferredItem<FuelRodItem> UNSTABLE_EMBER_ROD = rod(FuelSpec.UNSTABLE_EMBER);
    /** 稳定离火燃料棒：R0 1.2，寿命翻倍。 */
    public static final DeferredItem<FuelRodItem> STABLE_EMBER_ROD = rod(FuelSpec.STABLE_EMBER);
    /** 钍粉：钍棒原料（由放射性污泥回收）。 */
    public static final DeferredItem<Item> THORIUM_POWDER = register("thorium_powder");
    /** 锂粉：回旋加速器原料，轰击后产氚。 */
    public static final DeferredItem<Item> LITHIUM_DUST = register("lithium_dust");
    /** 氦-3：氦冷却剂离心分离产物，D-He3 芯核燃料。 */
    public static final DeferredItem<Item> HELIUM_3 = register("helium_3");
    /** 锂-6：锂粉同位素分离产物，氚增殖燃料。 */
    public static final DeferredItem<Item> LITHIUM_6 = register("lithium_6");
    /** 硼-11：碳化硼棒分离产物，p-B11 芯核燃料。 */
    public static final DeferredItem<Item> BORON_11 = register("boron_11");
    /** 钴粉：辐照仓原料，吸收中子后变钴-60。 */
    public static final DeferredItem<Item> COBALT_DUST = register("cobalt_dust");
    /** 钴-60：中子辐照产物（工业辐照 / 生化诱变）。 */
    public static final DeferredItem<Item> CO_60 = register("co_60");
    /** 锆-90：高放废料嬗变产物（稳定核素），可回炉做锆合金管。 */
    public static final DeferredItem<Item> ZR_90 = register("zr_90");
    /** 钼-95：高放废料嬗变产物（稳定核素），裂解催化剂载体。 */
    public static final DeferredItem<Item> MO_95 = register("mo_95");
    /** 钕-143：高放废料嬗变产物（稳定核素），稀土磁体原料。 */
    public static final DeferredItem<Item> ND_143 = register("nd_143");
    /** 乏燃料：按燃料族 / 档位分开，带余热组件；冷却后可进后处理厂。 */
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL = spent(FuelSpec.U_LOW);
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL_U_MID = spent(FuelSpec.U_MID);
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL_U_HIGH = spent(FuelSpec.U_HIGH);
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL_MOX_LOW = spent(FuelSpec.MOX_LOW);
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL_MOX_MID = spent(FuelSpec.MOX_MID);
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL_MOX_HIGH = spent(FuelSpec.MOX_HIGH);
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL_TH_LOW = spent(FuelSpec.TH_LOW);
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL_TH_MID = spent(FuelSpec.TH_MID);
    public static final DeferredItem<SpentFuelItem> SPENT_FUEL_TH_HIGH = spent(FuelSpec.TH_HIGH);
    /** §7.8 拓展棒的乏燃料。 */
    public static final DeferredItem<SpentFuelItem> SPENT_U233_SEED = spent(FuelSpec.U233_SEED);
    public static final DeferredItem<SpentFuelItem> SPENT_MA_ROD = spent(FuelSpec.MA_TRANSMUTE);
    public static final DeferredItem<SpentFuelItem> SPENT_PU_INERT = spent(FuelSpec.PU_INERT);
    public static final DeferredItem<SpentFuelItem> SPENT_POISON_ROD = spent(FuelSpec.BURNABLE_POISON);
    public static final DeferredItem<SpentFuelItem> SPENT_UC_ROD = spent(FuelSpec.UC_HIGH_POWER);
    public static final DeferredItem<SpentFuelItem> SPENT_UZR_ROD = spent(FuelSpec.UZR_METAL);
    /** 离火棒乏燃料（不稳定 / 稳定共用）。 */
    public static final DeferredItem<SpentFuelItem> SPENT_EMBER_ROD = spent(FuelSpec.UNSTABLE_EMBER);
    /** 钚：后处理产物（MOX / 高级材料）。 */
    public static final DeferredItem<Item> PLUTONIUM = register("plutonium");
    /** U-233：钍系后处理产物。 */
    public static final DeferredItem<Item> U233 = register("u233");
    /** 高放废料：需深埋或嬗变。 */
    public static final DeferredItem<Item> NUCLEAR_WASTE = register("nuclear_waste");
    /** 高放玻璃固化体：高放废料的玻璃固化产物，可进一步做屏蔽建材。 */
    public static final DeferredItem<Item> HLW_GLASS = register("hlw_glass");
    /** 短衰变废料：后处理中间体，离心后出锶 / 铯。 */
    public static final DeferredItem<Item> SHORT_LIVED_WASTE = register("short_lived_waste");
    /** 长衰变废料：后处理中间体，离心后出锝 / 铂族 / 稀土。 */
    public static final DeferredItem<Item> LONG_LIVED_WASTE = register("long_lived_waste");
    /** 锶-90：短衰变废料分离产物（同位素电源 / 研究）。 */
    public static final DeferredItem<Item> STRONTIUM_90 = register("strontium_90");
    /** 铯-137：短衰变废料分离产物（工业辐照 / 研究）。 */
    public static final DeferredItem<Item> CESIUM_137 = register("cesium_137");
    /** 锝-99：长衰变废料分离产物（耐蚀合金 / 催化剂）。 */
    public static final DeferredItem<Item> TECHNETIUM_99 = register("technetium_99");
    /** 锝钢锭：锝-99 + 钢板热压成的耐蚀高强合金 —— 粒子源整机 / 高功率同位素电源的必需结构件。 */
    public static final DeferredItem<Item> TECHNETIUM_STEEL = register("technetium_steel");
    /** 辐照硬化合金：离子注入机产物（嬗变室 / 对撞机整机的耐辐照结构件）。 */
    public static final DeferredItem<Item> HARDENED_ALLOY = register("hardened_alloy");
    /** 铂族金属：长衰变废料分离产物（催化剂 / 电子件）。 */
    public static final DeferredItem<Item> PLATINUM_GROUP = register("platinum_group");
    /** 铂族催化剂：裂变铂族 + 裂变稀土做的石化催化件。 */
    public static final DeferredItem<Item> PLATINUM_CATALYST = register("platinum_catalyst");
    /** 稀土磁体：裂变稀土做的加速器磁铁组件。 */
    public static final DeferredItem<Item> RARE_EARTH_MAGNET = register("rare_earth_magnet");
    /** 次锕系：MOX 乏燃料专属，ADS / 嬗变燃料。 */
    public static final DeferredItem<Item> MINOR_ACTINIDES = register("minor_actinides");
    /** 镎-237：次锕系分离产物，中子俘获 → 钚-238。 */
    public static final DeferredItem<Item> NEPTUNIUM_237 = register("neptunium_237");
    /** 钚-238：RTG 燃料（镎-237 + 中子）。 */
    public static final DeferredItem<Item> PLUTONIUM_238 = register("plutonium_238");
    /** 镅-241：Am-Be 中子源材料（中子压力罐的替代路线）。 */
    public static final DeferredItem<Item> AMERICIUM_241 = register("americium_241");
    /** 锔-244：高功率同位素电源燃料。 */
    public static final DeferredItem<Item> CURIUM_244 = register("curium_244");
    /** 碘-129：高放废料分离出的长寿命核素（嬗变靶）。 */
    public static final DeferredItem<Item> IODINE_129 = register("iodine_129");
    /** 钐-149：裂变产物分离出的中子毒物核素（嬗变靶 / 研究）。 */
    public static final DeferredItem<Item> SAMARIUM_149 = register("samarium_149");
    /** 超重元素前体：回旋加速器产物，对撞机合成超重元素的更省靶料路线。 */
    public static final DeferredItem<Item> SUPERHEAVY_PRECURSOR = register("superheavy_precursor");
    /** 鈇-298（Fl-298）：对撞机第一档超重核素（超重元素，终局结构件）。 */
    public static final DeferredItem<Item> SUPERHEAVY_ELEMENT = register("superheavy_element");
    /** 鉝-300（Lv-300）：第二档超重核素，加速环段材料。 */
    public static final DeferredItem<Item> LIVERMORIUM_300 = register("livermorium_300");
    /** 鎶-304（Cn-304）：第三档超重核素，反物质靶 / 湮灭堆稳定岛点火。 */
    public static final DeferredItem<Item> COPERNICIUM_304 = register("copernicium_304");
    /** 奇异物质：对撞机高能模式产物（终局反射棒升级）。 */
    public static final DeferredItem<Item> EXOTIC_MATTER = register("exotic_matter");
    /** 反物质：对撞机最高能模式产物（湮灭反应堆燃料）。 */
    public static final DeferredItem<Item> ANTIMATTER = register("antimatter");
    /** 超重反射棒耐久（普通反射棒的两倍）。 */
    public static final int SUPERHEAVY_REFLECTOR_DURABILITY = 4_000;
    /** 超重反射棒：超重元素 + 反射棒合成，每根按 <strong>2 根</strong>反射棒计，耐久 4,000。 */
    public static final DeferredItem<ReflectorRodItem> SUPERHEAVY_REFLECTOR_ROD = ITEMS.register(
            "superheavy_reflector_rod", key -> new ReflectorRodItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .durability(SUPERHEAVY_REFLECTOR_DURABILITY)));
    /** 奇异反射棒耐久（超重反射棒的两倍）。 */
    public static final int EXOTIC_REFLECTOR_DURABILITY = 8_000;
    /** 奇异反射棒：奇异物质 + 超重反射棒，每根按 <strong>4 根</strong>反射棒计，耐久 8,000。 */
    public static final DeferredItem<ReflectorRodItem> EXOTIC_REFLECTOR_ROD = ITEMS.register(
            "exotic_reflector_rod", key -> new ReflectorRodItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .durability(EXOTIC_REFLECTOR_DURABILITY)));
    /** 浸出渣：非铀矿酸浸副产；用 {@link FissionComponents#LEACH_RESIDUE_TYPE} 区分来源。 */
    public static final DeferredItem<Item> LEACH_RESIDUE = ITEMS.register("leach_residue",
            key -> new LeachResidueItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))));
    /** 放射性污泥：铀酸浸 / 萃余液结晶副产，可回收钍。 */
    public static final DeferredItem<Item> RADIOACTIVE_SLUDGE = register("radioactive_sludge");
    /** 重铀酸铵滤饼：萃取—沉淀中间体，煅烧前体。 */
    public static final DeferredItem<Item> ADU_CAKE = register("adu_cake");
    /** 二氧化铀粉：煅烧产物，铀转化（氟化）前体。 */
    public static final DeferredItem<Item> UO2_POWDER = register("uo2_powder");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<? extends Item>> ALL = List.of(
            YELLOWCAKE, U235, U238,
            ROD_LEU, ROD_MEU, ROD_HEU,
            MOX_FUEL_ROD, MOX_ROD_MID, MOX_ROD_HIGH,
            THORIUM_FUEL_ROD, THORIUM_ROD_MID, THORIUM_ROD_HIGH,
            U233_SEED_ROD, MA_TRANSMUTATION_ROD, PU_INERT_MATRIX_ROD,
            BURNABLE_POISON_ROD, UC_HIGH_POWER_ROD, UZR_METAL_ROD,
            UNSTABLE_EMBER_ROD, STABLE_EMBER_ROD,
            STAR_CORE_PELLET, STAR_ASH, STAR_CORE_SLAG, RAW_EMBER_ESSENCE, EMBER_ESSENCE, SILENT_ASH,
            EMBER_FUEL_ROD_SHELL, UNQUENCHABLE_ALLOY,
            THORIUM_POWDER, LITHIUM_DUST, HELIUM_3, LITHIUM_6, BORON_11,
            COBALT_DUST, CO_60, ZR_90, MO_95, ND_143,
            ZIRCALOY_TUBE, BORON_CARBIDE_ROD, GRAPHITE_ROD, SPENT_GRAPHITE_ROD,
            REFLECTOR_ROD, SUPERHEAVY_REFLECTOR_ROD, EXOTIC_REFLECTOR_ROD, RADIOACTIVE_SLAG,
            SPENT_FUEL, SPENT_FUEL_U_MID, SPENT_FUEL_U_HIGH,
            SPENT_FUEL_MOX_LOW, SPENT_FUEL_MOX_MID, SPENT_FUEL_MOX_HIGH,
            SPENT_FUEL_TH_LOW, SPENT_FUEL_TH_MID, SPENT_FUEL_TH_HIGH,
            SPENT_U233_SEED, SPENT_MA_ROD, SPENT_PU_INERT,
            SPENT_POISON_ROD, SPENT_UC_ROD, SPENT_UZR_ROD, SPENT_EMBER_ROD,
            PLUTONIUM, U233,
            NUCLEAR_WASTE, HLW_GLASS,
            SHORT_LIVED_WASTE, LONG_LIVED_WASTE,
            STRONTIUM_90, CESIUM_137, TECHNETIUM_99, TECHNETIUM_STEEL, HARDENED_ALLOY,
            PLATINUM_GROUP, PLATINUM_CATALYST, RARE_EARTH_MAGNET, MINOR_ACTINIDES,
            NEPTUNIUM_237, PLUTONIUM_238, AMERICIUM_241, CURIUM_244,
            IODINE_129, SAMARIUM_149,
            SUPERHEAVY_PRECURSOR, SUPERHEAVY_ELEMENT,
            LIVERMORIUM_300, COPERNICIUM_304, EXOTIC_MATTER, ANTIMATTER,
            LEACH_RESIDUE, RADIOACTIVE_SLUDGE, ADU_CAKE, UO2_POWDER);

    /** 反应堆可烧的燃料（9 根：铀 / MOX / 钍）。 */
    public static boolean isReactorFuel(Item item) {
        return item instanceof FuelRodItem;
    }

    /** 某档燃料燃尽后对应的乏燃料物品。 */
    public static Item spentItem(FuelSpec spec) {
        return switch (spec) {
            case U_LOW -> SPENT_FUEL.get();
            case U_MID -> SPENT_FUEL_U_MID.get();
            case U_HIGH -> SPENT_FUEL_U_HIGH.get();
            case MOX_LOW -> SPENT_FUEL_MOX_LOW.get();
            case MOX_MID -> SPENT_FUEL_MOX_MID.get();
            case MOX_HIGH -> SPENT_FUEL_MOX_HIGH.get();
            case TH_LOW -> SPENT_FUEL_TH_LOW.get();
            case TH_MID -> SPENT_FUEL_TH_MID.get();
            case TH_HIGH -> SPENT_FUEL_TH_HIGH.get();
            case U233_SEED -> SPENT_U233_SEED.get();
            case MA_TRANSMUTE -> SPENT_MA_ROD.get();
            case PU_INERT -> SPENT_PU_INERT.get();
            case BURNABLE_POISON -> SPENT_POISON_ROD.get();
            case UC_HIGH_POWER -> SPENT_UC_ROD.get();
            case UZR_METAL -> SPENT_UZR_ROD.get();
            case UNSTABLE_EMBER, STABLE_EMBER -> SPENT_EMBER_ROD.get();
        };
    }

    /** 是否石墨棒（裂变堆的慢化 / 通量控制件）。 */
    public static boolean isGraphiteRod(Item item) {
        return item == GRAPHITE_ROD.get();
    }

    /** 是否反射棒。 */
    public static boolean isReflectorRod(Item item) {
        return item == REFLECTOR_ROD.get() || item == SUPERHEAVY_REFLECTOR_ROD.get()
                || item == EXOTIC_REFLECTOR_ROD.get();
    }

    /** 一根反射棒的当量（奇异 = 4，超重 = 2，普通 = 1）。 */
    public static int reflectorWeight(Item item) {
        if (item == EXOTIC_REFLECTOR_ROD.get()) {
            return 4;
        }
        return item == SUPERHEAVY_REFLECTOR_ROD.get() ? 2 : 1;
    }

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    /** 石墨棒：耐久制（堆叠 1）。 */
    private static DeferredItem<Item> graphiteRod() {
        return ITEMS.register("graphite_rod", key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .durability(20_000)));
    }

    /** 燃料棒：耐久制（堆叠 1 由原版耐久组件保证）。 */
    private static DeferredItem<FuelRodItem> rod(FuelSpec spec) {
        return ITEMS.<FuelRodItem>register(spec.rodId(), key -> new FuelRodItem(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .durability(spec.maxDamage()), spec));
    }

    /** 乏燃料：堆叠 1（余热不同不堆叠），带来源档位。 */
    private static DeferredItem<SpentFuelItem> spent(FuelSpec spec) {
        return ITEMS.<SpentFuelItem>register(spec.spentId(), key -> new SpentFuelItem(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .stacksTo(1), spec));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private FissionMaterials() {}
}
