package dev.znecby.stellar_evolution.particle;

import dev.znecby.stellar_evolution.bio.BioMaterials;
import dev.znecby.stellar_evolution.fission.FissionFluids;
import dev.znecby.stellar_evolution.fission.FissionMachineKind;
import dev.znecby.stellar_evolution.fission.FissionMaterials;
import dev.znecby.stellar_evolution.material.IntermediateItems;
import dev.znecby.stellar_evolution.material.LeadMaterials;
import dev.znecby.stellar_evolution.ore.MonaziteMaterials;
import dev.znecby.stellar_evolution.petro.PetroMaterials;
import dev.znecby.stellar_evolution.stream.StreamType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;

/**
 * 束流机器配方表（<strong>辐照仓</strong> / <strong>对撞机</strong>）。
 *
 * <p>每条配方自带：
 * <ul>
 *   <li><strong>束流需求列表</strong> {@link StreamDemand}：辐照仓只有一路；对撞机要求<strong>两路同时供能</strong>
 *       （束-束对撞，游戏上的核心差异）；</li>
 *   <li>可选的<strong>输出流</strong> {@link #outputStream()}：对撞机在运行时会往外<strong>喷出 μ 子流</strong>
 *       （次级粒子），由 {@code StreamEndpoint} 侧输出。</li>
 * </ul>
 *
 * <p>机器按「接了哪些管」得到可用流集合，再按<strong>表序</strong>挑第一条「输入够 + 所有需求流都接着」的配方并粘住它
 * （见 {@link BeamMachineBlockEntity}）。
 */
public final class BeamRecipes {
    /** 一路束流需求。 */
    public record StreamDemand(StreamType type, double demand, double required) {}

    /**
     * 一条束流配方。
     *
     * @param machine      承载它的机种
     * @param demands      需要的粒子流（1 路 = 普通辐照；2 路 = 对撞）
     * @param input        靶料（物品；null = 这条配方只吃流体）
     * @param inputCount   一次结算消耗几个
     * @param outputs      物品产物
     * @param fluidInputs  流体输入（无物品靶料的配方用，例如氘代溶剂 / 无菌培养基）
     * @param fluidOutputs 流体产物
     * @param outputStream 运行时的输出流类型（null = 不输出）
     * @param outputRate   输出流速率（单位/t）
     */
    public record Recipe(String id, FissionMachineKind machine, List<StreamDemand> demands,
                         Item input, int inputCount, List<Output> outputs, List<FluidInput> fluidInputs,
                         List<FluidOutput> fluidOutputs,
                         StreamType outputStream, double outputRate, int minRingLevel) {
        /** 旧写法（无流体输入）的兼容构造：绝大多数配方只吃物品靶料。 */
        public Recipe(String id, FissionMachineKind machine, List<StreamDemand> demands,
                      Item input, int inputCount, List<Output> outputs, List<FluidOutput> fluidOutputs,
                      StreamType outputStream, double outputRate) {
            this(id, machine, demands, input, inputCount, outputs, List.of(), fluidOutputs,
                    outputStream, outputRate, 0);
        }

        /** 带流体输入、但不要求加速环等级的兼容构造。 */
        public Recipe(String id, FissionMachineKind machine, List<StreamDemand> demands,
                      Item input, int inputCount, List<Output> outputs, List<FluidInput> fluidInputs,
                      List<FluidOutput> fluidOutputs,
                      StreamType outputStream, double outputRate) {
            this(id, machine, demands, input, inputCount, outputs, fluidInputs, fluidOutputs,
                    outputStream, outputRate, 0);
        }
    }

    /** 一条物品产物。 */
    public record Output(Item item, int count) {}

    /** 一条流体输入。 */
    public record FluidInput(net.minecraft.world.level.material.Fluid fluid, int amount) {}

    /** 一条流体产物。 */
    public record FluidOutput(net.minecraft.world.level.material.Fluid fluid, int amount) {}

    private static List<Recipe> cache;

