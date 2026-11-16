package dev.znecby.stellar_evolution.fission;

/**
 * 燃料棒谱系（17 根 = 3 族 × 低/中/高 + 6 根拓展棒 + 2 根离火棒）：<strong>纯数值 + 物品 id</strong>，不依赖 Minecraft 运行时。
 *
 * <p>三族的运行姿态：
 * <ul>
 *   <li><strong>铀</strong>：效率基线，通量 50–60% 平台；压力上限 300 bar，汽轮机封顶 12,000 RPM。</li>
 *   <li><strong>钚（MOX）</strong>：60–80% 超功率区（η 1.0→1.2），压力上限 500 bar，可开 16k/20k；
 *       芯限温更低（2750℃），乏燃料更脏。</li>
 *   <li><strong>钍</strong>：0 MW，只当中子吸收体与增殖体；必须与铀/MOX 点火棒混装，
 *       燃尽后经后处理得到 U-233。</li>
 * </ul>
 *
 * <p>每根棒另有 4 项 {@code traits}（谱 / 角色 / 空泡 / 形态），只写进 tooltip；
 * 混装的后果由 {@link ReactorCoreModel#mixingPenalty(java.util.List)} 算成隐藏点数 P。
 *
 * <p>寿命口径：60% 通量下的满棒时长（2026-10-26 起耐久再减至 1/4）：
 * 铀 ≈0.36 / 0.48 / 0.73h，MOX ≈0.63 / 0.83 / 0.83h，钍 ≈0.42 / 0.83 / 1.67h。
 */
public enum FuelSpec {
    // ---- 铀系（热谱基线） ------------------------------------------------------
    U_LOW("fuel_rod_leu", Family.URANIUM, "leu", 11, 1, 8,
            60, 3_000, 100, 2_800.0D, 300.0D, 0.0D, 0, "spent_fuel",
            Spectrum.THERMAL, Role.POWER, VoidReactivity.NEGATIVE, Form.OXIDE),
    U_MID("fuel_rod_meu", Family.URANIUM, "meu", 40, 2, 3,
            150, 6_000, 150, 2_800.0D, 300.0D, 0.0D, 0, "spent_fuel_u_mid",
            Spectrum.THERMAL, Role.POWER, VoidReactivity.NEGATIVE, Form.OXIDE),
    U_HIGH("fuel_rod_heu", Family.URANIUM, "heu", 89, 8, 1,
            450, 12_000, 200, 2_800.0D, 300.0D, 0.0D, 0, "spent_fuel_u_high",
            Spectrum.THERMAL, Role.POWER, VoidReactivity.NEGATIVE, Form.OXIDE),

    // ---- 钚系 MOX（快谱/超临界） ----------------------------------------------
    MOX_LOW("mox_fuel_rod", Family.MOX, "mox_low", 5, 0, 0,
            250, 4_500, 100, 2_750.0D, 500.0D, 0.0D, 0, "spent_fuel_mox_low",
            Spectrum.THERMAL, Role.HIGH_BURNUP, VoidReactivity.NEGATIVE, Form.OXIDE),
    MOX_MID("mox_fuel_rod_mid", Family.MOX, "mox_mid", 15, 0, 0,
            500, 6_000, 100, 2_750.0D, 500.0D, 0.0D, 0, "spent_fuel_mox_mid",
            Spectrum.EPITHERMAL, Role.HIGH_BURNUP, VoidReactivity.NEGATIVE, Form.OXIDE),
    MOX_HIGH("mox_fuel_rod_high", Family.MOX, "mox_high", 30, 0, 0,
            800, 6_000, 100, 2_750.0D, 500.0D, 0.0D, 0, "spent_fuel_mox_high",
            Spectrum.EPITHERMAL, Role.HIGH_BURNUP, VoidReactivity.NEGATIVE, Form.OXIDE),

