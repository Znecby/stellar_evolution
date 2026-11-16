package dev.modmind.stellar_evolution.petro;

import dev.modmind.stellar_evolution.StellarEvolution;
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
 * 石化线的<strong>流体类物质</strong>（《石化线》O0 ①：物质注册与贴图规范；清单见 §4）。
 *
 * <p>照 {@code BioFluids} 口径：<strong>不落地流体</strong>（只注册 {@code FluidType + Source + Flowing}，<strong>无方块</strong>），
 * <strong>每条流体各配一只桶</strong>，可在储罐 / 机器之间装倒（{@code craftRemainder(空桶)}）。
 *
 * <p>贴图由 {@code tools/gen_petro_materials.ps1} 生成；流体外观由 {@code StellarEvolutionClient}
 * 在 {@code RegisterFluidModelsEvent} 里逐条登记。双语名走 lang 键
 * {@code fluid_type.stellar_evolution.<name>} / {@code item.stellar_evolution.<name>_bucket}。
 */
public final class PetroFluids {
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

    // ---- 原料（§4.1）
    /** 原油：石化线总原料；<strong>可落地流体</strong>，世界生成原油喷泉（GT 同款 raw_oil_sprout）。 */
    public static final Entry CRUDE_OIL = entry("crude_oil", 1000, 1000, true);
    // ---- 中间体 / 分馏馏分（§4.2）
    /** 石脑油：裂解 → 烯烃；重整 → 苯。 */
    public static final Entry NAPHTHA = entry("naphtha", 700, 500);
    /** 煤油：燃烧引擎燃料 / 溶剂。 */
    public static final Entry KEROSENE = entry("kerosene", 820, 900);
    /** 柴油：燃烧引擎燃料（比煤油更强）。 */
    public static final Entry DIESEL = entry("diesel", 850, 1400);
    // ---- 精制燃料（三台发电机组的等级料）
    /** 高十六烷值柴油：柴油加氢改质，压燃机的高档料。 */
    public static final Entry HIGH_CETANE_DIESEL = entry("high_cetane_diesel", 860, 1500);
    /** 高辛烷值汽油：石脑油重整的高档汽油。 */
    public static final Entry HIGH_OCTANE_GASOLINE = entry("high_octane_gasoline", 730, 450);
    /** 含铅汽油：高辛烷值汽油掺铅，抗爆性最好（也更不环保）。 */
    public static final Entry LEADED_GASOLINE = entry("leaded_gasoline", 760, 480);
    /** 航空煤油：煤油加氢精制，燃气轮机 / 火箭的常用料。 */
    public static final Entry AVIATION_KEROSENE = entry("aviation_kerosene", 810, 800);
    /** 石油气：裂解副产的轻质可燃气，燃气轮机的低价料（原炼厂气）。 */
    public static final Entry PETROLEUM_GAS = entry("petroleum_gas", -30, 120);
    /** 火箭燃料（RP-1 口径）：航空煤油 + 液氧预混的火箭级燃料。 */
    public static final Entry ROCKET_FUEL = entry("rocket_fuel", 800, 700);
    // ---- 新增精制 / 推进剂（燃料分级：普通汽油 MEDIUM；三级火箭均 ROCKET）
    /** 重整油：石脑油重整产物，高辛烷值汽油的调合组分（不入燃料表）。 */
    public static final Entry REFORMATE = entry("reformate", 760, 400);
    /** 普通汽油：石脑油加氢异构的中档汽油（MEDIUM 级燃料）。 */
    public static final Entry GASOLINE = entry("gasoline", 720, 350);
    /** 四硝基甲烷：高十六烷值柴油的十六烷值改进剂。 */
    public static final Entry TETRANITROMETHANE = entry("tetranitromethane", 1650, 150);
    /** 液甲烷：沼气 / 合成气路线的低温推进剂。 */
    public static final Entry LIQUID_METHANE = entry("liquid_methane", -160, 100);
    /** 甲烷氧火箭燃料：液甲烷 + 液氧预混（ROCKET）。 */
    public static final Entry METHALOX = entry("methalox", -120, 120);
    /** 肼：偏二甲肼的上游。 */
    public static final Entry HYDRAZINE = entry("hydrazine", 1000, 80);
    /** 偏二甲肼：自燃火箭燃料的燃料组分。 */
    public static final Entry UDMH = entry("udmh", 790, 90);
    /** 四氧化二氮：自燃火箭燃料的氧化剂组分。 */
    public static final Entry DINITROGEN_TETROXIDE = entry("dinitrogen_tetroxide", 1450, 120);
    /** 自燃火箭燃料：偏二甲肼 + 四氧化二氮预混（ROCKET 顶级）。 */
    public static final Entry HYPERGOLIC_FUEL = entry("hypergolic_fuel", 1100, 130);
    /** 重油：裂解；沥青前体。 */
    public static final Entry HEAVY_OIL = entry("heavy_oil", 950, 6000);
    /** 沥青：建材 / 防水。 */
    public static final Entry BITUMEN = entry("bitumen", 1100, 20000);
    // ---- 产物（§4.3）
    /** 乙烯：聚合单体。 */
    public static final Entry ETHYLENE = entry("ethylene", 400, 100);
    /** 丙烯：聚合单体（橡胶）。 */
    public static final Entry PROPYLENE = entry("propylene", 500, 120);
    /** 苯：溶剂 / 高级单体。 */
    public static final Entry BENZENE = entry("benzene", 880, 600);
    /** 氘代溶剂：氘核束轰击苯的同位素交换产物（高纯分析 / 反应介质）。 */
    public static final Entry DEUTERATED_SOLVENT = entry("deuterated_solvent", 950, 620);
    /** 润滑油：机械润滑（热压差提速为预留机制）。 */
    public static final Entry LUBRICANT = entry("lubricant", 900, 8000);
    /** 轻油：裂解炉焦化沥青时的副产轻馏分。 */
    public static final Entry LIGHT_OIL = entry("light_oil", 830, 700);
    /** 氢气：重整副产，加氢脱硫的原料。 */
    public static final Entry HYDROGEN = entry("hydrogen", -100, 200);
    /** 酸性气：加氢脱硫副产的富硫气。 */
    public static final Entry SOUR_GAS = entry("sour_gas", 60, 300);
    /** 低硫油：加氢脱硫后的清洁油品。 */
    public static final Entry LOW_SULFUR_OIL = entry("low_sulfur_oil", 840, 1200);
    // ---- 聚合单体（§4.3；由芯线化工体系供给，聚合釜按单体分派）
    /** 苯酚：电木（酚醛树脂）单体。 */
    public static final Entry PHENOL = entry("phenol", 1070, 2500);
    /** 甲醛：电木（酚醛树脂）单体。 */
    public static final Entry FORMALDEHYDE = entry("formaldehyde", 815, 500);
    /** 甲基丙烯酸甲酯：有机玻璃（PMMA）单体。 */
    public static final Entry MMA = entry("mma", 940, 600);
    /** 四氟乙烯：聚四氟乙烯（PTFE）单体。 */
    public static final Entry TETRAFLUOROETHYLENE = entry("tetrafluoroethylene", 30, 200);
    /** 己内酰胺：尼龙（PA6）单体。 */
    public static final Entry CAPROLACTAM = entry("caprolactam", 1010, 3000);
    /** 丁二烯：丁苯 / 丁腈橡胶共聚单体。 */
    public static final Entry BUTADIENE = entry("butadiene", 20, 250);
    /** 苯乙烯：丁苯橡胶共聚单体。 */
    public static final Entry STYRENE = entry("styrene", 906, 700);
    /** 丙烯腈：丁腈橡胶共聚单体。 */
    public static final Entry ACRYLONITRILE = entry("acrylonitrile", 810, 550);
    /** 氯丁二烯：氯丁橡胶单体。 */
    public static final Entry CHLOROPRENE = entry("chloroprene", 960, 700);
    /** 硅氧烷单体：硅橡胶单体。 */
    public static final Entry SILOXANE = entry("siloxane", 950, 900);
    // ---- 2026-11-11 批次 1：硝基苯 / 单组元推进剂 / 金属化推进剂
    /** 硝基苯：苯 + 硝酸，内燃机 HIGH；也是单组元推进剂原料。 */
    public static final Entry NITROBENZENE = entry("nitrobenzene", 1200, 2000);
    /** 硝基苯推进剂：硝基苯 + 过氧化氢活化，火箭 ROCKET（单组元）。 */
    public static final Entry NITROBENZENE_MONOPROPELLANT = entry("nitrobenzene_monopropellant", 1250, 1800);
    /** 铝化煤油浆：煤油掺铝粉，火箭 ROCKET（比冲更高）。 */
    public static final Entry ALUMINIZED_KEROSENE = entry("aluminized_kerosene", 1000, 3000);

