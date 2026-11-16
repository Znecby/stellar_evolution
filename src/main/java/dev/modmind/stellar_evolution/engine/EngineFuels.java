package dev.modmind.stellar_evolution.engine;

import dev.modmind.stellar_evolution.bio.BioFluids;
import dev.modmind.stellar_evolution.coal.CoalFluids;
import dev.modmind.stellar_evolution.petro.PetroFluids;
import dev.modmind.stellar_evolution.wood.WoodFluids;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * 燃烧类引擎的<strong>流体燃料表</strong>：流体 → 「每 mB 运行时长 + 出力 DP / HP·t⁻¹ + 等级 + 品质」。
 *
 * <p>燃烧引擎（老机器）只烧往复式内燃机能用的等级（LOW / MEDIUM / HIGH）；三台新机器按
 * {@link EngineFuelGrade} 选料：
 * <ul>
 *   <li>工业内燃机组：{@link EngineFuelGrade#LOW} / {@link EngineFuelGrade#MEDIUM} / {@link EngineFuelGrade#HIGH}；</li>
 *   <li>联合燃气机组：{@link EngineFuelGrade#GAS} / {@link EngineFuelGrade#AERO}；</li>
 *   <li>航空航天火箭引擎：{@link EngineFuelGrade#ROCKET}（煤油氧 / 甲烷氧 / 自燃三级，燃料自带氧化剂）。</li>
 * </ul>
 *
 * <p>{@code quality}（0.6–1.45）是十六烷值 / 辛烷值的抽象：内燃机效率、燃气轮机的温升都乘它。
 */
public final class EngineFuels {
    /** 一种流体燃料：每 mB 能跑多少刻、运行期间的出力、等级与品质。 */
    public record Fuel(int ticksPerMb, double capacity, double flowRate, EngineFuelGrade grade, double quality) {}

    private static final Map<Fluid, Fuel> TABLE = new HashMap<>();

    /** 该流体的燃料数值；不是燃料则 null。 */
    @Nullable
    public static Fuel of(Fluid fluid) {
        return TABLE.get(fluid);
    }

    /** 是不是往复式内燃机（燃烧引擎 / 工业内燃机组）能烧的等级：LOW / MEDIUM / HIGH。 */
    public static boolean pistonGrade(@Nullable Fuel fuel) {
        return fuel != null && (fuel.grade() == EngineFuelGrade.LOW
                || fuel.grade() == EngineFuelGrade.MEDIUM
                || fuel.grade() == EngineFuelGrade.HIGH);
    }

    /** 全部流体燃料（JEI 展示用）。 */
    public static Map<Fluid, Fuel> all() {
        return Map.copyOf(TABLE);
    }

    static {
        // 生化线：低品质 → 中品质
        put(BioFluids.BIOGAS, 200, 5120.0D, 1280.0D, EngineFuelGrade.GAS, 0.60D);
        put(BioFluids.ETHANOL, 200, 6144.0D, 1536.0D, EngineFuelGrade.MEDIUM, 0.90D);
        put(BioFluids.BIODIESEL, 400, 10_240.0D, 2560.0D, EngineFuelGrade.HIGH, 1.10D);
        put(BioFluids.ACETONE, 160, 5120.0D, 1280.0D, EngineFuelGrade.LOW, 0.70D);
        // 石化线：煤油（航空级）> 柴油（高十六烷）；精制燃料是内燃机 / 燃气轮机 / 火箭的上位料
        put(PetroFluids.KEROSENE, 240, 7168.0D, 1792.0D, EngineFuelGrade.AERO, 1.00D);
        put(PetroFluids.DIESEL, 320, 9216.0D, 2048.0D, EngineFuelGrade.HIGH, 1.20D);
        put(PetroFluids.HIGH_CETANE_DIESEL, 420, 12_288.0D, 3072.0D, EngineFuelGrade.HIGH, 1.30D);
        put(PetroFluids.HIGH_OCTANE_GASOLINE, 380, 11_520.0D, 2880.0D, EngineFuelGrade.HIGH, 1.25D);
        put(PetroFluids.LEADED_GASOLINE, 400, 12_800.0D, 3200.0D, EngineFuelGrade.HIGH, 1.32D);
        put(PetroFluids.AVIATION_KEROSENE, 340, 10_240.0D, 2560.0D, EngineFuelGrade.AERO, 1.15D);
        put(PetroFluids.PETROLEUM_GAS, 140, 7168.0D, 1792.0D, EngineFuelGrade.GAS, 0.85D);
        put(PetroFluids.ROCKET_FUEL, 500, 16_384.0D, 4096.0D, EngineFuelGrade.ROCKET, 1.20D);
        put(PetroFluids.GASOLINE, 260, 7168.0D, 1792.0D, EngineFuelGrade.MEDIUM, 0.95D);
        put(PetroFluids.METHALOX, 560, 18_432.0D, 4608.0D, EngineFuelGrade.ROCKET, 1.25D);
        put(PetroFluids.HYPERGOLIC_FUEL, 640, 20_480.0D, 5120.0D, EngineFuelGrade.ROCKET, 1.35D);
        // 煤化工线：合成气（燃气轮机）；甲醇 / 萘已退出燃料表，只作化工中间体
        put(CoalFluids.SYNGAS, 120, 6144.0D, 1536.0D, EngineFuelGrade.GAS, 0.70D);
        put(CoalFluids.CLEAN_SYNGAS, 160, 7168.0D, 1792.0D, EngineFuelGrade.GAS, 0.90D);
        // ---- 2026-11-11 批次 1：新燃料（煤 / 木 / 石化延伸）
        // 燃气轮机 GAS
        put(CoalFluids.PRODUCER_GAS, 110, 4608.0D, 1152.0D, EngineFuelGrade.GAS, 0.55D);
        put(CoalFluids.COKE_OVEN_GAS, 180, 7680.0D, 1920.0D, EngineFuelGrade.GAS, 0.95D);
        put(CoalFluids.SNG, 240, 8704.0D, 2176.0D, EngineFuelGrade.GAS, 1.10D);
        // 燃气轮机 AERO
        put(CoalFluids.SYNTHETIC_JET, 360, 11_264.0D, 2816.0D, EngineFuelGrade.AERO, 1.20D);
        // 木化工：杂酚油作为低级液体燃料（2026-11-16）
        put(WoodFluids.CREOSOTE, 120, 4608.0D, 1152.0D, EngineFuelGrade.LOW, 0.55D);
        // 工业内燃机 MEDIUM / HIGH
        put(WoodFluids.TURPENTINE, 280, 8704.0D, 2176.0D, EngineFuelGrade.MEDIUM, 0.92D);
        put(CoalFluids.DME, 210, 9216.0D, 2304.0D, EngineFuelGrade.HIGH, 1.15D);
        put(PetroFluids.NITROBENZENE, 360, 11_264.0D, 2816.0D, EngineFuelGrade.HIGH, 1.22D);
        // 火箭 ROCKET
        put(WoodFluids.TURPENTINE_NITRIC, 520, 17_408.0D, 4352.0D, EngineFuelGrade.ROCKET, 1.22D);
        put(WoodFluids.ETHALOX, 540, 18_432.0D, 4608.0D, EngineFuelGrade.ROCKET, 1.24D);
        put(PetroFluids.NITROBENZENE_MONOPROPELLANT, 550, 18_944.0D, 4736.0D, EngineFuelGrade.ROCKET, 1.26D);
        put(PetroFluids.ALUMINIZED_KEROSENE, 560, 18_944.0D, 4736.0D, EngineFuelGrade.ROCKET, 1.28D);
        put(WoodFluids.FURFURYL_NITRIC, 580, 19_456.0D, 4864.0D, EngineFuelGrade.ROCKET, 1.30D);
    }

    private static void put(BioFluids.Entry entry, int ticksPerMb, double capacity, double flowRate,
                            EngineFuelGrade grade, double quality) {
        put(entry.source().get(), ticksPerMb, capacity, flowRate, grade, quality);
    }

    private static void put(CoalFluids.Entry entry, int ticksPerMb, double capacity, double flowRate,
                            EngineFuelGrade grade, double quality) {
        put(entry.source().get(), ticksPerMb, capacity, flowRate, grade, quality);
    }

    private static void put(PetroFluids.Entry entry, int ticksPerMb, double capacity, double flowRate,
                            EngineFuelGrade grade, double quality) {
        put(entry.source().get(), ticksPerMb, capacity, flowRate, grade, quality);
    }

    private static void put(WoodFluids.Entry entry, int ticksPerMb, double capacity, double flowRate,
                            EngineFuelGrade grade, double quality) {
        put(entry.source().get(), ticksPerMb, capacity, flowRate, grade, quality);
    }

    private static void put(Fluid fluid, int ticksPerMb, double capacity, double flowRate,
                            EngineFuelGrade grade, double quality) {
        TABLE.put(fluid, new Fuel(ticksPerMb, capacity, flowRate, grade, quality));
    }

    private EngineFuels() {}
}
