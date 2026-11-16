package dev.modmind.stellar_evolution.bedrock;

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
 * 基岩线的<strong>流体类物质</strong>（《基岩无限资源线蓝图》§3 / §8.2）。
 *
 * <p>4 条同位素溶剂 + 4 条中间体，全部<strong>不落地</strong>（无方块），每条各配一只桶。
 * 贴图由 {@code tools/gen_bedrock_materials.ps1} 生成；流体外观在
 * {@code StellarEvolutionClient#onRegisterFluidModels} 里逐条登记。
 */
public final class BedrockFluids {
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

    // ---- 同位素溶剂（§3）
    /** γ 活化溶剂：Co-60 + Cs-137 + 低硫油。 */
    public static final Entry GAMMA_SOLVENT = entry("gamma_solvent", 950, 900);
    /** 卤化挥发溶剂：I-129 + Tc-99 + 萘 + 氯气。 */
    public static final Entry HALIDE_SOLVENT = entry("halide_solvent", 1200, 1500);
    /** β 自热熔盐：Sr-90 + Pu-238 + 氢氧化钠 + 甘油。 */
    public static final Entry SELFHEAT_SOLVENT = entry("selfheat_solvent", 1800, 5000);
    /** α 螯合溶剂：Am-241 + Cm-244 + Np-237 + U-233 + Sm-149 + 丁醇 + 低硫油。 */
    public static final Entry CHELATE_SOLVENT = entry("chelate_solvent", 880, 1000);

    // ---- 中间体（§5）
    /** 挥发性卤化物：S1 产物，分离出 Zr/Hf/Ti/Si/Ge/Sn/Nb/Ta。 */
    public static final Entry VOLATILE_HALIDE_MIX = entry("volatile_halide_mix", 30, 200);
    /** 熔盐浸出液：S2 产物，分离出 Al/Cr/Mn/Fe/Ni/Co/Mo/W/V。 */
    public static final Entry MOLTEN_SALT_LEACHATE = entry("molten_salt_leachate", 1900, 3500);
    /** 负载有机相：S3 产物，反萃出锕系 / 稀土。 */
    public static final Entry LOADED_ORGANIC = entry("loaded_organic", 850, 1100);
    /** 生物浸出液：S5 产物，分离出 Au/Be/B/Li/Na/Mg/Ga/C/Cl。 */
    public static final Entry BIOLEACHATE = entry("bioleachate", 1100, 1500);

    /** 全部流体（客户端登记外观时按这个顺序走）。 */
    public static final List<Entry> ALL = List.of(
            GAMMA_SOLVENT, HALIDE_SOLVENT, SELFHEAT_SOLVENT, CHELATE_SOLVENT,
            VOLATILE_HALIDE_MIX, MOLTEN_SALT_LEACHATE, LOADED_ORGANIC, BIOLEACHATE);

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

    private BedrockFluids() {}
}