    // ---- 钍系（0 MW 增殖） -----------------------------------------------------
    TH_LOW("thorium_fuel_rod", Family.THORIUM, "th_low", 25, 0, 0,
            0, 3_000, 100, 3_300.0D, 300.0D, 0.3D, 1, "spent_fuel_th_low",
            Spectrum.THERMAL, Role.BREEDER, VoidReactivity.NEGATIVE, Form.OXIDE),
    TH_MID("thorium_fuel_rod_mid", Family.THORIUM, "th_mid", 50, 0, 0,
            0, 6_000, 100, 3_300.0D, 300.0D, 0.6D, 2, "spent_fuel_th_mid",
            Spectrum.THERMAL, Role.BREEDER, VoidReactivity.NEGATIVE, Form.OXIDE),
    TH_HIGH("thorium_fuel_rod_high", Family.THORIUM, "th_high", 90, 0, 0,
            0, 12_000, 100, 3_300.0D, 300.0D, 1.0D, 4, "spent_fuel_th_high",
            Spectrum.THERMAL, Role.BREEDER, VoidReactivity.NEGATIVE, Form.OXIDE),

    // ---- 拓展棒（§7.8.1）：每根只改变一种运行方式 ---------------------------------
    /** U-233 钍种子棒：钍循环闭合，热谱点火，燃尽后可回收 U-233。 */
    U233_SEED("u233_seed_rod", Family.URANIUM, "u233_seed", 12, 0, 0,
            120, 8_000, 100, 2_900.0D, 320.0D, 0.0D, 1, "spent_u233_seed",
            Spectrum.THERMAL, Role.BREEDER, VoidReactivity.NEGATIVE, Form.OXIDE),
    /** MA 嬗变棒：烧次锕系，快谱 + 正空泡。 */
    MA_TRANSMUTE("ma_transmutation_rod", Family.MOX, "ma_transmutation", 8, 0, 0,
            200, 4_000, 80, 2_700.0D, 500.0D, 0.0D, 0, "spent_ma_rod",
            Spectrum.FAST, Role.TRANSMUTATION, VoidReactivity.POSITIVE, Form.OXIDE),
    /** Pu-240/242 惰性基体棒：高燃耗、低剩余钚。 */
    PU_INERT("pu_inert_matrix_rod", Family.MOX, "pu_inert", 6, 0, 0,
            300, 9_000, 60, 2_750.0D, 500.0D, 0.0D, 0, "spent_pu_inert",
            Spectrum.EPITHERMAL, Role.HIGH_BURNUP, VoidReactivity.NEGATIVE, Form.OXIDE),
    /** 可燃毒物棒：0 MW，只吸收中子（碳化硼 / 氧化钆 / 氧化钐）。 */
    BURNABLE_POISON("burnable_poison_rod", Family.URANIUM, "burnable_poison", 0, 0, 0,
            0, 2_400, 100, 3_300.0D, 0.0D, 0.5D, 0, "spent_poison_rod",
            Spectrum.THERMAL, Role.POISON, VoidReactivity.NEGATIVE, Form.OXIDE),
    /** UC 高功率棒：碳化物导热好，功率密度与芯限温都更高。 */
    UC_HIGH_POWER("uc_high_power_rod", Family.URANIUM, "uc_high_power", 30, 0, 0,
            700, 5_000, 150, 3_100.0D, 400.0D, 0.0D, 0, "spent_uc_rod",
            Spectrum.FAST, Role.POWER, VoidReactivity.NEGATIVE, Form.CARBIDE),
    /** U-Zr 金属棒：快堆增殖，金属燃料 + 正空泡。 */
    UZR_METAL("uzr_metal_rod", Family.MOX, "uzr_metal", 10, 0, 0,
            350, 8_000, 80, 2_900.0D, 500.0D, 0.0D, 0, "spent_uzr_rod",
            Spectrum.FAST, Role.BREEDER, VoidReactivity.POSITIVE, Form.METAL),

