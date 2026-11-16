package dev.znecby.stellar_evolution.fission;

/**
 * 反应堆<strong>配置记录</strong>（《核裂变线预规划蓝图》§3 / §13.1 F0）。
 *
 * <p>描述「这台堆是什么型号」——把现实里的堆型差异（<strong>冷却剂 · 中子谱 · 循环模式 · 燃料形态</strong>）
 * 连同<strong>反馈系数</strong>与基准数值一起固化，交给 {@link ReactorCorePhysics} 运行。
 * <strong>纯数值、不依赖 Minecraft</strong>，可直接 JUnit 单测。
 *
 * <p>口径（蓝图 §3.5）：<strong>型号 = 配置组合</strong>——同一条 {@code ReactorConfig} 描述
 * PWR / RBMK / HTGR / MSR / FBR …，而不是各写一台机器。
 */
public record ReactorConfig(
        String id,
        Coolant coolant,
        Spectrum spectrum,
        LoopMode loop,
        FuelForm fuel,
        double baseCapacity,
        double baseFlowRate,
        double voidCoefficient,
        double tempCoefficient,
        double meltdownTemp,
        boolean rodTipEffect) {

    /** 冷却剂：决定二次回路 · 蒸汽等级 · 风险。 */
    public enum Coolant { LIGHT_WATER, HEAVY_WATER, HELIUM, SODIUM, LEAD, MOLTEN_SALT }

    /** 中子谱：决定反应性与同位素谱（材料轴）。 */
    public enum Spectrum { THERMAL, FAST }

    /** 循环模式：决定热效率与是否污染轮机。 */
    public enum LoopMode { PRESSURIZED, BOILING_DIRECT, POOL }

    /** 燃料形态：决定上游链与乏燃料组成。 */
    public enum FuelForm { ROD, PEBBLE, MOLTEN_SALT, MOX, THORIUM }

    /**
     * 慢化剂轴（邻接方块决定，见蓝图 §3.4「中子谱」轴）——<strong>燃料 × 慢化剂 = 堆型</strong>。
     *
     * <p>声明顺序即<strong>优先级</strong>（大 → 小）：同时邻接多种时取靠前者。
     */
    public enum Moderator {
        /** 无慢化剂：轻水自带慢化 → 基础堆型（PWR）。 */
        NONE,
        /** 重水慢化剂 → CANDU。 */
        HEAVY_WATER,
        /** 石墨慢化剂 → RBMK（沸腾直接 + 正空泡）。 */
        GRAPHITE,
        /** 研究堆通道 → 研究堆（低功率高通量）。 */
        RESEARCH
    }

    // ---- 预设：蓝图 §3.5「旧型号 → 原型 + 配置」映射的可运行版本 ------------------

    /** 压水堆 PWR：轻水 · 热谱 · 加压间接 · 双负反馈（自稳）。教学机。 */
    public static final ReactorConfig PWR = new ReactorConfig(
            "pwr", Coolant.LIGHT_WATER, Spectrum.THERMAL, LoopMode.PRESSURIZED, FuelForm.ROD,
            16384.0D, 4096.0D, -0.6D, -0.6D, 1200.0D, false);

    /** 石墨沸水堆 RBMK：石墨慢化 · 沸腾直接 · <strong>正空泡</strong> + 控制棒尖端效应。危险。 */
    public static final ReactorConfig RBMK = new ReactorConfig(
            "rbmk", Coolant.LIGHT_WATER, Spectrum.THERMAL, LoopMode.BOILING_DIRECT, FuelForm.ROD,
            32768.0D, 16384.0D, 0.6D, -0.2D, 1000.0D, true);

    /** 重水堆 CANDU：重水慢化 · 加压间接 · 天然铀友好（免富集分支的落点）。 */
    public static final ReactorConfig CANDU = new ReactorConfig(
            "candu", Coolant.HEAVY_WATER, Spectrum.THERMAL, LoopMode.PRESSURIZED, FuelForm.ROD,
            12288.0D, 3072.0D, -0.5D, -0.6D, 1150.0D, false);

    /** 研究堆：轻水 · 低功率 · 高通量（非发电；后续产出辐照同位素）。 */
    public static final ReactorConfig RESEARCH = new ReactorConfig(
            "research", Coolant.LIGHT_WATER, Spectrum.THERMAL, LoopMode.PRESSURIZED, FuelForm.ROD,
            4096.0D, 2048.0D, -0.7D, -0.8D, 900.0D, false);

    /** 高温气冷堆 HTGR：氦冷 · 高热流率 · 强负反馈（固有安全）。 */
    public static final ReactorConfig HTGR = new ReactorConfig(
            "htgr", Coolant.HELIUM, Spectrum.THERMAL, LoopMode.PRESSURIZED, FuelForm.PEBBLE,
            12288.0D, 24576.0D, -0.8D, -0.6D, 1600.0D, false);

    /** 钠冷快堆 FBR：钠 · 快谱 · 池式 · MOX —— 增殖 / 材料轴，快谱产钚更多。 */
    public static final ReactorConfig FBR = new ReactorConfig(
            "fbr", Coolant.SODIUM, Spectrum.FAST, LoopMode.POOL, FuelForm.MOX,
            16384.0D, 8192.0D, -0.4D, -0.5D, 1300.0D, false);

    /** 钍基堆：重水 · 热谱 · 钍燃料（增殖 U-233）。 */
    public static final ReactorConfig THORIUM = new ReactorConfig(
            "thorium", Coolant.HEAVY_WATER, Spectrum.THERMAL, LoopMode.PRESSURIZED, FuelForm.THORIUM,
            12288.0D, 6144.0D, -0.5D, -0.5D, 1300.0D, false);

    /** 全部预设（按 id 反查用）。 */
    private static final ReactorConfig[] PRESETS = {PWR, RBMK, CANDU, RESEARCH, HTGR, FBR, THORIUM};

    /**
     * <strong>燃料 × 慢化剂 → 配置</strong>（蓝图 §3.4 / §3.5）。
     *
     * <p>口径：只有<strong>热谱铀系</strong>（{@code base = PWR}，即铀燃料棒）吃慢化剂——
     * 铀棒 + 石墨 → RBMK、铀棒 + 重水 → CANDU、铀棒 + 研究堆通道 → 研究堆、铀棒无慢化剂 → PWR。
     * 快谱（MOX → FBR）与钍基（THORIUM）<strong>不受慢化剂影响</strong>（快堆不吃慢化剂；钍基自带重水）。
     *
     * @param base      燃料轴得到的基础配置（{@code null} = 不是燃料）
     * @param moderator 6 面扫到的慢化剂（无 → {@link Moderator#NONE}）
     */
    public static ReactorConfig resolve(ReactorConfig base, Moderator moderator) {
        if (base == null || base.fuel() != FuelForm.ROD) {
            return base;
        }
        return switch (moderator) {
            case GRAPHITE -> RBMK;
            case HEAVY_WATER -> CANDU;
            case RESEARCH -> RESEARCH;
            case NONE -> base;
        };
    }

    /** 按 id 反查预设（存档还原配置用）；未知 id → {@code null}。 */
    public static ReactorConfig byId(String id) {
        for (ReactorConfig preset : PRESETS) {
            if (preset.id.equals(id)) {
                return preset;
            }
        }
        return null;
    }
}
