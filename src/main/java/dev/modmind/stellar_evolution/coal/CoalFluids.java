package dev.modmind.stellar_evolution.coal;

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
 * 煤化工线（C 线）的<strong>流体类物质</strong>。
 *
 * <p>照 {@code PetroFluids} / {@code BioFluids} 口径：<strong>不落地流体</strong>
 * （只注册 {@code FluidType + Source + Flowing}，无方块），<strong>每条流体各配一只桶</strong>，
 * 可在储罐 / 机器之间装倒。
 *
 * <p>清单（过程节点 5 + 终端流体 1）：
 * <ul>
 *   <li>{@code syngas} 合成气 / {@code clean_syngas} 净化合成气 —— 气化与净化的主干；</li>
 *   <li>{@code coal_tar} 煤焦油 —— 干馏副产，精制出苯 / 苯酚 / 萘；</li>
 *   <li>{@code methanol} 甲醇 —— MTO 与燃料；</li>
 *   <li>{@code carbon_dioxide} 二氧化碳 —— 尿素与回炉气化；</li>
 *   <li>{@code naphthalene} 萘 —— <strong>唯一新增终端流体</strong>，进燃烧引擎燃料表。</li>
 * </ul>
 *
 * <p>贴图由 {@code tools/gen_coal_materials.ps1} 生成；外观在 {@code StellarEvolutionClient}
 * 的 {@code RegisterFluidModelsEvent} 里逐条登记。双语名走 lang 键
 * {@code fluid_type.stellar_evolution.<name>} / {@code item.stellar_evolution.<name>_bucket}。
 */
public final class CoalFluids {
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

    /** 合成气：煤 / 焦炭 / 煤焦油气化的产物。 */
    public static final Entry SYNGAS = entry("syngas", -50, 150);
    /** 净化合成气：脱硫脱碳后的合成气，进甲醇 / 氨。 */
    public static final Entry CLEAN_SYNGAS = entry("clean_syngas", -50, 120);
    /** 煤焦油：干馏副产，焦油精制与气化的原料。 */
    public static final Entry COAL_TAR = entry("coal_tar", 1150, 12000);
    /** 甲醇：MTO 原料 / 燃烧引擎燃料。 */
    public static final Entry METHANOL = entry("methanol", 790, 600);
    /** 二氧化碳：尿素原料 / 回炉气化。 */
    public static final Entry CARBON_DIOXIDE = entry("carbon_dioxide", 20, 100);
    /** 萘（熔融）：煤焦油精制的终端流体，进燃烧引擎燃料表。 */
    public static final Entry NAPHTHALENE = entry("naphthalene", 1000, 2500);
    // ---- 2026-11-11 批次 1：干馏 / 气化 / 电石 / 合成气延伸
    /** 焦炉煤气：煤绝氧干馏副产，燃气轮机 GAS。 */
    public static final Entry COKE_OVEN_GAS = entry("coke_oven_gas", -40, 100);
    /** 发生炉煤气：煤 / 木炭空气气化，燃气轮机 GAS（木煤气并入本流体）。 */
    public static final Entry PRODUCER_GAS = entry("producer_gas", -35, 120);
    /** 二甲醚：甲醇脱水，内燃机 HIGH。 */
    public static final Entry DME = entry("dme", -20, 90);
    /** 合成天然气：合成气甲烷化，燃气轮机 GAS。 */
    public static final Entry SNG = entry("sng", -70, 100);
    /** 合成航煤：费托合成，燃气轮机 AERO。 */
    public static final Entry SYNTHETIC_JET = entry("synthetic_jet", 815, 900);
    /** 乙炔：电石水解，氯乙烯 / 醋酸乙烯原料。 */
    public static final Entry ACETYLENE = entry("acetylene", -45, 80);
    /** 氯乙烯：乙炔 + 氯化氢，PVC 单体。 */
    public static final Entry VCM = entry("vcm", -10, 100);
    /** 醋酸乙烯：乙炔 + 醋酸（预留胶黏剂 / 涂料）。 */
    public static final Entry VINYL_ACETATE = entry("vinyl_acetate", 930, 600);
    /** 混合芳烃：轻油重整的苯系混合物。 */
    public static final Entry BTX = entry("btx", 870, 500);
    /** 煤水浆：煤炭粉 + 水，煤直接液化的进料。 */
    public static final Entry COAL_SLURRY = entry("coal_slurry", 1150, 9000);
    /** 煤液化油：煤水浆加氢的直接液化产物，可直接进分馏塔。 */
    public static final Entry COAL_LIQUEFIED_OIL = entry("coal_liquefied_oil", 900, 2500);

    /** 全部流体（客户端登记外观时按这个顺序走）。 */
    public static final List<Entry> ALL = List.of(
            SYNGAS, CLEAN_SYNGAS, COAL_TAR, METHANOL, CARBON_DIOXIDE, NAPHTHALENE,
            COKE_OVEN_GAS, PRODUCER_GAS, DME, SNG, SYNTHETIC_JET,
            ACETYLENE, VCM, VINYL_ACETATE, BTX, COAL_SLURRY, COAL_LIQUEFIED_OIL);

    private static Entry entry(String name, int density, int viscosity) {
        DeferredHolder<FluidType, FluidType> type = TYPES.register(name, () -> new FluidType(
                FluidType.Properties.create()
                        .density(density)
                        .viscosity(viscosity)
                        .temperature(300)
                        .canConvertToSource(false)));
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
        return new Entry(name, type, source, flowing, bucket);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
        ITEMS.register(bus);
    }

    private CoalFluids() {}
}
