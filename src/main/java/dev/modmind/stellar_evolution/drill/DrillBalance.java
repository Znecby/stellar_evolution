package dev.modmind.stellar_evolution.drill;

/**
 * 开采钻「精准采集」模式的平衡公式（<strong>纯数值，无 Minecraft 依赖</strong>，便于单测）。
 *
 * <p>锚点（2026-11-09）：3×3 区块 = 2.0× 需要 2,048 HP/t、热阻 8（占用 16,384 DP，正好 2 台普通柴油燃烧引擎）。
 * <ul>
 *   <li>1×1 与 3×3 的 1.0× 热流都是 2,048 HP/t；1×1 热阻减半为 4（8,192 DP，1 台柴油引擎）；</li>
 *   <li>更大范围：热流按 {@code N/3} 线性增长、热阻按 {@code (N/3)²} 增长；</li>
 *   <li>周期：3×3 = 260 t（旧 200 t × 1.3），按 {@code N/3} 线性变长；</li>
 *   <li>满速上限 = 该范围 1× 热流的 4 倍（不是固定值）。</li>
 * </ul>
 *
 * <p><strong>范围破坏（AREA）</strong>：以 5×5 方块为基准，热流固定 {@link #AREA_BASE_FLOW_RATE} HP/t；
 * 热阻<strong>线性</strong> {@link #AREA_RESISTANCE} × (N/5)（63×63 仅 12,902.4 DP）；
 * 周期按<strong>面积</strong> {@link #AREA_CYCLE_TICKS} × (N/5)²；满速上限固定 {@link #AREA_MAX_USEFUL_FLOW_RATE} HP/t。
 */
public final class DrillBalance {
    /** 精准采集：范围（区块边长）最小 / 最大。 */
    public static final int ORE_MIN = 1;
    public static final int ORE_MAX = 15;
    /** 基准范围（区块边长）：该范围 1.0× 需要 {@link #ORE_BASE_FLOW_RATE} HP/t。 */
    public static final int ORE_REFERENCE = 3;
    /** 基准热流率（HP/t）：3×3 与 1×1 的 1.0× 需求。 */
    public static final double ORE_BASE_FLOW_RATE = 2_048.0D;
    /** 基准热阻：3×3 占用 8 × 2,048 = 16,384 DP；1×1 减半为 4。 */
    public static final double ORE_RESISTANCE = 8.0D;
    /** 基准周期（刻/层）：3×3 为 260 t；1×1 约 1/3。 */
    public static final double ORE_CYCLE_TICKS = 260.0D;
    /** 满速上限相对 1× 热流的倍率。 */
    public static final double MAX_SPEED_MULTIPLIER = 4.0D;

    // ---- 范围破坏（AREA）：以 5×5 方块为基准，热阻线性、周期按面积（2026-11-09）
    /** 范围破坏：基准边长（方块）。 */
    public static final int AREA_REFERENCE = 5;
    /** 范围破坏：1.0× 热流（HP/t），沿用旧值。 */
    public static final double AREA_BASE_FLOW_RATE = 256.0D;
    /** 范围破坏：基准热阻（5×5 时占用 = 4 × 256 = 1,024 DP）。 */
    public static final double AREA_RESISTANCE = 4.0D;
    /** 范围破坏：基准周期（刻/层）。 */
    public static final double AREA_CYCLE_TICKS = 200.0D;
    /** 范围破坏：满速上限（HP/t）= 4 倍基准。 */
    public static final double AREA_MAX_USEFUL_FLOW_RATE = 1_024.0D;

    /** 范围相对基准 3×3 的边长倍率。 */
    public static double scale(int range) {
        return range / (double) ORE_REFERENCE;
    }

    /** 1.0× 热流（HP/t）：1×1 与 3×3 同为 2,048，更大范围按 N/3 线性增长。 */
    public static double baseFlow(int range) {
        return ORE_BASE_FLOW_RATE * Math.max(1.0D, scale(range));
    }

    /** 热阻：1×1 = 4；N≥3 = 8 × (N/3)²。 */
    public static double resistance(int range) {
        double scale = scale(range);
        return range <= 1 ? ORE_RESISTANCE / 2.0D : ORE_RESISTANCE * scale * scale;
    }

    /** 每层周期（刻）：3×3 = 260 t，按 N/3 线性变长。 */
    public static double cycleTicks(int range) {
        return ORE_CYCLE_TICKS * scale(range);
    }

    /** 满速上限（HP/t）= 该范围 1× 热流 × 4。 */
    public static double maxUsefulFlow(int range) {
        return baseFlow(range) * MAX_SPEED_MULTIPLIER;
    }

    /** 该范围 1.0× 的差压占用（DP）= 热流 × 热阻。 */
    public static double dpOccupancy(int range) {
        return baseFlow(range) * resistance(range);
    }

    /** 范围破坏：范围相对 5×5 的边长倍率。 */
    public static double areaScale(int range) {
        return range / (double) AREA_REFERENCE;
    }

    /** 范围破坏热阻：<strong>线性</strong> 4 × (N/5)（不再用面积平方，避免大范围 DP 爆炸）。 */
    public static double areaResistance(int range) {
        return AREA_RESISTANCE * areaScale(range);
    }

    /** 范围破坏每层周期（刻）：按<strong>面积</strong> 200 × (N/5)²，挖得越宽越慢。 */
    public static double areaCycleTicks(int range) {
        double scale = areaScale(range);
        return AREA_CYCLE_TICKS * scale * scale;
    }

    /** 范围破坏 1.0× 的差压占用（DP）= 热流 × 热阻。 */
    public static double areaDpOccupancy(int range) {
        return AREA_BASE_FLOW_RATE * areaResistance(range);
    }

    private DrillBalance() {}
}