    /** 全部流体（客户端登记外观时按这个顺序走）。 */
    public static final List<Entry> ALL = List.of(
            CRUDE_OIL, NAPHTHA, KEROSENE, DIESEL, HEAVY_OIL, BITUMEN,
            HIGH_CETANE_DIESEL, HIGH_OCTANE_GASOLINE, LEADED_GASOLINE, AVIATION_KEROSENE,
            PETROLEUM_GAS, ROCKET_FUEL, REFORMATE, GASOLINE,
            TETRANITROMETHANE, LIQUID_METHANE, METHALOX,
            HYDRAZINE, UDMH, DINITROGEN_TETROXIDE, HYPERGOLIC_FUEL,
            ETHYLENE, PROPYLENE, BENZENE, DEUTERATED_SOLVENT, LUBRICANT,
            LIGHT_OIL, HYDROGEN, SOUR_GAS, LOW_SULFUR_OIL,
            PHENOL, FORMALDEHYDE, MMA, TETRAFLUOROETHYLENE, CAPROLACTAM,
            BUTADIENE, STYRENE, ACRYLONITRILE, CHLOROPRENE, SILOXANE,
            NITROBENZENE, NITROBENZENE_MONOPROPELLANT, ALUMINIZED_KEROSENE);

    private static Entry entry(String name, int density, int viscosity) {
        return entry(name, density, viscosity, false);
    }

