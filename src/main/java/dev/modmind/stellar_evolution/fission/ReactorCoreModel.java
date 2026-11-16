package dev.modmind.stellar_evolution.fission;

import java.util.EnumSet;
import java.util.List;

/**
 * 反应堆<strong>棒组数值模型</strong>（2026-10-24 重做，纯数值、不依赖 Minecraft，可直接 JUnit）。
 *
 * <p>输入箱里的棒分三类：
 * <ul>
 *   <li><strong>燃料棒 F</strong>：产热；每根独立算核心温度；</li>
 *   <li><strong>石墨棒 G</strong>：只降低目标通量与通量增长速度（不再给功率加成）；</li>
 *   <li><strong>反射棒 R</strong>：提高目标通量、单棒功率、蒸汽压力与压力上限。</li>
 * </ul>
 *
 * <p>核心量：
 * <pre>
 *   装载比 r      = F / (F + G)
 *   目标通量 F*   = min(100, 100 × r × (1 + 0.05R))     // 到 100% 即燃料熔毁
 *   效率 η(F)     = F≤50: F/50；50–60: 1；60–100: 1 − 0.8(F−60)/40
 *   燃耗 c(F)     = F≤50: 1；50–60: 1+0.015(F−50)；60–100: 1.15+0.04(F−60)
 *   单棒功率      = 额定 × η(F) × (1 + 0.075R)
 *   壳温目标      = 100 + 总功率MW × 1.0（封顶 1800）
 *   核心温度      = 壳温 + 100 × (额定/60) × (F/100) × (1+0.1R) / 冷却裕度
 *   包壳温度      = 壳温 + (核心温度 − 壳温) × 0.5
 * </pre>
 *
 * <p>失效线：包壳 &gt;2000℃ → 该棒破壳报废；燃料芯 &gt;2800℃（MOX 2750 / 钍 3300）→ 整堆熔毁；
 * 壳温 &gt;1500℃ 累积损毁，≥1800℃ 瞬间熔毁。
 */
public final class ReactorCoreModel {
    // ---- 温度 / 损伤 ----
    /** 堆壳硬上限（℃）：达到即整堆熔毁。 */
    public static final double SHELL_MAX_C = 1800.0D;
    /** 堆壳开始累积损毁的温度（℃）。 */
    public static final double DAMAGE_START_C = 1500.0D;
    /** 包壳破裂温度（℃）：该燃料棒报废。 */
    public static final double CLADDING_MAX_C = 2000.0D;
    /** 铀燃料芯熔毁温度（℃）。 */
    public static final double CORE_LIMIT_URANIUM_C = 2800.0D;
    /** MOX 燃料芯熔毁温度（℃）。 */
    public static final double CORE_LIMIT_MOX_C = 2750.0D;
    /** 钍燃料芯熔毁温度（℃）。 */
    public static final double CORE_LIMIT_THORIUM_C = 3300.0D;
    /** 堆壳每秒最大损毁（10%/s）。 */
    public static final double MAX_DAMAGE_PER_SECOND = 0.10D;
    /** 堆壳恢复到正常温度后的损毁回退（0.5%/s）。 */
    public static final double DAMAGE_RECOVERY_PER_SECOND = 0.005D;
    /** 低于该温度才开始回退损毁（℃）。 */
    public static final double DAMAGE_RECOVERY_BELOW_C = 800.0D;

    // ---- 通量 ----
    /** 基础通量爬升速度（%/t）。 */
    public static final double FLUX_RAMP_PER_TICK = 0.02D;
    /** 反射棒对通量爬升的加速（每根 +20%）。 */
    public static final double FLUX_RAMP_PER_REFLECTOR = 0.20D;
    /** 石墨对通量爬升的减速（以装载比计：0.5+0.5r）。 */

