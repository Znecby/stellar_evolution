package dev.modmind.stellar_evolution.steam;

import dev.modmind.stellar_evolution.fission.SteamLoop;

/**
 * 蒸汽网络里流动的<strong>蒸汽数据</strong>（纯数值、不依赖 Minecraft，可直接写单元测试）。
 *
 * <p>蒸汽 / 超临界蒸汽<strong>流体</strong>本身保留（JEI 看配方用），但反应堆不再输出流体，
 * 改为向蒸汽网络写这个数据包：<strong>每 tick 的量（mB/t）+ 温度（℃）+ 压力（bar）</strong>。
 * 汽轮机按这组指数决定转速与出力。
 *
 * <p>等级口径（“达到标准显示超临界蒸汽”：温度与压力都要过线）：
 * <ul>
 *   <li>{@code < 100℃} → 无蒸汽；</li>
 *   <li>{@code ≥ 227℃} → 过热蒸汽；</li>
 *   <li>{@code ≥ 374℃ 且 ≥ 221 bar} → <strong>超临界蒸汽</strong>；</li>
 *   <li>{@code > 600℃ 且 > 500 bar} → <strong>过超临界蒸汽</strong>（只有聚变仪能到）。</li>
 * </ul>
 *
 * <p><strong>过超临界靠数值门槛而不是来源标记</strong>：裂变堆的 MOX 压力硬顶正好 500 bar（严格大于才达标），
 * 永远够不到；聚变仪压力可到 900 bar、工质温度封顶 700 ℃，所以只有它能进这一档。
 */
public record SteamState(double amount, double temperatureC, double pressureBar) {
    /** 无蒸汽。 */
    public static final SteamState NONE = new SteamState(0.0D, 20.0D, 0.0D);

    /** 饱和蒸汽起始温（℃）。 */
    public static final double SATURATED_C = 100.0D;
    /** 过热蒸汽起始温（℃）。 */
    public static final double SUPERHEATED_C = 227.0D;
    /** 超临界起始温（℃）。 */
    public static final double SUPERCRITICAL_C = 374.0D;
    /** 超临界起始压力（bar，≈22.1 MPa）。 */
    public static final double SUPERCRITICAL_BAR = 221.0D;
    /** 过超临界起始温（℃）——真实超超临界机组的主蒸汽量级，配合下面的 500 bar 门槛仍只有聚变能到。 */
    public static final double ULTRA_C = 600.0D;
    /** 过超临界起始压力（bar）——严格大于，MOX 的 500 bar 硬顶刚好够不到。 */
    public static final double ULTRA_BAR = 500.0D;
    /** 压力对 DP 的加成上限（bar）：900 bar 时 +180%（过超临界档）。 */
    public static final double PRESSURE_CAP_BAR = 900.0D;

    /** 有没有蒸汽。 */
    public boolean isEmpty() {
        return amount <= 0.0D;
    }

    /** 蒸汽等级（按温度 + 压力判定；过超临界优先）。 */
    public SteamLoop.SteamGrade grade() {
        if (isEmpty() || temperatureC < SATURATED_C) {
            return SteamLoop.SteamGrade.NONE;
        }
        if (temperatureC > ULTRA_C && pressureBar > ULTRA_BAR) {
            return SteamLoop.SteamGrade.ULTRA_SUPERCRITICAL;
        }
        if (temperatureC >= SUPERCRITICAL_C && pressureBar >= SUPERCRITICAL_BAR) {
            return SteamLoop.SteamGrade.SUPERCRITICAL;
        }
        if (temperatureC >= SUPERHEATED_C) {
            return SteamLoop.SteamGrade.SUPERHEATED;
        }
        return SteamLoop.SteamGrade.SATURATED;
    }

    /** 是否超临界（含过超临界）。 */
    public boolean supercritical() {
        return grade() == SteamLoop.SteamGrade.SUPERCRITICAL
                || grade() == SteamLoop.SteamGrade.ULTRA_SUPERCRITICAL;
    }

    /** 压力对 DP 的额外倍率：最高 +180%（900 bar）；压力对 HP 不加成。 */
    public double dpMultiplier() {
        double pressure = Math.max(0.0D, Math.min(PRESSURE_CAP_BAR, pressureBar));
        return 1.0D + pressure / 500.0D;
    }

    /** 按量加权混合两股蒸汽（温度 / 压力按 mB/t 加权；无蒸汽时退化为另一股）。 */
    public SteamState blend(SteamState other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        double total = amount + other.amount;
        double temperature = (temperatureC * amount + other.temperatureC * other.amount) / total;
        double pressure = (pressureBar * amount + other.pressureBar * other.amount) / total;
        return new SteamState(total, temperature, pressure);
    }
}
