package dev.modmind.stellar_evolution.ore;

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
 * 选冶线的<strong>流体类物质</strong>（14 条富精矿浆 + 过氧化氢 + 硫酸 + 氯气 + 四氯化钛 + 汞 + 盐酸 + 硼酸）。
 *
 * <p>照 {@code BioFluids} 口径：<strong>不落地流体</strong>（只注册 {@code FluidType + Source + Flowing}，无方块），
 * 每条流体各配一只桶；贴图由 {@code tools/gen_ore_materials.ps1} 生成（原版水动画条换色），
 * 外观由 {@code StellarEvolutionClient} 在 {@code RegisterFluidModelsEvent} 里逐条登记。
 * <p>双语名走 {@code fluid_type.stellar_evolution.<name>} / {@code item.stellar_evolution.<name>_bucket}。
 */
public final class OreFluids {
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

    // ---- 富精矿浆（分解仪产物 → 结晶机出精砂）
    public static final Entry RICH_IRON_CONCENTRATE_SLURRY = entry("rich_iron_concentrate_slurry", 1400, 3000);
    public static final Entry RICH_COPPER_CONCENTRATE_SLURRY = entry("rich_copper_concentrate_slurry", 1400, 3000);
    public static final Entry RICH_GOLD_CONCENTRATE_SLURRY = entry("rich_gold_concentrate_slurry", 1500, 3200);
    public static final Entry RICH_URANIUM_CONCENTRATE_SLURRY = entry("rich_uranium_concentrate_slurry", 1600, 3200);
    /** 富钨精矿浆：粗钨浮选产物（2026-10-24 补）。 */
    public static final Entry RICH_TUNGSTEN_CONCENTRATE_SLURRY = entry("rich_tungsten_concentrate_slurry", 1650, 3300);
    /** 富铅精矿浆：粗铅浮选产物（2026-10-26 补）。 */
    public static final Entry RICH_LEAD_CONCENTRATE_SLURRY = entry("rich_lead_concentrate_slurry", 1600, 3200);
    public static final Entry RICH_DEBRIS_CONCENTRATE_SLURRY = entry("rich_debris_concentrate_slurry", 1600, 3600);
    public static final Entry RICH_COAL_CONCENTRATE_SLURRY = entry("rich_coal_concentrate_slurry", 1300, 2600);
    public static final Entry RICH_DIAMOND_CONCENTRATE_SLURRY = entry("rich_diamond_concentrate_slurry", 1500, 3000);
    public static final Entry RICH_EMERALD_CONCENTRATE_SLURRY = entry("rich_emerald_concentrate_slurry", 1500, 3000);
    public static final Entry RICH_LAPIS_CONCENTRATE_SLURRY = entry("rich_lapis_concentrate_slurry", 1450, 3000);
    public static final Entry RICH_REDSTONE_CONCENTRATE_SLURRY = entry("rich_redstone_concentrate_slurry", 1400, 2800);
    public static final Entry RICH_QUARTZ_CONCENTRATE_SLURRY = entry("rich_quartz_concentrate_slurry", 1450, 3000);
    /** 过氧化氢：发酵罐（高温带 + 矿物盐催化）产物 → 与硫粉 / 水在反应器合成硫酸。 */
    public static final Entry HYDROGEN_PEROXIDE = entry("hydrogen_peroxide", 1150, 800);
    /** 硫酸：铁 / 铜 / 残骸 / 非金属精砂的浸出剂。 */
    public static final Entry SULFURIC_ACID = entry("sulfuric_acid", 1250, 900);
    /** 氯气：B1 氯化钠电解产物；B2 锆 / 钛氯化。 */
    public static final Entry CHLORINE = entry("chlorine", 3, 25);
    /** 四氯化钛：钛线金红石粉氯化产物，钠热法还原成钛。 */
    public static final Entry TITANIUM_TETRACHLORIDE = entry("titanium_tetrachloride", 1720, 900);
    /** 汞：分解仪红石再处理 / 红石渣二段浸出的液态金属产物；稀土合金汞齐原料（2026-11-08 由汞粉改为流体）。 */
    public static final Entry MERCURY = entry("mercury", 3600, 1200);
    /** 盐酸：氯化钠 + 硫酸 / 氯气 + 氢气合成；替代部分硫酸、硝酸用于铁 / 钨 / 独居石 / 基岩酸浸（2026-11-08）。 */
    public static final Entry HYDROCHLORIC_ACID = entry("hydrochloric_acid", 1200, 700);
    /** 硼酸：硼砂酸浸产物（2026-11-08 由物品改为流体）；硼粉还原与裂变堆化学停堆剂。 */
    public static final Entry BORIC_ACID = entry("boric_acid", 1100, 800);

    /** 全部流体（客户端登记外观时按这个顺序走）。 */
    public static final List<Entry> ALL = List.of(
            RICH_IRON_CONCENTRATE_SLURRY, RICH_COPPER_CONCENTRATE_SLURRY,
            RICH_GOLD_CONCENTRATE_SLURRY, RICH_URANIUM_CONCENTRATE_SLURRY,
            RICH_TUNGSTEN_CONCENTRATE_SLURRY,
            RICH_LEAD_CONCENTRATE_SLURRY,
            RICH_DEBRIS_CONCENTRATE_SLURRY, RICH_COAL_CONCENTRATE_SLURRY,
            RICH_DIAMOND_CONCENTRATE_SLURRY, RICH_EMERALD_CONCENTRATE_SLURRY,
            RICH_LAPIS_CONCENTRATE_SLURRY, RICH_REDSTONE_CONCENTRATE_SLURRY,
            RICH_QUARTZ_CONCENTRATE_SLURRY, HYDROGEN_PEROXIDE, SULFURIC_ACID,
            CHLORINE, TITANIUM_TETRACHLORIDE, MERCURY,
            HYDROCHLORIC_ACID, BORIC_ACID);

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

    private OreFluids() {}
}
