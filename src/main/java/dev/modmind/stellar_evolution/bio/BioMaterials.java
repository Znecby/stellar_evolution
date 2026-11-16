package dev.modmind.stellar_evolution.bio;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 生化线的<strong>物品类物质</strong>（《生化线》B0 ①：物质注册与贴图规范；E1 扩表见《产线蓝图》§3.2）。
 *
 * <p>清单与用途（详见《生化线》§4 / 《产线蓝图》§3.2）：
 * <ul>
 *   <li>{@link #BIOMASS 生物质} —— 生化线**总原料**，由机械土壤的收割产物换得（B0 ④）；</li>
 *   <li>{@link #CULTURE_STRAIN 菌种} —— 发酵 / 反应器的「模具」（活性 / 耐久机制留到后续批次）；</li>
 *   <li>{@link #MINERAL_SALT 矿物盐} —— 培养基配方原料（本期先用合成配方，石化副产留到石化线）；</li>
 *   <li>{@link #DRIED_ENZYME 固体酶} —— 结晶机产出的稳定化催化剂（便携 / 可堆叠）。</li>
 *   <li><strong>E1 新增 7 种</strong>：木质纤维 / 氨基酸粉 / 蛋白粉 / 生物塑料颗粒 / 干燥菌粉 / 维生素 / 有机肥
 *       （糖已改用原版 {@code minecraft:sugar}）。</li>
 * </ul>
 * <p>贴图由 {@code tools/gen_bio_materials.ps1} 生成（16×16 程序化图标，零外部素材）；双语名在 lang 里。
 */
public final class BioMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 生物质：生化线总原料。 */
    public static final DeferredItem<Item> BIOMASS = register("biomass");
    /** 菌种：发酵 / 反应器的「模具」。 */
    public static final DeferredItem<Item> CULTURE_STRAIN = register("culture_strain");
    /** 矿物盐：培养基配方原料。 */
    public static final DeferredItem<Item> MINERAL_SALT = register("mineral_salt");
    /** 固体酶：结晶机产出的稳定化催化剂。 */
    public static final DeferredItem<Item> DRIED_ENZYME = register("dried_enzyme");

    // ---- E1 新增（《产线蓝图》§3.2）
    /** 木质纤维：木质原料预处理产物 → 糖化 / 有机肥。 */
    public static final DeferredItem<Item> LIGNOCELLULOSE = register("lignocellulose");
    /** 氨基酸粉：氨基酸液结晶产物 → 药链 / 血肉线。 */
    public static final DeferredItem<Item> AMINO_POWDER = register("amino_powder");
    /** 蛋白粉：蛋白溶液结晶产物 → 高级试剂 / 血肉线。 */
    public static final DeferredItem<Item> PROTEIN_POWDER = register("protein_powder");
    /** 生物塑料颗粒：聚合产物 → 电路基材外壳 / 容器。 */
    public static final DeferredItem<Item> BIOPLASTIC_PELLETS = register("bioplastic_pellets");
    /** 干燥菌粉：菌液结晶产物 → 再生菌种（省菌种）。 */
    public static final DeferredItem<Item> DRIED_STRAIN = register("dried_strain");
    /** 维生素：生物反应产物 → 药链 / 血肉线。 */
    public static final DeferredItem<Item> VITAMINS = register("vitamins");
    /** 有机肥：废渣产物 → 回田（喂机械土壤肥料点数池）。 */
    public static final DeferredItem<Item> ORGANIC_FERTILIZER = register("organic_fertilizer");

    // ---- 生化 3（辐照仓）：菌种升级
    /** 高产菌种：质子束诱变的高产菌株（生物反应釜收率提升）。 */
    public static final DeferredItem<Item> HIGH_YIELD_STRAIN = register("high_yield_strain");
    /** 高产酶菌种：中子束选育的产酶菌株（酶溶液增产）。 */
    public static final DeferredItem<Item> HIGH_YIELD_ENZYME_STRAIN = register("high_yield_enzyme_strain");
    /** 极端环境菌种：铅离子选育的耐高温菌株（高温带醋酸增产）。 */
    public static final DeferredItem<Item> EXTREMOPHILE_STRAIN = register("extremophile_strain");
    /** 手性中间体：极化质子束处理氨基酸得到的手性药物中间体。 */
    public static final DeferredItem<Item> CHIRAL_INTERMEDIATE = register("chiral_intermediate");
    /** 放射性示踪剂：放射性离子标记的诊断 / 监测用试剂。 */
    public static final DeferredItem<Item> RADIOACTIVE_TRACER = register("radioactive_tracer");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            BIOMASS, CULTURE_STRAIN, MINERAL_SALT, DRIED_ENZYME,
            LIGNOCELLULOSE, AMINO_POWDER, PROTEIN_POWDER,
            BIOPLASTIC_PELLETS, DRIED_STRAIN, VITAMINS, ORGANIC_FERTILIZER,
            HIGH_YIELD_STRAIN, HIGH_YIELD_ENZYME_STRAIN, EXTREMOPHILE_STRAIN,
            CHIRAL_INTERMEDIATE, RADIOACTIVE_TRACER);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private BioMaterials() {}
}
