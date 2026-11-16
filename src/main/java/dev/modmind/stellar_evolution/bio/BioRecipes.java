package dev.modmind.stellar_evolution.bio;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.bedrock.BedrockBalance;
import dev.modmind.stellar_evolution.bedrock.BedrockComponents;
import dev.modmind.stellar_evolution.bedrock.BedrockFluids;
import dev.modmind.stellar_evolution.bedrock.BedrockMaterials;
import dev.modmind.stellar_evolution.bedrock.BedrockStage;
import dev.modmind.stellar_evolution.bio.BioRecipe.FluidIngredient;
import dev.modmind.stellar_evolution.bio.BioRecipe.ItemIngredient;
import dev.modmind.stellar_evolution.coal.CoalFluids;
import dev.modmind.stellar_evolution.coal.CoalMaterials;
import dev.modmind.stellar_evolution.fission.FissionComponents;
import dev.modmind.stellar_evolution.fission.FissionFluids;
import dev.modmind.stellar_evolution.fission.FissionMaterials;
import dev.modmind.stellar_evolution.fission.LeachResidueType;
import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.large.LargeMachineItemHandler;
import dev.modmind.stellar_evolution.material.ElementDusts;
import dev.modmind.stellar_evolution.material.LeadMaterials;
import dev.modmind.stellar_evolution.material.MaterialItems;
import dev.modmind.stellar_evolution.material.TungstenMaterials;
import dev.modmind.stellar_evolution.ore.BauxiteMaterials;
import dev.modmind.stellar_evolution.ore.MonaziteMaterials;
import dev.modmind.stellar_evolution.ore.OreFluids;
import dev.modmind.stellar_evolution.ore.OreMaterials;
import dev.modmind.stellar_evolution.ore.SaltMaterials;
import dev.modmind.stellar_evolution.ore.TitaniumMaterials;
import dev.modmind.stellar_evolution.ore.TraceElementDusts;
import dev.modmind.stellar_evolution.petro.PetroFluids;
import dev.modmind.stellar_evolution.petro.PetroMaterials;
import dev.modmind.stellar_evolution.space.SpaceMaterials;
import dev.modmind.stellar_evolution.welding.WeldingFluids;
import dev.modmind.stellar_evolution.wood.WoodFluids;
import dev.modmind.stellar_evolution.wood.WoodMaterials;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * 生化线的<strong>正式配方链</strong>（内置表，见《生化线》§4 / §5；B1–B3）。
 *
 * <pre>
 *   机械土壤 →(生物质) 调浆机 →(生物质浆) 发酵罐 →(沼气 | 发酵液 | 醋酸)
 *                                            离心机 →(乙醇 + 菌种)
 *                       灭菌罐 →(水解糖液) 生物反应釜 →(培养基 + 酶溶液) 结晶机 →(固体酶)
 *                                            生物反应釜 →(蛋白溶液 + 抗生素)
 *                       结晶机 →(矿物盐)
 * </pre>
 *
 * <p>匹配口径：
 * <ul>
 *   <li>原料从<strong>输入容器</strong>（标 {@code INPUT} 的铜箱子 / 储罐）取，产物进<strong>输出容器</strong>（标 {@code OUTPUT}）；
 *       抽料 + 产出在<strong>同一条事务</strong>里，任一步不成立就整体回滚；</li>
 *   <li>按表内顺序<strong>取第一条「条件满足 且 料够 且 放得下」</strong>的配方 —— 发酵罐的三条靠
 *       {@link BioRecipe#requiredBand 温度带}互斥，所以同一份进料会按当前热流率分到不同产物。</li>
 * </ul>
 * 配方**代码内置**（不进数据包）；JEI 直接读这张表，保证「机器真跑的」与「JEI 显示的」一致。
 */
public final class BioRecipes {
    /** 配方基准时长（刻）的缺省值；机器实际按<strong>结算配方的 {@code baseTicks}</strong> 推进（见 {@code BioMachineBlockEntity#onThermalTick}），这个只当兜底缺省。 */
    public static final int DEFAULT_TICKS = 200;

    private static final List<BioRecipe> ALL = build();
    /** 概率产物的掷点源（只在 execute 结算时用）。 */
    private static final RandomSource RANDOM = RandomSource.create();

    /** 全部配方（JEI / 命令遍历用）。 */
    public static List<BioRecipe> all() {
        return ALL;
    }

    /** 某类配方（按表内顺序）。 */
    public static List<BioRecipe> forType(BioRecipeType type) {
        List<BioRecipe> result = new ArrayList<>();
        for (BioRecipe recipe : ALL) {
            if (recipe.type() == type) {
                result.add(recipe);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 尝试按该类配方结算一次：从前往后取第一条「条件满足 + 料够 + 放得下」的，抽料与产出同事务提交。
     *
     * @param band      当前温度带（由机器上的供能铜块数量决定，见 {@link FermentationKinetics#bandForCopper}）
     * @param flowRate  当前网络热流率（HP/t）
     * @return 真正结算的那条配方；一条都没成则 null（机器据此把周期改成该配方的 {@code baseTicks}）
     */
    @Nullable
    public static BioRecipe craftOnce(BioRecipeType type, LargeMachineItemHandler items, LargeMachineFluidHandler fluids,
                                      FermentationKinetics.Band band, double flowRate) {
        for (BioRecipe recipe : forType(type)) {
            if (!recipe.conditionsMet(band, flowRate)) {
                continue;
            }
            if (apply(recipe, items, fluids, true)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 只试不落：当前条件 / 料情下有没有一条配方能结算（事务开完就关，等于整体回滚）。
     * <p>机器用它判断<strong>「有没有活干」</strong> —— 没活就停在这一刻（进度清零、Jade 不画配方槽、可动部件也停）。
     */
    public static boolean canCraft(BioRecipeType type, LargeMachineItemHandler items, LargeMachineFluidHandler fluids,
                                   FermentationKinetics.Band band, double flowRate) {
        for (BioRecipe recipe : forType(type)) {
            if (recipe.conditionsMet(band, flowRate) && apply(recipe, items, fluids, false)) {
                return true;
            }
        }
        return false;
    }

    /** 该类配方里是否有一条带「温度带」条件（Jade 据此决定显示「XX 模式」还是通用状态行）。 */
    public static boolean requiresBand(BioRecipeType type) {
        for (BioRecipe recipe : forType(type)) {
            if (recipe.requiredBand() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 抽料 + 产出；任一步不成立就关掉事务（自动回滚）返回 false。
     *
     * @param execute {@code false} = 只试不落（不提交事务）—— 供 {@link #canCraft} 干跑
     */
    private static boolean apply(BioRecipe recipe, LargeMachineItemHandler items, LargeMachineFluidHandler fluids,
                                 boolean execute) {
        try (Transaction transaction = Transaction.openRoot()) {
            // 催化剂：只判定「在不在输入容器里」，不消耗
            if (recipe.catalyst() != null
                    && !items.hasInInputs(ItemResource.of(new ItemStack(recipe.catalyst())), 1)) {
                return false;
            }
            for (ItemIngredient input : recipe.itemInputs()) {
                if (items.extractFromInputs(input.resource(), input.count(), transaction) < input.count()) {
                    return false;
                }
            }
            for (FluidIngredient input : recipe.fluidInputs()) {
                if (fluids.extractFromInputs(input.resource(), input.amount(), transaction) < input.amount()) {
                    return false;
                }
            }
            for (ItemIngredient output : recipe.itemOutputs()) {
                // 概率产物：真结算时先掷点，不中就不产；干跑（容量校验）按「会产」处理
                if (execute && output.chance() < 1.0D && RANDOM.nextFloat() >= (float) output.chance()) {
                    continue;
                }
                if (items.insertIntoOutputs(output.resource(), output.count(), transaction) < output.count()) {
                    return false;
                }
            }
            for (FluidIngredient output : recipe.fluidOutputs()) {
                if (fluids.insertIntoOutputs(output.resource(), output.amount(), transaction) < output.amount()) {
                    return false;
                }
            }
            if (execute) {
                transaction.commit();
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ 配方链

    private static List<BioRecipe> build() {
        List<BioRecipe> recipes = new ArrayList<>();

        // ================================================================ 调浆机（SLURRYING）
        recipes.add(recipe("slurry_from_biomass", BioRecipeType.SLURRYING,
                List.of(item(BioMaterials.BIOMASS.get(), 2)), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1000)),
                null, 0.0D));
        recipes.add(recipe("slurry_from_lignocellulose", BioRecipeType.SLURRYING,
                List.of(item(BioMaterials.LIGNOCELLULOSE.get(), 2)), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 600)),
                null, 0.0D));
        recipes.add(recipe("slurry_dissolve_sugar", BioRecipeType.SLURRYING,
                List.of(item(Items.SUGAR, 2)), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 900)),
                null, 0.0D));
        // 甘蔗直接制糖液：糖液链的种子来源（机械沙地可种甘蔗）
        recipes.add(recipe("slurry_sugar_cane", BioRecipeType.SLURRYING,
                List.of(item(Items.SUGAR_CANE, 2)), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 500)),
                null, 0.0D));
        recipes.add(recipe("slurry_from_fertilizer", BioRecipeType.SLURRYING,
                List.of(item(BioMaterials.ORGANIC_FERTILIZER.get(), 3)), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 800)),
                null, 0.0D));
        recipes.add(recipe("slurry_enriched", BioRecipeType.SLURRYING,
                List.of(item(BioMaterials.BIOMASS.get(), 1)), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 500)),
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1200)),
                null, 0.0D));
        // 焊接助焊剂：硼砂 + 水（盐湖线给硼砂的第一个前期去向，2026-11-08）
        recipes.add(recipe("slurry_flux", BioRecipeType.SLURRYING,
                List.of(item(SaltMaterials.BORAX.get(), 1)), List.of(fluid(Fluids.WATER, 500)),
                List.of(), List.of(fluid(WeldingFluids.FLUX.source().get(), 500)),
                null, 0.0D));
        // 煤水浆：煤炭粉 + 水（2026-11-12 煤直接液化前置；破碎机 → 煤粉 → 调浆机）
        recipes.add(recipe("slurry_coal", BioRecipeType.SLURRYING,
                List.of(item(CoalMaterials.COAL_DUST.get(), 2)), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(CoalFluids.COAL_SLURRY.source().get(), 1000)),
                null, 0.0D));

        // ================================================================ 发酵罐（FERMENTING，温度带互斥）
        // 生物质浆：中温 → 发酵液；厌氧 → 沼气；高温 → 醋酸 + 沼气
        recipes.add(recipe("ferment_to_broth", BioRecipeType.FERMENTING,
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.FERMENTATION_BROTH), 1000)),
                FermentationKinetics.Band.MESOPHILIC, 0.0D));
        recipes.add(recipe("ferment_to_biogas", BioRecipeType.FERMENTING,
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOGAS), 600)),
                FermentationKinetics.Band.ANAEROBIC, 0.0D));
        // 生化 3：极端环境菌种让高温带醋酸收率接近翻倍（排在普通高温行之前，有菌种就先走它）
        recipes.add(recipe("ferment_acetic_acid_extremophile", BioRecipeType.FERMENTING,
                List.of(item(BioMaterials.EXTREMOPHILE_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.ACETIC_ACID), 900), fluid(fluidOf(BioFluids.BIOGAS), 200)),
                FermentationKinetics.Band.THERMOPHILIC, 0.0D));
        recipes.add(recipe("ferment_to_acetic_acid", BioRecipeType.FERMENTING,
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.ACETIC_ACID), 500), fluid(fluidOf(BioFluids.BIOGAS), 100)),
                FermentationKinetics.Band.THERMOPHILIC, 0.0D));
        // 糖液：中温 → 乙醇；高温 → 乳酸；厌氧 → 丙酮 + 丁醇 + 乙醇（ABE）
        recipes.add(recipe("ferment_sugar_to_ethanol", BioRecipeType.FERMENTING,
                List.of(), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.ETHANOL), 600), fluid(fluidOf(BioFluids.BIOGAS), 100)),
                FermentationKinetics.Band.MESOPHILIC, 0.0D));
        recipes.add(recipe("ferment_sugar_to_lactic", BioRecipeType.FERMENTING,
                List.of(), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.LACTIC_ACID), 500)),
                FermentationKinetics.Band.THERMOPHILIC, 0.0D));
        recipes.add(recipe("ferment_sugar_to_abe", BioRecipeType.FERMENTING,
                List.of(), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.ACETONE), 200), fluid(fluidOf(BioFluids.BUTANOL), 300),
                        fluid(fluidOf(BioFluids.ETHANOL), 100)),
                FermentationKinetics.Band.ANAEROBIC, 0.0D));
        // 过氧化氢：水 1000 + 矿物盐（催化剂，不消耗），高温带 → 过氧化氢 500
        recipes.add(recipe("ferment_hydrogen_peroxide", BioRecipeType.FERMENTING,
                List.of(), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(fluidOf(OreFluids.HYDROGEN_PEROXIDE), 500)),
                FermentationKinetics.Band.THERMOPHILIC, 0.0D, BioMaterials.MINERAL_SALT.get()));

        // ================================================================ 分解仪（DECOMPOSING）
        recipes.add(recipe("decomposer_broth", BioRecipeType.DECOMPOSING,
                List.of(), List.of(fluid(fluidOf(BioFluids.FERMENTATION_BROTH), 1000)),
                List.of(item(BioMaterials.CULTURE_STRAIN.get(), 1)), List.of(fluid(fluidOf(BioFluids.ETHANOL), 500)),
                null, 0.0D));
        recipes.add(recipe("decomposer_medium_strain", BioRecipeType.DECOMPOSING,
                List.of(), List.of(fluid(fluidOf(BioFluids.NUTRIENT_MEDIUM), 1000)),
                List.of(item(BioMaterials.CULTURE_STRAIN.get(), 1)), List.of(),
                null, 0.0D));
        recipes.add(recipe("decomposer_slurry", BioRecipeType.DECOMPOSING,
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1000)),
                List.of(item(BioMaterials.ORGANIC_FERTILIZER.get(), 1)), List.of(),
                null, 0.0D));
        recipes.add(recipe("decomposer_protein", BioRecipeType.DECOMPOSING,
                List.of(), List.of(fluid(fluidOf(BioFluids.PROTEIN_SOLUTION), 500)),
                List.of(), List.of(fluid(fluidOf(BioFluids.AMINO_ACID_SOLUTION), 300)),
                null, 0.0D));
        // 红石粉再处理 → 汞（2026-11-08 自离心机迁入，汞改为流体；铬粉必定产出）
        recipes.add(recipe("decompose_redstone_mercury", BioRecipeType.DECOMPOSING,
                List.of(item(Items.REDSTONE, 3)),
                List.of(fluid(OreFluids.HYDROGEN_PEROXIDE.source().get(), 1_000)),
                List.of(item(ElementDusts.CHROMIUM_DUST.get(), 1), item(Items.GRAVEL, 1)),
                List.of(fluid(OreFluids.MERCURY.source().get(), 500)),
                null, 0.0D));
        // 矿物盐分选：硼砂的唯一来源（2026-11-08 自离心机迁入分解仪，不再卡离心机）
        recipes.add(recipe("salt_split", BioRecipeType.DECOMPOSING,
                List.of(item(BioMaterials.MINERAL_SALT.get(), 4)),
                List.of(fluid(Fluids.WATER, 1_000)),
                List.of(item(SaltMaterials.BORAX.get(), 1),
                        item(SaltMaterials.SODIUM_CHLORIDE.get(), 2),
                        item(TraceElementDusts.MAGNESIUM_DUST.get(), 1),
                        item(Items.GRAVEL, 1)),
                List.of(), null, 0.0D));

        // ================================================================ 选冶线（G 线）：浮选
        // 粗矿 / 无粗矿的原矿 + 水 1000 + 糖液 250 → 富精矿浆 3000 + 砂砾 1
        // 有粗矿的四种：铁 / 铜 / 金 / 铀
        recipes.add(flotation("flotate_raw_iron", Items.RAW_IRON, OreFluids.RICH_IRON_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_raw_copper", Items.RAW_COPPER, OreFluids.RICH_COPPER_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_raw_gold", Items.RAW_GOLD, OreFluids.RICH_GOLD_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_raw_uranium", OreMaterials.RAW_URANIUM.get(),
                OreFluids.RICH_URANIUM_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_raw_tungsten", TungstenMaterials.RAW_TUNGSTEN.get(),
                OreFluids.RICH_TUNGSTEN_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_raw_lead", LeadMaterials.RAW_LEAD.get(),
                OreFluids.RICH_LEAD_CONCENTRATE_SLURRY));
        // 无粗矿：原矿直接当粗矿（下界金矿 / 远古残骸）
        recipes.add(flotation("flotate_nether_gold_ore", Items.NETHER_GOLD_ORE,
                OreFluids.RICH_GOLD_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_ancient_debris", Items.ANCIENT_DEBRIS,
                OreFluids.RICH_DEBRIS_CONCENTRATE_SLURRY));
        // 无粗矿：非金属（含深板岩变种）
        recipes.add(flotation("flotate_coal_ore", Items.COAL_ORE, OreFluids.RICH_COAL_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_deepslate_coal_ore", Items.DEEPSLATE_COAL_ORE,
                OreFluids.RICH_COAL_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_diamond_ore", Items.DIAMOND_ORE,
                OreFluids.RICH_DIAMOND_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_deepslate_diamond_ore", Items.DEEPSLATE_DIAMOND_ORE,
                OreFluids.RICH_DIAMOND_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_emerald_ore", Items.EMERALD_ORE,
                OreFluids.RICH_EMERALD_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_deepslate_emerald_ore", Items.DEEPSLATE_EMERALD_ORE,
                OreFluids.RICH_EMERALD_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_lapis_ore", Items.LAPIS_ORE,
                OreFluids.RICH_LAPIS_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_deepslate_lapis_ore", Items.DEEPSLATE_LAPIS_ORE,
                OreFluids.RICH_LAPIS_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_redstone_ore", Items.REDSTONE_ORE,
                OreFluids.RICH_REDSTONE_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_deepslate_redstone_ore", Items.DEEPSLATE_REDSTONE_ORE,
                OreFluids.RICH_REDSTONE_CONCENTRATE_SLURRY));
        recipes.add(flotation("flotate_nether_quartz_ore", Items.NETHER_QUARTZ_ORE,
                OreFluids.RICH_QUARTZ_CONCENTRATE_SLURRY));

        // ================================================================ 灭菌罐（STERILIZING，吃热流率）
        // 热流率门槛按「一条并行 = 128 HP/t（一台热力引擎）」折算：160 ≈ 1.25 台、224 ≈ 1.75 台
        // γ 辐照灭菌（裂变线铯-137 的消耗性去向）：铯-137 自带 γ 源，不吃热流率、水解糖液产率更高。
        // 排在最前：输入里放了铯-137 才走这条（不放就还是走下面的热力灭菌）。
        recipes.add(recipe("sterilize_gamma_hydrolyse", BioRecipeType.STERILIZING,
                List.of(item(FissionMaterials.CESIUM_137.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.HYDROLYSATE), 1000)),
                null, 0.0D));
        recipes.add(recipe("hydrolyse_slurry", BioRecipeType.STERILIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.BIOMASS_SLURRY), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.HYDROLYSATE), 800)),
                null, 160.0D));
        recipes.add(recipe("saccharify_lignocellulose", BioRecipeType.STERILIZING,
                List.of(item(BioMaterials.LIGNOCELLULOSE.get(), 2)), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 900)),
                null, 192.0D));
        recipes.add(recipe("refine_hydrolysate", BioRecipeType.STERILIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.HYDROLYSATE), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 1000)),
                null, 160.0D));
        recipes.add(recipe("hydrolyse_protein", BioRecipeType.STERILIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.PROTEIN_SOLUTION), 1000)),
                List.of(), List.of(fluid(fluidOf(BioFluids.AMINO_ACID_SOLUTION), 600)),
                null, 224.0D));

        // ================================================================ 结晶机（CRYSTALLIZING）
        recipes.add(recipe("crystallize_salt", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(Fluids.WATER, 1000)),
                List.of(item(BioMaterials.MINERAL_SALT.get(), 1)), List.of(),
                null, 0.0D));
        recipes.add(recipe("crystallize_dried_enzyme", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.ENZYME_SOLUTION), 500)),
                List.of(item(BioMaterials.DRIED_ENZYME.get(), 1)), List.of(),
                null, 0.0D));
        recipes.add(recipe("crystallize_amino", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.AMINO_ACID_SOLUTION), 500)),
                List.of(item(BioMaterials.AMINO_POWDER.get(), 1)), List.of(),
                null, 0.0D));
        // 石化 3 第二批去向：氘代溶剂做重结晶介质，蛋白粉收率 ×3（排在普通结晶行之前）
        recipes.add(recipe("crystallize_protein_deuterated", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.PROTEIN_SOLUTION), 500),
                        fluid(PetroFluids.DEUTERATED_SOLVENT.source().get(), 250)),
                List.of(item(BioMaterials.PROTEIN_POWDER.get(), 3)), List.of(),
                null, 0.0D));
        recipes.add(recipe("crystallize_protein", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.PROTEIN_SOLUTION), 500)),
                List.of(item(BioMaterials.PROTEIN_POWDER.get(), 1)), List.of(),
                null, 0.0D));
        recipes.add(recipe("crystallize_sugar", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 1000)),
                List.of(item(Items.SUGAR, 2)), List.of(),
                null, 0.0D));
        recipes.add(recipe("crystallize_dried_strain", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(fluidOf(BioFluids.FERMENTATION_BROTH), 1000)),
                List.of(item(BioMaterials.DRIED_STRAIN.get(), 1)), List.of(),
                null, 0.0D));
        // ---- 裂变线湿法前端（复用结晶机）：沉淀 ADU / 蒸干萃余液
        recipes.add(recipe("precipitate_adu", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(FissionFluids.LOADED_EXTRACTANT.source().get(), 1000),
                        fluid(FissionFluids.AMMONIA_SOLUTION.source().get(), 500)),
                List.of(item(FissionMaterials.ADU_CAKE.get(), 1)),
                List.of(fluid(PetroFluids.KEROSENE.source().get(), 1000)),
                null, 0.0D));
        recipes.add(recipe("recover_raffinate", BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(FissionFluids.RAFFINATE.source().get(), 250)),
                List.of(item(FissionMaterials.RADIOACTIVE_SLUDGE.get(), 1)),
                List.of(fluid(Fluids.WATER, 250)),
                null, 0.0D));

        // ================================================================ 选冶线（G 线）：浆 → 精砂
        // 1500 富精矿浆 → 1 精砂（1 粗矿出 3000 浆 = 2 砂）
        recipes.add(crystallizeSand("settle_iron_sand", OreFluids.RICH_IRON_CONCENTRATE_SLURRY,
                OreMaterials.IRON_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_copper_sand", OreFluids.RICH_COPPER_CONCENTRATE_SLURRY,
                OreMaterials.COPPER_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_gold_sand", OreFluids.RICH_GOLD_CONCENTRATE_SLURRY,
                OreMaterials.GOLD_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_uranium_sand", OreFluids.RICH_URANIUM_CONCENTRATE_SLURRY,
                OreMaterials.URANIUM_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_tungsten_sand", OreFluids.RICH_TUNGSTEN_CONCENTRATE_SLURRY,
                TungstenMaterials.TUNGSTEN_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_lead_sand", OreFluids.RICH_LEAD_CONCENTRATE_SLURRY,
                LeadMaterials.LEAD_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_debris_sand", OreFluids.RICH_DEBRIS_CONCENTRATE_SLURRY,
                OreMaterials.DEBRIS_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_coal_sand", OreFluids.RICH_COAL_CONCENTRATE_SLURRY,
                OreMaterials.COAL_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_diamond_sand", OreFluids.RICH_DIAMOND_CONCENTRATE_SLURRY,
                OreMaterials.DIAMOND_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_emerald_sand", OreFluids.RICH_EMERALD_CONCENTRATE_SLURRY,
                OreMaterials.EMERALD_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_lapis_sand", OreFluids.RICH_LAPIS_CONCENTRATE_SLURRY,
                OreMaterials.LAPIS_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_redstone_sand", OreFluids.RICH_REDSTONE_CONCENTRATE_SLURRY,
                OreMaterials.REDSTONE_CONCENTRATE_SAND.get()));
        recipes.add(crystallizeSand("settle_quartz_sand", OreFluids.RICH_QUARTZ_CONCENTRATE_SLURRY,
                OreMaterials.QUARTZ_CONCENTRATE_SAND.get()));

        // ================================================================ 生物反应釜（BIOREACTION，多进多出）
        // 顺序要紧：维生素那条**没有物品输入**，若排在「培养基 + 菌种」之后会被永久遮蔽 → 放最前。
        recipes.add(recipe("bioreaction_vitamins", BioRecipeType.BIOREACTION,
                List.of(),
                List.of(fluid(fluidOf(BioFluids.AMINO_ACID_SOLUTION), 250), fluid(fluidOf(BioFluids.NUTRIENT_MEDIUM), 250)),
                List.of(item(BioMaterials.VITAMINS.get(), 1)), List.of(),
                null, 0.0D));
        // 生化 3：升级菌种的高收率行（放在普通菌种行之前，箱里有升级菌种就先走它）
        recipes.add(recipe("bioreaction_high_yield_amino", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.HIGH_YIELD_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 500)),
                List.of(), List.of(fluid(fluidOf(BioFluids.AMINO_ACID_SOLUTION), 600),
                        fluid(fluidOf(BioFluids.GLYCEROL), 150)),
                null, 0.0D));
        recipes.add(recipe("bioreaction_high_yield_enzyme", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.HIGH_YIELD_ENZYME_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.HYDROLYSATE), 500)),
                List.of(), List.of(fluid(fluidOf(BioFluids.ENZYME_SOLUTION), 600),
                        fluid(fluidOf(BioFluids.NUTRIENT_MEDIUM), 250)),
                null, 0.0D));
        recipes.add(recipe("bioreaction_medium", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.MINERAL_SALT.get(), 1), item(BioMaterials.CULTURE_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.HYDROLYSATE), 500)),
                List.of(), List.of(fluid(fluidOf(BioFluids.NUTRIENT_MEDIUM), 500), fluid(fluidOf(BioFluids.ENZYME_SOLUTION), 250)),
                null, 0.0D));
        // 生化 3 去向：手性中间体 + 无菌培养基 → 高纯抗生素（收率 ×6，副产维生素）
        recipes.add(recipe("bioreaction_chiral_antibiotics", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.CHIRAL_INTERMEDIATE.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.STERILE_MEDIUM), 500)),
                List.of(item(BioMaterials.VITAMINS.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.ANTIBIOTICS), 600)),
                null, 0.0D));
        // 生化 3 第二批去向：无菌培养基直接进反应釜，抗生素收率 ×4
        recipes.add(recipe("bioreaction_antibiotics_sterile", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.CULTURE_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.STERILE_MEDIUM), 500)),
                List.of(), List.of(fluid(fluidOf(BioFluids.ANTIBIOTICS), 400)),
                null, 0.0D));
        recipes.add(recipe("bioreaction_protein", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.CULTURE_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.NUTRIENT_MEDIUM), 500)),
                List.of(), List.of(fluid(fluidOf(BioFluids.PROTEIN_SOLUTION), 300), fluid(fluidOf(BioFluids.ANTIBIOTICS), 100)),
                null, 0.0D));
        recipes.add(recipe("bioreaction_amino", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.CULTURE_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 500)),
                List.of(), List.of(fluid(fluidOf(BioFluids.AMINO_ACID_SOLUTION), 300), fluid(fluidOf(BioFluids.GLYCEROL), 100)),
                null, 0.0D));
        recipes.add(recipe("bioreaction_bioplastic", BioRecipeType.BIOREACTION,
                List.of(item(Items.SUGAR, 1), item(BioMaterials.CULTURE_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.LACTIC_ACID), 250)),
                List.of(item(BioMaterials.BIOPLASTIC_PELLETS.get(), 1)), List.of(),
                null, 0.0D));
        recipes.add(recipe("bioreaction_biodiesel", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.CULTURE_STRAIN.get(), 1)),
                List.of(fluid(fluidOf(BioFluids.ENZYME_SOLUTION), 250), fluid(fluidOf(BioFluids.ETHANOL), 250)),
                List.of(), List.of(fluid(fluidOf(BioFluids.BIODIESEL), 500), fluid(fluidOf(BioFluids.GLYCEROL), 100)),
                null, 0.0D));
        // ---- 裂变线湿法前端 / 氟循环（复用反应器）
        // 萃取：黄饼 + 硝酸 + 煤油（TBP 载体）→ 载铀有机相 + 萃余液
        recipes.add(recipe("extract_uranium", BioRecipeType.BIOREACTION,
                List.of(item(FissionMaterials.YELLOWCAKE.get(), 1)),
                List.of(fluid(FissionFluids.NITRIC_ACID.source().get(), 250),
                        fluid(PetroFluids.KEROSENE.source().get(), 1000)),
                List.of(), List.of(fluid(FissionFluids.LOADED_EXTRACTANT.source().get(), 1000),
                        fluid(FissionFluids.RAFFINATE.source().get(), 250)),
                FermentationKinetics.Band.MESOPHILIC, 0.0D));
        // 电解：2 HF → F2 + H2（高温带 + 热流率门槛）
        recipes.add(recipe("electrolyze_hf", BioRecipeType.BIOREACTION,
                List.of(), List.of(fluid(FissionFluids.HYDROGEN_FLUORIDE.source().get(), 2000)),
                List.of(), List.of(fluid(FissionFluids.FLUORINE.source().get(), 1000),
                        fluid(PetroFluids.HYDROGEN.source().get(), 1000)),
                FermentationKinetics.Band.THERMOPHILIC, 64.0D));
        // 电解水：水 1000 → 氢气 1000 + 氧气 500（焊接线氧气来源；高温带 + 热流率门槛）
        recipes.add(recipe("electrolyse_water", BioRecipeType.BIOREACTION,
                List.of(), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(PetroFluids.HYDROGEN.source().get(), 1000),
                        fluid(WeldingFluids.OXYGEN.source().get(), 500)),
                FermentationKinetics.Band.THERMOPHILIC, 64.0D));
        // ---- 湿法前端酸液（原工作台桶配方移入反应器：水 + 原版原料）
        // 硝酸：氨水 + 氧气（奥斯特瓦尔德法简化，2026-11-08 替换火药 + 水占位）
        recipes.add(recipe("nitric_acid_from_ammonia", BioRecipeType.BIOREACTION,
                List.of(),
                List.of(fluid(FissionFluids.AMMONIA_SOLUTION.source().get(), 1000),
                        fluid(WeldingFluids.OXYGEN.source().get(), 1000)),
                List.of(),
                List.of(fluid(FissionFluids.NITRIC_ACID.source().get(), 1000), fluid(Fluids.WATER, 500)),
                FermentationKinetics.Band.THERMOPHILIC, 64.0D));
        recipes.add(recipe("ammonia_solution_from_water", BioRecipeType.BIOREACTION,
                List.of(item(Items.BONE_MEAL, 1)), List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(FissionFluids.AMMONIA_SOLUTION.source().get(), 1000)),
                null, 0.0D));
        recipes.add(recipe("hydrogen_fluoride_from_water", BioRecipeType.BIOREACTION,
                List.of(item(Items.AMETHYST_SHARD, 1), item(Items.CALCITE, 1)),
                List.of(fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(FissionFluids.HYDROGEN_FLUORIDE.source().get(), 1000)),
                null, 0.0D));
        // 硫酸：过氧化氢 1000 + 水 1000 + 硫粉 ×1 → 硫酸 1000
        recipes.add(recipe("sulfuric_acid_from_peroxide", BioRecipeType.BIOREACTION,
                List.of(item(PetroMaterials.SULFUR_DUST.get(), 1)),
                List.of(fluid(fluidOf(OreFluids.HYDROGEN_PEROXIDE), 1000), fluid(Fluids.WATER, 1000)),
                List.of(), List.of(fluid(fluidOf(OreFluids.SULFURIC_ACID), 1000)),
                null, 0.0D));
        // ---- 盐湖 / 矿物盐支线（B1）
        // 硼酸流体 + 石油焦 → 硼粉 + 二氧化碳（2026-11-08 硼酸改流体）
        recipes.add(recipe("reduce_boric_acid", BioRecipeType.BIOREACTION,
                List.of(item(PetroMaterials.PETROLEUM_COKE.get(), 3)),
                List.of(fluid(OreFluids.BORIC_ACID.source().get(), 1000)),
                List.of(item(SaltMaterials.BORON_DUST.get(), 1)),
                List.of(fluid(CoalFluids.CARBON_DIOXIDE.source().get(), 500)),
                FermentationKinetics.Band.THERMOPHILIC, 512.0D));
        // 氯化钠电解 → 钠粉 + 氯气
        recipes.add(recipe("electrolyse_salt", BioRecipeType.BIOREACTION,
                List.of(item(SaltMaterials.SODIUM_CHLORIDE.get(), 2)),
                List.of(),
                List.of(item(SaltMaterials.SODIUM_DUST.get(), 1)),
                List.of(fluid(OreFluids.CHLORINE.source().get(), 500)),
                FermentationKinetics.Band.THERMOPHILIC, 1024.0D));
        // 钠粉 + 水 → 氢氧化钠（B3 拜耳法）
        recipes.add(recipe("sodium_hydroxide", BioRecipeType.BIOREACTION,
                List.of(item(SaltMaterials.SODIUM_DUST.get(), 1)),
                List.of(fluid(Fluids.WATER, 1000)),
                List.of(item(SaltMaterials.SODIUM_HYDROXIDE.get(), 2)),
                List.of(), null, 0.0D));
        // ---- 盐酸（2026-11-08）：合成法（前期）/ 实验室法（石化后量产）+ 中和泄压
        // 氯气 + 氢气 → 盐酸 1000（前期主力，氯和氢都来自电解，不卡石化）
        recipes.add(recipe("hydrochloric_acid_from_chlorine", BioRecipeType.BIOREACTION,
                List.of(),
                List.of(fluid(OreFluids.CHLORINE.source().get(), 500),
                        fluid(PetroFluids.HYDROGEN.source().get(), 500)),
                List.of(),
                List.of(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 1000)),
                FermentationKinetics.Band.THERMOPHILIC, 64.0D));
        // 氯化钠 + 硫酸 → 盐酸 1000（石化后量产，硫酸消耗点）
        recipes.add(recipe("hydrochloric_acid_from_salt", BioRecipeType.BIOREACTION,
                List.of(item(SaltMaterials.SODIUM_CHLORIDE.get(), 2)),
                List.of(fluid(OreFluids.SULFURIC_ACID.source().get(), 500)),
                List.of(),
                List.of(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 1000)),
                null, 0.0D));
        // 中和：盐酸 + 氢氧化钠 → 氯化钠 + 水（氯循环泄压阀，防止氯气堵储罐）
        recipes.add(recipe("neutralize_hydrochloric_acid", BioRecipeType.BIOREACTION,
                List.of(item(SaltMaterials.SODIUM_HYDROXIDE.get(), 2)),
                List.of(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 1000)),
                List.of(item(SaltMaterials.SODIUM_CHLORIDE.get(), 2)),
                List.of(fluid(Fluids.WATER, 1000)),
                null, 0.0D));
        // 钛还原（B2）：四氯化钛 + 钠粉 → 钛粉 + 氯化钠（钠热法，闭环回 B1）
        recipes.add(recipe("reduce_titanium", BioRecipeType.BIOREACTION,
                List.of(item(SaltMaterials.SODIUM_DUST.get(), 8)),
                List.of(fluid(OreFluids.TITANIUM_TETRACHLORIDE.source().get(), 1000)),
                List.of(item(ElementDusts.TITANIUM_DUST.get(), 2),
                        item(SaltMaterials.SODIUM_CHLORIDE.get(), 8)),
                List.of(),
                FermentationKinetics.Band.THERMOPHILIC, 1024.0D));
        // 冰晶石（B3）：氟化氢 + 氢氧化钠 + 氧化铝
        recipes.add(recipe("make_cryolite", BioRecipeType.BIOREACTION,
                List.of(item(SaltMaterials.SODIUM_HYDROXIDE.get(), 3),
                        item(BauxiteMaterials.ALUMINA.get(), 1)),
                List.of(fluid(FissionFluids.HYDROGEN_FLUORIDE.source().get(), 1000)),
                List.of(item(BauxiteMaterials.CRYOLITE.get(), 2)),
                List.of(),
                FermentationKinetics.Band.THERMOPHILIC, 512.0D));
        // 电解铝（B3）：氧化铝 + 冰晶石 + 石油焦 → 铝粉 + 二氧化碳
        recipes.add(recipe("electrolyse_alumina", BioRecipeType.BIOREACTION,
                List.of(item(BauxiteMaterials.ALUMINA.get(), 2),
                        item(BauxiteMaterials.CRYOLITE.get(), 1),
                        item(PetroMaterials.PETROLEUM_COKE.get(), 2)),
                List.of(),
                List.of(item(ElementDusts.ALUMINUM_DUST.get(), 4)),
                List.of(fluid(CoalFluids.CARBON_DIOXIDE.source().get(), 1000)),
                FermentationKinetics.Band.THERMOPHILIC, 2048.0D));

        // ================================================================
        // 木化工（2026-11-11 批次 1）：纤维素下游 / 松香助焊剂 / 糠醛-糠醇
        // 纤维素统一用既有物品「木屑粉」（lignocellulose，粉碎原木获得），不再单独注册。
        // ================================================================
        // 预留：醋酸纤维 / 硝化纤维当前只产不用，等后续内容线接下游
        recipes.add(recipe("cellulose_acetate", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.LIGNOCELLULOSE.get(), 2)),
                List.of(fluid(BioFluids.ACETIC_ACID.source().get(), 500)),
                List.of(item(WoodMaterials.CELLULOSE_ACETATE.get(), 1)), List.of(),
                null, 0.0D));
        recipes.add(recipe("nitrocellulose", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.LIGNOCELLULOSE.get(), 2)),
                List.of(fluid(FissionFluids.NITRIC_ACID.source().get(), 500)),
                List.of(item(WoodMaterials.NITROCELLULOSE.get(), 2)), List.of(),
                null, 0.0D));
        recipes.add(recipe("rosin_flux", BioRecipeType.BIOREACTION,
                List.of(item(WoodMaterials.ROSIN.get(), 1)),
                List.of(fluid(BioFluids.ETHANOL.source().get(), 500)),
                List.of(), List.of(fluid(WeldingFluids.FLUX.source().get(), 250)),
                null, 0.0D));
        recipes.add(recipe("furfural", BioRecipeType.BIOREACTION,
                List.of(item(BioMaterials.LIGNOCELLULOSE.get(), 2)),
                List.of(fluid(OreFluids.SULFURIC_ACID.source().get(), 100)),
                List.of(), List.of(fluid(WoodFluids.FURFURAL.source().get(), 500)),
                null, 0.0D));
        recipes.add(recipe("furfuryl_alcohol", BioRecipeType.BIOREACTION,
                List.of(),
                List.of(fluid(WoodFluids.FURFURAL.source().get(), 1000),
                        fluid(PetroFluids.HYDROGEN.source().get(), 200)),
                List.of(), List.of(fluid(WoodFluids.FURFURYL_ALCOHOL.source().get(), 800)),
                null, 0.0D));

        // ================================================================
        // 选冶线 / 矿石线：酸浸 + 氟化（2026-11-06 从裂变级酸浸槽 / 氟化炉迁入）
        // 目标：选冶线在生化级就能跑完，不再需要裂变机器。
        // 全部走反应器；每条配方的物品输入互不重叠，不会互相抢配方。
        // ================================================================
        // ---- 一段酸浸：精砂 + 酸 → 粉 ×2 + 浸出渣
        recipes.add(leach("leach_uranium_sand",
                items(item(OreMaterials.URANIUM_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(FissionFluids.NITRIC_ACID.source().get(), 125)),
                items(item(OreMaterials.URANIUM_DUST.get(), 2), residue(LeachResidueType.URANIUM, 1),
                        chancedResidue(LeachResidueType.URANIUM, 1, 0.35))));
        recipes.add(leach("leach_iron_sand",
                items(item(OreMaterials.IRON_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 125)),
                items(item(OreMaterials.IRON_DUST.get(), 2), residue(LeachResidueType.IRON, 1),
                        chancedResidue(LeachResidueType.IRON, 1, 0.35))));
        recipes.add(leach("leach_tungsten_sand",
                items(item(TungstenMaterials.TUNGSTEN_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 125)),
                items(item(TungstenMaterials.TUNGSTEN_DUST.get(), 2), residue(LeachResidueType.TUNGSTEN, 1),
                        chancedResidue(LeachResidueType.TUNGSTEN, 1, 0.35))));
        recipes.add(leach("leach_lead_sand",
                items(item(LeadMaterials.LEAD_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(LeadMaterials.LEAD_DUST.get(), 2), residue(LeachResidueType.LEAD, 1),
                        chancedResidue(LeachResidueType.LEAD, 1, 0.35))));
        recipes.add(leach("leach_copper_sand",
                items(item(OreMaterials.COPPER_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(MaterialItems.COPPER_DUST.get(), 2), residue(LeachResidueType.COPPER, 1),
                        chancedResidue(LeachResidueType.COPPER, 1, 0.35))));
        recipes.add(leach("leach_gold_sand",
                items(item(OreMaterials.GOLD_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(FissionFluids.NITRIC_ACID.source().get(), 125)),
                items(item(OreMaterials.GOLD_DUST.get(), 2), residue(LeachResidueType.GOLD, 1),
                        chancedResidue(LeachResidueType.GOLD, 1, 0.35))));
        recipes.add(leach("leach_debris_sand",
                items(item(OreMaterials.DEBRIS_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(OreMaterials.DEBRIS_DUST.get(), 2), residue(LeachResidueType.DEBRIS, 1),
                        chancedResidue(LeachResidueType.DEBRIS, 1, 0.35))));
        recipes.add(leach("leach_coal_sand",
                items(item(OreMaterials.COAL_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(Items.COAL, 4), residue(LeachResidueType.COAL, 1),
                        chancedResidue(LeachResidueType.COAL, 1, 0.35))));
        recipes.add(leach("leach_diamond_sand",
                items(item(OreMaterials.DIAMOND_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(OreMaterials.DIAMOND_DUST.get(), 4), residue(LeachResidueType.DIAMOND, 1),
                        chancedResidue(LeachResidueType.DIAMOND, 1, 0.35))));
        recipes.add(leach("leach_emerald_sand",
                items(item(OreMaterials.EMERALD_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(OreMaterials.EMERALD_DUST.get(), 4), residue(LeachResidueType.EMERALD, 1),
                        chancedResidue(LeachResidueType.EMERALD, 1, 0.35))));
        recipes.add(leach("leach_lapis_sand",
                items(item(OreMaterials.LAPIS_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(OreMaterials.LAPIS_DUST.get(), 4), residue(LeachResidueType.LAPIS, 1),
                        chancedResidue(LeachResidueType.LAPIS, 1, 0.35))));
        recipes.add(leach("leach_redstone_sand",
                items(item(OreMaterials.REDSTONE_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(Items.REDSTONE, 4), residue(LeachResidueType.REDSTONE, 1),
                        chancedResidue(LeachResidueType.REDSTONE, 1, 0.35))));
        recipes.add(leach("leach_quartz_sand",
                items(item(OreMaterials.QUARTZ_CONCENTRATE_SAND.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(OreMaterials.QUARTZ_DUST.get(), 4), residue(LeachResidueType.QUARTZ, 1),
                        chancedResidue(LeachResidueType.QUARTZ, 1, 0.35))));

        // ---- 二段酸浸：浸出渣 ×2 + 酸 → 副产粉 + 沙砾
        recipes.add(leach("second_iron",
                items(residue(LeachResidueType.IRON, 1)),
                fluids(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 125)),
                items(item(ElementDusts.CHROMIUM_DUST.get(), 2),
                        chanced(ElementDusts.CHROMIUM_DUST.get(), 1, 0.35),
                        item(TraceElementDusts.VANADIUM_DUST.get(), 2),
                        chanced(TraceElementDusts.VANADIUM_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_copper",
                items(residue(LeachResidueType.COPPER, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(ElementDusts.NICKEL_DUST.get(), 2),
                        chanced(ElementDusts.NICKEL_DUST.get(), 1, 0.35),
                        item(FissionMaterials.COBALT_DUST.get(), 2),
                        chanced(FissionMaterials.COBALT_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_tungsten",
                items(residue(LeachResidueType.TUNGSTEN, 1)),
                fluids(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 125)),
                items(item(ElementDusts.MOLYBDENUM_DUST.get(), 2),
                        chanced(ElementDusts.MOLYBDENUM_DUST.get(), 1, 0.35),
                        item(MonaziteMaterials.TIN_DUST.get(), 2),
                        chanced(MonaziteMaterials.TIN_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_coal",
                items(residue(LeachResidueType.COAL, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(PetroMaterials.SULFUR_DUST.get(), 2),
                        chanced(PetroMaterials.SULFUR_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_diamond",
                items(residue(LeachResidueType.DIAMOND, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(ElementDusts.GRAPHITE_DUST.get(), 2),
                        chanced(ElementDusts.GRAPHITE_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_emerald",
                items(residue(LeachResidueType.EMERALD, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(ElementDusts.BERYLLIUM_DUST.get(), 2),
                        chanced(ElementDusts.BERYLLIUM_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(new BioRecipe("second_redstone", BioRecipeType.BIOREACTION,
                items(residue(LeachResidueType.REDSTONE, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(ElementDusts.CHROMIUM_DUST.get(), 2),
                        chanced(ElementDusts.CHROMIUM_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1)),
                List.of(fluid(OreFluids.MERCURY.source().get(), 250)),
                600, null, 0.0D));
        recipes.add(leach("second_gold",
                items(residue(LeachResidueType.GOLD, 1)),
                fluids(fluid(FissionFluids.NITRIC_ACID.source().get(), 125)),
                items(item(OreMaterials.GOLD_DUST.get(), 2),
                        chanced(OreMaterials.GOLD_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_lead",
                items(residue(LeachResidueType.LEAD, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(LeadMaterials.LEAD_DUST.get(), 4),
                        chanced(LeadMaterials.LEAD_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_uranium",
                items(residue(LeachResidueType.URANIUM, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(TraceElementDusts.VANADIUM_DUST.get(), 2),
                        chanced(TraceElementDusts.VANADIUM_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_quartz",
                items(residue(LeachResidueType.QUARTZ, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(TraceElementDusts.SILICON_DUST.get(), 2),
                        chanced(TraceElementDusts.SILICON_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));
        recipes.add(leach("second_lapis",
                items(residue(LeachResidueType.LAPIS, 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 125)),
                items(item(PetroMaterials.SULFUR_DUST.get(), 2),
                        chanced(PetroMaterials.SULFUR_DUST.get(), 1, 0.35),
                        item(SaltMaterials.SODIUM_DUST.get(), 2),
                        chanced(SaltMaterials.SODIUM_DUST.get(), 1, 0.35),
                        item(Items.GRAVEL, 1))));

        // ---- 盐湖支线：硼砂 + 硫酸 → 硼酸流体（2026-11-08 由物品改为流体）
        recipes.add(new BioRecipe("boric_acid_from_borax", BioRecipeType.BIOREACTION,
                items(item(SaltMaterials.BORAX.get(), 2)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 500)),
                items(item(Items.GRAVEL, 1)),
                List.of(fluid(OreFluids.BORIC_ACID.source().get(), 1000)),
                600, null, 0.0D));

        // ---- 独居石（B2）：浸出 / 氯化 / 锆铪分离（2026-11-08 酸浸改盐酸）
        recipes.add(leach("leach_monazite",
                items(item(MonaziteMaterials.MONAZITE_SAND.get(), 1)),
                fluids(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 500)),
                items(item(MonaziteMaterials.ZIRCON_SAND.get(), 1),
                        item(MonaziteMaterials.MONAZITE_CONCENTRATE.get(), 1),
                        item(MonaziteMaterials.PHOSPHATE.get(), 1),
                        residue(LeachResidueType.MONAZITE, 1))));
        recipes.add(fluorinate("chlorinate_zircon",
                items(item(MonaziteMaterials.ZIRCON_SAND.get(), 1), item(CoalMaterials.COAL_DUST.get(), 1)),
                fluids(fluid(OreFluids.CHLORINE.source().get(), 500)),
                items(item(MonaziteMaterials.CRUDE_ZIRCONIUM_POWDER.get(), 2)), List.of()));
        recipes.add(leach("separate_zirconium_hafnium",
                items(item(MonaziteMaterials.CRUDE_ZIRCONIUM_POWDER.get(), 20)),
                fluids(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 500)),
                items(item(ElementDusts.ZIRCONIUM_DUST.get(), 10),
                        item(MonaziteMaterials.HAFNIUM_POWDER.get(), 1), item(Items.GRAVEL, 1))));
        recipes.add(leach("leach_monazite_concentrate",
                items(item(MonaziteMaterials.MONAZITE_CONCENTRATE.get(), 2)),
                fluids(fluid(OreFluids.HYDROCHLORIC_ACID.source().get(), 500)),
                items(item(MonaziteMaterials.RARE_EARTH_CHLORIDE.get(), 2),
                        item(MonaziteMaterials.THORIUM_CONCENTRATE.get(), 1), item(Items.GRAVEL, 1))));
        recipes.add(leach("thorium_from_concentrate",
                items(item(MonaziteMaterials.THORIUM_CONCENTRATE.get(), 1)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 500)),
                items(item(FissionMaterials.THORIUM_POWDER.get(), 3), item(Items.GRAVEL, 1))));
        // 稀土合金锭（2026-11-08 自热室迁入反应器）：独居石 + 石脑油 + 汞汞齐化；重砂路线料更多
        recipes.add(recipe("rare_earth_alloy_from_sand", BioRecipeType.BIOREACTION,
                List.of(item(MonaziteMaterials.MONAZITE_SAND.get(), 2)),
                List.of(fluid(PetroFluids.NAPHTHA.source().get(), 250),
                        fluid(OreFluids.MERCURY.source().get(), 125)),
                List.of(item(SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get(), 1)), List.of(),
                null, 0.0D));
        recipes.add(recipe("rare_earth_alloy_from_concentrate", BioRecipeType.BIOREACTION,
                List.of(item(MonaziteMaterials.MONAZITE_CONCENTRATE.get(), 1)),
                List.of(fluid(PetroFluids.NAPHTHA.source().get(), 250),
                        fluid(OreFluids.MERCURY.source().get(), 125)),
                List.of(item(SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get(), 1)), List.of(),
                null, 0.0D));
        // ---- 钛线（2026-11-12 从独居石分离）：钛铁矿砂 → 钛铁粉 → 金红石粉 → 四氯化钛
        recipes.add(recipe("reduce_ilmenite", BioRecipeType.BIOREACTION,
                List.of(item(TitaniumMaterials.ILMENITE_SAND.get(), 2),
                        item(CoalMaterials.COAL_DUST.get(), 2)),
                List.of(),
                List.of(item(TitaniumMaterials.FERROTITANIUM_POWDER.get(), 1)),
                List.of(fluid(CoalFluids.CARBON_DIOXIDE.source().get(), 1_000)),
                FermentationKinetics.Band.THERMOPHILIC, 512.0D));
        recipes.add(leach("leach_ferrotitanium",
                items(item(TitaniumMaterials.FERROTITANIUM_POWDER.get(), 2)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 500)),
                items(item(TitaniumMaterials.RUTILE_POWDER.get(), 2),
                        item(OreMaterials.IRON_DUST.get(), 1), item(Items.GRAVEL, 1))));
        recipes.add(fluorinate("chlorinate_rutile",
                items(item(TitaniumMaterials.RUTILE_POWDER.get(), 1), item(CoalMaterials.COAL_DUST.get(), 1)),
                fluids(fluid(OreFluids.CHLORINE.source().get(), 1_000)),
                List.of(), fluids(fluid(OreFluids.TITANIUM_TETRACHLORIDE.source().get(), 1_000))));

        // ---- 铝土矿（B3）：拜耳法溶出 / 红泥回收
        recipes.add(leach("bayer_digest",
                items(item(BauxiteMaterials.BAUXITE_POWDER.get(), 2),
                        item(SaltMaterials.SODIUM_HYDROXIDE.get(), 2)),
                List.of(),
                items(item(BauxiteMaterials.ALUMINA.get(), 2), item(BauxiteMaterials.RED_MUD.get(), 1))));
        recipes.add(leach("red_mud_recovery",
                items(item(BauxiteMaterials.RED_MUD.get(), 2)),
                fluids(fluid(OreFluids.SULFURIC_ACID.source().get(), 500)),
                items(item(OreMaterials.IRON_DUST.get(), 1), item(TitaniumMaterials.ILMENITE_SAND.get(), 1),
                        item(TraceElementDusts.GALLIUM_DUST.get(), 1), item(Items.GRAVEL, 1))));

        // ================================================================
        // 基岩无限资源线（2026-11-01）
        // ================================================================
        // ---- 4 种同位素溶剂配制
        recipes.add(bedrockRecipe("bedrock_mix_gamma", BioRecipeType.BIOREACTION,
                BedrockBalance.SOLVENT_TICKS,
                List.of(item(FissionMaterials.CO_60.get(), 1),
                        item(FissionMaterials.CESIUM_137.get(), 1)),
                List.of(fluid(PetroFluids.LOW_SULFUR_OIL.source().get(), 1_000)),
                List.of(),
                List.of(fluid(BedrockFluids.GAMMA_SOLVENT.source().get(), 1_000)),
                BedrockBalance.SOLVENT_MIN_FLOW));
        recipes.add(bedrockRecipe("bedrock_mix_halide", BioRecipeType.BIOREACTION,
                BedrockBalance.SOLVENT_TICKS,
                List.of(item(FissionMaterials.IODINE_129.get(), 1),
                        item(FissionMaterials.TECHNETIUM_99.get(), 1)),
                List.of(fluid(CoalFluids.NAPHTHALENE.source().get(), 200),
                        fluid(OreFluids.CHLORINE.source().get(), 500)),
                List.of(),
                List.of(fluid(BedrockFluids.HALIDE_SOLVENT.source().get(), 1_000)),
                BedrockBalance.SOLVENT_MIN_FLOW));
        recipes.add(bedrockRecipe("bedrock_mix_selfheat", BioRecipeType.BIOREACTION,
                BedrockBalance.SOLVENT_TICKS,
                List.of(item(FissionMaterials.STRONTIUM_90.get(), 1),
                        item(FissionMaterials.PLUTONIUM_238.get(), 1),
                        item(SaltMaterials.SODIUM_HYDROXIDE.get(), 2)),
                List.of(fluid(BioFluids.GLYCEROL.source().get(), 200)),
                List.of(),
                List.of(fluid(BedrockFluids.SELFHEAT_SOLVENT.source().get(), 1_000)),
                BedrockBalance.SOLVENT_MIN_FLOW));
        recipes.add(bedrockRecipe("bedrock_mix_chelate", BioRecipeType.BIOREACTION,
                BedrockBalance.SOLVENT_TICKS,
                List.of(item(FissionMaterials.AMERICIUM_241.get(), 1),
                        item(FissionMaterials.CURIUM_244.get(), 1),
                        item(FissionMaterials.NEPTUNIUM_237.get(), 1),
                        item(FissionMaterials.U233.get(), 1),
                        item(FissionMaterials.SAMARIUM_149.get(), 1)),
                List.of(fluid(BioFluids.BUTANOL.source().get(), 500),
                        fluid(PetroFluids.LOW_SULFUR_OIL.source().get(), 500)),
                List.of(),
                List.of(fluid(BedrockFluids.CHELATE_SOLVENT.source().get(), 1_000)),
                BedrockBalance.SOLVENT_MIN_FLOW));

        // ---- S3 α 螯合 + 反萃（锕系 / 稀土）
        recipes.add(bedrockRecipe("bedrock_s3_chelate", BioRecipeType.BIOREACTION,
                BedrockBalance.STAGE_TICKS,
                List.of(bedrockResidue(BedrockStage.S2, 1)),
                List.of(fluid(BedrockFluids.CHELATE_SOLVENT.source().get(), 200),
                        fluid(BioFluids.ACETIC_ACID.source().get(), 200)),
                List.of(bedrockResidue(BedrockStage.S3, 1)),
                List.of(fluid(BedrockFluids.LOADED_ORGANIC.source().get(), 2_000)),
                BedrockBalance.S3_MIN_FLOW));
        recipes.add(bedrockRecipe("bedrock_s3_actinides", BioRecipeType.BIOREACTION,
                BedrockBalance.SEPARATION_TICKS,
                List.of(),
                List.of(fluid(BedrockFluids.LOADED_ORGANIC.source().get(), 1_000),
                        fluid(BioFluids.ACETIC_ACID.source().get(), 200),
                        fluid(Fluids.WATER, 500)),
                List.of(item(OreMaterials.URANIUM_DUST.get(), 6),
                        item(FissionMaterials.THORIUM_POWDER.get(), 4),
                        item(FissionMaterials.PLUTONIUM.get(), 1),
                        item(FissionMaterials.MINOR_ACTINIDES.get(), 1),
                        item(FissionMaterials.NUCLEAR_WASTE.get(), 1)),
                List.of(), BedrockBalance.SEPARATION_MIN_FLOW));
        recipes.add(bedrockRecipe("bedrock_s3_rare_earth", BioRecipeType.BIOREACTION,
                BedrockBalance.SEPARATION_TICKS,
                List.of(),
                List.of(fluid(BedrockFluids.LOADED_ORGANIC.source().get(), 1_000),
                        fluid(Fluids.WATER, 1_000)),
                List.of(item(MonaziteMaterials.NEODYMIUM_DUST.get(), 4),
                        item(MonaziteMaterials.SAMARIUM_DUST.get(), 2),
                        item(MonaziteMaterials.GADOLINIUM_DUST.get(), 2),
                        item(MonaziteMaterials.CERIUM_DUST.get(), 4),
                        item(MonaziteMaterials.PHOSPHATE.get(), 8)),
                List.of(), BedrockBalance.SEPARATION_MIN_FLOW));

        // ---- S1 / S2 / S5 轻组分：结晶机
        recipes.add(bedrockRecipe("bedrock_s1_light", BioRecipeType.CRYSTALLIZING,
                BedrockBalance.SEPARATION_TICKS,
                List.of(),
                List.of(fluid(BedrockFluids.VOLATILE_HALIDE_MIX.source().get(), 1_000),
                        fluid(Fluids.WATER, 500)),
                List.of(item(ElementDusts.TITANIUM_DUST.get(), 3),
                        item(TraceElementDusts.SILICON_DUST.get(), 8),
                        item(MonaziteMaterials.TIN_DUST.get(), 2),
                        item(Items.GRAVEL, 1)),
                List.of(), BedrockBalance.SEPARATION_MIN_FLOW));
        recipes.add(bedrockRecipe("bedrock_s2_light", BioRecipeType.CRYSTALLIZING,
                BedrockBalance.SEPARATION_TICKS,
                List.of(),
                List.of(fluid(BedrockFluids.MOLTEN_SALT_LEACHATE.source().get(), 1_000),
                        fluid(Fluids.WATER, 500)),
                List.of(item(ElementDusts.ALUMINUM_DUST.get(), 3),
                        item(ElementDusts.CHROMIUM_DUST.get(), 3),
                        item(OreMaterials.IRON_DUST.get(), 10),
                        item(Items.GRAVEL, 1)),
                List.of(), BedrockBalance.SEPARATION_MIN_FLOW));
        recipes.add(bedrockRecipe("bedrock_s5_light", BioRecipeType.CRYSTALLIZING,
                BedrockBalance.SEPARATION_TICKS,
                List.of(),
                List.of(fluid(BedrockFluids.BIOLEACHATE.source().get(), 1_000),
                        fluid(Fluids.WATER, 500)),
                List.of(item(OreMaterials.GOLD_DUST.get(), 1),
                        item(ElementDusts.BERYLLIUM_DUST.get(), 2),
                        item(SaltMaterials.BORON_DUST.get(), 6),
                        item(FissionMaterials.LITHIUM_DUST.get(), 6),
                        item(Items.GRAVEL, 1)),
                List.of(), BedrockBalance.SEPARATION_MIN_FLOW));

        // ---- S4 浮选（亲铜组）
        recipes.add(bedrockRecipe("bedrock_s4_base", BioRecipeType.DECOMPOSING,
                BedrockBalance.SEPARATION_TICKS,
                List.of(item(BedrockMaterials.MIXED_SULFIDE.get(), 2)),
                List.of(fluid(Fluids.WATER, 1_000),
                        fluid(CoalFluids.NAPHTHALENE.source().get(), 100)),
                List.of(item(MaterialItems.COPPER_DUST.get(), 6),
                        item(LeadMaterials.LEAD_DUST.get(), 4),
                        item(Items.GRAVEL, 1)),
                List.of(fluid(OreFluids.MERCURY.source().get(), 250)), BedrockBalance.SEPARATION_MIN_FLOW));

        // ---- S5 生物浸出主链
        recipes.add(bedrockRecipe("bedrock_s5_bio", BioRecipeType.BIOREACTION,
                BedrockBalance.STAGE_TICKS,
                List.of(bedrockResidue(BedrockStage.S4, 1)),
                List.of(fluid(BioFluids.ANTIBIOTICS.source().get(), 100),
                        fluid(BioFluids.NUTRIENT_MEDIUM.source().get(), 500),
                        fluid(BioFluids.GLYCEROL.source().get(), 200),
                        fluid(PetroFluids.LUBRICANT.source().get(), 100)),
                List.of(item(Items.GRAVEL, 4)),
                List.of(fluid(BedrockFluids.BIOLEACHATE.source().get(), 2_000)),
                BedrockBalance.STAGE_MIN_FLOW));

        return checkDuplicates(List.copyOf(recipes));
    }

    /**
     * 开发守卫：同一类型里出现<strong>两条输入签名相同</strong>的配方时记一条 error ——
     * `craftOnce` 按表内顺序取第一条能成的，所以后面那条<strong>永远匹配不到</strong>（等于白写）。
     * 签名 = 类型 + 排序后的「物品输入 + 流体输入」+ 温度带 + 热流率门槛 + 催化剂（不看产出）。
     * <p><strong>输入必须带数据组件</strong>：浸出渣 / 基岩渣这类同一物品、靠组件区分来源的原料，
     * 只比注册名会把「不同渣的 second_* 配方」误判成重复（2026-11-09 修正）。
     * <p>为什么不做成 JUnit：配方表要读注册表（{@code Item}/{@code Fluid}），而单测环境没有 NeoForge 引导，
     * 所以放在类加载（首次用到配方表）时自检。
     */
    private static List<BioRecipe> checkDuplicates(List<BioRecipe> recipes) {
        Set<String> seen = new HashSet<>();
        for (BioRecipe recipe : recipes) {
            List<String> inputs = new ArrayList<>();
            for (ItemIngredient item : recipe.itemInputs()) {
                inputs.add(BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString()
                        + item.resource().getComponentsPatch() + "x" + item.count());
            }
            for (FluidIngredient fluid : recipe.fluidInputs()) {
                inputs.add(BuiltInRegistries.FLUID.getKey(fluid.stack().getFluid()).toString()
                        + fluid.resource().getComponentsPatch() + "x" + fluid.amount());
            }
            inputs.sort(Comparator.naturalOrder());
            String catalyst = recipe.catalyst() == null
                    ? "-" : BuiltInRegistries.ITEM.getKey(recipe.catalyst()).toString();
            String signature = recipe.type().name() + '|' + String.join("+", inputs) + '|'
                    + recipe.requiredBand() + '|' + recipe.minFlowRate() + '|' + catalyst;
            if (!seen.add(signature)) {
                StellarEvolution.LOGGER.error("生化配方输入签名重复：{} 的 {} 与前面某条相同（这条永远匹配不到）",
                        recipe.type(), recipe.id());
            }
            if (recipe.itemInputs().isEmpty() && recipe.fluidInputs().isEmpty()) {
                StellarEvolution.LOGGER.error("生化配方没有任何输入：{} 的 {}（会永远匹配、吃掉一切）",
                        recipe.type(), recipe.id());
            }
        }
        return recipes;
    }

    private static Fluid fluidOf(BioFluids.Entry entry) {
        return entry.source().get();
    }

    private static Fluid fluidOf(OreFluids.Entry entry) {
        return entry.source().get();
    }

    /** 选冶线浮选：原料 ×1 + 水 1000 + 糖液 250 → 富精矿浆 3000 + 砂砾 1。 */
    private static BioRecipe flotation(String id, Item ore, OreFluids.Entry slurry) {
        return recipe(id, BioRecipeType.DECOMPOSING,
                List.of(item(ore, 1)),
                List.of(fluid(Fluids.WATER, 1000), fluid(fluidOf(BioFluids.SUGAR_LIQUOR), 250)),
                List.of(item(Items.GRAVEL, 1)),
                List.of(fluid(slurry.source().get(), 3000)),
                null, 0.0D);
    }

    /** 选冶线结晶：1500 富精矿浆 + 750 过氧化氢 → 1 精砂。 */
    private static BioRecipe crystallizeSand(String id, OreFluids.Entry slurry, Item sand) {
        return recipe(id, BioRecipeType.CRYSTALLIZING,
                List.of(), List.of(fluid(slurry.source().get(), 1500),
                        fluid(fluidOf(OreFluids.HYDROGEN_PEROXIDE), 750)),
                List.of(item(sand, 1)), List.of(),
                null, 0.0D);
    }

    /** 选冶线 / 矿石线酸浸（原裂变酸浸槽）：600t，无温度带 / 热流率要求。 */
    private static BioRecipe leach(String id, List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                                   List<ItemIngredient> itemOutputs) {
        return new BioRecipe(id, BioRecipeType.BIOREACTION, itemInputs, fluidInputs, itemOutputs, List.of(),
                600, null, 0.0D);
    }

    /** 选冶线 / 矿石线氟化（原裂变氟化炉）：1000t，无温度带 / 热流率要求。 */
    private static BioRecipe fluorinate(String id, List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                                        List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs) {
        return new BioRecipe(id, BioRecipeType.BIOREACTION, itemInputs, fluidInputs, itemOutputs, fluidOutputs,
                1_000, null, 0.0D);
    }

    /** 带来源类型的浸出渣（与裂变线共用同一物品 / 组件）。 */
    private static ItemIngredient residue(LeachResidueType type, int count) {
        ItemStack stack = new ItemStack(FissionMaterials.LEACH_RESIDUE.get());
        stack.set(FissionComponents.LEACH_RESIDUE_TYPE.get(), type);
        return new ItemIngredient(ItemResource.of(stack), count);
    }

    /** 概率浸出渣（同物品 / 组件，仅用于输出位）。 */
    private static ItemIngredient chancedResidue(LeachResidueType type, int count, double chance) {
        return new ItemIngredient(residue(type, 1).resource(), count, chance);
    }

    /** 把若干物品原料 / 产物打成列表。 */
    private static List<ItemIngredient> items(ItemIngredient... entries) {
        return List.of(entries);
    }

    /** 把若干流体原料 / 产物打成列表。 */
    private static List<FluidIngredient> fluids(FluidIngredient... entries) {
        return List.of(entries);
    }

    private static BioRecipe recipe(String id, BioRecipeType type,
                                    List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                                    List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs,
                                    @Nullable FermentationKinetics.Band band, double minFlowRate) {
        return recipe(id, type, itemInputs, fluidInputs, itemOutputs, fluidOutputs, band, minFlowRate, null);
    }

    /** 带<strong>催化剂</strong>的配方（催化剂只判定存在、不消耗）。 */
    private static BioRecipe recipe(String id, BioRecipeType type,
                                    List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                                    List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs,
                                    @Nullable FermentationKinetics.Band band, double minFlowRate,
                                    @Nullable Item catalyst) {
        return new BioRecipe(id, type, itemInputs, fluidInputs, itemOutputs, fluidOutputs,
                ticksFor(type), band, minFlowRate, catalyst);
    }

    /** 基岩线专用：自定义基准周期（不走 {@link #ticksFor}）。 */
    private static BioRecipe bedrockRecipe(String id, BioRecipeType type, int ticks,
                                           List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                                           List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs,
                                           double minFlowRate) {
        return new BioRecipe(id, type, itemInputs, fluidInputs, itemOutputs, fluidOutputs,
                ticks, null, minFlowRate, null);
    }

    /** 带段位的基岩浸出渣。 */
    private static ItemIngredient bedrockResidue(BedrockStage stage, int count) {
        ItemStack stack = new ItemStack(BedrockMaterials.BEDROCK_RESIDUE.get());
        stack.set(BedrockComponents.BEDROCK_STAGE.get(), stage);
        return new ItemIngredient(ItemResource.of(stack), count);
    }

    /**
     * 各工序的基准周期（刻）：越靠后的工艺越慢。
     * <p>调浆 200 → 发酵 240 → 分解 280 → 灭菌 320 → 结晶 360 → 反应 420。
     */
    private static int ticksFor(BioRecipeType type) {
        return switch (type) {
            case SLURRYING -> 200;
            case FERMENTING -> 240;
            case DECOMPOSING -> 280;
            case STERILIZING -> 320;
            case CRYSTALLIZING -> 360;
            case BIOREACTION -> 420;
        };
    }

    private static ItemIngredient item(Item item, int count) {
        return new ItemIngredient(ItemResource.of(new ItemStack(item)), count);
    }

    /** 概率产物（仅用于输出位）：chance < 1.0 时真结算按概率掷点。 */
    private static ItemIngredient chanced(Item item, int count, double chance) {
        return new ItemIngredient(ItemResource.of(new ItemStack(item)), count, chance);
    }

    private static FluidIngredient fluid(Fluid fluid, int amount) {
        return new FluidIngredient(FluidResource.of(fluid), amount);
    }

    private BioRecipes() {}
}
