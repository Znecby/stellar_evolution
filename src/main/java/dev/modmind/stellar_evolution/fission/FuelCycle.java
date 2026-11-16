package dev.modmind.stellar_evolution.fission;

/**
 * 燃料循环账本（《核裂变线预规划蓝图》§5 / §13.2 F1）——<strong>纯数值、不依赖 Minecraft</strong>，可直接 JUnit 单测。
 *
 * <p>把「铀矿 → 铀精矿 → 六氟化铀 → 富集 / 贫铀 → 燃料棒」这条<strong>前端链</strong>的换算比例，
 * 以及后端「乏燃料 → 后处理」的守恒关系固化下来，供各台裂变线机器按同一份口径取值。
 *
 * <p>离心富集的<strong>质量平衡 / 分离功</strong>另有专职账本 {@link EnrichmentCascade}，本类只做转换比例。
 */
public final class FuelCycle {
    /** 酸浸收率：1 份矿石 → 多少份铀精矿。 */
    public static final double LEACH_YIELD = 0.85D;
    /** 氟化：1 份铀精矿 → 多少 mB 六氟化铀（UF6）。 */
    public static final double UF6_PER_YELLOWCAKE = 500.0D;
    /** 制棒：1 份富集铀 → 多少根燃料棒。 */
    public static final double RODS_PER_ENRICHED = 1.0D;
    /** 每根燃料棒消耗的包壳（件）。 */
    public static final double CASING_PER_ROD = 1.0D;

    /** 后处理结果：回收的可裂变物质 + 高放废料（同单位，二者之和 = 进料）。 */
    public record ReprocessResult(double recovered, double waste) {}

    /** 酸浸：矿石 → 铀精矿（件）。{@code ore ≤ 0} → 0。 */
    public static double leachOutput(double ore) {
        return ore > 0.0D ? ore * LEACH_YIELD : 0.0D;
    }

    /** 氟化：铀精矿 → 六氟化铀（mB）。{@code yellowcake ≤ 0} → 0。 */
    public static double fluorinate(double yellowcake) {
        return yellowcake > 0.0D ? yellowcake * UF6_PER_YELLOWCAKE : 0.0D;
    }

    /** 制棒：富集铀 → 燃料棒（根）。{@code enriched ≤ 0} → 0。 */
    public static double rodsFromEnriched(double enriched) {
        return enriched > 0.0D ? enriched * RODS_PER_ENRICHED : 0.0D;
    }

    /**
     * 后处理：乏燃料 → 可回收物 + 高放废料（<strong>守恒</strong>：二者之和 = 进料）。
     *
     * @param spentFuel 乏燃料量；{@code ≤ 0} → 全零
     * @param recovery  回收率，夹到 {@code [0,1]}
     */
    public static ReprocessResult reprocess(double spentFuel, double recovery) {
        if (spentFuel <= 0.0D) {
            return new ReprocessResult(0.0D, 0.0D);
        }
        double r = Math.max(0.0D, Math.min(1.0D, recovery));
        double recovered = spentFuel * r;
        return new ReprocessResult(recovered, spentFuel - recovered);
    }

    private FuelCycle() {}
}