    // ---- 反射棒 ----
    /** 每根反射棒的单棒功率加成。 */
    public static final double POWER_PER_REFLECTOR = 0.075D;
    /** 每根反射棒的核心温度加成。 */
    public static final double CORE_HEAT_PER_REFLECTOR = 0.10D;
    /** 每根反射棒对目标通量的加成。 */
    public static final double FLUX_TARGET_PER_REFLECTOR = 0.05D;
    /** 每根反射棒提供的蒸汽压力（bar）。 */
    public static final double PRESSURE_PER_REFLECTOR = 50.0D;
    /** 反应堆基础压力上限（bar）。 */
    public static final double BASE_PRESSURE_CAP = 300.0D;
    /** 每根反射棒提高的压力上限（bar）。 */
    public static final double PRESSURE_CAP_PER_REFLECTOR = 50.0D;
    /** 功率换算压力的系数（bar/MW）。 */
    public static final double PRESSURE_PER_MW = 0.5D;

    /** 额外耗水：温度超过 400℃ 后按 ((T−400)/400)^1.5 × 0.5 叠加。 */
    public static final double EXTRA_WATER_START_C = 400.0D;
    public static final double EXTRA_WATER_SPAN_C = 400.0D;
    public static final double EXTRA_WATER_FACTOR = 0.5D;

    /** 装载比 r = F/(F+G)；没有燃料时返回 0。 */
    public static double loadRatio(int fuelRods, int graphiteRods) {
        int total = Math.max(0, fuelRods) + Math.max(0, graphiteRods);
        return total <= 0 ? 0.0D : (double) Math.max(0, fuelRods) / total;
    }

    /** 目标通量（%）：装载比 × 反射加成，封顶 100（到 100 即熔毁）。 */
    public static double targetFlux(int fuelRods, int graphiteRods, int reflectorRods) {
        double ratio = loadRatio(fuelRods, graphiteRods);
        double target = 100.0D * ratio * (1.0D + FLUX_TARGET_PER_REFLECTOR * Math.max(0, reflectorRods));
        return Math.min(100.0D, Math.max(0.0D, target));
    }

    /** 通量每 tick 的爬升速度（%）：石墨越多（r 越低）越慢，反射棒越多越快。 */
    public static double fluxRampPerTick(int fuelRods, int graphiteRods, int reflectorRods) {
        double ratio = loadRatio(fuelRods, graphiteRods);
        double graphiteSlow = 0.5D + 0.5D * ratio;
        return FLUX_RAMP_PER_TICK
                * (1.0D + FLUX_RAMP_PER_REFLECTOR * Math.max(0, reflectorRods))
                * graphiteSlow;
    }

    /** 效率 η(F)：0–50 线性到 1；50–60 平台；60–100 线性降到 0.2。 */
    public static double efficiency(double flux) {
        double f = clamp(flux, 0.0D, 100.0D);
        if (f <= 50.0D) {
            return f / 50.0D;
        }
        if (f <= 60.0D) {
            return 1.0D;
        }
        return Math.max(0.2D, 1.0D - 0.8D * (f - 60.0D) / 40.0D);
    }

    /** 燃耗系数 c(F)：50% 以下固定 1；50–60 缓涨；60–100 快速涨到 2.75。 */
    public static double burnFactor(double flux) {
        double f = clamp(flux, 0.0D, 100.0D);
        if (f <= 50.0D) {
            return 1.0D;
        }
        if (f <= 60.0D) {
            return 1.0D + 0.015D * (f - 50.0D);
        }
        return 1.15D + 0.04D * (f - 60.0D);
    }

    /** 单棒实际功率（MW）。 */
    public static double rodPower(double ratedMw, double flux, int reflectorRods) {
        return ratedMw * efficiency(flux) * reflectorPowerFactor(reflectorRods);
    }

    /** 反射棒功率倍率。 */
    public static double reflectorPowerFactor(int reflectorRods) {
        return 1.0D + POWER_PER_REFLECTOR * Math.max(0, reflectorRods);
    }

    /** 堆壳温度目标（℃）。 */
    public static double shellTarget(double totalPowerMw) {
        return Math.min(SHELL_MAX_C, 100.0D + Math.max(0.0D, totalPowerMw));
    }