    /** 全部配方（JEI / 命令遍历 / 机器匹配用；<strong>表序即优先级</strong>）。 */
    public static List<Recipe> all() {
        if (cache == null) {
            cache = check(List.of(
                    // ================= 辐照仓（单路流）
                    single("irradiate_cobalt", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 2.0D, 2_000.0D,
                            FissionMaterials.COBALT_DUST.get(), 1,
                            List.of(new Output(FissionMaterials.CO_60.get(), 1))),
                    single("irradiate_np237", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 4.0D, 2_000.0D,
                            FissionMaterials.NEPTUNIUM_237.get(), 1,
                            List.of(new Output(FissionMaterials.PLUTONIUM_238.get(), 1))),
                    singleFluid("breed_tritium", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 4.0D, 800.0D,
                            FissionMaterials.LITHIUM_DUST.get(), 1,
                            FissionFluids.TRITIUM.source().get(), 1_000),
                    // 锂-6 增殖：同位素富集后的靶料，同样 800 中子换 2,000 氚
                    singleFluid("breed_tritium_li6", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 4.0D, 800.0D,
                            FissionMaterials.LITHIUM_6.get(), 1,
                            FissionFluids.TRITIUM.source().get(), 2_000),
                    single("transmute_waste", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.PROTON, 4.0D, 8_000.0D,
                            FissionMaterials.NUCLEAR_WASTE.get(), 1,
                            List.of(new Output(FissionMaterials.ZR_90.get(), 1),
                                    new Output(FissionMaterials.SHORT_LIVED_WASTE.get(), 1))),
                    single("transmute_minor_actinides", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.PROTON, 5.0D, 10_000.0D,
                            FissionMaterials.MINOR_ACTINIDES.get(), 1,
                            List.of(new Output(FissionMaterials.ZR_90.get(), 1),
                                    new Output(FissionMaterials.MO_95.get(), 1))),
                    // 残余同位素嬗变（二期 D3）：碘-129 用质子、钐-149 用中子，产物都是稳定核素
                    single("transmute_iodine129", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.PROTON, 4.0D, 6_000.0D,
                            FissionMaterials.IODINE_129.get(), 1,
                            List.of(new Output(FissionMaterials.ND_143.get(), 1),
                                    new Output(FissionMaterials.SHORT_LIVED_WASTE.get(), 1))),
                    single("transmute_samarium149", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 4.0D, 4_000.0D,
                            FissionMaterials.SAMARIUM_149.get(), 1,
                            List.of(new Output(FissionMaterials.ND_143.get(), 1),
                                    new Output(FissionMaterials.MO_95.get(), 1))),
                    // B2：天然钐粉中子辐照 → 钐-149（给独居石稀土一个明确去向）
                    single("enrich_samarium149", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 4.0D, 4_000.0D,
                            MonaziteMaterials.SAMARIUM_DUST.get(), 1,
                            List.of(new Output(FissionMaterials.SAMARIUM_149.get(), 1))),
                    // §7.12 残骸线：星骸粉 + 强中子流 → 粗离火素 + 寂灰
                    single("irradiate_star_ash", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 4.0D, 4_000.0D,
                            FissionMaterials.STAR_ASH.get(), 2,
                            List.of(new Output(FissionMaterials.RAW_EMBER_ESSENCE.get(), 1),
                                    new Output(FissionMaterials.SILENT_ASH.get(), 1))),
                    single("activate_dried_strain", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.PROTON, 2.0D, 1_000.0D,
                            BioMaterials.DRIED_STRAIN.get(), 1,
                            List.of(new Output(BioMaterials.CULTURE_STRAIN.get(), 3))),
                    // 中子废料线排后面：两路都接时优先走质子的稳定核素
                    single("transmute_hlw", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 4.0D, 4_000.0D,
                            FissionMaterials.NUCLEAR_WASTE.get(), 1,
                            List.of(new Output(FissionMaterials.ZR_90.get(), 1),
                                    new Output(FissionMaterials.MO_95.get(), 1),
                                    new Output(FissionMaterials.ND_143.get(), 1))),
                    // μ 子催化聚变：对撞机喷出的 μ 子在这里变成氚
                    singleFluid("muon_catalysed_tritium", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.MUON, 3.0D, 600.0D,
                            FissionMaterials.LITHIUM_DUST.get(), 1,
                            FissionFluids.TRITIUM.source().get(), 1_500),
                    // 离子注入（铅离子 → 耐辐照结构件）
                    single("implant_hardened_alloy", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.LEAD_ION, 2.0D, 6_000.0D,
                            IntermediateItems.STEEL_PLATE.get(), 4,
                            List.of(new Output(FissionMaterials.HARDENED_ALLOY.get(), 1))),
                    // 氘核增殖：D + Li 在锂靶内串成增殖链（D-Li 反应），产物氚
                    singleFluid("breed_tritium_deuteron", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.DEUTERON, 4.0D, 1_200.0D,
                            FissionMaterials.LITHIUM_DUST.get(), 1,
                            FissionFluids.TRITIUM.source().get(), 1_500),
                    // α 活化：α 束处理干菌种，出菌种（比质子路线的单批产量高，束流门槛也更高）
                    single("activate_strain_alpha", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.ALPHA, 3.0D, 2_000.0D,
                            BioMaterials.DRIED_STRAIN.get(), 1,
                            List.of(new Output(BioMaterials.CULTURE_STRAIN.get(), 4))),
                    // 极化质子：自旋极化束做辐照损伤硬化（铅离子离子注入的替代路线）
                    single("harden_alloy_polarized", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.POLARIZED_PROTON, 3.0D, 4_000.0D,
                            IntermediateItems.STEEL_PLATE.get(), 4,
                            List.of(new Output(FissionMaterials.HARDENED_ALLOY.get(), 1))),
                    // 放射性离子：轰击钢靶产生短寿命同位素（离心后可分出锶-90 / 铯-137）
                    single("activate_steel_radioactive", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.RADIOACTIVE_ION, 2.0D, 4_000.0D,
                            IntermediateItems.STEEL_PLATE.get(), 4,
                            List.of(new Output(FissionMaterials.SHORT_LIVED_WASTE.get(), 1))),

                    // ================= 石化 3 / 生化 3（第三期束流用途）
                    // 石化 3：塑料 / 膜 / 接枝塑料（下游走挤出机 / 硫化机 / 热室）
                    single("crosslink_plastic", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.PROTON, 4.0D, 4_000.0D,
                            BioMaterials.BIOPLASTIC_PELLETS.get(), 4,
                            List.of(new Output(PetroMaterials.CROSSLINKED_PLASTIC.get(), 2))),
                    single("etch_track_membrane", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.LEAD_ION, 2.0D, 6_000.0D,
                            PetroMaterials.PTFE.get(), 2,
                            List.of(new Output(PetroMaterials.TRACK_ETCH_MEMBRANE.get(), 1))),
                    single("graft_plastic_radioactive", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.RADIOACTIVE_ION, 2.0D, 4_000.0D,
                            BioMaterials.BIOPLASTIC_PELLETS.get(), 3,
                            List.of(new Output(PetroMaterials.GRAFTED_PLASTIC.get(), 1))),
                    // 生化 3：三级菌种（下游走生物反应釜 / 发酵罐）
                    single("mutate_high_yield_strain", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.PROTON, 3.0D, 3_000.0D,
                            BioMaterials.CULTURE_STRAIN.get(), 1,
                            List.of(new Output(BioMaterials.HIGH_YIELD_STRAIN.get(), 1))),
                    single("select_enzyme_strain", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 3.0D, 3_000.0D,
                            BioMaterials.HIGH_YIELD_STRAIN.get(), 1,
                            List.of(new Output(BioMaterials.HIGH_YIELD_ENZYME_STRAIN.get(), 1))),
                    single("select_extremophile_strain", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.LEAD_ION, 2.0D, 4_000.0D,
                            BioMaterials.DRIED_STRAIN.get(), 2,
                            List.of(new Output(BioMaterials.EXTREMOPHILE_STRAIN.get(), 1))),
                    // 第二批：只吃流体的束流配方
                    // 氘代溶剂：氘核束轰击苯（同位素交换）
                    fluidOnly("deuterate_benzene", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.DEUTERON, 4.0D, 4_000.0D,
                            dev.znecby.stellar_evolution.petro.PetroFluids.BENZENE.source().get(), 1_000,
                            new FluidOutput(
                                    dev.znecby.stellar_evolution.petro.PetroFluids.DEUTERATED_SOLVENT.source().get(),
                                    1_000)),
                    // 无菌培养基：α 束直接灭菌（替代热力 / γ 灭菌路线）
                    fluidOnly("sterilize_medium_alpha", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.ALPHA, 3.0D, 2_000.0D,
                            dev.znecby.stellar_evolution.bio.BioFluids.NUTRIENT_MEDIUM.source().get(), 1_000,
                            new FluidOutput(
                                    dev.znecby.stellar_evolution.bio.BioFluids.STERILE_MEDIUM.source().get(),
                                    1_000)),
                    // 中子重油轻质化：重油裂解成石脑油 + 轻油（只吃流体，放在所有中子物品配方之后）
                    fluidOnly("upgrade_heavy_oil_neutron", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.NEUTRON, 4.0D, 4_000.0D,
                            dev.znecby.stellar_evolution.petro.PetroFluids.HEAVY_OIL.source().get(), 1_000,
                            List.of(new FluidOutput(
                                            dev.znecby.stellar_evolution.petro.PetroFluids.NAPHTHA.source().get(), 500),
                                    new FluidOutput(
                                            dev.znecby.stellar_evolution.petro.PetroFluids.LIGHT_OIL.source().get(), 400))),
                    // 铀离子直接离子注入：省掉铅离子 / 极化质子的绕行
                    single("harden_alloy_uranium", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.URANIUM_ION, 3.0D, 6_000.0D,
                            IntermediateItems.STEEL_PLATE.get(), 4,
                            List.of(new Output(FissionMaterials.HARDENED_ALLOY.get(), 1))),
                    // 极化质子：手性聚合物（电木）/ 手性中间体（氨基酸粉）
                    single("synthesize_chiral_polymer", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.POLARIZED_PROTON, 4.0D, 4_000.0D,
                            PetroMaterials.BAKELITE.get(), 2,
                            List.of(new Output(PetroMaterials.CHIRAL_POLYMER.get(), 1))),
                    single("synthesize_chiral_intermediate", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.POLARIZED_PROTON, 3.0D, 3_000.0D,
                            BioMaterials.AMINO_POWDER.get(), 2,
                            List.of(new Output(BioMaterials.CHIRAL_INTERMEDIATE.get(), 1))),
                    // 放射性离子：用短寿命废料标记出放射性示踪剂（ADS 监测用）
                    single("label_radioactive_tracer", FissionMachineKind.IRRADIATION_CHAMBER,
                            StreamType.RADIOACTIVE_ION, 2.0D, 3_000.0D,
                            FissionMaterials.SHORT_LIVED_WASTE.get(), 1,
                            List.of(new Output(BioMaterials.RADIOACTIVE_TRACER.get(), 2))),

                    // ================= ADS（质子流驱动次临界嬗变）
                    // 次锕系：一次吃 12 000 质子，出 4 份稳定核素；比辐照仓的纯嬗变快得多
                    single("ads_transmute_actinides", FissionMachineKind.ADS,
                            StreamType.PROTON, 6.0D, 12_000.0D,
                            FissionMaterials.MINOR_ACTINIDES.get(), 1,
                            List.of(new Output(FissionMaterials.ZR_90.get(), 2),
                                    new Output(FissionMaterials.MO_95.get(), 1),
                                    new Output(FissionMaterials.ND_143.get(), 1),
                                    new Output(FissionMaterials.SHORT_LIVED_WASTE.get(), 1))),
                    // 高放废料：同样 12 000 质子，出 3 份稳定核素
                    single("ads_transmute_hlw", FissionMachineKind.ADS,
                            StreamType.PROTON, 6.0D, 12_000.0D,
                            FissionMaterials.NUCLEAR_WASTE.get(), 2,
                            List.of(new Output(FissionMaterials.ZR_90.get(), 1),
                                    new Output(FissionMaterials.MO_95.get(), 1),
                                    new Output(FissionMaterials.ND_143.get(), 1))),
                    // 加示踪剂监测的中子通量优化：稳定核素 ×6（示踪剂去向）
                    single("ads_transmute_traced", FissionMachineKind.ADS,
                            StreamType.PROTON, 6.0D, 12_000.0D,
                            BioMaterials.RADIOACTIVE_TRACER.get(), 1,
                            List.of(new Output(FissionMaterials.ZR_90.get(), 2),
                                    new Output(FissionMaterials.MO_95.get(), 2),
                                    new Output(FissionMaterials.ND_143.get(), 2),
                                    new Output(FissionMaterials.SHORT_LIVED_WASTE.get(), 1))),

                    // ================= 对撞机（多路流进 + μ 子流输出）
                    // 最高能模式（B2）：三路流吃满 + 奇异物质靶 → 反物质（排在最前）
                    new Recipe("collide_antimatter", FissionMachineKind.COLLIDER,
                            List.of(new StreamDemand(StreamType.MUON, 4.0D, 4_000.0D),
                                    new StreamDemand(StreamType.URANIUM_ION, 6.0D, 9_000.0D),
                                    new StreamDemand(StreamType.LEAD_ION, 4.0D, 6_000.0D)),
                            FissionMaterials.EXOTIC_MATTER.get(), 1,
                            List.of(new Output(FissionMaterials.ANTIMATTER.get(), 1)), List.of(),
                            null, 0.0D),
                    // 稳定岛科技树：环等级 8 + 鎶-304 靶 → 反物质 ×2（比奇异物质靶更高效）
                    new Recipe("collide_bulk_antimatter", FissionMachineKind.COLLIDER,
                            List.of(new StreamDemand(StreamType.MUON, 4.0D, 4_000.0D),
                                    new StreamDemand(StreamType.URANIUM_ION, 6.0D, 9_000.0D),
                                    new StreamDemand(StreamType.LEAD_ION, 4.0D, 6_000.0D)),
                            FissionMaterials.COPERNICIUM_304.get(), 1,
                            List.of(new Output(FissionMaterials.ANTIMATTER.get(), 2)),
                            List.of(), List.of(), null, 0.0D, 8),
                    // 环等级 4 + 鈇-298 ×3 → 奇异物质 ×4（按箱内数量区分：3 个批量 / 2 个升鉝 / 1 个旧单发）
                    new Recipe("collide_bulk_exotic", FissionMachineKind.COLLIDER,
                            List.of(new StreamDemand(StreamType.MUON, 4.0D, 4_000.0D),
                                    new StreamDemand(StreamType.URANIUM_ION, 5.0D, 9_000.0D),
                                    new StreamDemand(StreamType.LEAD_ION, 3.0D, 6_000.0D)),
                            FissionMaterials.SUPERHEAVY_ELEMENT.get(), 3,
                            List.of(new Output(FissionMaterials.EXOTIC_MATTER.get(), 4)),
                            List.of(), List.of(), null, 0.0D, 4),
                    // 稳定岛阶梯：鈇-298 ×2 → 鉝-300 → 鎶-304（按箱内数量区分：3 个走批量、2 个升鉝、1 个走旧单发）
                    new Recipe("collide_livermorium", FissionMachineKind.COLLIDER,
                            List.of(new StreamDemand(StreamType.URANIUM_ION, 4.0D, 6_000.0D),
                                    new StreamDemand(StreamType.LEAD_ION, 2.0D, 3_000.0D)),
                            FissionMaterials.SUPERHEAVY_ELEMENT.get(), 2,
                            List.of(new Output(FissionMaterials.LIVERMORIUM_300.get(), 1)), List.of(),
                            null, 0.0D),
                    new Recipe("collide_copernicium", FissionMachineKind.COLLIDER,
                            List.of(new StreamDemand(StreamType.URANIUM_ION, 4.0D, 6_000.0D),
                                    new StreamDemand(StreamType.LEAD_ION, 2.0D, 3_000.0D)),
                            FissionMaterials.LIVERMORIUM_300.get(), 1,
                            List.of(new Output(FissionMaterials.COPERNICIUM_304.get(), 1)), List.of(),
                            null, 0.0D),
                    // 高能模式：缪子 + 铀离子 + 铅离子 + 超重元素靶 → 奇异物质（无输出流）
                    // 排在反物质之后：箱里只有超重元素时走它；否则继续退回下面两条两路配方
                    new Recipe("collide_exotic_matter", FissionMachineKind.COLLIDER,
                            List.of(new StreamDemand(StreamType.MUON, 3.0D, 3_000.0D),
                                    new StreamDemand(StreamType.URANIUM_ION, 4.0D, 6_000.0D),
                                    new StreamDemand(StreamType.LEAD_ION, 2.0D, 3_000.0D)),
                            FissionMaterials.SUPERHEAVY_ELEMENT.get(), 1,
                            List.of(new Output(FissionMaterials.EXOTIC_MATTER.get(), 1)), List.of(),
                            null, 0.0D),
                    // 前体路线（三期 E2）：铀离子 + 铅离子 + 超重元素前体 → 超重元素 ×2（比铅板路线更省靶料）
                    new Recipe("collide_superheavy_precursor", FissionMachineKind.COLLIDER,
                            List.of(new StreamDemand(StreamType.URANIUM_ION, 4.0D, 6_000.0D),
                                    new StreamDemand(StreamType.LEAD_ION, 2.0D, 3_000.0D)),
                            FissionMaterials.SUPERHEAVY_PRECURSOR.get(), 1,
                            List.of(new Output(FissionMaterials.SUPERHEAVY_ELEMENT.get(), 2)), List.of(),
                            StreamType.MUON, 0.4D),
                    // 纯束流打靶：质子 × 铅离子 → 没有物品产物，只喷 μ 子（给辐照仓供 μ 子催化）
                    new Recipe("collide_muon_source", FissionMachineKind.COLLIDER,
                            List.of(new StreamDemand(StreamType.PROTON, 3.0D, 3_000.0D),
                                    new StreamDemand(StreamType.LEAD_ION, 2.0D, 2_000.0D)),
                            LeadMaterials.LEAD_PLATE.get(), 2,
                            List.of(), List.of(),
                            StreamType.MUON, 1.2D)));
        }
        return cache;
    }

