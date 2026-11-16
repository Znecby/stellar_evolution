package dev.modmind.stellar_evolution.fission;

/**
 * 富集 / 分离功账本（《核裂变线预规划蓝图》§5.1 / §13.1 F0）——<strong>纯数值、不依赖 Minecraft</strong>。
 *
 * <p>口径：把「天然铀 → 富集铀 + 贫铀」的<strong>质量平衡</strong>与<strong>分离功（SWU）</strong>固化下来，
 * 供 {@code fission_centrifuge} 消耗热压差换取富集产品。
 *
 * <p>公式（标准同位素分离）：
 * <pre>
 *   质量平衡：F = P + T ，F·xf = P·xp + T·xt
 *   → P = F·(xf − xt) / (xp − xt) ，T = F − P
 *   价值函数：V(x) = (2x − 1)·ln(x / (1 − x))
 *   分离功：SWU = P·V(xp) + T·V(xt) − F·V(xf)
 * </pre>
 */
public final class EnrichmentCascade {
    /** 天然铀中 U-235 丰度。 */
    public static final double NATURAL_ASSAY = 0.0072D;
    /** 典型低浓铀（LEU）丰度。 */
    public static final double LEU_ASSAY = 0.0375D;
    /** 典型贫铀尾料丰度。 */
    public static final double TAILS_ASSAY = 0.0025D;

    /** 一次富集的结果：进料 / 产品 / 尾料（同单位）+ 分离功（SWU）。 */
    public record EnrichResult(double feed, double product, double tails, double swu) {}

    /**
     * 按进料量 / 丰度算产品与尾料。
     *
     * @param feedMass 进料量（mB 或件）；{@code ≤0} 或丰度非法 → 全零结果（不报错）
     */
    public static EnrichResult enrich(double feedMass, double feedAssay, double productAssay, double tailsAssay) {
        if (feedMass <= 0.0D || !valid(feedAssay) || !valid(productAssay) || !valid(tailsAssay)
                || productAssay <= tailsAssay || feedAssay <= tailsAssay || feedAssay >= productAssay) {
            return new EnrichResult(0.0D, 0.0D, 0.0D, 0.0D);
        }
        double product = feedMass * (feedAssay - tailsAssay) / (productAssay - tailsAssay);
        double tails = feedMass - product;
        double swu = product * value(productAssay) + tails * value(tailsAssay) - feedMass * value(feedAssay);
        return new EnrichResult(feedMass, product, tails, Math.max(0.0D, swu));
    }

    /** 用默认丰度（天然 → LEU → 尾料）做一次富集。 */
    public static EnrichResult enrich(double feedMass) {
        return enrich(feedMass, NATURAL_ASSAY, LEU_ASSAY, TAILS_ASSAY);
    }

    /** 同位素分离价值函数 {@code V(x)}，{@code x ∈ (0,1)}。 */
    public static double value(double assay) {
        double x = Math.max(1.0E-9D, Math.min(1.0D - 1.0E-9D, assay));
        return (2.0D * x - 1.0D) * Math.log(x / (1.0D - x));
    }

    private static boolean valid(double assay) {
        return assay > 0.0D && assay < 1.0D;
    }

    private EnrichmentCascade() {}
}
