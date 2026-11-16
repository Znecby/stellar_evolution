package dev.modmind.stellar_evolution.fission;

/**
 * 堆芯数值内核（《核裂变线预规划蓝图》§4 / §13.1 F0）——<strong>纯数值、不依赖 Minecraft</strong>，可直接 JUnit 单测。
 *
 * <p>一个 {@code ReactorCorePhysics} = 一条 {@link ReactorConfig} + 运行态。每 tick 调 {@link #step(double)}，
 * 产出「功率 / 温度 / 燃耗 / 同位素谱 / 熔毁进度」。
 *
 * <p><strong>反应性 ≠ 功率</strong>（2026-10-14 修正）：现实里临界堆的净反应性 ≈ 0，但中子通量（功率）维持在
 * 一个由控制棒决定的高位——旧实现令 {@code 功率 = 反应性}，于是「自稳 = 反应性→0 = 功率→0」，堆自稳后就不再出蒸汽。
 * 本版把两者分开：
 * <pre>
 *   反应性 reactivity() = kRod + 空泡系数 × 空泡度 + 温度系数 × (堆温 − T_ref)/1000 − 燃耗惩罚   // 决定「升降」
 *   目标通量 target     = 控制棒档位 × 反馈调制（夹在 [下限, 上限]）                          // 决定「功率水平」
 *   中子通量 flux       → 一阶跟随 target（抽棒爬坡 / 插棒衰减）
 *   功率 power          = flux × baseFlowRate
 * </pre>
 * 于是：<strong>自稳</strong> = 反应性回 0、通量停在<strong>非零</strong>水平；<strong>抽棒</strong> → 通量上升；
 * <strong>插棒</strong> → 目标通量 0 → 通量衰减（停机）；<strong>无燃料</strong> → 目标通量 0（不产热）。
 *
 * <p><strong>反馈系数 = 配置的灵魂</strong>（蓝图 §4.3）：
 * <ul>
 *   <li><strong>负空泡系数</strong>（PWR / HTGR）：升温 → 空泡↑ → 目标通量↓ → <strong>自稳</strong>；</li>
 *   <li><strong>正空泡系数</strong>（RBMK）：升温 → 空泡↑ → 目标通量↑ → <strong>正反馈失控</strong>；</li>
 *   <li><strong>控制棒尖端效应</strong>（RBMK）：深插入区抽棒的反应性斜率更大（{@code rodTipEffect}）。</li>
 * </ul>
 *
 * <p>反馈调制夹在 {@code [FEEDBACK_MIN, FEEDBACK_MAX]}：下限保证「断冷却 / 蒸汽罐满」时堆仍保有<strong>最小功率</strong>，
 * 热量排不掉 → 升温 → 熔毁（不再是「越热越熄火、永远到不了熔毁」）。
 */
public final class ReactorCorePhysics {
    /** 冷态参考温（K）。 */
    public static final double T_REF = 300.0D;
    /** 燃满时扣掉的反应性。 */
    public static final double BURNUP_PENALTY = 0.30D;
    /** 控制棒尖端效应的指数（&gt;1 → 深插入区斜率更大）。 */
    public static final double ROD_TIP_EXP = 1.6D;

    /** 功率 → 温升的系数（每 HP·t⁻¹）。 */
    private static final double HEAT_PER_POWER = 0.010D;
    /** 冷却系数（乘冷却剂流量与温差）。 */
    private static final double COOLING_COEFF = 0.050D;
    /** 每点功率带来的燃耗增量。 */
    private static final double BURNUP_PER_POWER = 1.0E-6D;
    /** 超温时每 tick 的熔毁进度增量系数。 */
    private static final double DAMAGE_RATE = 0.05D;
    /** 未超温时每 tick 的熔毁进度回退系数。 */
    private static final double DAMAGE_RECOVER = 0.02D;

    /** 通量跟随目标通量的一阶响应系数（每 tick）。 */
    private static final double FLUX_RESPONSE = 0.02D;
    /** 反馈调制下限（保证断冷时仍保有最小功率 → 能过热熔毁）。 */
    private static final double FEEDBACK_MIN = 0.10D;
    /** 反馈调制上限（防正空泡失控时数值溢出）。 */
    private static final double FEEDBACK_MAX = 2.0D;

    private ReactorConfig config;

    /** 控制棒深度：{@code 1} = 全插入（最小反应性），{@code 0} = 全抽出。 */
    private double controlRod = 1.0D;
    /** 堆芯温度（K）。 */
    private double coreTemp = T_REF;
    /** 燃耗：{@code 0..1}。 */
    private double burnup = 0.0D;
    /** 熔毁进度：{@code 0..1}；到 1 即熔毁。 */
    private double damage = 0.0D;
    /** 冷却剂流量：{@code 0..1}（1 = 满冷却）。 */
    private double coolantFlow = 1.0D;
    /** 中子通量（归一化功率水平，随目标通量升降）。 */
    private double flux = 0.0D;
    /** 是否装填了裂变燃料（无料 → 目标通量 0、不产热）。 */
    private boolean fueled = true;

    public ReactorCorePhysics(ReactorConfig config) {
        this.config = config;
    }

    /** 一步运行结果。 */
    public record StepResult(double reactivity, double flux, double power,
                             double coreTemp, double burnup, double damage, boolean meltedDown) {}

