package dev.znecby.stellar_evolution.bio;

/**
 * 发酵动力学：<strong>纯数值账本</strong>（《生化线》B0 ③）。
 *
 * <p>刻意<strong>不依赖任何 Minecraft 类</strong>（照 {@code ThermalLoad} 的路子），所以能直接 JUnit 单测。
 * 它把「发酵罐的温度带 / 速率 / 产物偏向」这套纯数学固化下来，B1 的 `fermenter` 配方只需调用本类。
 *
 * <p>口径（《生化线》§5.2「发酵罐」）：<strong>温度带由「贴在机器上的供能铜块数量」决定</strong> ——
 * <ul>
 *   <li>{@link Band#ANAEROBIC 厌氧（低温带）}：1 块 → 偏 {@link Product#BIOGAS 沼气}；</li>
 *   <li>{@link Band#MESOPHILIC 中温带}：{@value #MESOPHILIC_COPPER} 块 → 偏 {@link Product#ETHANOL 乙醇}；</li>
 *   <li>{@link Band#THERMOPHILIC 高温带}：≥ {@value #THERMOPHILIC_COPPER} 块 → 偏 {@link Product#ACETIC_ACID 醋酸}。</li>
 * </ul>
 * <p><strong>带位与速率解耦</strong>：带位看铜块数量，快慢看网络热流率（{@code ThermalLoad#speedFor}）——
 * 于是「厌氧带」也能跑得很快（少贴铜块 + 大热流率）。{@link #bandFor(double)} 与
 * {@link #rateMultiplier(double)} 是「按热流率」的纯数值口径，保留给账本 / 单元测试。
 * 速率在<strong>中温带中心</strong>（{@value #MESOPHILIC_CENTER} HP/t）最高，越偏离越慢（有下限 {@value #MIN_RATE}，不会停）。
 */
public final class FermentationKinetics {
    /** 温度带：按「贴在机器上的供能铜块数量」分档（1 厌氧 / 2 中温 / ≥4 高温）。 */
    public enum Band {
        /** 厌氧 / 低温带。 */
        ANAEROBIC,
        /** 中温带。 */
        MESOPHILIC,
        /** 高温带。 */
        THERMOPHILIC
    }

    /** 发酵产物（顺序与 {@link Band} 刻意对齐，仅供阅读时对照）。 */
    public enum Product {
        /** 沼气（厌氧带主产）。 */
        BIOGAS,
        /** 乙醇（中温带主产）。 */
        ETHANOL,
        /** 醋酸（高温带主产）。 */
        ACETIC_ACID
    }

    /** 低于此热流率（HP/t）算厌氧（低温）带。 */
    public static final double ANAEROBIC_BELOW = 8.0D;
    /** 达到此热流率（HP/t）算高温带。 */
    public static final double THERMOPHILIC_FROM = 20.0D;
    /** 机器上贴到这么多「供能铜块」算中温带。 */
    public static final int MESOPHILIC_COPPER = 2;
    /** 机器上贴到这么多「供能铜块」算高温带。 */
    public static final int THERMOPHILIC_COPPER = 4;
    /** 中温带中心（速率最高点，HP/t）。 */
    public static final double MESOPHILIC_CENTER = 14.0D;
    /** 每偏离中心 1 HP/t，速率掉的比例。 */
    public static final double RATE_FALLOFF_PER_HP = 0.04D;
    /** 速率下限：再偏离也不会停（只是很慢）。 */
    public static final double MIN_RATE = 0.25D;

    /** 该网络热流率落在哪个温度带（纯数值口径，供账本 / 测试用）。 */
    public static Band bandFor(double heatFlowRate) {
        if (heatFlowRate < ANAEROBIC_BELOW) {
            return Band.ANAEROBIC;
        }
        if (heatFlowRate < THERMOPHILIC_FROM) {
            return Band.MESOPHILIC;
        }
        return Band.THERMOPHILIC;
    }

    /**
     * 温度带<strong>按「贴在机器上的供能铜块数量」分档</strong>（机器的实际口径）：
     * 1 块 → 厌氧、2 块 → 中温、≥{@value #THERMOPHILIC_COPPER} 块 → 高温。
     * <p>与「热流率」解耦：热流率只管快慢，铜块数量才决定带位 —— 于是低带也能跑得很快。
     */
    public static Band bandForCopper(int copperBlocks) {
        if (copperBlocks >= THERMOPHILIC_COPPER) {
            return Band.THERMOPHILIC;
        }
        if (copperBlocks >= MESOPHILIC_COPPER) {
            return Band.MESOPHILIC;
        }
        return Band.ANAEROBIC;
    }

    /** 速率倍率：中温带中心 = 1.0，越偏离越小，夹在 [{@value #MIN_RATE}, 1.0]。 */
    public static double rateMultiplier(double heatFlowRate) {
        double rate = 1.0D - RATE_FALLOFF_PER_HP * Math.abs(heatFlowRate - MESOPHILIC_CENTER);
        return Math.max(MIN_RATE, Math.min(1.0D, rate));
    }

    /** 实际发酵周期（刻）= 基准刻数 / 速率倍率。 */
    public static double cycleTicks(double baseTicks, double heatFlowRate) {
        return baseTicks / rateMultiplier(heatFlowRate);
    }

    /** 该温度带的主产物。 */
    public static Product primaryProduct(Band band) {
        return switch (band) {
            case ANAEROBIC -> Product.BIOGAS;
            case MESOPHILIC -> Product.ETHANOL;
            case THERMOPHILIC -> Product.ACETIC_ACID;
        };
    }

    /** 该温度带下某产物的收率：主产 1.0、相邻带 0.35、最远带 0.1。 */
    public static double yieldOf(Band band, Product product) {
        return switch (band) {
            case ANAEROBIC -> switch (product) {
                case BIOGAS -> 1.0D;
                case ETHANOL -> 0.35D;
                case ACETIC_ACID -> 0.1D;
            };
            case MESOPHILIC -> switch (product) {
                case BIOGAS -> 0.35D;
                case ETHANOL -> 1.0D;
                case ACETIC_ACID -> 0.35D;
            };
            case THERMOPHILIC -> switch (product) {
                case BIOGAS -> 0.1D;
                case ETHANOL -> 0.35D;
                case ACETIC_ACID -> 1.0D;
            };
        };
    }

    /** 某产物的实际产量（四舍五入；主产恰好 = 基准量）。 */
    public static int amount(int baseAmount, Band band, Product product) {
        return (int) Math.round(baseAmount * yieldOf(band, product));
    }

    private FermentationKinetics() {}
}