    // ---- 残骸线：离火燃料棒（§7.12） ---------------------------------------------
    /** 不稳定离火燃料棒：新棒通量爬升 ×3.0，随耐久回落；高通量省燃料、低通量费燃料。 */
    UNSTABLE_EMBER("unstable_ember_fuel_rod", Family.EMBER, "unstable_ember", 100, 0, 0,
            900, 6_000, 100, 3_200.0D, 600.0D, 0.0D, 0, "spent_ember_fuel_rod",
            Spectrum.EXOTIC, Role.POWER, VoidReactivity.POSITIVE, Form.CERAMIC, 3.0D),
    /** 稳定离火燃料棒：R0 1.2，没有起步猛冲，寿命翻倍、功率略低。 */
    STABLE_EMBER("stable_ember_fuel_rod", Family.EMBER, "stable_ember", 100, 0, 0,
            700, 12_000, 60, 3_200.0D, 600.0D, 0.0D, 0, "spent_ember_fuel_rod",
            Spectrum.EXOTIC, Role.POWER, VoidReactivity.POSITIVE, Form.METAL, 1.2D);

    /** 燃料族：决定通量曲线、压力档与堆内角色。 */
    public enum Family {
        URANIUM,
        MOX,
        THORIUM,
        /** 残骸线的离火燃料：效率曲线沿用铀系，但 tooltip 单列。 */
        EMBER
    }

    /** 中子谱：混装时热谱 + 快谱会互相拖累。 */
    public enum Spectrum {
        THERMAL,
        EPITHERMAL,
        FAST,
        /** 离火棒的奇异谱：与热谱混装会明显拖累。 */
        EXOTIC
    }

    /** 堆内角色：发电 / 增殖 / 嬗变 / 毒物 / 高燃耗。 */
    public enum Role {
        POWER,
        BREEDER,
        TRANSMUTATION,
        POISON,
        HIGH_BURNUP
    }

    /** 空泡反馈：失水时反应性下降 / 上升 / 不变。 */
    public enum VoidReactivity {
        NEGATIVE,
        POSITIVE,
        NEUTRAL
    }

    /** 燃料形态：决定导热与包壳温度。 */
    public enum Form {
        OXIDE,
        METAL,
        CARBIDE,
        NITRIDE,
        /** 离火棒的陶瓷基体。 */
        CERAMIC
    }

    private final String rodId;
    private final Family family;
    private final String key;
    private final int fissilePercent;
    private final int u235PerPellet;
    private final int u238PerPellet;
    private final int thermalPowerMw;
    private final int maxDamage;
    private final int wearPerCycle;
    private final double coreLimitC;
    private final double pressureCapBar;
    private final double breederSink;
    private final int u233Yield;
    private final String spentId;
    private final Spectrum spectrum;
    private final Role role;
    private final VoidReactivity voidReactivity;
    private final Form form;
    /** 离火棒的通量爬升基准倍率 R0（1.0 = 普通棒，无起步冲刺）。 */
    private final double rampR0;

    FuelSpec(String rodId, Family family, String key, int fissilePercent,
             int u235PerPellet, int u238PerPellet,
             int thermalPowerMw, int maxDamage, int wearPerCycle,
             double coreLimitC, double pressureCapBar,
             double breederSink, int u233Yield, String spentId,
             Spectrum spectrum, Role role, VoidReactivity voidReactivity, Form form) {
        this(rodId, family, key, fissilePercent, u235PerPellet, u238PerPellet,
                thermalPowerMw, maxDamage, wearPerCycle, coreLimitC, pressureCapBar,
                breederSink, u233Yield, spentId, spectrum, role, voidReactivity, form, 1.0D);
    }

