package dev.modmind.stellar_evolution.fission;

/**
 * 乏燃料余热账本（纯数值、不依赖 Minecraft，可直接 JUnit）。
 *
 * <p>反应堆产出乏燃料时写入初始温度；只有乏燃料池能降温。
 * 冷却速度按水池周围水源数分档：<strong>8 个水源达到满速</strong>，1 个水源为 1/8 速。
 * 满速 2 温度/秒（即 1000 温度约 8.3 分钟，是早期草案 16.7 分钟的一半）。
 */
public final class SpentFuelHeat {
    /** 余热上限。 */
    public static final float MAX = 1000.0F;
    /** 「自燃」阈值：高于它，物品在背包里会着火 + 凋零。 */
    public static final float HOT = 200.0F;
    /** 8 个水源时的满速冷却（温度/秒）。 */
    public static final float BASE_COOLING_PER_SECOND = 2.0F;

    /** 各档乏燃料的初始余热。 */
    public static float initial(FuelSpec spec) {
        return switch (spec) {
            case U_LOW -> 400.0F;
            case U_MID -> 600.0F;
            case U_HIGH -> 800.0F;
            case MOX_LOW -> 600.0F;
            case MOX_MID -> 800.0F;
            case MOX_HIGH -> 1000.0F;
            case TH_LOW -> 300.0F;
            case TH_MID -> 500.0F;
            case TH_HIGH -> 700.0F;
            case U233_SEED -> 500.0F;
            case MA_TRANSMUTE -> 900.0F;
            case PU_INERT -> 800.0F;
            case BURNABLE_POISON -> 200.0F;
            case UC_HIGH_POWER -> 1000.0F;
            case UZR_METAL -> 900.0F;
            case UNSTABLE_EMBER -> 700.0F;
            case STABLE_EMBER -> 600.0F;
        };
    }

    /** 燃尽 / 破壳时的初始余热：档位基础值 + 通量附加（0–200），封顶 {@link #MAX}。 */
    public static float initialWithFlux(FuelSpec spec, double flux) {
        float bonus = (float) Math.max(0.0D, Math.min(200.0D, flux * 2.0D));
        return Math.min(MAX, initial(spec) + bonus);
    }

    /** 当前水源数下的冷却速度（温度/秒）：1 个水源为 1/8 速，8 个及以上满速。 */
    public static float coolingPerSecond(int waterSources) {
        int n = Math.max(0, Math.min(8, waterSources));
        return BASE_COOLING_PER_SECOND * n / 8.0F;
    }

    /** 冷却 {@code seconds} 秒后的温度；不会低于 0。 */
    public static float cool(float heat, int waterSources, float seconds) {
        if (heat <= 0.0F || seconds <= 0.0F) {
            return Math.max(0.0F, heat);
        }
        return Math.max(0.0F, heat - coolingPerSecond(waterSources) * seconds);
    }

    /** 是否处于「自燃」状态。 */
    public static boolean isHot(float heat) {
        return heat > HOT;
    }

    private SpentFuelHeat() {}
}
