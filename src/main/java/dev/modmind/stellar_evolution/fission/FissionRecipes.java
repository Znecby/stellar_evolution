package dev.modmind.stellar_evolution.fission;

import dev.modmind.stellar_evolution.bedrock.BedrockBalance;
import dev.modmind.stellar_evolution.bedrock.BedrockComponents;
import dev.modmind.stellar_evolution.bedrock.BedrockFluids;
import dev.modmind.stellar_evolution.bedrock.BedrockMaterials;
import dev.modmind.stellar_evolution.bedrock.BedrockStage;
import dev.modmind.stellar_evolution.bio.BioFluids;
import dev.modmind.stellar_evolution.bio.BioMaterials;
import dev.modmind.stellar_evolution.bio.FermentationKinetics;
import dev.modmind.stellar_evolution.coal.CoalFluids;
import dev.modmind.stellar_evolution.fission.FissionRecipe.FluidIngredient;
import dev.modmind.stellar_evolution.fission.FissionRecipe.ItemIngredient;
import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.large.LargeMachineItemHandler;
import dev.modmind.stellar_evolution.material.ElementDusts;
import dev.modmind.stellar_evolution.material.IntermediateItems;
import dev.modmind.stellar_evolution.material.LeadMaterials;
import dev.modmind.stellar_evolution.material.MaterialItems;
import dev.modmind.stellar_evolution.material.TungstenMaterials;
import dev.modmind.stellar_evolution.muon.MuonMaterials;
import dev.modmind.stellar_evolution.ore.MonaziteMaterials;
import dev.modmind.stellar_evolution.ore.OreFluids;
import dev.modmind.stellar_evolution.ore.OreMaterials;
import dev.modmind.stellar_evolution.ore.SaltMaterials;
import dev.modmind.stellar_evolution.ore.TraceElementDusts;
import dev.modmind.stellar_evolution.particle.ParticleComponents;
import dev.modmind.stellar_evolution.particle.ParticleItems;
import dev.modmind.stellar_evolution.particle.ParticleType;
import dev.modmind.stellar_evolution.petro.PetroFluids;
import dev.modmind.stellar_evolution.petro.PetroMaterials;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 裂变线<strong>内置配方表 + 结算器</strong>（唯一事实源）：机器怎么跑、JEI 就怎么显示。
 *
 * <p>所有机器（含 4 台燃料机器）都走<strong>普通定时配方</strong>：
 * 抽输入（从大机器物流网络的输入容器）+ 放输出（进输出容器）在<strong>同一条事务</strong>里，
 * 任一步不成立即整体回滚（{@link #canCraft} 就是「干跑不落」的试算）。
 *
 * <p><strong>反应堆 / 汽轮机不再吃本表</strong>（2026-10-23 蒸汽网络改版）：反应堆按温度 + 功率产出
 * 蒸汽数据（压力管网络），汽轮机按转速出力、蒸汽直接消耗、不产凝结水。本表里这两类条目只用于
 * <strong>JEI 展示</strong>（蒸汽 / 超临界蒸汽流体也只为看配方保留）。
 * <p>热压差只决定 4 台燃料机器的<strong>倍速</strong>，不改写产率。
 *
 * <p><strong>2026-11-06</strong>：选冶线 / 矿石线的酸浸与氟化已整体迁到 {@code bio.BioRecipes} 的
 * <strong>反应器（BIOREACTION）</strong>，让选冶线在生化级就能跑完；本表只保留裂变前段
 * （铀锭 → 黄饼、放射性污泥 → 钍粉）、铀转化（UF6）、残骸线与高端离心分离。
 * <p><strong>2026-11-11</strong>：氟化炉删除，其两段配方合并为一条（UO2 + HF + F2 → UF6 + 水）挂在
 * <strong>化学处理槽（ORE_LEACHER）</strong>；中间体 UF4 随之取消。
 */
public final class FissionRecipes {
    private static List<FissionRecipe> cache;

    /** 酸浸周期（刻）：前期湿法段。 */
    private static final int LEACH_TICKS = 600;
    /** 氟化周期（刻）：中期转化段。 */
    private static final int FLUORINATION_TICKS = 1_000;
    /** 离心分离周期（刻）：后期核心，刻意拉长。 */
    private static final int ENRICH_TICKS = 12_000;
    /** 罐装周期（刻）：后期装配段。 */
    private static final int CANNING_TICKS = 1_200;
    /** 后处理周期（刻）：单根冷却乏燃料，慢速批次。 */
    private static final int REPROCESS_TICKS = 3_000;
    /** 废料离心分离周期（刻）：单份废料。 */
    private static final int WASTE_SEPARATION_TICKS = 300;

    /** 内置配方表（懒加载：物品 / 流体必须在注册表可用后才取）。 */
    public static List<FissionRecipe> all() {
        if (cache == null) {
            cache = List.of(
                    // ---- 酸浸槽：选冶线进料（铀锭 ×2 → 黄饼；精砂 ×1 → 粉 ×2）
                    recipe("leach_uranium_ingot", FissionMachineKind.ORE_LEACHER, LEACH_TICKS,
                            items(item(OreMaterials.URANIUM_INGOT.get(), 2)),
                            fluids(fluid(FissionFluids.NITRIC_ACID.source().get(), 250)),
                            items(item(FissionMaterials.YELLOWCAKE.get(), 1),
                                    item(FissionMaterials.RADIOACTIVE_SLUDGE.get(), 2)), List.of()),
                    // ---- 酸浸槽：裂变前段（铀锭 → 黄饼 / 高放废液），选冶酸浸已迁到生化反应器
                    // 红石粉再处理 → 汞：2026-11-08 已迁到分解仪（见 BioRecipes.decompose_redstone_mercury）

                    // ---- 盐湖 / 矿物盐支线（B1）
                    // 矿物盐分选（硼砂唯一来源）2026-11-08 已迁到分解仪（见 BioRecipes.salt_split）
                    // §7.12 残骸线：残骸渣二段浸出（轻相 → 星骸粉，重相 → 星核渣）
                    recipe("leach_debris_residue", FissionMachineKind.ORE_LEACHER, LEACH_TICKS,
                            items(residue(LeachResidueType.DEBRIS, 2)),
                            fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 250),
                                    fluid(OreFluids.HYDROGEN_PEROXIDE.source().get(), 500)),
                            items(item(FissionMaterials.STAR_ASH.get(), 2),
                                    item(FissionMaterials.STAR_CORE_SLAG.get(), 1),
                                    item(Items.GRAVEL, 1)), List.of()),
                    // 星核渣 → 星核粒：硝酸浸出 + 离心
                    recipe("separate_star_core", FissionMachineKind.CENTRIFUGE, LEACH_TICKS,
                            items(item(FissionMaterials.STAR_CORE_SLAG.get(), 2)),
                            fluids(fluid(FissionFluids.NITRIC_ACID.source().get(), 250)),
                            items(item(FissionMaterials.STAR_CORE_PELLET.get(), 1),
                                    item(Items.GRAVEL, 1)), List.of()),
                    // 粗离火素提纯：硝酸 + 过氧化氢酸洗
                    recipe("purify_ember_essence", FissionMachineKind.CENTRIFUGE, LEACH_TICKS,
                            items(item(FissionMaterials.RAW_EMBER_ESSENCE.get(), 2)),
                            fluids(fluid(FissionFluids.NITRIC_ACID.source().get(), 250),
                                    fluid(OreFluids.HYDROGEN_PEROXIDE.source().get(), 250)),
                            items(item(FissionMaterials.EMBER_ESSENCE.get(), 1),
                                    item(FissionMaterials.SILENT_ASH.get(), 1)), List.of()),
                    // 青金石二段 / 硼砂制硼酸：已迁到生化反应器（选冶线前中期）

                    // ---- 独居石（B2）：选冶段（浸出 / 氯化 / 锆铪分离 / 精矿浸出）已迁到生化反应器，
                    //      这里只留稀土分离（离心机，中后期）
                    // 稀土分离：Nd / Sm / Gd / Ce
                    recipe("separate_rare_earth", FissionMachineKind.CENTRIFUGE, ENRICH_TICKS,
                            items(item(MonaziteMaterials.RARE_EARTH_CHLORIDE.get(), 4)),
                            fluids(fluid(OreFluids.HYDROGEN_PEROXIDE.source().get(), 1500)),
                            items(item(MonaziteMaterials.NEODYMIUM_DUST.get(), 1),
                                    item(MonaziteMaterials.SAMARIUM_DUST.get(), 1),
                                    item(MonaziteMaterials.GADOLINIUM_DUST.get(), 1),
                                    item(MonaziteMaterials.CERIUM_DUST.get(), 1)), List.of()),
                    // 钍精矿浸出 / 钛铁矿氯化：已迁到生化反应器（选冶线前中期）
                    // 锡渣提铌（抽象：锡粉 + 硫酸 → 铌粉）
                    recipe("niobium_from_tin_slag", FissionMachineKind.CENTRIFUGE, LEACH_TICKS,
                            items(item(MonaziteMaterials.TIN_DUST.get(), 2)),
                            fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 250)),
                            items(item(ElementDusts.NIOBIUM_DUST.get(), 1),
                                    item(Items.GRAVEL, 1)), List.of()),

                    // ---- 铝土矿（B3）：拜耳法溶出 / 红泥回收已迁到生化反应器（选冶线前中期）

                    // 钍：铀处理线的放射性污泥 + 硫酸 → 钍粉 + 硫粉（钍的大量来源）
                    recipe("thorium_from_sludge", FissionMachineKind.ORE_LEACHER, LEACH_TICKS,
                            items(item(FissionMaterials.RADIOACTIVE_SLUDGE.get(), 1)),
                            fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 1000)),
                            items(item(FissionMaterials.THORIUM_POWDER.get(), 3),
                                    item(PetroMaterials.SULFUR_DUST.get(), 1)), List.of()),
                    // 锂：矿物盐（生化结晶） + 硫酸 → 锂粉（回旋加速器原料）
                    recipe("lithium_from_salt", FissionMachineKind.ORE_LEACHER, LEACH_TICKS,
                            items(item(BioMaterials.MINERAL_SALT.get(), 2)),
                            fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 500)),
                            items(item(FissionMaterials.LITHIUM_DUST.get(), 1)), List.of()),

                    // ---- 铀转化（2026-11-11 两段合一）：二氧化铀粉 + 氟化氢 + 氟气 → 六氟化铀 + 水
                    // 原为「UO2 + HF → UF4 + 水」→「UF4 + F2 → UF6」两步，各自需要一台氟化炉；
                    // 中间体 UF4 只在这两步之间流转，合并后取消该物品，整条链只经「化学处理槽」一台机器。
                    // 1 份 UO2 → 500 mB UF6（2026-11-11 翻倍产出：1 黄饼 = 2 铀锭 = 500 mB，1000 mB 只需 4 锭）
                    recipe("fluorinate", FissionMachineKind.ORE_LEACHER, FLUORINATION_TICKS,
                            items(item(FissionMaterials.UO2_POWDER.get(), 1)),
                            fluids(fluid(FissionFluids.HYDROGEN_FLUORIDE.source().get(), 1000),
                                    fluid(FissionFluids.FLUORINE.source().get(), 250)),
                            List.of(),
                            fluids(fluid(FissionFluids.URANIUM_HEXAFLUORIDE.source().get(), 500),
                                    fluid(Fluids.WATER, 500))),

                    // ---- 离心机：六氟化铀 → 铀-235 粒 + 铀-238 粒 + 氟化氢（级联分离，氟随载气回收）
                    // 产出比 1:8 与低浓缩铀芯块（1 U235 + 8 U238）自洽，避免 U238 断供
                    recipe("enrich_uf6", FissionMachineKind.CENTRIFUGE, ENRICH_TICKS,
                            List.of(), fluids(fluid(FissionFluids.URANIUM_HEXAFLUORIDE.source().get(), 1000)),
                            items(item(FissionMaterials.U235.get(), 1), item(FissionMaterials.U238.get(), 8)),
                            fluids(fluid(FissionFluids.HYDROGEN_FLUORIDE.source().get(), 6000))),
                    // ---- 离心机：后处理中间体分离（短衰变废料 → 锶 / 铯，过氧化氢 1,500）
                    recipe("separate_short_lived", FissionMachineKind.CENTRIFUGE, WASTE_SEPARATION_TICKS,
                            items(item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1)),
                            fluids(fluid(OreFluids.HYDROGEN_PEROXIDE.source().get(), 1500)),
                            items(item(FissionMaterials.STRONTIUM_90.get(), 1),
                                    item(FissionMaterials.CESIUM_137.get(), 1)), List.of()),
                    // ---- 离心机：长衰变废料 → 锝 / 铂族 / 单个稀土（过氧化氢 1,500）
                    recipe("separate_long_lived", FissionMachineKind.CENTRIFUGE, WASTE_SEPARATION_TICKS,
                            items(item(FissionMaterials.LONG_LIVED_WASTE.get(), 1)),
                            fluids(fluid(OreFluids.HYDROGEN_PEROXIDE.source().get(), 1500)),
                            items(item(FissionMaterials.TECHNETIUM_99.get(), 1),
                                    item(FissionMaterials.PLATINUM_GROUP.get(), 1),
                                    item(MonaziteMaterials.NEODYMIUM_DUST.get(), 1),
                                    item(MonaziteMaterials.SAMARIUM_DUST.get(), 1),
                                    item(MonaziteMaterials.GADOLINIUM_DUST.get(), 1),
                                    item(MonaziteMaterials.CERIUM_DUST.get(), 1)), List.of()),
                    // ---- 离心机：残余同位素（二期 D3）
                    // 碘-129：高放废料 + 硝酸 → 碘-129 + 短寿命废料（长寿命核素优先分离）
                    recipe("separate_iodine129", FissionMachineKind.CENTRIFUGE, WASTE_SEPARATION_TICKS,
                            items(item(FissionMaterials.NUCLEAR_WASTE.get(), 2)),
                            fluids(fluid(FissionFluids.NITRIC_ACID.source().get(), 1000)),
                            items(item(FissionMaterials.IODINE_129.get(), 1),
                                    item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1)), List.of()),
                    // 钐-149：钐粉 + 过氧化氢 → 钐-149 + 锆-90（中子毒物单独抽出）
                    recipe("separate_samarium149", FissionMachineKind.CENTRIFUGE, WASTE_SEPARATION_TICKS,
                            items(item(MonaziteMaterials.SAMARIUM_DUST.get(), 2)),
                            fluids(fluid(OreFluids.HYDROGEN_PEROXIDE.source().get(), 1500)),
                            items(item(FissionMaterials.SAMARIUM_149.get(), 1),
                                    item(FissionMaterials.ZR_90.get(), 1)), List.of()),
                    // ---- 离心机：次锕系分离（3 → 镎-237 + 镅-241 + 锔-244，质量守恒）
                    recipe("separate_minor_actinides", FissionMachineKind.CENTRIFUGE, WASTE_SEPARATION_TICKS,
                            items(item(FissionMaterials.MINOR_ACTINIDES.get(), 3)),
                            fluids(fluid(OreFluids.HYDROGEN_PEROXIDE.source().get(), 1500)),
                            items(item(FissionMaterials.NEPTUNIUM_237.get(), 1),
                                    item(FissionMaterials.AMERICIUM_241.get(), 1),
                                    item(FissionMaterials.CURIUM_244.get(), 1)), List.of()),
                    // ---- 离心机：聚变燃料链（水 → 重水 → 氘；极慢）
                    recipe("heavy_water_from_water", FissionMachineKind.CENTRIFUGE, ENRICH_TICKS,
                            List.of(), fluids(fluid(Fluids.WATER, 1_000)),
                            List.of(), fluids(fluid(FissionFluids.HEAVY_WATER.source().get(), 100))),
                    recipe("deuterium_from_heavy_water", FissionMachineKind.CENTRIFUGE, ENRICH_TICKS,
                            List.of(), fluids(fluid(FissionFluids.HEAVY_WATER.source().get(), 1_000)),
                            List.of(), fluids(fluid(FissionFluids.DEUTERIUM.source().get(), 500))),
                    // ---- 罐装机：氘 + 氚 → 氘氚燃料（常温预混）
                    canning("can_dt_fuel", null, CANNING_TICKS,
                            List.of(),
                            fluids(fluid(FissionFluids.DEUTERIUM.source().get(), 500),
                                    fluid(FissionFluids.TRITIUM.source().get(), 500)),
                            List.of(), fluids(fluid(FissionFluids.DT_FUEL.source().get(), 1_000))),
                    // ---- 罐装机：氘氚燃料 → 氘氚聚变芯核（高温带）
                    canning("can_fusion_core", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1)),
                            fluids(fluid(FissionFluids.DT_FUEL.source().get(), 1_000)),
                            items(item(MuonMaterials.DT_FUSION_CORE.get(), 1)), List.of()),
                    // ---- 罐装机：氘氘 / 氘-氦3 / 质子-硼11 芯核（二期 A1，高温带）
                    canning("can_fusion_core_dd", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1)),
                            fluids(fluid(FissionFluids.DEUTERIUM.source().get(), 1_000)),
                            items(item(MuonMaterials.DD_FUSION_CORE.get(), 1)), List.of()),
                    canning("can_fusion_core_dhe3", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1),
                                    item(FissionMaterials.HELIUM_3.get(), 1)),
                            fluids(fluid(FissionFluids.DEUTERIUM.source().get(), 500)),
                            items(item(MuonMaterials.DHE3_FUSION_CORE.get(), 1)), List.of()),
                    canning("can_fusion_core_pb11", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1),
                                    item(FissionMaterials.BORON_11.get(), 1)),
                            fluids(fluid(PetroFluids.HYDROGEN.source().get(), 1_000)),
                            items(item(MuonMaterials.PB11_FUSION_CORE.get(), 1)), List.of()),

                    // ---- 离心机：氦冷却剂 → 氦-3；锂粉 → 锂-6；碳化硼棒 → 硼-11（同位素分离）
                    recipe("separate_helium3", FissionMachineKind.CENTRIFUGE, ENRICH_TICKS,
                            List.of(), fluids(fluid(FissionFluids.HELIUM_COOLANT.source().get(), 1_000)),
                            items(item(FissionMaterials.HELIUM_3.get(), 1)), List.of()),
                    recipe("separate_lithium6", FissionMachineKind.CENTRIFUGE, ENRICH_TICKS,
                            items(item(FissionMaterials.LITHIUM_DUST.get(), 3)),
                            List.of(), items(item(FissionMaterials.LITHIUM_6.get(), 1)), List.of()),
                    recipe("separate_boron11", FissionMachineKind.CENTRIFUGE, ENRICH_TICKS,
                            items(item(FissionMaterials.BORON_CARBIDE_ROD.get(), 2)),
                            List.of(), items(item(FissionMaterials.BORON_11.get(), 1)), List.of()),

                    // ---- 罐装机：空压力罐 + 介质 → 带粒子类型的压力罐（粒子源燃料；不限温度带）
                    canning("can_pressure_proton", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1)),
                            fluids(fluid(PetroFluids.HYDROGEN.source().get(), 1_000)),
                            items(tank(ParticleType.PROTON)), List.of()),
                    canning("can_pressure_deuteron", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1)),
                            fluids(fluid(FissionFluids.DEUTERIUM.source().get(), 1_000)),
                            items(tank(ParticleType.DEUTERON)), List.of()),
                    canning("can_pressure_alpha", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1)),
                            fluids(fluid(FissionFluids.HELIUM_COOLANT.source().get(), 1_000)),
                            items(tank(ParticleType.ALPHA)), List.of()),
                    canning("can_pressure_neutron", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1),
                                    item(FissionMaterials.LITHIUM_DUST.get(), 1)),
                            List.of(),
                            items(tank(ParticleType.NEUTRON)), List.of()),
                    // 镅-241 路线：Am-Be 中子源（与锂粉路线二选一，谁先满足用谁）
                    canning("can_pressure_neutron_am", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1),
                                    item(FissionMaterials.AMERICIUM_241.get(), 1)),
                            List.of(),
                            items(tank(ParticleType.NEUTRON)), List.of()),
                    // 铍粉路线（B0：绿宝石渣副产 → 中子压力罐）
                    canning("can_pressure_neutron_be", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1),
                                    item(ElementDusts.BERYLLIUM_DUST.get(), 1)),
                            List.of(),
                            items(tank(ParticleType.NEUTRON)), List.of()),
                    canning("can_pressure_lead", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1),
                                    item(LeadMaterials.LEAD_DUST.get(), 1)),
                            List.of(),
                            items(tank(ParticleType.LEAD_ION)), List.of()),
                    canning("can_pressure_uranium", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1),
                                    item(OreMaterials.URANIUM_DUST.get(), 1)),
                            List.of(),
                            items(tank(ParticleType.URANIUM_ION)), List.of()),
                    canning("can_pressure_polarized", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1),
                                    item(FissionMaterials.RARE_EARTH_MAGNET.get(), 1)),
                            fluids(fluid(PetroFluids.HYDROGEN.source().get(), 1_000)),
                            items(tank(ParticleType.POLARIZED_PROTON)), List.of()),
                    canning("can_pressure_radioactive", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1),
                                    item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                            List.of(),
                            items(tank(ParticleType.RADIOACTIVE_ION)), List.of()),
                    canning("can_pressure_muon", null, CANNING_TICKS,
                            items(item(ParticleItems.PRESSURE_TANK.get(), 1),
                                    item(FissionMaterials.RARE_EARTH_MAGNET.get(), 1)),
                            fluids(fluid(FissionFluids.TRITIUM.source().get(), 1_000)),
                            items(tank(ParticleType.MUON)), List.of()),

                    // ---- 罐装机：铀 / MOX / 钍棒；全部按温度带选档（铀芯块已删除，直接用 U-235 / U-238 配比）
                    canning("can_rod_leu", FermentationKinetics.Band.ANAEROBIC, CANNING_TICKS,
                            items(item(FissionMaterials.U235.get(), 1),
                                    item(FissionMaterials.U238.get(), 8),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.ROD_LEU.get(), 1)), List.of()),
                    canning("can_rod_meu", FermentationKinetics.Band.MESOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.U235.get(), 2),
                                    item(FissionMaterials.U238.get(), 3),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.ROD_MEU.get(), 1)), List.of()),
                    canning("can_rod_heu", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.U235.get(), 8),
                                    item(FissionMaterials.U238.get(), 1),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.ROD_HEU.get(), 1)), List.of()),
                    canning("can_mox_rod", FermentationKinetics.Band.ANAEROBIC, CANNING_TICKS,
                            items(item(FissionMaterials.PLUTONIUM.get(), 3),
                                    item(FissionMaterials.U238.get(), 8),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.MOX_FUEL_ROD.get(), 1)), List.of()),
                    canning("can_mox_rod_mid", FermentationKinetics.Band.MESOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.PLUTONIUM.get(), 6),
                                    item(FissionMaterials.U238.get(), 6),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.MOX_ROD_MID.get(), 1)), List.of()),
                    canning("can_mox_rod_high", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.PLUTONIUM.get(), 12),
                                    item(FissionMaterials.U238.get(), 2),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.MOX_ROD_HIGH.get(), 1)), List.of()),
                    canning("can_thorium_rod", FermentationKinetics.Band.ANAEROBIC, CANNING_TICKS,
                            items(item(FissionMaterials.THORIUM_POWDER.get(), 2),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.THORIUM_FUEL_ROD.get(), 1)), List.of()),
                    canning("can_thorium_rod_mid", FermentationKinetics.Band.MESOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.THORIUM_POWDER.get(), 4),
                                    item(FissionMaterials.U233.get(), 2),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.THORIUM_ROD_MID.get(), 1)), List.of()),
                    canning("can_thorium_rod_high", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.THORIUM_POWDER.get(), 8),
                                    item(FissionMaterials.U233.get(), 3),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.THORIUM_ROD_HIGH.get(), 1)), List.of()),

                    // ---- §7.8 拓展棒：U-233 种子 / MA 嬗变 / Pu 惰性基体 / 可燃毒物 / UC 高功率 / U-Zr 金属
                    canning("can_u233_seed_rod", FermentationKinetics.Band.MESOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.U233.get(), 2),
                                    item(FissionMaterials.THORIUM_POWDER.get(), 4),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.U233_SEED_ROD.get(), 1)), List.of()),
                    canning("can_ma_transmutation_rod", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.MINOR_ACTINIDES.get(), 4),
                                    item(FissionMaterials.U238.get(), 4),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.MA_TRANSMUTATION_ROD.get(), 1)), List.of()),
                    canning("can_pu_inert_matrix_rod", FermentationKinetics.Band.MESOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.PLUTONIUM.get(), 2),
                                    item(FissionMaterials.U238.get(), 8),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.PU_INERT_MATRIX_ROD.get(), 1)), List.of()),
                    canning("can_burnable_poison_rod", FermentationKinetics.Band.ANAEROBIC, CANNING_TICKS,
                            items(item(SaltMaterials.BORON_DUST.get(), 4),
                                    item(FissionMaterials.SAMARIUM_149.get(), 1),
                                    item(MonaziteMaterials.GADOLINIUM_DUST.get(), 1),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.BURNABLE_POISON_ROD.get(), 1)), List.of()),
                    canning("can_uc_high_power_rod", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.U235.get(), 10),
                                    item(ElementDusts.GRAPHITE_DUST.get(), 4),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.UC_HIGH_POWER_ROD.get(), 1)), List.of()),
                    canning("can_uzr_metal_rod", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.U238.get(), 6),
                                    item(MonaziteMaterials.ZIRCALOY_INGOT.get(), 2),
                                    item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                                    item(IntermediateItems.STEEL_PLATE.get(), 1)), List.of(),
                            items(item(FissionMaterials.UZR_METAL_ROD.get(), 1)), List.of()),

                    // ---- §7.12 残骸线：离火棒（不稳定 / 稳定）
                    canning("can_unstable_ember_rod", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.EMBER_ESSENCE.get(), 2),
                                    item(FissionMaterials.EMBER_FUEL_ROD_SHELL.get(), 1)), List.of(),
                            items(item(FissionMaterials.UNSTABLE_EMBER_ROD.get(), 1)), List.of()),
                    canning("can_stable_ember_rod", FermentationKinetics.Band.THERMOPHILIC, CANNING_TICKS,
                            items(item(FissionMaterials.UNSTABLE_EMBER_ROD.get(), 1),
                                    item(FissionMaterials.STAR_CORE_PELLET.get(), 2),
                                    item(FissionMaterials.UNQUENCHABLE_ALLOY.get(), 1)), List.of(),
                            items(item(FissionMaterials.STABLE_EMBER_ROD.get(), 1)), List.of()),

                    // ---- 后处理厂：每次 1 根冷却乏燃料；热乏燃料不会匹配（带 spent_heat 组件）
                    reprocess("reprocess_u_low", FissionMaterials.SPENT_FUEL.get(),
                            item(FissionMaterials.PLUTONIUM.get(), 2),
                            item(FissionMaterials.U235.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_u_mid", FissionMaterials.SPENT_FUEL_U_MID.get(),
                            item(FissionMaterials.PLUTONIUM.get(), 2),
                            item(FissionMaterials.U235.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_u_high", FissionMaterials.SPENT_FUEL_U_HIGH.get(),
                            item(FissionMaterials.PLUTONIUM.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 2),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 2)),
                    reprocess("reprocess_mox_low", FissionMaterials.SPENT_FUEL_MOX_LOW.get(),
                            item(FissionMaterials.PLUTONIUM.get(), 2),
                            item(FissionMaterials.U238.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.MINOR_ACTINIDES.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 2)),
                    reprocess("reprocess_mox_mid", FissionMaterials.SPENT_FUEL_MOX_MID.get(),
                            item(FissionMaterials.PLUTONIUM.get(), 1),
                            item(FissionMaterials.U238.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 2),
                            item(FissionMaterials.MINOR_ACTINIDES.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 2)),
                    reprocess("reprocess_mox_high", FissionMaterials.SPENT_FUEL_MOX_HIGH.get(),
                            item(FissionMaterials.PLUTONIUM.get(), 1),
                            item(FissionMaterials.U238.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 2),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 2),
                            item(FissionMaterials.MINOR_ACTINIDES.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 2)),
                    reprocess("reprocess_th_low", FissionMaterials.SPENT_FUEL_TH_LOW.get(),
                            item(FissionMaterials.U233.get(), 2),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_th_mid", FissionMaterials.SPENT_FUEL_TH_MID.get(),
                            item(FissionMaterials.U233.get(), 2),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_th_high", FissionMaterials.SPENT_FUEL_TH_HIGH.get(),
                            item(FissionMaterials.U233.get(), 3),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 2),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_u233_seed", FissionMaterials.SPENT_U233_SEED.get(),
                            item(FissionMaterials.U233.get(), 2),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_ma_rod", FissionMaterials.SPENT_MA_ROD.get(),
                            item(FissionMaterials.ZR_90.get(), 1),
                            item(FissionMaterials.MO_95.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 2),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_pu_inert", FissionMaterials.SPENT_PU_INERT.get(),
                            item(FissionMaterials.PLUTONIUM.get(), 1),
                            item(FissionMaterials.U238.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 2)),
                    reprocess("reprocess_poison_rod", FissionMaterials.SPENT_POISON_ROD.get(),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_uc_rod", FissionMaterials.SPENT_UC_ROD.get(),
                            item(FissionMaterials.U235.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.SHORT_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                    reprocess("reprocess_uzr_rod", FissionMaterials.SPENT_UZR_ROD.get(),
                            item(FissionMaterials.PLUTONIUM.get(), 2),
                            item(FissionMaterials.U238.get(), 1),
                            item(FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1),
                            item(FissionMaterials.NUCLEAR_WASTE.get(), 2)),
                    reprocess("reprocess_ember_rod", FissionMaterials.SPENT_EMBER_ROD.get(),
                            item(FissionMaterials.SILENT_ASH.get(), 2),
                            item(FissionMaterials.STAR_CORE_PELLET.get(), 1),
                            item(FissionMaterials.LONG_LIVED_WASTE.get(), 1)),

                    // ---- 水冷反应堆：以下九条只用于 <b>JEI 展示</b>（耐久制由反应堆方块实体自己算）
                    burn("burn_rod_leu", FissionMaterials.ROD_LEU.get(),
                            FissionMaterials.SPENT_FUEL.get(), 3000),
                    burn("burn_rod_meu", FissionMaterials.ROD_MEU.get(),
                            FissionMaterials.SPENT_FUEL_U_MID.get(), 3200),
                    burn("burn_rod_heu", FissionMaterials.ROD_HEU.get(),
                            FissionMaterials.SPENT_FUEL_U_HIGH.get(), 3600),
                    burn("burn_mox_rod", FissionMaterials.MOX_FUEL_ROD.get(),
                            FissionMaterials.SPENT_FUEL_MOX_LOW.get(), 4000),
                    burn("burn_mox_rod_mid", FissionMaterials.MOX_ROD_MID.get(),
                            FissionMaterials.SPENT_FUEL_MOX_MID.get(), 4800),
                    burn("burn_mox_rod_high", FissionMaterials.MOX_ROD_HIGH.get(),
                            FissionMaterials.SPENT_FUEL_MOX_HIGH.get(), 5600),
                    burn("burn_thorium_rod", FissionMaterials.THORIUM_FUEL_ROD.get(),
                            FissionMaterials.SPENT_FUEL_TH_LOW.get(), 3600),
                    burn("burn_thorium_rod_mid", FissionMaterials.THORIUM_ROD_MID.get(),
                            FissionMaterials.SPENT_FUEL_TH_MID.get(), 3800),
                    burn("burn_thorium_rod_high", FissionMaterials.THORIUM_ROD_HIGH.get(),
                            FissionMaterials.SPENT_FUEL_TH_HIGH.get(), 4200),
                    burn("burn_u233_seed_rod", FissionMaterials.U233_SEED_ROD.get(),
                            FissionMaterials.SPENT_U233_SEED.get(), 8000),
                    burn("burn_ma_transmutation_rod", FissionMaterials.MA_TRANSMUTATION_ROD.get(),
                            FissionMaterials.SPENT_MA_ROD.get(), 4000),
                    burn("burn_pu_inert_matrix_rod", FissionMaterials.PU_INERT_MATRIX_ROD.get(),
                            FissionMaterials.SPENT_PU_INERT.get(), 9000),
                    burn("burn_burnable_poison_rod", FissionMaterials.BURNABLE_POISON_ROD.get(),
                            FissionMaterials.SPENT_POISON_ROD.get(), 2400),
                    burn("burn_uc_high_power_rod", FissionMaterials.UC_HIGH_POWER_ROD.get(),
                            FissionMaterials.SPENT_UC_ROD.get(), 5000),
                    burn("burn_uzr_metal_rod", FissionMaterials.UZR_METAL_ROD.get(),
                            FissionMaterials.SPENT_UZR_ROD.get(), 8000),
                    burn("burn_unstable_ember_rod", FissionMaterials.UNSTABLE_EMBER_ROD.get(),
                            FissionMaterials.SPENT_EMBER_ROD.get(), 6000),
                    burn("burn_stable_ember_rod", FissionMaterials.STABLE_EMBER_ROD.get(),
                            FissionMaterials.SPENT_EMBER_ROD.get(), 12000),

                    // ---- 蒸汽轮机：蒸汽 → 凝结水
                    recipe("condense_steam", FissionMachineKind.STEAM_TURBINE, 40,
                            List.of(), fluids(fluid(FissionFluids.STEAM.source().get(), 1000)),
                            List.of(), fluids(fluid(Fluids.WATER, 1000))),

                    // ================================================================
                    // 基岩无限资源线（2026-11-01）
                    // ================================================================
                    // ---- γ 活化：必须排在基础层之前 —— 否则同一份基岩粉会先匹配大块浸出
                    recipe("bedrock_gamma_activate", FissionMachineKind.ORE_LEACHER,
                            BedrockBalance.SEPARATION_TICKS,
                            items(item(BedrockMaterials.BEDROCK_POWDER.get(), 1)),
                            fluids(fluid(BedrockFluids.GAMMA_SOLVENT.source().get(), 200)),
                            items(item(BedrockMaterials.ACTIVATED_BEDROCK_POWDER.get(), 1)),
                            List.of(), BedrockBalance.STAGE_MIN_FLOW),
                    // ---- 基础层：8 种断头流体的大块浸出（只出常见元素）
                    recipe("bedrock_bulk_acid", FissionMachineKind.ORE_LEACHER,
                            BedrockBalance.SEPARATION_TICKS,
                            items(item(BedrockMaterials.BEDROCK_POWDER.get(), 1)),
                            fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 500),
                                    fluid(BioFluids.ACETIC_ACID.source().get(), 200),
                                    fluid(CoalFluids.NAPHTHALENE.source().get(), 100),
                                    fluid(PetroFluids.SOUR_GAS.source().get(), 250)),
                            items(item(OreMaterials.IRON_DUST.get(), 4),
                                    item(TraceElementDusts.SILICON_DUST.get(), 4),
                                    item(MaterialItems.COPPER_DUST.get(), 3),
                                    item(LeadMaterials.LEAD_DUST.get(), 2),
                                    item(PetroMaterials.SULFUR_DUST.get(), 4),
                                    item(Items.GRAVEL, 2)),
                            List.of(), BedrockBalance.STAGE_MIN_FLOW),
                    recipe("bedrock_bulk_bio", FissionMachineKind.ORE_LEACHER,
                            BedrockBalance.SEPARATION_TICKS,
                            items(item(BedrockMaterials.BEDROCK_POWDER.get(), 1)),
                            fluids(fluid(BioFluids.ANTIBIOTICS.source().get(), 50),
                                    fluid(BioFluids.GLYCEROL.source().get(), 100),
                                    fluid(PetroFluids.LUBRICANT.source().get(), 50),
                                    fluid(BioFluids.BUTANOL.source().get(), 100),
                                    fluid(PetroFluids.LOW_SULFUR_OIL.source().get(), 100)),
                            items(item(SaltMaterials.BORON_DUST.get(), 2),
                                    item(FissionMaterials.LITHIUM_DUST.get(), 2),
                                    item(SaltMaterials.SODIUM_DUST.get(), 3),
                                    item(TraceElementDusts.MAGNESIUM_DUST.get(), 2),
                                    item(ElementDusts.GRAPHITE_DUST.get(), 4),
                                    item(Items.GRAVEL, 2)),
                            List.of(), BedrockBalance.STAGE_MIN_FLOW),

                    // ---- 高性能层：S1 / S2 / S4
                    recipe("bedrock_s1_halide", FissionMachineKind.ORE_LEACHER,
                            BedrockBalance.STAGE_TICKS,
                            items(item(BedrockMaterials.ACTIVATED_BEDROCK_POWDER.get(), 1)),
                            fluids(fluid(BedrockFluids.HALIDE_SOLVENT.source().get(), 200),
                                    fluid(OreFluids.CHLORINE.source().get(), 1_000),
                                    fluid(CoalFluids.NAPHTHALENE.source().get(), 100)),
                            items(bedrockResidue(BedrockStage.S1, 1)),
                            fluids(fluid(BedrockFluids.VOLATILE_HALIDE_MIX.source().get(), 2_000)),
                            BedrockBalance.STAGE_MIN_FLOW),
                    recipe("bedrock_s2_molten_salt", FissionMachineKind.ORE_LEACHER,
                            BedrockBalance.STAGE_TICKS,
                            items(bedrockResidue(BedrockStage.S1, 1),
                                    item(SaltMaterials.SODIUM_HYDROXIDE.get(), 4)),
                            fluids(fluid(BedrockFluids.SELFHEAT_SOLVENT.source().get(), 200),
                                    fluid(BioFluids.GLYCEROL.source().get(), 200)),
                            items(bedrockResidue(BedrockStage.S2, 1)),
                            fluids(fluid(BedrockFluids.MOLTEN_SALT_LEACHATE.source().get(), 2_000)),
                            BedrockBalance.STAGE_MIN_FLOW),
                    recipe("bedrock_s4_sulfide", FissionMachineKind.ORE_LEACHER,
                            BedrockBalance.STAGE_TICKS,
                            items(bedrockResidue(BedrockStage.S3, 1)),
                            fluids(fluid(PetroFluids.SOUR_GAS.source().get(), 500),
                                    fluid(BioFluids.ACETIC_ACID.source().get(), 200),
                                    fluid(CoalFluids.NAPHTHALENE.source().get(), 100)),
                            items(item(BedrockMaterials.MIXED_SULFIDE.get(), 4),
                                    bedrockResidue(BedrockStage.S4, 1)),
                            List.of(), BedrockBalance.STAGE_MIN_FLOW),

                    // ---- 离心分离：S1 重卤化物 / S2 重金属 / S4 半金属 / S5 钠镁
                    recipe("bedrock_s1_heavy", FissionMachineKind.CENTRIFUGE,
                            BedrockBalance.CENTRIFUGE_SEPARATION_TICKS,
                            List.of(),
                            fluids(fluid(BedrockFluids.VOLATILE_HALIDE_MIX.source().get(), 1_000)),
                            items(item(ElementDusts.ZIRCONIUM_DUST.get(), 2),
                                    item(MonaziteMaterials.HAFNIUM_POWDER.get(), 1),
                                    item(ElementDusts.NIOBIUM_DUST.get(), 2),
                                    item(Items.GRAVEL, 1)),
                            List.of(), BedrockBalance.SEPARATION_MIN_FLOW),
                    recipe("bedrock_s2_heavy", FissionMachineKind.CENTRIFUGE,
                            BedrockBalance.CENTRIFUGE_SEPARATION_TICKS,
                            List.of(),
                            fluids(fluid(BedrockFluids.MOLTEN_SALT_LEACHATE.source().get(), 1_000),
                                    fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 250)),
                            items(item(ElementDusts.NICKEL_DUST.get(), 4),
                                    item(FissionMaterials.COBALT_DUST.get(), 2),
                                    item(ElementDusts.MOLYBDENUM_DUST.get(), 2),
                                    item(TungstenMaterials.TUNGSTEN_DUST.get(), 2),
                                    item(TraceElementDusts.VANADIUM_DUST.get(), 2),
                                    item(Items.GRAVEL, 1)),
                            List.of(), BedrockBalance.SEPARATION_MIN_FLOW),
                    recipe("bedrock_s4_heavy", FissionMachineKind.CENTRIFUGE,
                            BedrockBalance.CENTRIFUGE_SEPARATION_TICKS,
                            items(item(BedrockMaterials.MIXED_SULFIDE.get(), 2)),
                            fluids(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 250)),
                            items(item(PetroMaterials.SULFUR_DUST.get(), 10),
                                    item(Items.GRAVEL, 1)),
                            List.of(), BedrockBalance.SEPARATION_MIN_FLOW),
                    recipe("bedrock_s5_heavy", FissionMachineKind.CENTRIFUGE,
                            BedrockBalance.CENTRIFUGE_SEPARATION_TICKS,
                            List.of(),
                            fluids(fluid(BedrockFluids.BIOLEACHATE.source().get(), 1_000),
                                    fluid(OreFluids.SULFURIC_ACID.source().get(), 250)),
                            items(item(SaltMaterials.SODIUM_DUST.get(), 6),
                                    item(TraceElementDusts.MAGNESIUM_DUST.get(), 4),
                                    item(TraceElementDusts.GALLIUM_DUST.get(), 1),
                                    item(ElementDusts.GRAPHITE_DUST.get(), 10),
                                    item(Items.GRAVEL, 1)),
                            fluids(fluid(OreFluids.CHLORINE.source().get(), 1_000)),
                            BedrockBalance.SEPARATION_MIN_FLOW));
        }
        return cache;
    }

    /** 后处理：1 根同一档冷却乏燃料 + 硝酸 250 + 煤油 250 → 回收锕系 / 锆管 / 高放废料。 */
    private static FissionRecipe reprocess(String id, Item spent, ItemIngredient... outputs) {
        return recipe(id, FissionMachineKind.REPROCESSOR, REPROCESS_TICKS,
                items(item(spent, 1)),
                fluids(fluid(FissionFluids.NITRIC_ACID.source().get(), 250),
                        fluid(PetroFluids.KEROSENE.source().get(), 250)),
                items(outputs), List.of());
    }

    /** JEI 用的「烧一根棒」展示配方：输入棒 + 水，产出对应乏燃料 + 蒸汽。 */
    private static FissionRecipe burn(String id, Item rod, Item spent, int steamAmount) {
        return recipe(id, FissionMachineKind.FISSION_REACTOR, 600,
                items(item(rod, 1)), fluids(fluid(Fluids.WATER, 1000)),
                items(item(spent, 1)), fluids(fluid(FissionFluids.STEAM.source().get(), steamAmount)));
    }

    private static FissionRecipe recipe(String id, FissionMachineKind kind, int ticks,
                                        List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                                        List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs) {
        return new FissionRecipe(id, kind, ticks, itemInputs, fluidInputs, itemOutputs, fluidOutputs, null);
    }

    /** 带热流率门槛的配方（基岩线用）。 */
    private static FissionRecipe recipe(String id, FissionMachineKind kind, int ticks,
                                        List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                                        List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs,
                                        double minFlowRate) {
        return new FissionRecipe(id, kind, ticks, itemInputs, fluidInputs, itemOutputs, fluidOutputs,
                null, minFlowRate);
    }

    /** 罐装机配方：额外要求温度带（低 / 中 / 高）。 */
    private static FissionRecipe canning(String id, FermentationKinetics.Band band, int ticks,
                                         List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                                         List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs) {
        return new FissionRecipe(id, FissionMachineKind.CANNING_MACHINE, ticks,
                itemInputs, fluidInputs, itemOutputs, fluidOutputs, band);
    }

    private static ItemIngredient item(net.minecraft.world.level.ItemLike item, int count) {
        return new ItemIngredient(ItemResource.of(new ItemStack(item)), count);
    }

    /** 带粒子类型的压力罐产物（满耐久）。 */
    private static ItemIngredient tank(ParticleType type) {
        ItemStack stack = new ItemStack(ParticleItems.PRESSURE_TANK.get());
        stack.set(ParticleComponents.PARTICLE_TYPE.get(), type);
        return new ItemIngredient(ItemResource.of(stack), 1);
    }

    /** 带来源类型的浸出渣（B0 两段浸出的输入 / 一段的产出）。 */
    private static ItemIngredient residue(LeachResidueType type, int count) {
        ItemStack stack = new ItemStack(FissionMaterials.LEACH_RESIDUE.get());
        stack.set(FissionComponents.LEACH_RESIDUE_TYPE.get(), type);
        return new ItemIngredient(ItemResource.of(stack), count);
    }

    /** 带段位的基岩浸出渣。 */
    private static ItemIngredient bedrockResidue(BedrockStage stage, int count) {
        ItemStack stack = new ItemStack(BedrockMaterials.BEDROCK_RESIDUE.get());
        stack.set(BedrockComponents.BEDROCK_STAGE.get(), stage);
        return new ItemIngredient(ItemResource.of(stack), count);
    }

    private static FluidIngredient fluid(net.minecraft.world.level.material.Fluid fluid, int amount) {
        return new FluidIngredient(FluidResource.of(new FluidStack(fluid, amount)), amount);
    }

    private static List<ItemIngredient> items(ItemIngredient... entries) {
        return List.of(entries);
    }

    private static List<FluidIngredient> fluids(FluidIngredient... entries) {
        return List.of(entries);
    }

    /** 某个机种的配方（按声明顺序）。 */
    public static List<FissionRecipe> forKind(FissionMachineKind kind) {
        List<FissionRecipe> list = new ArrayList<>();
        for (FissionRecipe recipe : all()) {
            if (recipe.kind() == kind) {
                list.add(recipe);
            }
        }
        return List.copyOf(list);
    }

    /** 只试不落：当前料情下本机种能不能结算一次（事务开完即关 = 整体回滚）。 */
    public static boolean canCraft(FissionMachineKind kind, LargeMachineItemHandler items,
                                   LargeMachineFluidHandler fluids, FermentationKinetics.Band band,
                                   double flowRate) {
        return find(kind, items, fluids, band, flowRate) != null;
    }

    /**
     * 找到当前料情会结算的那条配方（按声明顺序）；没有返回 {@code null}。
     * <p>机器用它的 {@link FissionRecipe#ticks()} 当周期，所以同一台机器上的不同配方可以有不同时长
     * （离心机：UF6 富集 12,000t，废料分离 600t）。
     * <p>{@code band} 只对罐装机的三档配方生效；其它机种的配方 {@code requiredBand == null}，不受影响。
     */
    public static FissionRecipe find(FissionMachineKind kind, LargeMachineItemHandler items,
                                     LargeMachineFluidHandler fluids, FermentationKinetics.Band band,
                                     double flowRate) {
        for (FissionRecipe recipe : all()) {
            if (recipe.kind() == kind && tryRecipe(recipe, items, fluids, false, band, flowRate)) {
                return recipe;
            }
        }
        return null;
    }

    /** 按机种结算一次：抽输入 + 放输出同事务提交。 */
    public static boolean craftOnce(FissionMachineKind kind, LargeMachineItemHandler items,
                                    LargeMachineFluidHandler fluids, FermentationKinetics.Band band,
                                    double flowRate) {
        for (FissionRecipe recipe : all()) {
            if (recipe.kind() == kind && tryRecipe(recipe, items, fluids, true, band, flowRate)) {
                return true;
            }
        }
        return false;
    }

    /** 跑一条配方：抽输入 + 放输出，全在一条根事务里；{@code execute=false} 时事务开完即关（等于回滚）。 */
    private static boolean tryRecipe(FissionRecipe recipe, LargeMachineItemHandler items,
                                     LargeMachineFluidHandler fluids, boolean execute,
                                     FermentationKinetics.Band band, double flowRate) {
        if (!recipe.availableIn(band) || !recipe.availableAt(flowRate)) {
            return false;
        }
        return inTransaction(execute, transaction -> {
            for (FluidIngredient input : recipe.fluidInputs()) {
                if (fluids.extractFromInputs(input.resource(), input.amount(), transaction) < input.amount()) {
                    return false;
                }
            }
            for (ItemIngredient input : recipe.itemInputs()) {
                if (items.extractFromInputs(input.resource(), input.count(), transaction) < input.count()) {
                    return false;
                }
            }
            for (FluidIngredient output : recipe.fluidOutputs()) {
                if (fluids.insertIntoOutputs(output.resource(), output.amount(), transaction) < output.amount()) {
                    return false;
                }
            }
            for (ItemIngredient output : recipe.itemOutputs()) {
                if (items.insertIntoOutputs(output.resource(), output.count(), transaction) < output.count()) {
                    return false;
                }
            }
            return true;
        });
    }

    /** 在一条根事务里跑「抽料 + 产出」；{@code execute=false} 时事务开完即关（等于回滚），只当试算。 */
    private static boolean inTransaction(boolean execute, Predicate<TransactionContext> body) {
        try (Transaction transaction = Transaction.openRoot()) {
            if (!body.test(transaction)) {
                return false;
            }
            if (execute) {
                transaction.commit();
            }
            return true;
        }
    }

    private FissionRecipes() {}
}