    /**
     * 对外报出的<strong>蒸汽温度</strong>上限（℃）：与堆壳温度<strong>解耦</strong>——真实超超临界机组的主蒸汽
     * 也就 600~620℃，堆壳的 1000~1800℃ 不可能变成同温度的水蒸气（水在 2000℃ 以上已热解离）。
     * <p>只影响 {@code SteamState} 的温度字段（档位 / Jade 显示）；损毁、耗水、芯温仍用堆壳温度原值。
     */
    public static final double STEAM_MAX_C = 600.0D;

    /** 对外报出的蒸汽温度（℃）：堆壳温度钳到 {@link #STEAM_MAX_C}。 */
    public static double steamTemperature(double shellTempC) {
        return Math.min(STEAM_MAX_C, Math.max(0.0D, shellTempC));
    }

    /** 额外耗水倍率（叠加在基础耗水上）。 */
    public static double extraWaterFactor(double shellTempC) {
        if (shellTempC <= EXTRA_WATER_START_C) {
            return 0.0D;
        }
        double x = (shellTempC - EXTRA_WATER_START_C) / EXTRA_WATER_SPAN_C;
        return EXTRA_WATER_FACTOR * Math.pow(x, 1.5D);
    }

    /** 核心温度（℃）；冷却裕度 {@code 2} = 满水，{@code 0.5} = 缺水。 */
    public static double coreTemp(double shellTempC, double ratedMw, double flux, int reflectorRods,
                                  double coolingMargin) {
        double margin = Math.max(0.5D, Math.min(2.0D, coolingMargin));
        double delta = 100.0D
                * (ratedMw / 60.0D)
                * (clamp(flux, 0.0D, 100.0D) / 100.0D)
                * (1.0D + CORE_HEAT_PER_REFLECTOR * Math.max(0, reflectorRods))
                / margin;
        return shellTempC + delta;
    }

    /** 包壳温度（℃）：核心与壳的中间值。 */
    public static double claddingTemp(double shellTempC, double coreTempC) {
        return shellTempC + (coreTempC - shellTempC) * 0.5D;
    }

    /** 堆壳损毁速率（每 tick，0–0.005）；{@code 0.005} = 10%/s。壳温 ≥1800 由调用方直接判熔毁。 */
    public static double damagePerTick(double shellTempC) {
        if (shellTempC <= DAMAGE_START_C) {
            return 0.0D;
        }
        double x = Math.min(1.0D, (shellTempC - DAMAGE_START_C) / (SHELL_MAX_C - DAMAGE_START_C));
        return MAX_DAMAGE_PER_SECOND / 20.0D * Math.pow(x, 1.5D);
    }

    /** 壳温 ≥1800 → 瞬间熔毁。 */
    public static boolean instantMeltdown(double shellTempC) {
        return shellTempC >= SHELL_MAX_C;
    }

    /** 蒸汽压力（bar）：功率 + 反射棒，受压力上限约束。 */
    public static double pressureBar(double totalPowerMw, int reflectorRods) {
        int reflectors = Math.max(0, reflectorRods);
        double cap = BASE_PRESSURE_CAP + PRESSURE_CAP_PER_REFLECTOR * reflectors;
        double raw = 1.0D + Math.max(0.0D, totalPowerMw) * PRESSURE_PER_MW
                + PRESSURE_PER_REFLECTOR * reflectors;
        return Math.min(cap, raw);
    }

    /** 燃料芯上限（℃）：铀 / MOX / 钍。 */
    public static final double CORE_LIMIT_URANIUM = CORE_LIMIT_URANIUM_C;
    public static final double CORE_LIMIT_MOX = CORE_LIMIT_MOX_C;
    public static final double CORE_LIMIT_THORIUM = CORE_LIMIT_THORIUM_C;

    // ---- 九根燃料棒：按族参数化的曲线（2026-10-25） --------------------------

