package dev.znecby.stellar_evolution.ore;

/**
 * 选冶线的<strong>纯数值账本</strong>：把「倍率」写死在一处，供单测与文档对照。
 *
 * <p>口径（2026-10-25 定稿）：
 * <ul>
 *   <li>1 矿石 → 3 粗矿（破碎机）；无粗矿的矿（下界金矿 / 残骸 / 非金属）直接按 1 个原料计；</li>
 *   <li>1 粗矿 + 水 1000 + 糖液 250 → 富精矿浆 3000；结晶机 <strong>1500 浆 → 1 精砂</strong>，即 1 粗矿 = 2 砂；</li>
 *   <li>金属砂：直还原 1 砂 → 1 锭（+石油焦 1）；酸浸 1 砂 → 2 粉 → 2 锭；</li>
 *   <li>非金属砂：酸浸 1 砂 → <strong>4 粉</strong>（8× 档）；粉 / 砂在热室 1:1 压回宝石；</li>
 *   <li>无粗矿的矿：1 原料 = 2 砂 → 直出 2 产物 / 酸浸 4 产物（非金属 8 产物）；</li>
 *   <li>铀：精砂酸浸 1 砂 → 2 铀粉（+ 铀渣），2 铀锭 → 1 黄饼；完整线 6 黄饼/矿、基础线 3。</li>
 * </ul>
 * 本类<strong>不依赖任何 Minecraft 类型</strong>，配 {@code OreBalanceTest}。
 */
public final class OreBalance {
    /** 1 矿石 → 粗矿数量（破碎机）。 */
    public static final int RAW_PER_ORE = 3;
    /** 富精矿浆总量（mB）/ 粗矿。 */
    public static final int SLURRY_PER_RAW = 3000;
    /** 结晶机每 1 精砂消耗的浆量（mB）。 */
    public static final int SLURRY_PER_SAND = 1500;
    /** 结晶机每 1 精砂额外消耗的过氧化氢（mB）。 */
    public static final int HYDROGEN_PEROXIDE_PER_SAND = 750;
    /** 1 粗矿 → 精砂数量。 */
    public static final int SAND_PER_RAW = SLURRY_PER_RAW / SLURRY_PER_SAND;
    /** 粗矿 / 无粗矿原料 → 粉的直粉碎数量。 */
    public static final int DUST_PER_RAW = 2;
    /** 酸浸：1 精砂 → 粉数量（金属 / 残骸 / 下界金矿）。 */
    public static final int DUST_PER_SAND_ACID = 2;
    /** 酸浸：1 精砂 → 粉数量（非金属，2026-10-25 定稿 = 8× 档）。 */
    public static final int DUST_PER_SAND_ACID_NONMETAL = 4;
    /** 直还原：1 精砂 → 锭数量（+ 石油焦 1）。 */
    public static final int INGOT_PER_SAND_DIRECT = 1;
    /** 1 粉 → 锭数量（熔炉）。 */
    public static final int INGOT_PER_DUST = 1;
    /** 铀：多少铀锭换 1 黄饼。 */
    public static final int URANIUM_INGOTS_PER_YELLOWCAKE = 2;

    /** 有粗矿的矿：粉线每矿锭数（3 粗矿 × 2 粉）。 */
    public static int crushedIngotsPerOre() {
        return RAW_PER_ORE * DUST_PER_RAW * INGOT_PER_DUST;
    }

    /** 有粗矿的矿：砂直还原每矿锭数（3 粗矿 × 2 砂）。 */
    public static int directIngotsPerOre() {
        return RAW_PER_ORE * SAND_PER_RAW * INGOT_PER_SAND_DIRECT;
    }

    /** 有粗矿的矿：酸浸每矿锭数（3 粗矿 × 2 砂 × 2 粉）。 */
    public static int acidIngotsPerOre() {
        return RAW_PER_ORE * SAND_PER_RAW * DUST_PER_SAND_ACID * INGOT_PER_DUST;
    }

    /** 无粗矿的矿：每矿砂数。 */
    public static int sandPerOrelessOre() {
        return SAND_PER_RAW;
    }

    /** 无粗矿的矿：粉线 / 直出每矿产物数。 */
    public static int directProductsPerOrelessOre() {
        return SAND_PER_RAW * INGOT_PER_SAND_DIRECT;
    }

    /** 无粗矿的矿：酸浸每矿产物数（下界金矿 / 残骸）。 */
    public static int acidProductsPerOrelessOre() {
        return SAND_PER_RAW * DUST_PER_SAND_ACID * INGOT_PER_DUST;
    }

    /** 非金属矿：酸浸每矿产物数（8× 档）。 */
    public static int acidProductsPerNonMetalOre() {
        return SAND_PER_RAW * DUST_PER_SAND_ACID_NONMETAL * INGOT_PER_DUST;
    }

    /** 铀：完整线每矿黄饼数。 */
    public static int yellowcakePerUraniumOreFull() {
        return acidIngotsPerOre() / URANIUM_INGOTS_PER_YELLOWCAKE;
    }

    /** 铀：只粉碎 / 直还原每矿黄饼数。 */
    public static int yellowcakePerUraniumOreBasic() {
        return directIngotsPerOre() / URANIUM_INGOTS_PER_YELLOWCAKE;
    }

    /** 糖液消耗（mB）/ 粗矿（分解仪配比 0.25 桶）。 */
    public static final int SUGAR_LIQUOR_PER_RAW = 250;
    /** 水消耗（mB）/ 粗矿（分解仪配比 1 桶）。 */
    public static final int WATER_PER_RAW = 1000;
    /** 尾矿砂砾 / 粗矿。 */
    public static final int GRAVEL_PER_RAW = 1;
    /** B0 二段浸出：每 1 份副产粉消耗的浸出渣数（双副产线 2 渣 → 1 + 1）。 */
    public static final int SECOND_STAGE_RESIDUE_PER_PRODUCT = 2;
    /** B0 二段浸出：单副产线（石墨）1 渣 → 1 粉。 */
    public static final int SECOND_STAGE_SINGLE_RESIDUE = 1;

    private OreBalance() {}
}