    FuelSpec(String rodId, Family family, String key, int fissilePercent,
             int u235PerPellet, int u238PerPellet,
             int thermalPowerMw, int maxDamage, int wearPerCycle,
             double coreLimitC, double pressureCapBar,
             double breederSink, int u233Yield, String spentId,
             Spectrum spectrum, Role role, VoidReactivity voidReactivity, Form form,
             double rampR0) {
        this.rodId = rodId;
        this.family = family;
        this.key = key;
        this.fissilePercent = fissilePercent;
        this.u235PerPellet = u235PerPellet;
        this.u238PerPellet = u238PerPellet;
        this.thermalPowerMw = thermalPowerMw;
        this.maxDamage = maxDamage;
        this.wearPerCycle = wearPerCycle;
        this.coreLimitC = coreLimitC;
        this.pressureCapBar = pressureCapBar;
        this.breederSink = breederSink;
        this.u233Yield = u233Yield;
        this.spentId = spentId;
        this.spectrum = spectrum;
        this.role = role;
        this.voidReactivity = voidReactivity;
        this.form = form;
        this.rampR0 = rampR0;
    }

    /** 燃料棒物品 id（如 {@code fuel_rod_leu}）。 */
    public String rodId() {
        return rodId;
    }

    public Family family() {
        return family;
    }

    /** 短 key：配方 / 语言键用（如 {@code leu} / {@code mox_low} / {@code th_high}）。 */
    public String key() {
        return key;
    }

    /** 显示用的“浓度”：铀-235 富集度 / PuO₂ 含量 / ThO₂ 装量（%）。 */
    public int fissilePercent() {
        return fissilePercent;
    }

    public int u235PerPellet() {
        return u235PerPellet;
    }

    public int u238PerPellet() {
        return u238PerPellet;
    }

    /** 设计热功率（MW）；钍棒为 0。 */
    public int thermalPowerMw() {
        return thermalPowerMw;
    }

    /** 满耐久（点）。 */
    public int maxDamage() {
        return maxDamage;
    }

    /** 每个反应堆周期（600t）磨损点数。 */
    public int wearPerCycle() {
        return wearPerCycle;
    }

    /** 满棒周期数（每周期 600t）。 */
    public int cycles() {
        return maxDamage / wearPerCycle;
    }

    /** 燃料芯熔毁温度（℃）。 */
    public double coreLimitC() {
        return coreLimitC;
    }

    /** 该族允许的蒸汽压力上限（bar）。 */
    public double pressureCapBar() {
        return pressureCapBar;
    }

    /** 作为增殖棒时占用的“装载比”当量（0 = 不是增殖棒）。 */
    public double breederSink() {
        return breederSink;
    }

    /** 燃尽后（后处理）可回收的 U-233 数量；0 = 不产 U-233。 */
    public int u233Yield() {
        return u233Yield;
    }

    /** 燃尽后对应的乏燃料物品 id。 */
    public String spentId() {
        return spentId;
    }

    /** 中子谱。 */
    public Spectrum spectrum() {
        return spectrum;
    }

    /** 堆内角色。 */
    public Role role() {
        return role;
    }

    /** 空泡反馈。 */
    public VoidReactivity voidReactivity() {
        return voidReactivity;
    }

    /** 燃料形态。 */
    public Form form() {
        return form;
    }

    /** 离火棒的通量爬升基准倍率 R0；普通棒为 1.0。 */
    public double rampR0() {
        return rampR0;
    }

    /** 是否能自己维持链式反应（铀 / MOX；可燃毒物棒只吸收中子）。 */
    public boolean isSeed() {
        return family != Family.THORIUM && role != Role.POISON;
    }

    /** 是否只当中子吸收体（钍棒 / 可燃毒物棒）。 */
    public boolean isBreeder() {
        return !isSeed();
    }

    /** 按物品 id 反查；未知返回 {@code null}。 */
    public static FuelSpec byRodId(String rodId) {
        for (FuelSpec spec : values()) {
            if (spec.rodId.equals(rodId)) {
                return spec;
            }
        }
        return null;
    }
}