    /** 乏燃料同位素谱（材料轴；蓝图 §4.2 / §5）。 */
    public record IsotopeProfile(double plutonium, double u233, double minorActinides, double fissionProducts) {}

    /** 当前净反应性（无单位，可 &gt;1 表示失控）——决定通量的「升降」，不是功率本身。 */
    public double reactivity() {
        return rodGain()
                + config.voidCoefficient() * voidFraction()
                + config.tempCoefficient() * Math.max(0.0D, coreTemp - T_REF) / 1000.0D
                - BURNUP_PENALTY * burnup;
    }

    /** 控制棒带来的基础增益（{@code 0} = 全插入无增益；{@code 1} = 全抽出满增益）。 */
    private double rodGain() {
        double missing = Math.max(0.0D, 1.0D - controlRod);
        return config.rodTipEffect() ? Math.pow(missing, 1.0D / ROD_TIP_EXP) : missing;
    }

    /** 空泡度（{@code 0..1}）：随堆温相对熔毁阈升高而升高。 */
    private double voidFraction() {
        double span = Math.max(1.0D, config.meltdownTemp() - T_REF);
        return clamp01((coreTemp - T_REF) / span);
    }

    /** 推进 {@code dt} 个 tick，返回本步结果。 */
    public StepResult step(double dt) {
        if (damage >= 1.0D) {
            return new StepResult(0.0D, 0.0D, 0.0D, coreTemp, burnup, damage, true);
        }
        double reac = reactivity();

        // 目标通量 = 控制棒档位 × 反馈调制（反馈只作调制、且有下限，避免自稳时把功率压成 0）
        double demand = fueled ? rodGain() : 0.0D;
        double feedback = 1.0D
                + config.voidCoefficient() * voidFraction()
                + config.tempCoefficient() * Math.max(0.0D, coreTemp - T_REF) / 1000.0D;
        feedback = Math.max(FEEDBACK_MIN, Math.min(FEEDBACK_MAX, feedback));
        double target = Math.max(0.0D, demand * feedback);

        // 通量一阶跟随目标：抽棒 → 爬坡；插棒 / 无料 → 衰减
        flux = Math.max(0.0D, flux + (target - flux) * FLUX_RESPONSE * dt);

        double power = flux * config.baseFlowRate();
        double d = Math.max(0.0D, coreTemp - T_REF);
        double heatIn = power * HEAT_PER_POWER;
        double heatOut = coolantFlow * COOLING_COEFF * d;
        coreTemp = Math.max(T_REF, coreTemp + (heatIn - heatOut) * dt);

        burnup = Math.min(1.0D, burnup + power * BURNUP_PER_POWER * dt);

        if (coreTemp > config.meltdownTemp()) {
            damage = Math.min(1.0D, damage
                    + DAMAGE_RATE * ((coreTemp - config.meltdownTemp()) / config.meltdownTemp()) * dt);
        } else {
            damage = Math.max(0.0D, damage - DAMAGE_RECOVER * dt);
        }
        return new StepResult(reac, flux, power, coreTemp, burnup, damage, damage >= 1.0D);
    }

    /** 按谱段 / 燃料推算乏燃料同位素谱（比例量）：快谱产钚更多，钍系产 U-233。 */
    public IsotopeProfile isotopeProfile() {
        boolean fast = config.spectrum() == ReactorConfig.Spectrum.FAST;
        double pu = (fast ? 0.50D : 0.20D) * burnup;
        double u233 = (config.fuel() == ReactorConfig.FuelForm.THORIUM ? 0.60D : 0.0D) * burnup;
        double ma = (fast ? 0.15D : 0.05D) * burnup;
        return new IsotopeProfile(pu, u233, ma, burnup);
    }

    public ReactorConfig config() {
        return config;
    }

    /**
     * <strong>热切换</strong>配置（《核裂变线预规划蓝图》§3.4：燃料 × 慢化剂 → 堆型）——
     * 保留堆态（温度 / 燃耗 / 损伤 / 通量 / 控制棒），仅换参数。
     *
     * <p>用于「邻接慢化剂方块变化 → 堆型改变」：不重建内核，避免用「放/拆石墨」刷掉堆温与损伤。
     */
    public void setConfig(ReactorConfig config) {
        this.config = config;
    }

    public double controlRod() {
        return controlRod;
    }

    /** 设定控制棒深度，夹到 {@code [0,1]}。 */
    public void setControlRod(double rod) {
        this.controlRod = clamp01(rod);
    }

    /** 当前中子通量（归一化功率水平）。 */
    public double flux() {
        return flux;
    }

    public double coreTemp() {
        return coreTemp;
    }

    public double burnup() {
        return burnup;
    }

    public double damage() {
        return damage;
    }

    public boolean meltedDown() {
        return damage >= 1.0D;
    }

    public double coolantFlow() {
        return coolantFlow;
    }

    /** 设定冷却剂流量，夹到 {@code [0,1]}。 */
    public void setCoolantFlow(double flow) {
        this.coolantFlow = clamp01(flow);
    }

    /** 是否装填燃料（无料 → 目标通量 0、不产热）。 */
    public boolean fueled() {
        return fueled;
    }

    public void setFueled(boolean fueled) {
        this.fueled = fueled;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
