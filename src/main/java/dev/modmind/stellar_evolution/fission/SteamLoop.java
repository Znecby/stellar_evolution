package dev.modmind.stellar_evolution.fission;

/**
 * 蒸汽循环账本（《核裂变线预规划蓝图》§4.5 / §13.1 F0）——<strong>纯数值、不依赖 Minecraft</strong>。
 *
 * <p>口径：反应堆<strong>不直接产热压差</strong>，而是把冷却剂的热量经中间换热变成
 * <strong>蒸汽 / 超临界蒸汽</strong>，再经蒸汽轮机接入热压差主网。本账本把「堆温 → 蒸汽等级」的换算固化，
 * 供 {@code steam_turbine_unit} 按等级出力（等级越高，单位蒸汽出功越多）。
 */
public final class SteamLoop {
    /** 饱和蒸汽起始温（K）。 */
    public static final double SATURATED_TEMP = 373.0D;
    /** 过热蒸汽起始温（K）。 */
    public static final double SUPERHEATED_TEMP = 500.0D;
    /** 超临界起始温（K，水的临界点附近）。 */
    public static final double SUPERCRITICAL_TEMP = 647.0D;

    /** 蒸汽等级（由低到高）。 */
    public enum SteamGrade {
        /** 未起蒸汽（堆温不足）。 */
        NONE(0.0D),
        /** 饱和蒸汽。 */
        SATURATED(1.0D),
        /** 过热蒸汽。 */
        SUPERHEATED(1.6D),
        /** 超临界蒸汽。 */
        SUPERCRITICAL(2.4D),
        /** 过超临界蒸汽：预留的高温档，汽轮机上限抬到 25000 RPM。 */
        ULTRA_SUPERCRITICAL(3.0D);

        private final double turbineFactor;

        SteamGrade(double turbineFactor) {
            this.turbineFactor = turbineFactor;
        }

        /** 该等级蒸汽相对饱和蒸汽的出力倍率（供轮机换算）。 */
        public double turbineFactor() {
            return turbineFactor;
        }
    }

    /** 按堆温判定蒸汽等级。 */
    public static SteamGrade gradeFor(double coreTemp) {
        if (coreTemp >= SUPERCRITICAL_TEMP) {
            return SteamGrade.SUPERCRITICAL;
        }
        if (coreTemp >= SUPERHEATED_TEMP) {
            return SteamGrade.SUPERHEATED;
        }
        if (coreTemp >= SATURATED_TEMP) {
            return SteamGrade.SATURATED;
        }
        return SteamGrade.NONE;
    }

    /**
     * 某冷却剂在某堆温下能产出的蒸汽量（比例量，{@code 0..1}）——按堆温相对熔毁温的归一化进度映射，
     * 超出饱和线才开始起蒸汽。
     *
     * @param coreTemp     堆芯温度（K）
     * @param meltdownTemp 该堆熔毁阈值（K）
     */
    public static double steamFraction(double coreTemp, double meltdownTemp) {
        if (coreTemp < SATURATED_TEMP) {
            return 0.0D;
        }
        double span = Math.max(1.0D, meltdownTemp - SATURATED_TEMP);
        double f = (coreTemp - SATURATED_TEMP) / span;
        return Math.max(0.0D, Math.min(1.0D, f));
    }

    private SteamLoop() {}
}