    /**
     * @param placeable 是否注册<strong>流体方块</strong>（可存在于世界；只有原油需要）
     */
    private static Entry entry(String name, int density, int viscosity, boolean placeable) {
        DeferredHolder<FluidType, FluidType> type = TYPES.register(name, () -> new FluidType(
                FluidType.Properties.create()
                        .density(density)
                        .viscosity(viscosity)
                        .temperature(300)
                        .canConvertToSource(false)
                        .isWaterLike(placeable)
                        .canDrown(placeable)
                        .canPushEntity(placeable)
                        .supportsBoating(placeable)));
        // 源与流动互相引用：Properties 收的是 Supplier，故先用「一格数组」占位（照 BioFluids）。
        BaseFlowingFluid.Properties[] properties = new BaseFlowingFluid.Properties[1];
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source =
                FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties[0]));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing =
                FLUIDS.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(properties[0]));
        DeferredItem<BucketItem> bucket = ITEMS.registerItem(name + "_bucket",
                props -> new BucketItem(source.get(),
                        props.craftRemainder(Items.BUCKET).stacksTo(1)));
        properties[0] = new BaseFlowingFluid.Properties(type, () -> source.get(), () -> flowing.get())
                .bucket(() -> bucket.get());
        if (placeable) {
            properties[0].block(() -> PetroBlocks.CRUDE_OIL_BLOCK.get());
        }
        return new Entry(name, type, source, flowing, bucket);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
        ITEMS.register(bus);
    }

    private PetroFluids() {}
}