    /** 目标通量（%）：点火棒 / (点火棒 + 石墨 + 增殖棒当量) × 反射加成，封顶 100。 */
    public static double targetFlux(int seedRods, int graphiteRods, int reflectorRods, double breederSink) {
        double seed = Math.max(0, seedRods);
        double denominator = seed + Math.max(0, graphiteRods) + Math.max(0.0D, breederSink);
        if (denominator <= 0.0D) {
            return 0.0D;
        }
        double target = 100.0D * seed / denominator
                * (1.0D + FLUX_TARGET_PER_REFLECTOR * Math.max(0, reflectorRods));
        return Math.min(100.0D, Math.max(0.0D, target));
    }

    /** 通量爬升速度（%/t）：增殖棒像石墨一样稀释装载比、拖慢爬升。 */
    public static double fluxRampPerTick(int seedRods, double breederSink, int graphiteRods, int reflectorRods) {
        double seed = Math.max(0, seedRods);
        double denominator = seed + Math.max(0, graphiteRods) + Math.max(0.0D, breederSink);
        double ratio = denominator <= 0.0D ? 0.0D : seed / denominator;
        double graphiteSlow = 0.5D + 0.5D * ratio;
        return FLUX_RAMP_PER_TICK
                * (1.0D + FLUX_RAMP_PER_REFLECTOR * Math.max(0, reflectorRods))
                * graphiteSlow;
    }

    /** 按燃料族取效率：铀 50–60% 平台；MOX 60–80% 超功率（1.0→1.2）；钍不发热。 */
    public static double efficiency(FuelSpec spec, double flux) {
        if (spec == null || spec.family() == FuelSpec.Family.THORIUM) {
            return 0.0D;
        }
        double f = clamp(flux, 0.0D, 100.0D);
        if (spec.family() == FuelSpec.Family.MOX) {
            if (f <= 50.0D) {
                return f / 50.0D;
            }
            if (f <= 60.0D) {
                return 1.0D;
            }
            if (f <= 80.0D) {
                return 1.0D + 0.2D * (f - 60.0D) / 20.0D;
            }
            return Math.max(0.2D, 1.2D - 1.0D * (f - 80.0D) / 20.0D);
        }
        return efficiency(flux);
    }

    /** 按燃料族取燃耗系数：MOX 在 60–80% 涨得比铀慢（允许超功率），80% 以上快速报废。 */
    public static double burnFactor(FuelSpec spec, double flux) {
        if (spec == null || spec.family() == FuelSpec.Family.THORIUM) {
            return 1.0D;
        }
        double f = clamp(flux, 0.0D, 100.0D);
        if (spec.family() == FuelSpec.Family.MOX) {
            if (f <= 60.0D) {
                return 1.0D;
            }
            if (f <= 80.0D) {
                return 1.0D + 0.03D * (f - 60.0D);
            }
            return 1.6D + 0.0575D * (f - 80.0D);
        }
        return burnFactor(flux);
    }

    // ---- 离火棒（§7.12）：起步冲刺 + 通量反向燃烧 --------------------------------

    /** 通量爬升参考点：燃烧倍率在 50% 通量处为 1.0。 */
    public static final double EMBER_BURN_REFERENCE_FLUX = 50.0D;
    /** 高通量省燃料的下限。 */
    public static final double EMBER_BURN_MIN = 0.5D;
    /** 低通量费燃料的上限。 */
    public static final double EMBER_BURN_MAX = 3.0D;

    /**
     * 离火棒的通量爬升倍率：{@code R = 1 + (R0 − 1) × d}，{@code d = 剩余耐久 / 满耐久}。
     * <p>普通棒（R0 = 1）恒为 1；新棒最猛，随燃烧逐渐回落。
     */
    public static double emberRampFactor(FuelSpec spec, double durabilityFraction) {
        if (spec == null || spec.rampR0() <= 1.0D) {
            return 1.0D;
        }
        return 1.0D + (spec.rampR0() - 1.0D) * clamp(durabilityFraction, 0.0D, 1.0D);
    }

