package dev.modmind.stellar_evolution.wood;

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
 * 木化工线的<strong>流体类物质</strong>（2026-11-11 批次 1）。
 *
 * <p>照 {@code CoalFluids} 口径：<strong>不落地流体</strong>（只注册 {@code FluidType + Source + Flowing}，无方块）、
 * <strong>每条流体各配一只桶</strong>。木煤气并入煤化工的 {@code producer_gas}，这里不单独注册。
 *
 * <p>贴图由 {@code tools/gen_wood_materials.ps1} 生成；双语名走 lang 键
 * {@code fluid_type.stellar_evolution.<name>} / {@code item.stellar_evolution.<name>_bucket}。
 */
public final class WoodFluids {
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

    /** 木焦油：原木干馏副产，分馏出杂酚油 / 沥青。 */
    public static final Entry WOOD_TAR = entry("wood_tar", 1100, 8000);
    /** 木醋液：原木干馏副产，分馏出醋酸 / 甲醇 / 丙酮。 */
    public static final Entry WOOD_VINEGAR = entry("wood_vinegar", 1000, 1200);
    /** 松节油：松木蒸汽蒸馏，内燃机 MEDIUM；也是松节油-硝酸原料。 */
    public static final Entry TURPENTINE = entry("turpentine", 870, 900);
    /** 杂酚油：木焦油分馏；低级液体燃料（燃烧引擎 LOW）。 */
    public static final Entry CREOSOTE = entry("creosote", 1050, 5000);
    /** 糠醛：木质素 / 玉米芯水解。 */
    public static final Entry FURFURAL = entry("furfural", 1160, 1500);
    /** 糠醇：糠醛加氢，糠醇-硝酸原料。 */
    public static final Entry FURFURYL_ALCOHOL = entry("furfuryl_alcohol", 1130, 1600);
    /** 松节油-硝酸：松节油 + 发烟硝酸，火箭 ROCKET。 */
    public static final Entry TURPENTINE_NITRIC = entry("turpentine_nitric", 1200, 1100);
    /** 糠醇-硝酸：糠醇 + 发烟硝酸，火箭 ROCKET。 */
    public static final Entry FURFURYL_NITRIC = entry("furfuryl_nitric", 1200, 1200);
    /** 乙醇氧：乙醇 + 液氧预混，火箭 ROCKET。 */
    public static final Entry ETHALOX = entry("ethalox", 850, 700);

    /** 全部流体（客户端登记外观时按这个顺序走）。 */
    public static final List<Entry> ALL = List.of(
            WOOD_TAR, WOOD_VINEGAR, TURPENTINE, CREOSOTE, FURFURAL,
            FURFURYL_ALCOHOL, TURPENTINE_NITRIC, FURFURYL_NITRIC, ETHALOX);

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

    private WoodFluids() {}
}