    /** 某台机器的配方（按表内顺序）。 */
    public static List<Recipe> forMachine(FissionMachineKind machine) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : all()) {
            if (recipe.machine() == machine) {
                result.add(recipe);
            }
        }
        return List.copyOf(result);
    }

    // ------------------------------------------------------------------ 构造辅助

    private static Recipe single(String id, FissionMachineKind machine, StreamType type,
                                 double demand, double required, Item input, int count, List<Output> outputs) {
        return new Recipe(id, machine, List.of(new StreamDemand(type, demand, required)),
                input, count, outputs, List.of(), null, 0.0D);
    }

    private static Recipe singleFluid(String id, FissionMachineKind machine, StreamType type,
                                      double demand, double required, Item input, int count,
                                      net.minecraft.world.level.material.Fluid fluid, int amount) {
        return new Recipe(id, machine, List.of(new StreamDemand(type, demand, required)),
                input, count, List.of(), List.of(new FluidOutput(fluid, amount)), null, 0.0D);
    }

    /** 只吃流体的配方（无物品靶料）：例如氘代溶剂 / 无菌培养基。 */
    private static Recipe fluidOnly(String id, FissionMachineKind machine, StreamType type,
                                    double demand, double required,
                                    net.minecraft.world.level.material.Fluid input, int inputAmount,
                                    FluidOutput output) {
        return fluidOnly(id, machine, type, demand, required, input, inputAmount, List.of(output));
    }

    /** 只吃流体、多路流体产物的配方（例如中子重油轻质化）。 */
    private static Recipe fluidOnly(String id, FissionMachineKind machine, StreamType type,
                                    double demand, double required,
                                    net.minecraft.world.level.material.Fluid input, int inputAmount,
                                    List<FluidOutput> outputs) {
        return new Recipe(id, machine, List.of(new StreamDemand(type, demand, required)),
                null, 0, List.of(), List.of(new FluidInput(input, inputAmount)),
                outputs, null, 0.0D);
    }

    /**
     * 开发守卫：
     * <ul>
     *   <li>同一机种内「同输入 + 同需求流集合」重复 → 后面那条永远匹配不到；</li>
     *   <li>同一配方里同一种流出现两次 → 需求会被重复计数；</li>
     *   <li><strong>输出流不能是任何需求流</strong> → 否则机器会自己喂自己，无限循环。</li>
     * </ul>
     */
    private static List<Recipe> check(List<Recipe> recipes) {
        for (int i = 0; i < recipes.size(); i++) {
            Recipe a = recipes.get(i);
            Set<StreamType> types = new HashSet<>();
            for (StreamDemand demand : a.demands()) {
                if (!types.add(demand.type())) {
                    throw new IllegalStateException("Beam recipe " + a.id()
                            + " lists stream " + demand.type().id() + " twice");
                }
            }
            if (a.outputStream() != null && types.contains(a.outputStream())) {
                throw new IllegalStateException("Beam recipe " + a.id()
                        + " outputs " + a.outputStream().id() + " which is also one of its inputs");
            }
            for (int j = i + 1; j < recipes.size(); j++) {
                Recipe b = recipes.get(j);
                if (a.machine() == b.machine() && a.input() != null && a.input() == b.input()
                        && a.demands().equals(b.demands())) {
                    throw new IllegalStateException("Beam recipe " + b.id()
                            + " can never match: same input and demands as " + a.id());
                }
            }
        }
        return List.copyOf(recipes);
    }

    private BeamRecipes() {}
}