    /**
     * 离火棒的燃烧速度倍率：{@code clamp(50 / max(flux, 10), 0.5, 3.0)}。
     * <p>只对「不稳定」档（R0 ≥ 2）生效；稳定档按普通燃耗走。
     */
    public static double emberBurnFactor(FuelSpec spec, double flux) {
        if (spec == null || spec.rampR0() < 2.0D || flux <= 0.0D) {
            return 1.0D;
        }
        return clamp(EMBER_BURN_REFERENCE_FLUX / Math.max(flux, 10.0D), EMBER_BURN_MIN, EMBER_BURN_MAX);
    }

    /** 单棒实际功率（MW）：钍棒恒为 0。 */
    public static double rodPower(FuelSpec spec, double flux, int reflectorRods) {
        if (spec == null || spec.thermalPowerMw() <= 0) {
            return 0.0D;
        }
        return spec.thermalPowerMw() * efficiency(spec, flux) * reflectorPowerFactor(reflectorRods);
    }

    /**
     * 蒸汽压力（bar）：功率 + 反射棒，受<strong>燃料族压力上限</strong>约束。
     * <p>{@code familyCap ≤ 0} 时退回旧口径（300 + 50R）；否则以 {@code familyCap} 为硬顶（反射棒只能帮忙接近它）。
     */
    public static double pressureBar(double totalPowerMw, int reflectorRods, double familyCap) {
        int reflectors = Math.max(0, reflectorRods);
        // 燃料族上限是硬顶（铀 300 / MOX 500）；不传族上限时退回旧口径 300 + 50R。
        double cap = familyCap > 0.0D ? familyCap : 300.0D + PRESSURE_CAP_PER_REFLECTOR * reflectors;
        double raw = 1.0D + Math.max(0.0D, totalPowerMw) * PRESSURE_PER_MW
                + PRESSURE_PER_REFLECTOR * reflectors;
        return Math.min(cap, raw);
    }

    // ---- 混装惩罚（§7.8.2：只给属性、不给结论；数值不显示给玩家） --------------------

    /** 每点 P 的目标通量倍率增量。 */
    public static final double MIX_FLUX_PER_POINT = 0.10D;
    /** 每点 P 的效率削减。 */
    public static final double MIX_EFFICIENCY_PER_POINT = 0.05D;
    /** 每点 P 的堆壳温度加成（℃）。 */
    public static final double MIX_SHELL_TEMP_PER_POINT = 20.0D;
    /** 每点 P 的额外堆壳损毁（每秒）。 */
    public static final double MIX_DAMAGE_PER_SECOND_PER_POINT = 0.0002D;
    /** P ≥ 该值时通量目标额外飙升（每点 +5 个百分点）。 */
    public static final int MIX_SURGE_FROM_P = 6;
    /** 每点超出 {@link #MIX_SURGE_FROM_P} 的通量目标加成（百分点）。 */
    public static final double MIX_SURGE_PER_POINT = 5.0D;
    /** P ≥ 该值时自动停堆（通量目标归零）。 */
    public static final int MIX_AUTO_SHUTDOWN_P = 8;

    /**
     * 混装惩罚（隐藏）。
     *
     * @param points            不兼容点数 P
     * @param fluxFactor        目标通量倍率
     * @param efficiencyFactor  效率倍率
     * @param shellTempBonusC   堆壳温度加成
     * @param damagePerTickBonus 每 tick 额外损毁
     * @param fluxSurgePoints   目标通量额外百分点（P ≥ 6）
     * @param autoShutdown      P ≥ 8：自动停堆
     */
    public record MixingPenalty(int points, double fluxFactor, double efficiencyFactor,
                                double shellTempBonusC, double damagePerTickBonus,
                                double fluxSurgePoints, boolean autoShutdown) {
        /** 无惩罚。 */
        public static final MixingPenalty NONE = new MixingPenalty(0, 1.0D, 1.0D, 0.0D, 0.0D, 0.0D, false);
    }

