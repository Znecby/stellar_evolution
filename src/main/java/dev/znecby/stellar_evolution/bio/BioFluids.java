package dev.znecby.stellar_evolution.bio;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 生化线的<strong>流体类物质</strong>（《生化线》B0 ①：物质注册与贴图规范，清单见 §4.2 / §4.3）。
 *
 * <p><strong>不落地流体</strong>：只给 {@code FluidType + Source + Flowing}，<strong>不挂方块</strong>。
 * 于是 {@code createLegacyBlock()} 退化为空气 —— 流体只活在<strong>机器 / 储罐 / 铜管 / 桶</strong>里，
 * 不会在世界里摊开（往地面倒不会放流体，但桶也不会被消耗）。
 *
 * <p><strong>每条流体各配一只桶</strong>（{@code <name>_bucket}）：玩家可以拿桶从储罐 / 机器里装出流体、
 * 再倒进别的储罐 / 机器（{@code TankBlock} 走 NeoForge transfer API 的容器能力，桶自动被认）。
 * 桶用 {@code craftRemainder(Items.BUCKET)} —— 倒空后还回空桶。
 *
 * <p>贴图由 {@code tools/gen_bio_materials.ps1} 生成（每条流体 {@code block/<name>_{still,flow}.png} +
 * {@code item/<name>_bucket.png}），流体外观由 {@code StellarEvolutionClient} 在 {@code RegisterFluidModelsEvent} 里逐条登记
 * （26.x 的流体外观走模型而非客户端扩展）。
 * 双语名走 lang 键 {@code fluid_type.stellar_evolution.<name>} / {@code item.stellar_evolution.<name>_bucket}。
 */
public final class BioFluids {
    private static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, StellarEvolution.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 一条流体：名字 + 类型 + 源 + 流动 + 桶（五项同生共死）。 */
    public record Entry(String name,
                        DeferredHolder<FluidType, FluidType> type,
                        DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
                        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing,
                        DeferredItem<BucketItem> bucket) {}

    // ---- 中间体（§4.2）
    /** 生物质浆：调浆机产物。 */
    public static final Entry BIOMASS_SLURRY = entry("biomass_slurry", 1200, 4000);
    /** 水解糖液：灭菌罐高温水解产物。 */
    public static final Entry HYDROLYSATE = entry("hydrolysate", 1100, 2000);
    /** 发酵液：发酵罐产物。 */
    public static final Entry FERMENTATION_BROTH = entry("fermentation_broth", 1050, 1500);
    // ---- 产物（§4.3）
    /** 乙醇：溶剂 / 燃烧引擎燃料 / 石化助剂。 */
    public static final Entry ETHANOL = entry("ethanol", 800, 800);
    /** 醋酸：石化 / 血肉线反应介质。 */
    public static final Entry ACETIC_ACID = entry("acetic_acid", 1050, 1000);
    /** 沼气：燃烧引擎燃料。 */
    public static final Entry BIOGAS = entry("biogas", 100, 100);
    /** 培养基：血肉线核心进料。 */
    public static final Entry NUTRIENT_MEDIUM = entry("nutrient_medium", 1050, 1200);
    /** 无菌培养基：α 束直接灭菌的培养基（抗生素高产路线的进料）。 */
    public static final Entry STERILE_MEDIUM = entry("sterile_medium", 1050, 1100);
    /** 酶溶液：发酵提速 / 石化裂解催化。 */
    public static final Entry ENZYME_SOLUTION = entry("enzyme_solution", 1050, 1200);
    /** 蛋白溶液：高级试剂。 */
    public static final Entry PROTEIN_SOLUTION = entry("protein_solution", 1100, 2500);
    /** 抗生素：污染治理 / 治疗。 */
    public static final Entry ANTIBIOTICS = entry("antibiotics", 1050, 1200);

    // ---- E1 新增（《产线蓝图》§3.3）
    /** 糖液：灭菌罐酶解糖化产物 → 发酵多产物。 */
    public static final Entry SUGAR_LIQUOR = entry("sugar_liquor", 1300, 2000);
    /** 乳酸：产乳酸菌发酵产物 → 聚合可降解塑料。 */
    public static final Entry LACTIC_ACID = entry("lactic_acid", 1200, 2500);
    /** 丙酮：ABE 发酵产物 → 溶剂 / 石化。 */
    public static final Entry ACETONE = entry("acetone", 790, 400);
    /** 丁醇：ABE 发酵产物 → 燃烧引擎燃料 / 溶剂。 */
    public static final Entry BUTANOL = entry("butanol", 810, 900);
    /** 甘油：酯交换副产 → 试剂。 */
    public static final Entry GLYCEROL = entry("glycerol", 1260, 4000);
    /** 氨基酸液：生物反应产物 → 结晶氨基酸粉。 */
    public static final Entry AMINO_ACID_SOLUTION = entry("amino_acid_solution", 1100, 1500);
    /** 生物柴油：酯交换产物 → 燃烧引擎燃料。 */
    public static final Entry BIODIESEL = entry("biodiesel", 880, 1200);

    /** 全部流体（客户端登记外观时按这个顺序走）。 */
    public static final List<Entry> ALL = List.of(
            BIOMASS_SLURRY, HYDROLYSATE, FERMENTATION_BROTH,
            ETHANOL, ACETIC_ACID, BIOGAS,
            NUTRIENT_MEDIUM, STERILE_MEDIUM, ENZYME_SOLUTION, PROTEIN_SOLUTION, ANTIBIOTICS,
            SUGAR_LIQUOR, LACTIC_ACID, ACETONE, BUTANOL,
            GLYCEROL, AMINO_ACID_SOLUTION, BIODIESEL);

    private static Entry entry(String name, int density, int viscosity) {
        DeferredHolder<FluidType, FluidType> type = TYPES.register(name, () -> new FluidType(
                FluidType.Properties.create()
                        .density(density)
                        .viscosity(viscosity)
                        .temperature(300)
                        .canConvertToSource(false)
                        .canDrown(false)
                        .canPushEntity(false)
                        .supportsBoating(false)));
        // 源与流动互相引用：Properties 收的是 Supplier，故先用「一格数组」占位 ——
        // 注册 lambda 真正执行（填注册表）时，properties[0] 早已赋好值。
        BaseFlowingFluid.Properties[] properties = new BaseFlowingFluid.Properties[1];
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source =
                FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties[0]));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing =
                FLUIDS.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(properties[0]));
        // 桶：普通 BucketItem（自带 NeoForge 容器能力，储罐 / 机器可装可倒），倒空后还回空桶。
        // 桶在 ITEM 注册表填装时构造，那时 FLUID 注册表已填好，source.get() 可用。
        // 用 registerItem(...)：1.21.2+ 的 Item.Properties 必须先带物品 id，registerItem 会预置好。
        DeferredItem<BucketItem> bucket = ITEMS.registerItem(name + "_bucket",
                props -> new BucketItem(source.get(),
                        props.craftRemainder(Items.BUCKET).stacksTo(1)));
        properties[0] = new BaseFlowingFluid.Properties(type, () -> source.get(), () -> flowing.get())
                .bucket(() -> bucket.get());
        return new Entry(name, type, source, flowing, bucket);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
        ITEMS.register(bus);
    }

    private BioFluids() {}
}
