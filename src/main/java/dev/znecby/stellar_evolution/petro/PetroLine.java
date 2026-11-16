package dev.znecby.stellar_evolution.petro;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.air.AirFluids;
import dev.znecby.stellar_evolution.bio.BioFluids;
import dev.znecby.stellar_evolution.bio.BioMaterials;
import dev.znecby.stellar_evolution.coal.CoalFluids;
import dev.znecby.stellar_evolution.coal.CoalMaterials;
import dev.znecby.stellar_evolution.fission.FissionFluids;
import dev.znecby.stellar_evolution.fission.FissionMaterials;
import dev.znecby.stellar_evolution.material.LeadMaterials;
import dev.znecby.stellar_evolution.material.MaterialItems;
import dev.znecby.stellar_evolution.material.TungstenMaterials;
import dev.znecby.stellar_evolution.ore.OreMaterials;
import dev.znecby.stellar_evolution.ore.OreFluids;
import dev.znecby.stellar_evolution.welding.WeldingFluids;
import dev.znecby.stellar_evolution.wood.WoodFluids;
import dev.znecby.stellar_evolution.wood.WoodMaterials;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 石化<strong>产线配置表</strong>（唯一事实源）—— 照 HBM 的口径：<strong>机器 = 通用处理器</strong>，
 * 玩法由「每台机器一张配方表」决定，表<strong>按输入分派</strong>（一行一个反应），加材料只加一行，
 * 不改机器、不改代码分支。
 *
 * <p>结构：
 * <ul>
 *   <li>{@link Row}：一行 = 多路流体 / 物品输入 → 多路流体 / 物品输出 + 一句展示文案；</li>
 *   <li><strong>全部行都是定值配方</strong>（进料多少、产出多少写死在表里），网络热流率<strong>只决定倍速</strong>，
 *       不参与产率计算 —— 这样加速机制（堆热压差）才能线性生效；</li>
 *   <li>{@link #rows(PetroMachineKind)}：某机种的行表，<strong>按声明顺序</strong>结算（先命中先算）。</li>
 * </ul>
 *
 * <p>本表只描述「产线」，不含任何机器 / 热力 / 物流机制（那些仍在 {@link PetroMachineBlockEntity}
 * 与 {@link PetroRecipes}）。
 */
public final class PetroLine {
    /** 一行里的流体输入（量为 mB）。 */
    public record FluidIn(FluidResource resource, int amount) {}
    /**
     * 一行里的物品输入（量为个数）。
     *
     * @param tag 非空时按<strong>物品标签</strong>匹配（{@code resource} 只作为 JEI 展示代表）
     */
    public record ItemIn(ItemResource resource, int count, @Nullable TagKey<Item> tag) {
        /** 无标签的普通物品输入。 */
        public ItemIn(ItemResource resource, int count) {
            this(resource, count, null);
        }
    }
    /** 一行里的流体输出（量为 mB）。 */
    public record FluidOut(FluidResource resource, int amount) {}
    /** 一行里的物品输出（量为个数）。 */
    public record ItemOut(ItemResource resource, int count) {}

    /**
     * 一行配方。
     *
     * @param id 稳定 id（JEI 内联 id / 调试用）
     * @param kind 承载它的机种
     * @param note 展示文案（JEI 备注行 / {@code /se petro recipes}）
     * @param catalyst 可选<strong>催化剂</strong>：匹配时只检查输入容器里有没有，<strong>不消耗</strong>；
     *                 {@code null} = 无催化剂
     */
    public record Row(String id, PetroMachineKind kind, String note,
                      List<FluidIn> fluidIn, List<ItemIn> itemIn,
                      List<FluidOut> fluidOut, List<ItemOut> itemOut, Item catalyst) {

        /** 无催化剂的重载（保持旧表行写法不变）。 */
        public Row(String id, PetroMachineKind kind, String note,
                   List<FluidIn> fluidIn, List<ItemIn> itemIn,
                   List<FluidOut> fluidOut, List<ItemOut> itemOut) {
            this(id, kind, note, fluidIn, itemIn, fluidOut, itemOut, null);
        }
    }

    // ------------------------------------------------------------------ 表

    private static final Map<PetroMachineKind, List<Row>> TABLES = new EnumMap<>(PetroMachineKind.class);

    private static void put(PetroMachineKind kind, Row... rows) {
        TABLES.put(kind, List.of(rows));
    }

    /** 往已有机器表追加行（跨线复用：裂变线借石化机器做煅烧 / 精馏 / 水解 / 压片）。 */
    private static void add(PetroMachineKind kind, Row... rows) {
        List<Row> merged = new ArrayList<>(TABLES.getOrDefault(kind, List.of()));
        merged.addAll(List.of(rows));
        TABLES.put(kind, List.copyOf(merged));
    }

    static {
        // ---- 分馏塔：原油 → 五路馏分 + 硫
        put(PetroMachineKind.DISTILLATION_TOWER,
                new Row("distill_crude", PetroMachineKind.DISTILLATION_TOWER,
                        "原油 1000 → 石油气 150 + 石脑油 250 + 煤油 200 + 柴油 250 + 重油 200 + 沥青 75 + 硫粉 1",
                        List.of(fin(PetroFluids.CRUDE_OIL, 1000)), List.of(),
                        List.of(fout(PetroFluids.PETROLEUM_GAS, 150), fout(PetroFluids.NAPHTHA, 250),
                                fout(PetroFluids.KEROSENE, 200), fout(PetroFluids.DIESEL, 250),
                                fout(PetroFluids.HEAVY_OIL, 200), fout(PetroFluids.BITUMEN, 75)),
                        List.of(iout(PetroMaterials.SULFUR_DUST.get(), 1))));

        // ---- 液态空气分馏（钢格栅供气）：氮 / 氧 / 氩 / 氦
        add(PetroMachineKind.DISTILLATION_TOWER,
                new Row("separate_air", PetroMachineKind.DISTILLATION_TOWER,
                        "液态空气 5000 → 氮气 3840 + 氧气 1050 + 氩气 100 + 氦气 10（低温分馏）",
                        List.of(fin(AirFluids.LIQUID_AIR.source().get(), 5000)), List.of(),
                        List.of(fout(AirFluids.NITROGEN.source().get(), 3840),
                                fout(WeldingFluids.OXYGEN.source().get(), 1050),
                                fout(AirFluids.ARGON.source().get(), 100),
                                fout(FissionFluids.HELIUM_COOLANT, 10)),
                        List.of()));

        // ---- 裂解炉：石脑油 / 重油 → 烯烃；沥青 → 石油焦 + 轻油（焦化）
        put(PetroMachineKind.CRACKING_FURNACE,
                new Row("crack_naphtha", PetroMachineKind.CRACKING_FURNACE,
                        "石脑油 1000 → 乙烯 330 + 丙烯 220 + 重油 450（锝-99 催化）",
                        List.of(fin(PetroFluids.NAPHTHA, 1000)), List.of(),
                        List.of(fout(PetroFluids.ETHYLENE, 330), fout(PetroFluids.PROPYLENE, 220),
                                fout(PetroFluids.HEAVY_OIL, 450)),
                        List.of(), FissionMaterials.TECHNETIUM_99.get()),
                new Row("crack_naphtha_gasoline", PetroMachineKind.CRACKING_FURNACE,
                        "石脑油 1000 → 普通汽油 700 + 石油气 150（热裂化）",
                        List.of(fin(PetroFluids.NAPHTHA, 1000)), List.of(),
                        List.of(fout(PetroFluids.GASOLINE, 700), fout(PetroFluids.PETROLEUM_GAS, 150)),
                        List.of()),
                new Row("crack_heavy_oil", PetroMachineKind.CRACKING_FURNACE,
                        "重油 1000 → 乙烯 330 + 丙烯 220 + 沥青 450",
                        List.of(fin(PetroFluids.HEAVY_OIL, 1000)), List.of(),
                        List.of(fout(PetroFluids.ETHYLENE, 330), fout(PetroFluids.PROPYLENE, 220),
                                fout(PetroFluids.BITUMEN, 450)),
                        List.of()),
                new Row("coke_needle", PetroMachineKind.CRACKING_FURNACE,
                        "沥青 1000 → 针状焦 ×1 + 轻油 150（延迟焦化，针状焦前驱）",
                        List.of(fin(PetroFluids.BITUMEN, 1000)), List.of(),
                        List.of(fout(PetroFluids.LIGHT_OIL, 150)),
                        List.of(iout(CoalMaterials.NEEDLE_COKE.get(), 1))),
                new Row("coke_bitumen", PetroMachineKind.CRACKING_FURNACE,
                        "沥青 1000 → 轻油 200 + 石油焦 1（焦化，收率参考 HBM 后上调）",
                        List.of(fin(PetroFluids.BITUMEN, 1000)), List.of(),
                        List.of(fout(PetroFluids.LIGHT_OIL, 200)),
                        List.of(iout(PetroMaterials.PETROLEUM_COKE.get(), 1))),
                new Row("crack_light_oil", PetroMachineKind.CRACKING_FURNACE,
                        "轻油 1000 → 石脑油 500 + 丙烯 200 + 乙烯 100（轻油裂化）",
                        List.of(fin(PetroFluids.LIGHT_OIL, 1000)), List.of(),
                        List.of(fout(PetroFluids.NAPHTHA, 500), fout(PetroFluids.PROPYLENE, 200),
                                fout(PetroFluids.ETHYLENE, 100)),
                        List.of()));

        // ---- 重整器：石脑油 → 苯 / 煤油 / 氢气；重油 → 润滑油；柴油 + 氢气 → 低硫油 + 酸性气（加氢）
        put(PetroMachineKind.REFORMER,
                new Row("reform_naphtha", PetroMachineKind.REFORMER,
                        "石脑油 1000 → 苯 300 + 煤油 200 + 重整油 300 + 氢气 100（铂族催化）",
                        List.of(fin(PetroFluids.NAPHTHA, 1000)), List.of(),
                        List.of(fout(PetroFluids.BENZENE, 300), fout(PetroFluids.KEROSENE, 200),
                                fout(PetroFluids.REFORMATE, 300), fout(PetroFluids.HYDROGEN, 100)),
                        List.of(), FissionMaterials.PLATINUM_CATALYST.get()),
                new Row("reform_heavy_oil", PetroMachineKind.REFORMER,
                        "重油 1000 → 润滑油 500",
                        List.of(fin(PetroFluids.HEAVY_OIL, 1000)), List.of(),
                        List.of(fout(PetroFluids.LUBRICANT, 500)),
                        List.of()),
                new Row("hydro_diesel", PetroMachineKind.REFORMER,
                        "柴油 1000 + 氢气 100 → 低硫油 900 + 酸性气 150（加氢脱硫）",
                        List.of(fin(PetroFluids.DIESEL, 1000), fin(PetroFluids.HYDROGEN, 100)), List.of(),
                        List.of(fout(PetroFluids.LOW_SULFUR_OIL, 900), fout(PetroFluids.SOUR_GAS, 150)),
                        List.of(), BioMaterials.AMINO_POWDER.get()),
                new Row("reform_light_oil", PetroMachineKind.REFORMER,
                        "轻油 1000 → 苯 500 + 石脑油 100 + 氢气 150（轻油重整，参考 HBM）",
                        List.of(fin(PetroFluids.LIGHT_OIL, 1000)), List.of(),
                        List.of(fout(PetroFluids.BENZENE, 500), fout(PetroFluids.NAPHTHA, 100),
                                fout(PetroFluids.HYDROGEN, 150)),
                        List.of()));

        // ---- 聚合釜：单体 → 树脂 / 橡胶（塑料 4 + 橡胶 4）
        put(PetroMachineKind.POLYMERIZER,
                new Row("poly_bakelite", PetroMachineKind.POLYMERIZER,
                        "苯酚 500 + 甲醛 500 → 电木 ×2",
                        List.of(fin(PetroFluids.PHENOL, 500), fin(PetroFluids.FORMALDEHYDE, 500)), List.of(),
                        List.of(), List.of(iout(PetroMaterials.BAKELITE.get(), 2))),
                new Row("poly_pmma", PetroMachineKind.POLYMERIZER,
                        "甲基丙烯酸甲酯 1000 → 有机玻璃 ×2",
                        List.of(fin(PetroFluids.MMA, 1000)), List.of(),
                        List.of(), List.of(iout(PetroMaterials.PMMA.get(), 2))),
                new Row("poly_ptfe", PetroMachineKind.POLYMERIZER,
                        "四氟乙烯 1000 → 聚四氟乙烯 ×3（难获取 / 易量产）",
                        List.of(fin(PetroFluids.TETRAFLUOROETHYLENE, 1000)), List.of(),
                        List.of(), List.of(iout(PetroMaterials.PTFE.get(), 3))),
                new Row("poly_nylon", PetroMachineKind.POLYMERIZER,
                        "己内酰胺 1000 → 尼龙 ×2",
                        List.of(fin(PetroFluids.CAPROLACTAM, 1000)), List.of(),
                        List.of(), List.of(iout(PetroMaterials.NYLON.get(), 2))),
                new Row("poly_sbr", PetroMachineKind.POLYMERIZER,
                        "丁二烯 600 + 苯乙烯 400 → 丁苯橡胶 ×3（难获取 / 易量产）",
                        List.of(fin(PetroFluids.BUTADIENE, 600), fin(PetroFluids.STYRENE, 400)), List.of(),
                        List.of(), List.of(iout(PetroMaterials.SBR.get(), 3)), MaterialItems.COPPER_PLATE.get()),
                new Row("poly_nbr", PetroMachineKind.POLYMERIZER,
                        "丁二烯 700 + 丙烯腈 300 → 丁腈橡胶 ×3（难获取 / 易量产）",
                        List.of(fin(PetroFluids.BUTADIENE, 700), fin(PetroFluids.ACRYLONITRILE, 300)), List.of(),
                        List.of(), List.of(iout(PetroMaterials.NBR.get(), 3)), MaterialItems.IRON_PLATE.get()),
                new Row("poly_cr", PetroMachineKind.POLYMERIZER,
                        "氯丁二烯 1000 → 氯丁橡胶 ×1（易获取 / 难量产）",
                        List.of(fin(PetroFluids.CHLOROPRENE, 1000)), List.of(),
                        List.of(), List.of(iout(PetroMaterials.CR.get(), 1))),
                new Row("poly_silicone", PetroMachineKind.POLYMERIZER,
                        "硅氧烷单体 1000 → 硅橡胶 ×1（易获取 / 难量产）",
                        List.of(fin(PetroFluids.SILOXANE, 1000)), List.of(),
                        List.of(), List.of(iout(PetroMaterials.SILICONE_RUBBER.get(), 1))),
                new Row("poly_pvc", PetroMachineKind.POLYMERIZER,
                        "氯乙烯 1000 → 聚氯乙烯 ×2（悬浮聚合，T2 塑料）",
                        List.of(fin(CoalFluids.VCM, 1000)), List.of(),
                        List.of(), List.of(iout(CoalMaterials.PVC.get(), 2))));

        // ================================================================ 木化线（热解炉口径；不新增机器）
        // 木质纤维（粉碎任意原木）→ 木炭 + 木醋液（醋酸）+ 木焦油（苯酚）+ 木煤气（沼气）
        add(PetroMachineKind.CRACKING_FURNACE,
                new Row("pyrolyze_lignocellulose", PetroMachineKind.CRACKING_FURNACE,
                        "木质纤维 ×4 → 木炭 ×1 + 醋酸 300 + 苯酚 200 + 沼气 300（木醋液 / 木焦油 / 木煤气）",
                        List.of(), List.of(iin(BioMaterials.LIGNOCELLULOSE.get(), 4)),
                        List.of(fout(BioFluids.ACETIC_ACID, 300), fout(PetroFluids.PHENOL, 200),
                                fout(BioFluids.BIOGAS, 300)),
                        List.of(iout(Items.CHARCOAL, 1))),
                new Row("calcine_charcoal", PetroMachineKind.CRACKING_FURNACE,
                        "木炭粉 ×2 → 石油焦 ×1（木质碳源，可替代沥青焦化）",
                        List.of(), List.of(iin(CoalMaterials.CHARCOAL_DUST.get(), 2)),
                        List.of(), List.of(iout(PetroMaterials.PETROLEUM_COKE.get(), 1))),
                new Row("tfe_from_ethylene", PetroMachineKind.CRACKING_FURNACE,
                        "乙烯 500 + 氟气 500 → 四氟乙烯 400 + 氟化氢 400（高温氟化）",
                        List.of(fin(PetroFluids.ETHYLENE, 500), fin(FissionFluids.FLUORINE, 500)), List.of(),
                        List.of(fout(PetroFluids.TETRAFLUOROETHYLENE, 400),
                                fout(FissionFluids.HYDROGEN_FLUORIDE, 400)),
                        List.of()));

        // ================================================================ 单体上游（补 10 种聚合单体；全部复用现有机器）
        add(PetroMachineKind.REFORMER,
                new Row("caprolactam_from_phenol", PetroMachineKind.REFORMER,
                        "苯酚 500 + 氢气 300 + 氨水 300 → 己内酰胺 500 + 水 300",
                        List.of(fin(PetroFluids.PHENOL, 500), fin(PetroFluids.HYDROGEN, 300),
                                fin(FissionFluids.AMMONIA_SOLUTION, 300)), List.of(),
                        List.of(fout(PetroFluids.CAPROLACTAM, 500), fout(Fluids.WATER, 300)),
                        List.of(), BioMaterials.PROTEIN_POWDER.get()),
                new Row("mma_from_acetone", PetroMachineKind.REFORMER,
                        "丙酮 500 + 甲醛 500 + 氨水 500 → 甲基丙烯酸甲酯 500 + 水 500",
                        List.of(fin(BioFluids.ACETONE, 500), fin(PetroFluids.FORMALDEHYDE, 500),
                                fin(FissionFluids.AMMONIA_SOLUTION, 500)), List.of(),
                        List.of(fout(PetroFluids.MMA, 500), fout(Fluids.WATER, 500)),
                        List.of(), BioMaterials.DRIED_STRAIN.get()),
                new Row("styrene_from_benzene", PetroMachineKind.REFORMER,
                        "苯 500 + 乙烯 500 → 苯乙烯 600 + 氢气 200",
                        List.of(fin(PetroFluids.BENZENE, 500), fin(PetroFluids.ETHYLENE, 500)), List.of(),
                        List.of(fout(PetroFluids.STYRENE, 600), fout(PetroFluids.HYDROGEN, 200)),
                        List.of(), Items.GLASS),
                new Row("cumene_phenol", PetroMachineKind.REFORMER,
                        "苯 500 + 丙烯 500 + 水 500 → 苯酚 400 + 丙酮 300（异丙苯法）",
                        List.of(fin(PetroFluids.BENZENE, 500), fin(PetroFluids.PROPYLENE, 500),
                                fin(Fluids.WATER, 500)), List.of(),
                        List.of(fout(PetroFluids.PHENOL, 400), fout(BioFluids.ACETONE, 300)),
                        List.of(), Items.GLOWSTONE_DUST),
                new Row("acrylonitrile_from_propylene", PetroMachineKind.REFORMER,
                        "丙烯 500 + 氨水 300 → 丙烯腈 400 + 水 300（氨氧化）",
                        List.of(fin(PetroFluids.PROPYLENE, 500), fin(FissionFluids.AMMONIA_SOLUTION, 300)), List.of(),
                        List.of(fout(PetroFluids.ACRYLONITRILE, 400), fout(Fluids.WATER, 300)),
                        List.of(), BioMaterials.VITAMINS.get()),
                new Row("butadiene_from_ethylene", PetroMachineKind.REFORMER,
                        "乙烯 1000 → 丁二烯 500 + 氢气 300",
                        List.of(fin(PetroFluids.ETHYLENE, 1000)), List.of(),
                        List.of(fout(PetroFluids.BUTADIENE, 500), fout(PetroFluids.HYDROGEN, 300)),
                        List.of(), Items.REDSTONE),
                new Row("siloxane_from_quartz", PetroMachineKind.REFORMER,
                        "石英 ×4 + 沼气 500 → 硅氧烷单体 400 + 水 300",
                        List.of(fin(BioFluids.BIOGAS, 500)), List.of(iin(Items.QUARTZ, 4)),
                        List.of(fout(PetroFluids.SILOXANE, 400), fout(Fluids.WATER, 300)),
                        List.of(), Items.CHARCOAL),
                new Row("chloroprene_from_butadiene", PetroMachineKind.REFORMER,
                        "丁二烯 500 + 矿物盐 ×2 → 氯丁二烯 400 + 水 200",
                        List.of(fin(PetroFluids.BUTADIENE, 500)),
                        List.of(iin(BioMaterials.MINERAL_SALT.get(), 2)),
                        List.of(fout(PetroFluids.CHLOROPRENE, 400), fout(Fluids.WATER, 200)), List.of()));

        // ================================================================ 精制燃料（三台燃烧类发电机组）
        // 重整器只做「改质 / 调合」：高辛烷汽油、含铅汽油、航空煤油；
        // 高十六烷柴油（要四硝基甲烷）与三级火箭燃料放到合成塔（最复杂的化学段）。
        add(PetroMachineKind.REFORMER,
                new Row("refine_high_octane_gasoline", PetroMachineKind.REFORMER,
                        "重整油 600 + 苯 200 + 普通汽油 200 → 高辛烷值汽油 900（重整调合）",
                        List.of(fin(PetroFluids.REFORMATE, 600), fin(PetroFluids.BENZENE, 200),
                                fin(PetroFluids.GASOLINE, 200)), List.of(),
                        List.of(fout(PetroFluids.HIGH_OCTANE_GASOLINE, 900)),
                        List.of(), MaterialItems.COPPER_PLATE.get()),
                new Row("refine_leaded_gasoline", PetroMachineKind.REFORMER,
                        "高辛烷值汽油 1000 + 铅粉 ×1 → 含铅汽油 1000（抗爆添加剂）",
                        List.of(fin(PetroFluids.HIGH_OCTANE_GASOLINE, 1000)),
                        List.of(iin(LeadMaterials.LEAD_DUST.get(), 1)),
                        List.of(fout(PetroFluids.LEADED_GASOLINE, 1000)),
                        List.of(), Items.CLAY_BALL),
                new Row("refine_aviation_kerosene", PetroMachineKind.REFORMER,
                        "煤油 1000 + 氢气 100 → 航空煤油 900（加氢精制）",
                        List.of(fin(PetroFluids.KEROSENE, 1000), fin(PetroFluids.HYDROGEN, 100)), List.of(),
                        List.of(fout(PetroFluids.AVIATION_KEROSENE, 900)),
                        List.of(), Items.GLASS));

        // 甲醛放在聚合釜：沼气 → 甲醛 → 电木 → 磁通线圈 → 重整器。
        // 若放在重整器里，会形成「重整器要电木、电木要甲醛、甲醛要重整器」的死循环。
        add(PetroMachineKind.POLYMERIZER,
                new Row("formaldehyde_from_biogas", PetroMachineKind.POLYMERIZER,
                        "沼气 1000 + 水 500 → 甲醛 500 + 氢气 300（甲烷部分氧化；聚合釜前置）",
                        List.of(fin(BioFluids.BIOGAS, 1000), fin(Fluids.WATER, 500)), List.of(),
                        List.of(fout(PetroFluids.FORMALDEHYDE, 500), fout(PetroFluids.HYDROGEN, 300)),
                        List.of(), Items.COAL));

        // ================================================================ 裂变线复用（不加新机器）
        // 裂解炉 = 煅烧炉；芯块压制已移到工作台（9 格配比）。
        add(PetroMachineKind.CRACKING_FURNACE,
                new Row("calcine_adu", PetroMachineKind.CRACKING_FURNACE,
                        "重铀酸铵滤饼 ×1 → 二氧化铀粉 ×1",
                        List.of(), List.of(iin(FissionMaterials.ADU_CAKE.get(), 1)),
                        List.of(), List.of(iout(FissionMaterials.UO2_POWDER.get(), 1))));

        // ================================================================ 选冶线（G 线，复用裂解炉 / 合成塔）
        // ① 金属精砂 + 石油焦 → 锭（1:1 直还原）
        add(PetroMachineKind.CRACKING_FURNACE,
                new Row("smelt_iron_sand", PetroMachineKind.CRACKING_FURNACE,
                        "铁精砂 ×1 + 石油焦 ×1 → 铁锭 ×1",
                        List.of(), List.of(iin(OreMaterials.IRON_CONCENTRATE_SAND.get(), 1),
                                iin(PetroMaterials.PETROLEUM_COKE.get(), 1)),
                        List.of(), List.of(iout(Items.IRON_INGOT, 1))),
                new Row("smelt_copper_sand", PetroMachineKind.CRACKING_FURNACE,
                        "铜精砂 ×1 + 石油焦 ×1 → 铜锭 ×1 + 硫粉 ×1",
                        List.of(), List.of(iin(OreMaterials.COPPER_CONCENTRATE_SAND.get(), 1),
                                iin(PetroMaterials.PETROLEUM_COKE.get(), 1)),
                        List.of(), List.of(iout(Items.COPPER_INGOT, 1),
                                iout(PetroMaterials.SULFUR_DUST.get(), 1))),
                new Row("smelt_gold_sand", PetroMachineKind.CRACKING_FURNACE,
                        "金精砂 ×1 + 石油焦 ×1 → 金锭 ×1",
                        List.of(), List.of(iin(OreMaterials.GOLD_CONCENTRATE_SAND.get(), 1),
                                iin(PetroMaterials.PETROLEUM_COKE.get(), 1)),
                        List.of(), List.of(iout(Items.GOLD_INGOT, 1))),
                new Row("smelt_uranium_sand", PetroMachineKind.CRACKING_FURNACE,
                        "铀精砂 ×1 + 石油焦 ×1 → 铀锭 ×1",
                        List.of(), List.of(iin(OreMaterials.URANIUM_CONCENTRATE_SAND.get(), 1),
                                iin(PetroMaterials.PETROLEUM_COKE.get(), 1)),
                        List.of(), List.of(iout(OreMaterials.URANIUM_INGOT.get(), 1))),
                new Row("smelt_tungsten_sand", PetroMachineKind.CRACKING_FURNACE,
                        "钨精砂 ×1 + 石油焦 ×1 → 钨锭 ×1",
                        List.of(), List.of(iin(TungstenMaterials.TUNGSTEN_CONCENTRATE_SAND.get(), 1),
                                iin(PetroMaterials.PETROLEUM_COKE.get(), 1)),
                        List.of(), List.of(iout(TungstenMaterials.TUNGSTEN_INGOT.get(), 1))),
                new Row("smelt_lead_sand", PetroMachineKind.CRACKING_FURNACE,
                        "铅精砂 ×1 + 石油焦 ×1 → 铅锭 ×1",
                        List.of(), List.of(iin(LeadMaterials.LEAD_CONCENTRATE_SAND.get(), 1),
                                iin(PetroMaterials.PETROLEUM_COKE.get(), 1)),
                        List.of(), List.of(iout(LeadMaterials.LEAD_INGOT.get(), 1))),
                new Row("smelt_debris_sand", PetroMachineKind.CRACKING_FURNACE,
                        "残骸精砂 ×1 + 石油焦 ×1 → 下界合金碎片 ×1",
                        List.of(), List.of(iin(OreMaterials.DEBRIS_CONCENTRATE_SAND.get(), 1),
                                iin(PetroMaterials.PETROLEUM_COKE.get(), 1)),
                        List.of(), List.of(iout(Items.NETHERITE_SCRAP, 1))));

        // ② 制酸已移到生化线：发酵罐（高温带 + 矿物盐催化）→ 过氧化氢；反应器 + 硫粉 → 硫酸

        // ================================================================ 煤化工线（C 线）
        // ① 干馏（焦化炉，3×3×4 砖块多方块）：煤 / 原木绝氧干馏
        put(PetroMachineKind.COKE_OVEN,
                new Row("coke_coal", PetroMachineKind.COKE_OVEN,
                        "煤 ×4 → 焦炭 ×2 + 煤焦油 500 + 焦炉煤气 800（绝氧干馏）",
                        List.of(), List.of(iin(Items.COAL, 4)),
                        List.of(fout(CoalFluids.COAL_TAR, 500), fout(CoalFluids.COKE_OVEN_GAS, 800)),
                        List.of(iout(CoalMaterials.COKE.get(), 2))),
                new Row("coke_wood", PetroMachineKind.COKE_OVEN,
                        "原木 ×4（任意原木，不含菌柄）→ 木炭 ×3 + 木焦油 300 + 木醋液 500 + 松节油 200（绝氧干馏）",
                        List.of(), List.of(tagIn(ItemTags.LOGS_THAT_BURN, 4, Items.OAK_LOG)),
                        List.of(fout(WoodFluids.WOOD_TAR, 300), fout(WoodFluids.WOOD_VINEGAR, 500),
                                fout(WoodFluids.TURPENTINE, 200)),
                        List.of(iout(Items.CHARCOAL, 3))));

        // ①b 焦油精馏（分馏塔）：煤焦油 → 苯 / 苯酚 / 萘 / 沥青
        add(PetroMachineKind.DISTILLATION_TOWER,
                new Row("refine_coal_tar", PetroMachineKind.DISTILLATION_TOWER,
                        "煤焦油 1000 → 苯 250 + 苯酚 200 + 萘 150 + 沥青 400（焦油精制）",
                        List.of(fin(CoalFluids.COAL_TAR, 1000)), List.of(),
                        List.of(fout(PetroFluids.BENZENE, 250), fout(PetroFluids.PHENOL, 200),
                                fout(CoalFluids.NAPHTHALENE, 150), fout(PetroFluids.BITUMEN, 400)),
                        List.of()));

        // ①c 木化工（分馏塔）：木焦油 / 木醋液分馏
        add(PetroMachineKind.DISTILLATION_TOWER,
                new Row("distill_wood_tar", PetroMachineKind.DISTILLATION_TOWER,
                        "木焦油 1000 → 杂酚油 350 + 沥青 350 + 松香 ×1（杂酚油可作低级液体燃料）",
                        List.of(fin(WoodFluids.WOOD_TAR, 1000)), List.of(),
                        List.of(fout(WoodFluids.CREOSOTE, 350), fout(PetroFluids.BITUMEN, 350)),
                        List.of(iout(WoodMaterials.ROSIN.get(), 1))),
                new Row("distill_wood_vinegar", PetroMachineKind.DISTILLATION_TOWER,
                        "木醋液 1000 → 醋酸 400 + 甲醇 300 + 丙酮 150（木醋液分馏）",
                        List.of(fin(WoodFluids.WOOD_VINEGAR, 1000)), List.of(),
                        List.of(fout(BioFluids.ACETIC_ACID, 400), fout(CoalFluids.METHANOL, 300),
                                fout(BioFluids.ACETONE, 150)),
                        List.of()),
                new Row("distill_coal_liquefied", PetroMachineKind.DISTILLATION_TOWER,
                        "煤液化油 1000 → 石脑油 180 + 煤油 160 + 柴油 220 + 重油 300 + 沥青 120（偏重质）",
                        List.of(fin(CoalFluids.COAL_LIQUEFIED_OIL, 1000)), List.of(),
                        List.of(fout(PetroFluids.NAPHTHA, 180), fout(PetroFluids.KEROSENE, 160),
                                fout(PetroFluids.DIESEL, 220), fout(PetroFluids.HEAVY_OIL, 300),
                                fout(PetroFluids.BITUMEN, 120)),
                        List.of()));

        // ② 气化（气化炉）：煤 / 焦炭 / 煤焦油 / CO₂ → 合成气；CO₂ 行放最前，优先回炉
        put(PetroMachineKind.GASIFIER,
                new Row("gasify_co2", PetroMachineKind.GASIFIER,
                        "焦炭 ×1 + 二氧化碳 1000 → 合成气 2000（CO₂ 回炉）",
                        List.of(fin(CoalFluids.CARBON_DIOXIDE, 1000)), List.of(iin(CoalMaterials.COKE.get(), 1)),
                        List.of(fout(CoalFluids.SYNGAS, 2000)), List.of(), MaterialItems.COPPER_PLATE.get()),
                new Row("gasify_coke", PetroMachineKind.GASIFIER,
                        "焦炭 ×1 + 水 1000 → 合成气 1800",
                        List.of(fin(Fluids.WATER, 1000)), List.of(iin(CoalMaterials.COKE.get(), 1)),
                        List.of(fout(CoalFluids.SYNGAS, 1800)), List.of(), MaterialItems.IRON_PLATE.get()),
                new Row("gasify_coal", PetroMachineKind.GASIFIER,
                        "煤炭粉 ×2 + 水 1000 → 合成气 2000（粉煤气化）",
                        List.of(fin(Fluids.WATER, 1000)), List.of(iin(CoalMaterials.COAL_DUST.get(), 2)),
                        List.of(fout(CoalFluids.SYNGAS, 2000)), List.of(), Items.QUARTZ),
                new Row("gasify_tar", PetroMachineKind.GASIFIER,
                        "煤焦油 1000 + 水 1000 → 合成气 1800",
                        List.of(fin(CoalFluids.COAL_TAR, 1000), fin(Fluids.WATER, 1000)), List.of(),
                        List.of(fout(CoalFluids.SYNGAS, 1800)), List.of(), Items.REDSTONE));

        // ②b 沼气蒸汽重整（气化炉）：给合成塔补一条气体进料路线
        add(PetroMachineKind.GASIFIER,
                new Row("gasify_biogas", PetroMachineKind.GASIFIER,
                        "沼气 1000 + 水 500 → 合成气 1000 + 氢气 200（蒸汽重整）",
                        List.of(fin(BioFluids.BIOGAS, 1000), fin(Fluids.WATER, 500)), List.of(),
                        List.of(fout(CoalFluids.SYNGAS, 1000), fout(PetroFluids.HYDROGEN, 200)),
                        List.of()));

        // ②c 空气气化（气化炉）：焦炭 / 木炭 → 发生炉煤气（没有蒸汽时的路线，木煤气并入这一种）
        add(PetroMachineKind.GASIFIER,
                new Row("gasify_coke_air", PetroMachineKind.GASIFIER,
                        "焦炭 ×1 → 发生炉煤气 2000（空气气化）",
                        List.of(), List.of(iin(CoalMaterials.COKE.get(), 1)),
                        List.of(fout(CoalFluids.PRODUCER_GAS, 2000)), List.of()),
                new Row("gasify_charcoal_air", PetroMachineKind.GASIFIER,
                        "木炭粉 ×1 → 发生炉煤气 2000（空气气化）",
                        List.of(), List.of(iin(CoalMaterials.CHARCOAL_DUST.get(), 1)),
                        List.of(fout(CoalFluids.PRODUCER_GAS, 2000)), List.of()));

        // ③ 净化 / 变换（复用重整器）：有水先走变换，无水走净化
        add(PetroMachineKind.REFORMER,
                new Row("shift_syngas", PetroMachineKind.REFORMER,
                        "合成气 1000 + 水 500 → 氢气 600 + 二氧化碳 400（变换）",
                        List.of(fin(CoalFluids.SYNGAS, 1000), fin(Fluids.WATER, 500)), List.of(),
                        List.of(fout(PetroFluids.HYDROGEN, 600), fout(CoalFluids.CARBON_DIOXIDE, 400)),
                        List.of(), Items.FLINT),
                new Row("wash_syngas", PetroMachineKind.REFORMER,
                        "合成气 1000 → 净化合成气 900 + 二氧化碳 100 + 硫粉 ×1（净化）",
                        List.of(fin(CoalFluids.SYNGAS, 1000)), List.of(),
                        List.of(fout(CoalFluids.CLEAN_SYNGAS, 900), fout(CoalFluids.CARBON_DIOXIDE, 100)),
                        List.of(iout(PetroMaterials.SULFUR_DUST.get(), 1)), Items.CLAY_BALL));

        // ④ 合成 / 转化（合成塔）：按「先具体、后兜底」排 —— MTO / 补氢制氨 / 尿素 / 制甲醇
        put(PetroMachineKind.SYNTHESIS_TOWER,
                new Row("synth_dme", PetroMachineKind.SYNTHESIS_TOWER,
                        "甲醇 1000 + 硫酸 100 → 二甲醚 800 + 水 200（酸催化脱水）",
                        List.of(fin(CoalFluids.METHANOL, 1000),
                                fin(OreFluids.SULFURIC_ACID.source().get(), 100)), List.of(),
                        List.of(fout(CoalFluids.DME, 800), fout(Fluids.WATER, 200)),
                        List.of()),
                new Row("mto", PetroMachineKind.SYNTHESIS_TOWER,
                        "甲醇 1000 → 乙烯 300 + 丙烯 200 + 水 400（MTO）",
                        List.of(fin(CoalFluids.METHANOL, 1000)), List.of(),
                        List.of(fout(PetroFluids.ETHYLENE, 300), fout(PetroFluids.PROPYLENE, 200),
                                fout(Fluids.WATER, 400)),
                        List.of()),
                new Row("synth_ammonia", PetroMachineKind.SYNTHESIS_TOWER,
                        "氮气 500 + 氢气 1500 → 氨水 1000（合成氨）",
                        List.of(fin(AirFluids.NITROGEN.source().get(), 500),
                                fin(PetroFluids.HYDROGEN, 1500)), List.of(),
                        List.of(fout(FissionFluids.AMMONIA_SOLUTION, 1000)), List.of(),
                        MaterialItems.COPPER_PLATE.get()),
                new Row("synth_urea", PetroMachineKind.SYNTHESIS_TOWER,
                        "氨水 500 + 二氧化碳 500 → 尿素 ×2",
                        List.of(fin(FissionFluids.AMMONIA_SOLUTION, 500), fin(CoalFluids.CARBON_DIOXIDE, 500)),
                        List.of(), List.of(), List.of(iout(CoalMaterials.UREA.get(), 2))));

        // ⑤ 燃料合成（合成塔）：改进剂 / 三级推进剂 —— 按复杂度从低到高
        // 硝酸沿用生化线反应器（氨水 + 氧气），这里不再重复一条产线。
        add(PetroMachineKind.SYNTHESIS_TOWER,
                new Row("tetranitromethane", PetroMachineKind.SYNTHESIS_TOWER,
                        "硝酸 500 + 醋酸 500 → 四硝基甲烷 400 + 水 300（硝化）",
                        List.of(fin(FissionFluids.NITRIC_ACID, 500), fin(BioFluids.ACETIC_ACID, 500)), List.of(),
                        List.of(fout(PetroFluids.TETRANITROMETHANE, 400), fout(Fluids.WATER, 300)),
                        List.of()),
                new Row("refine_high_cetane_diesel", PetroMachineKind.SYNTHESIS_TOWER,
                        "低硫油 1000 + 四硝基甲烷 20 → 高十六烷值柴油 1000（十六烷值改进剂）",
                        List.of(fin(PetroFluids.LOW_SULFUR_OIL, 1000),
                                fin(PetroFluids.TETRANITROMETHANE, 20)), List.of(),
                        List.of(fout(PetroFluids.HIGH_CETANE_DIESEL, 1000)),
                        List.of(), MaterialItems.IRON_PLATE.get()),
                new Row("methanation", PetroMachineKind.SYNTHESIS_TOWER,
                        "合成气 800 + 氢气 600 → 液甲烷 600 + 水 400（甲烷化）",
                        List.of(fin(CoalFluids.SYNGAS, 800), fin(PetroFluids.HYDROGEN, 600)), List.of(),
                        List.of(fout(PetroFluids.LIQUID_METHANE, 600), fout(Fluids.WATER, 400)),
                        List.of(), MaterialItems.IRON_PLATE.get()),
                new Row("refine_rocket_fuel", PetroMachineKind.SYNTHESIS_TOWER,
                        "航空煤油 1000 + 氧气 600 → 火箭燃料 1600（煤油氧预混）",
                        List.of(fin(PetroFluids.AVIATION_KEROSENE, 1000),
                                fin(WeldingFluids.OXYGEN.source().get(), 600)), List.of(),
                        List.of(fout(PetroFluids.ROCKET_FUEL, 1600)),
                        List.of(), MaterialItems.COPPER_PLATE.get()),
                new Row("methalox_fuel", PetroMachineKind.SYNTHESIS_TOWER,
                        "液甲烷 1000 + 氧气 800 → 甲烷氧火箭燃料 1800（甲烷氧预混）",
                        List.of(fin(PetroFluids.LIQUID_METHANE, 1000),
                                fin(WeldingFluids.OXYGEN.source().get(), 800)), List.of(),
                        List.of(fout(PetroFluids.METHALOX, 1800)),
                        List.of(), MaterialItems.COPPER_PLATE.get()),
                new Row("hydrazine", PetroMachineKind.SYNTHESIS_TOWER,
                        "氨水 500 + 过氧化氢 500 → 肼 300 + 水 700（过氧化氢氧化）",
                        List.of(fin(FissionFluids.AMMONIA_SOLUTION, 500),
                                fin(OreFluids.HYDROGEN_PEROXIDE.source().get(), 500)), List.of(),
                        List.of(fout(PetroFluids.HYDRAZINE, 300), fout(Fluids.WATER, 700)),
                        List.of()),
                new Row("udmh", PetroMachineKind.SYNTHESIS_TOWER,
                        "肼 500 + 甲醇 300 → 偏二甲肼 400 + 水 200（甲基化）",
                        List.of(fin(PetroFluids.HYDRAZINE, 500), fin(CoalFluids.METHANOL, 300)), List.of(),
                        List.of(fout(PetroFluids.UDMH, 400), fout(Fluids.WATER, 200)),
                        List.of()),
                new Row("hypergolic_fuel", PetroMachineKind.SYNTHESIS_TOWER,
                        "偏二甲肼 600 + 四氧化二氮 400 → 自燃火箭燃料 1000（预混自燃）",
                        List.of(fin(PetroFluids.UDMH, 600),
                                fin(PetroFluids.DINITROGEN_TETROXIDE, 400)), List.of(),
                        List.of(fout(PetroFluids.HYPERGOLIC_FUEL, 1000)),
                        List.of(), MaterialItems.COPPER_PLATE.get()));

        // ⑥ 2026-11-11 批次 1：合成气延伸 / 电石乙炔 / 硝基苯 / 木化工推进剂 / 金属化推进剂
        // 顺序约定：带额外标记（催化剂 / 第二种流体）的专用路线排在「只吃单一原料」的兜底路线之前。
        add(PetroMachineKind.SYNTHESIS_TOWER,
                new Row("fischer_tropsch", PetroMachineKind.SYNTHESIS_TOWER,
                        "净化合成气 1000 + 氢气 200 → 合成航煤 360 + 水 200（费托合成，活性炭催化）",
                        List.of(fin(CoalFluids.CLEAN_SYNGAS, 1000),
                                fin(PetroFluids.HYDROGEN, 200)), List.of(),
                        List.of(fout(CoalFluids.SYNTHETIC_JET, 360), fout(Fluids.WATER, 200)),
                        List.of(), CoalMaterials.ACTIVATED_CARBON.get()),
                new Row("synth_sng", PetroMachineKind.SYNTHESIS_TOWER,
                        "净化合成气 1000 + 氢气 300 → 合成天然气 900 + 水 200（甲烷化）",
                        List.of(fin(CoalFluids.CLEAN_SYNGAS, 1000),
                                fin(PetroFluids.HYDROGEN, 300)), List.of(),
                        List.of(fout(CoalFluids.SNG, 900), fout(Fluids.WATER, 200)),
                        List.of(), MaterialItems.IRON_PLATE.get()),
                new Row("synth_methanol", PetroMachineKind.SYNTHESIS_TOWER,
                        "净化合成气 1000 → 甲醇 800",
                        List.of(fin(CoalFluids.CLEAN_SYNGAS, 1000)), List.of(),
                        List.of(fout(CoalFluids.METHANOL, 800)), List.of(), MaterialItems.IRON_PLATE.get()),
                new Row("nitrobenzene", PetroMachineKind.SYNTHESIS_TOWER,
                        "苯 500 + 硝酸 500 → 硝基苯 600 + 水 400（硝化）",
                        List.of(fin(PetroFluids.BENZENE, 500), fin(FissionFluids.NITRIC_ACID, 500)), List.of(),
                        List.of(fout(PetroFluids.NITROBENZENE, 600), fout(Fluids.WATER, 400)),
                        List.of()),
                new Row("nitrobenzene_monopropellant", PetroMachineKind.SYNTHESIS_TOWER,
                        "硝基苯 900 + 过氧化氢 100 → 硝基苯推进剂 1000（活化，单组元）",
                        List.of(fin(PetroFluids.NITROBENZENE, 900),
                                fin(OreFluids.HYDROGEN_PEROXIDE.source().get(), 100)), List.of(),
                        List.of(fout(PetroFluids.NITROBENZENE_MONOPROPELLANT, 1000)), List.of()),
                new Row("carbide_hydrolysis", PetroMachineKind.SYNTHESIS_TOWER,
                        "电石 ×1 + 水 1000 → 乙炔 500 + 氢氧化钙 ×1（水解）",
                        List.of(fin(Fluids.WATER, 1000)), List.of(iin(CoalMaterials.CALCIUM_CARBIDE.get(), 1)),
                        List.of(fout(CoalFluids.ACETYLENE, 500)),
                        List.of(iout(CoalMaterials.CALCIUM_HYDROXIDE.get(), 1))),
                new Row("vcm", PetroMachineKind.SYNTHESIS_TOWER,
                        "乙炔 500 + 盐酸 500 → 氯乙烯 800（加成）",
                        List.of(fin(CoalFluids.ACETYLENE, 500),
                                fin(OreFluids.HYDROCHLORIC_ACID.source().get(), 500)), List.of(),
                        List.of(fout(CoalFluids.VCM, 800)), List.of()),
                new Row("vinyl_acetate", PetroMachineKind.SYNTHESIS_TOWER,
                        "乙炔 500 + 醋酸 500 → 醋酸乙烯 800（酯化；预留：胶黏剂 / 涂料）",
                        List.of(fin(CoalFluids.ACETYLENE, 500), fin(BioFluids.ACETIC_ACID, 500)), List.of(),
                        List.of(fout(CoalFluids.VINYL_ACETATE, 800)), List.of()),
                new Row("btx_from_light_oil", PetroMachineKind.SYNTHESIS_TOWER,
                        "轻油 1000 → 混合芳烃 200（重整）",
                        List.of(fin(PetroFluids.LIGHT_OIL, 1000)), List.of(),
                        List.of(fout(CoalFluids.BTX, 200)), List.of()),
                new Row("turpentine_from_naphtha", PetroMachineKind.SYNTHESIS_TOWER,
                        "石脑油 500 + 原木 ×2 → 松节油 300 + 松香 ×1（GTM 路线，不含菌柄）",
                        List.of(fin(PetroFluids.NAPHTHA, 500)),
                        List.of(tagIn(ItemTags.LOGS_THAT_BURN, 2, Items.OAK_LOG)),
                        List.of(fout(WoodFluids.TURPENTINE, 300)),
                        List.of(iout(WoodMaterials.ROSIN.get(), 1))),
                new Row("turpentine_nitric", PetroMachineKind.SYNTHESIS_TOWER,
                        "松节油 900 + 硝酸 100 → 松节油-硝酸 1000（预混）",
                        List.of(fin(WoodFluids.TURPENTINE, 900), fin(FissionFluids.NITRIC_ACID, 100)), List.of(),
                        List.of(fout(WoodFluids.TURPENTINE_NITRIC, 1000)), List.of()),
                new Row("furfuryl_nitric", PetroMachineKind.SYNTHESIS_TOWER,
                        "糠醇 900 + 硝酸 100 → 糠醇-硝酸 1000（预混）",
                        List.of(fin(WoodFluids.FURFURYL_ALCOHOL, 900), fin(FissionFluids.NITRIC_ACID, 100)), List.of(),
                        List.of(fout(WoodFluids.FURFURYL_NITRIC, 1000)), List.of()),
                new Row("ethalox", PetroMachineKind.SYNTHESIS_TOWER,
                        "乙醇 900 + 氧气 600 → 乙醇氧 1500（乙醇氧预混）",
                        List.of(fin(BioFluids.ETHANOL, 900),
                                fin(WeldingFluids.OXYGEN.source().get(), 600)), List.of(),
                        List.of(fout(WoodFluids.ETHALOX, 1500)), List.of()),
                // ---- 煤直接液化（2026-11-12）：破碎 → 制浆 → 加氢 → 分馏，后期长链替代原油
                new Row("coal_liquefaction", PetroMachineKind.SYNTHESIS_TOWER,
                        "煤水浆 1000 + 氢气 2000 → 煤液化油 1000 + 水 400（直接液化，铂催化）",
                        List.of(fin(CoalFluids.COAL_SLURRY, 1000),
                                fin(PetroFluids.HYDROGEN, 2000)), List.of(),
                        List.of(fout(CoalFluids.COAL_LIQUEFIED_OIL, 1000), fout(Fluids.WATER, 400)),
                        List.of(), FissionMaterials.PLATINUM_CATALYST.get()),
                new Row("coal_co_processing", PetroMachineKind.SYNTHESIS_TOWER,
                        "煤炭粉 ×2 + 重油 500 + 氢气 1500 → 煤液化油 1000 + 水 200（煤油共炼）",
                        List.of(fin(PetroFluids.HEAVY_OIL, 500), fin(PetroFluids.HYDROGEN, 1500)),
                        List.of(iin(CoalMaterials.COAL_DUST.get(), 2)),
                        List.of(fout(CoalFluids.COAL_LIQUEFIED_OIL, 1000), fout(Fluids.WATER, 200)),
                        List.of(), FissionMaterials.PLATINUM_CATALYST.get()),
                new Row("aluminized_kerosene", PetroMachineKind.SYNTHESIS_TOWER,
                        "煤油 900 + 铝粉 ×2 → 铝化煤油浆 1000（掺混）",
                        List.of(fin(PetroFluids.KEROSENE, 900)),
                        List.of(iin(dev.znecby.stellar_evolution.material.ElementDusts.ALUMINUM_DUST.get(), 2)),
                        List.of(fout(PetroFluids.ALUMINIZED_KEROSENE, 1000)), List.of()),
                // 四氧化二氮：只吃硝酸，放最后，避免抢掉所有硝酸路线
                new Row("dinitrogen_tetroxide", PetroMachineKind.SYNTHESIS_TOWER,
                        "硝酸 500 → 四氧化二氮 400 + 水 100（脱水）",
                        List.of(fin(FissionFluids.NITRIC_ACID, 500)), List.of(),
                        List.of(fout(PetroFluids.DINITROGEN_TETROXIDE, 400), fout(Fluids.WATER, 100)),
                        List.of()),
                new Row("nto_from_ammonia", PetroMachineKind.SYNTHESIS_TOWER,
                        "氨水 600 + 氧气 400 → 四氧化二氮 400 + 水 600（氨氧化）",
                        List.of(fin(FissionFluids.AMMONIA_SOLUTION, 600),
                                fin(WeldingFluids.OXYGEN.source().get(), 400)), List.of(),
                        List.of(fout(PetroFluids.DINITROGEN_TETROXIDE, 400), fout(Fluids.WATER, 600)),
                        List.of()));
        auditCatalysts();
    }

    /**
     * 催化剂约定：用<strong>板 / 杆 / 粉 / 玻璃 / 电子料</strong>等形态，不要用锭。
     * 锭是基础原料，容易被误当成正常输入；发现即警告，提示改成对应的板。
     */
    private static void auditCatalysts() {
        for (Row row : all()) {
            if (row.catalyst() == null) {
                continue;
            }
            var id = BuiltInRegistries.ITEM.getKey(row.catalyst());
            if (id.getPath().endsWith("_ingot")) {
                StellarEvolution.LOGGER.warn(
                        "Petro recipe {} uses ingot catalyst {} — use the corresponding plate/rod/powder instead",
                        row.id(), id);
            }
        }
    }

    // ------------------------------------------------------------------ 查询

    /** 某机种的行表（按声明顺序）。 */
    public static List<Row> rows(PetroMachineKind kind) {
        return TABLES.getOrDefault(kind, List.of());
    }

    /** 全部行（JEI / 命令按机种顺序遍历）。 */
    public static List<Row> all() {
        List<Row> list = new ArrayList<>();
        for (PetroMachineKind kind : PetroMachineKind.values()) {
            list.addAll(rows(kind));
        }
        return List.copyOf(list);
    }

    // ------------------------------------------------------------------ 声明辅助

    private static FluidIn fin(PetroFluids.Entry entry, int amount) {
        return new FluidIn(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidOut fout(PetroFluids.Entry entry, int amount) {
        return new FluidOut(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidIn fin(BioFluids.Entry entry, int amount) {
        return new FluidIn(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidOut fout(BioFluids.Entry entry, int amount) {
        return new FluidOut(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidIn fin(FissionFluids.Entry entry, int amount) {
        return new FluidIn(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidOut fout(FissionFluids.Entry entry, int amount) {
        return new FluidOut(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidIn fin(CoalFluids.Entry entry, int amount) {
        return new FluidIn(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidOut fout(CoalFluids.Entry entry, int amount) {
        return new FluidOut(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidIn fin(WoodFluids.Entry entry, int amount) {
        return new FluidIn(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidOut fout(WoodFluids.Entry entry, int amount) {
        return new FluidOut(FluidResource.of(entry.source().get()), amount);
    }

    private static FluidIn fin(Fluid fluid, int amount) {
        return new FluidIn(FluidResource.of(fluid), amount);
    }

    private static FluidOut fout(Fluid fluid, int amount) {
        return new FluidOut(FluidResource.of(fluid), amount);
    }

    private static ItemIn iin(Item item, int count) {
        return new ItemIn(ItemResource.of(new ItemStack(item)), count);
    }

    /** 标签输入：匹配任意属于该标签的物品；{@code display} 只用于 JEI 展示。 */
    private static ItemIn tagIn(TagKey<Item> tag, int count, Item display) {
        return new ItemIn(ItemResource.of(new ItemStack(display)), count, tag);
    }

    private static ItemOut iout(Item item, int count) {
        return new ItemOut(ItemResource.of(new ItemStack(item)), count);
    }

    private PetroLine() {}
}