    /**
     * 按装载的燃料棒集合算混装点数 P。
     *
     * <p>只按「哪些属性同时出现」计一次，不按棒数累加；同谱同角色、同族不同富集度天然为 0。
     * 毒物棒由调用方放进吸收体槽，不影响这里的口径。
     */
    public static MixingPenalty mixingPenalty(List<FuelSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return MixingPenalty.NONE;
        }
        EnumSet<FuelSpec.Spectrum> spectra = EnumSet.noneOf(FuelSpec.Spectrum.class);
        EnumSet<FuelSpec.Role> roles = EnumSet.noneOf(FuelSpec.Role.class);
        EnumSet<FuelSpec.VoidReactivity> voids = EnumSet.noneOf(FuelSpec.VoidReactivity.class);
        EnumSet<FuelSpec.Form> forms = EnumSet.noneOf(FuelSpec.Form.class);
        for (FuelSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            spectra.add(spec.spectrum());
            roles.add(spec.role());
            voids.add(spec.voidReactivity());
            forms.add(spec.form());
        }
        int points = 0;
        if (spectra.contains(FuelSpec.Spectrum.THERMAL) && spectra.contains(FuelSpec.Spectrum.FAST)) {
            points += 2;
        }
        // 离火棒（EXOTIC）与热谱混装：通量明显飙升，必须单独堆
        if (spectra.contains(FuelSpec.Spectrum.THERMAL) && spectra.contains(FuelSpec.Spectrum.EXOTIC)) {
            points += 3;
        }
        if (roles.contains(FuelSpec.Role.POWER) && roles.contains(FuelSpec.Role.TRANSMUTATION)) {
            points += 2;
        }
        if (roles.contains(FuelSpec.Role.POWER) && roles.contains(FuelSpec.Role.POISON)) {
            points += 1;
        }
        if (roles.contains(FuelSpec.Role.BREEDER) && roles.contains(FuelSpec.Role.HIGH_BURNUP)) {
            points += 2;
        }
        if (forms.contains(FuelSpec.Form.OXIDE)
                && (forms.contains(FuelSpec.Form.METAL) || forms.contains(FuelSpec.Form.CARBIDE))) {
            points += 1;
        }
        if (voids.contains(FuelSpec.VoidReactivity.POSITIVE)
                && voids.contains(FuelSpec.VoidReactivity.NEGATIVE)) {
            points += 1;
        }
        return ofPoints(points);
    }

    /** 按点数 P 组装惩罚。 */
    public static MixingPenalty ofPoints(int points) {
        int p = Math.max(0, points);
        double surge = p >= MIX_SURGE_FROM_P ? MIX_SURGE_PER_POINT * (p - MIX_SURGE_FROM_P + 1) : 0.0D;
        return new MixingPenalty(
                p,
                1.0D + MIX_FLUX_PER_POINT * p,
                Math.max(0.0D, 1.0D - MIX_EFFICIENCY_PER_POINT * p),
                MIX_SHELL_TEMP_PER_POINT * p,
                MIX_DAMAGE_PER_SECOND_PER_POINT * p / 20.0D,
                surge,
                p >= MIX_AUTO_SHUTDOWN_P);
    }

    // ---- 硼酸化学停堆（§7.8.3） --------------------------------------------------

    /** 硼浓度达到该值 → 目标通量归零（停堆）。 */
    public static final double BORON_SHUTDOWN_PPM = 3_000.0D;
    /** 每次超温注入消耗的硼酸流体（mB）。 */
    public static final int BORON_ACID_PER_INJECTION_MB = 1_000;
    /** 1 桶（1000 mB）硼酸的当量浓度：一次注入直接到停堆线。 */
    public static final int BORON_PER_BUCKET = 3_000;
    /** 堆壳温度超过该值 → 从机械网络取硼酸注入（2026-11-08）。 */
    public static final double BORON_ACID_TRIGGER_C = 1_500.0D;
    /** 堆壳温度回落到该值以下 → 重新武装注入（避免同一超温事件反复消耗）。 */
    public static final double BORON_ACID_RESET_C = 1_400.0D;
    /** 自然稀释速度（ppm/t）；换水 / 冲洗的简化实现。 */
    public static final double BORON_DECAY_PER_TICK = 0.25D;

    /** 硼浓度对目标通量的倍率：{@code max(0, 1 − ppm / 3000)}。 */
    public static double boronFactor(double boronPpm) {
        return clamp(1.0D - Math.max(0.0D, boronPpm) / BORON_SHUTDOWN_PPM, 0.0D, 1.0D);
    }

    // ---- 氙毒（§7.8.4，默认关闭） ------------------------------------------------

    /** 碘半衰期（t）。 */
    public static final double IODINE_HALF_LIFE_TICKS = 800.0D;
    /** 氙半衰期（t）。 */
    public static final double XENON_HALF_LIFE_TICKS = 1_800.0D;
    /** 每 1% 通量、每 tick 的碘产额。 */
    public static final double IODINE_YIELD_PER_FLUX = 0.0005D;
    /** 氙的燃耗系数（每 1% 通量）。 */
    public static final double XENON_BURN_PER_FLUX = 0.00002D;
    /** 氙的反应性权重。 */
    public static final double XENON_REACTIVITY = 0.015D;
    /** 氙最多压掉的目标通量比例。 */
    public static final double XENON_MAX_REACTIVITY = 0.5D;

    /** 碘 / 氙两个标量。 */
    public record XenonState(double iodine, double xenon) {
        public static final XenonState EMPTY = new XenonState(0.0D, 0.0D);
    }

    /** 按中子谱取碘产额：热谱 1.0 / 超热 0.6 / 快谱 0.2。 */
    public static double iodineYield(FuelSpec.Spectrum spectrum) {
        if (spectrum == null) {
            return 1.0D;
        }
        return switch (spectrum) {
            case THERMAL -> 1.0D;
            case EPITHERMAL -> 0.6D;
            case FAST -> 0.2D;
            case EXOTIC -> 0.1D;
        };
    }

    /**
     * 推进一个 tick 的碘 / 氙：{@code dI = a·flux·yield − λI·I}，
     * {@code dXe = λI·I − λXe·Xe − c·flux·Xe}。停堆（flux = 0）时碘继续变氙、氙不被烧掉 → 碘坑。
     */
    public static XenonState xenonStep(XenonState state, double flux, double yieldFactor) {
        double i0 = state == null ? 0.0D : Math.max(0.0D, state.iodine());
        double x0 = state == null ? 0.0D : Math.max(0.0D, state.xenon());
        double f = clamp(flux, 0.0D, 100.0D);
        double lambdaI = Math.log(2.0D) / IODINE_HALF_LIFE_TICKS;
        double lambdaXe = Math.log(2.0D) / XENON_HALF_LIFE_TICKS;
        double iodine = i0 + IODINE_YIELD_PER_FLUX * f * Math.max(0.0D, yieldFactor) - lambdaI * i0;
        double xenon = x0 + lambdaI * i0 - lambdaXe * x0 - XENON_BURN_PER_FLUX * f * x0;
        return new XenonState(Math.max(0.0D, iodine), Math.max(0.0D, xenon));
    }

    /** 氙对目标通量的倍率：{@code 1 − min(0.5, 0.015 × Xe)}。 */
    public static double xenonFactor(double xenon) {
        return 1.0D - Math.min(XENON_MAX_REACTIVITY, XENON_REACTIVITY * Math.max(0.0D, xenon));
    }

    // ---- 组合：基础目标通量 × 混装 × 硼 × 氙 ------------------------------------

    /** 把混装 / 硼 / 氙三项折进目标通量；P ≥ 8 或硼满浓度时归零。 */
    public static double effectiveTargetFlux(double baseTarget, double boronPpm, double xenon,
                                             MixingPenalty penalty) {
        MixingPenalty mix = penalty == null ? MixingPenalty.NONE : penalty;
        if (mix.autoShutdown()) {
            return 0.0D;
        }
        double target = Math.max(0.0D, baseTarget) * mix.fluxFactor() + mix.fluxSurgePoints();
        target *= boronFactor(boronPpm) * xenonFactor(xenon);
        return clamp(target, 0.0D, 100.0D);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ReactorCoreModel() {}
}
