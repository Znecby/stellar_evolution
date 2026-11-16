package dev.znecby.stellar_evolution.fission;

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
 * 核裂变线的<strong>流体类物质</strong>（《核裂变线预规划蓝图》§12.3 / §13.1 F0）。
 *
 * <p>照 {@code PetroFluids} 口径：<strong>不落地流体</strong>（只注册 {@code FluidType + Source + Flowing}，<strong>无方块</strong>），
 * <strong>每条流体各配一只桶</strong>。含六氟化铀（前端）+ 四种冷却剂 + 蒸汽 / 超临界蒸汽（蒸汽循环）。
 *
 * <p>贴图由 {@code tools/gen_fission_materials.ps1} 生成；流体外观由 {@code StellarEvolutionClient}
 * 在 {@code RegisterFluidModelsEvent} 里逐条登记；双语名走 lang 键
 * {@code fluid_type.stellar_evolution.<name>} / {@code item.stellar_evolution.<name>_bucket}。
 */
public final class FissionFluids {
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

    // ---- 前端：氟化转化（§5.1）
    /** 六氟化铀：氟化炉直接产出，离心分离的原料。 */
    public static final Entry URANIUM_HEXAFLUORIDE = entry("uranium_hexafluoride", 5000, 6000);
    // ---- 湿法前端（§5.1：浸出 → 萃取 → 沉淀）
    /** 硝酸：酸浸 / 溶解。 */
    public static final Entry NITRIC_ACID = entry("nitric_acid", 1400, 800);
    /** 载铀有机相：萃取产物。 */
    public static final Entry LOADED_EXTRACTANT = entry("loaded_extractant", 850, 1000);
    /** 萃余液：萃取副产（可蒸干回收盐渣）。 */
    public static final Entry RAFFINATE = entry("raffinate", 1000, 900);
    /** 氨水：沉淀剂。 */
    public static final Entry AMMONIA_SOLUTION = entry("ammonia_solution", 900, 400);
    // ---- 氟循环（§5.1：氢氟化 / 氟化 / 电解再生）
    /** 氟化氢：氢氟化原料，水解后回收。 */
    public static final Entry HYDROGEN_FLUORIDE = entry("hydrogen_fluoride", 900, 350);
    /** 氟气：氟化原料，电解再生。 */
    public static final Entry FLUORINE = entry("fluorine", -150, 150);
    // ---- 冷却剂（二次回路 / 风险不同，蓝图 §3.4）
    /** 氦气（原氦冷却剂）：高温气冷堆冷却 / 液态空气分馏副产。 */
    public static final Entry HELIUM_COOLANT = entry("helium_coolant", -100, 12);
    // ---- 蒸汽循环（§4.5）
    /** 蒸汽：经蒸汽轮机接入热压差主网。 */
    public static final Entry STEAM = entry("steam", -700, 10);
    // ---- 聚变燃料（高能物理线第一期）
    /** 重水：离心富集产物，氘的来源。 */
    public static final Entry HEAVY_WATER = entry("heavy_water", 1100, 1200);
    /** 氘：重水分离产物，聚变芯核原料。 */
    public static final Entry DEUTERIUM = entry("deuterium", -100, 100);
    /** 氚：回旋加速器锂粉轰击产物，聚变芯核原料。 */
    public static final Entry TRITIUM = entry("tritium", -100, 100);
    /** 氘氚燃料：氘 + 氚预混，罐装机做聚变芯核的原料（二期 A2）。 */
    public static final Entry DT_FUEL = entry("dt_fuel", -50, 80);

    /** 全部流体（客户端登记外观时按这个顺序走）。 */
    public static final List<Entry> ALL = List.of(
            URANIUM_HEXAFLUORIDE,
            NITRIC_ACID, LOADED_EXTRACTANT, RAFFINATE, AMMONIA_SOLUTION,
            HYDROGEN_FLUORIDE, FLUORINE,
            HELIUM_COOLANT,
            STEAM,
            HEAVY_WATER, DEUTERIUM, TRITIUM, DT_FUEL);

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
        // 源与流动互相引用：Properties 收的是 Supplier，故先用「一格数组」占位（照 PetroFluids）。
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

    private FissionFluids() {}
}
