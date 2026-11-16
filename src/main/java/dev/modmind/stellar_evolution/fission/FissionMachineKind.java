package dev.modmind.stellar_evolution.fission;

/**
 * 裂变线<strong>机器 / 工艺机种</strong>（《核裂变线预规划蓝图》§6 / §13.2）——决定 id、热压差参数、周期与配方归属。
 *
 * <p>前 4 项是「燃料循环机器」（共用 {@code FissionMachineBlock} / {@code FissionMachineBlockEntity}）；
 * 后 2 项是「反应堆 / 蒸汽涡轮机组」——有自己的方块类，但同样走 {@link FissionRecipes} 的普通配方表，
 * 因此机种 id 也在这里统一列一份（配方 / JEI 按它分组）。
 */
public enum FissionMachineKind {
    /**
     * 化学处理槽（id 仍为 {@code ore_leacher}）：酸浸 + 铀转化合为一台。
     * <p>2026-11-11 合并原「酸浸槽」与「氟化炉」：两段氟化也合成一条配方（UO2 + HF + F2 → UF6 + 水），
     * 于是整条「矿石 → 黄饼 → UF6」只经这一台机器，不再需要单独一台氟化炉。
     */
    ORE_LEACHER("ore_leacher", 8.0D, 128.0D, 600),
    /** 离心机：UF6 → 低浓 UF6 + 贫铀 UF6（级联分离）。 */
    CENTRIFUGE("fission_centrifuge", 16.0D, 1024.0D, 12000),
    /** 罐装机：芯块 + 包壳 → 燃料棒。 */
    CANNING_MACHINE("canning_machine", 8.0D, 512.0D, 1200),
    /** 后处理厂：冷却乏燃料 → 钚 / U-233 / 回收铀 / 锆管 / 高放废料。 */
    REPROCESSOR("reprocessor", 8.0D, 512.0D, 2400),
    /** 缪子聚变仪：点火 → 自热 → 出蒸汽（压力管）+ 出缪子流（束流管）。 */
    MUON_FUSION("muon_fusion", 8.0D, 2048.0D, 600),
    /** 回旋加速器：不占 DP，按网络 DP 充差速、按粉出同位素 / 氚。 */
    CYCLOTRON("cyclotron", 0.0D, 0.0D, 20),
    /** 粒子源：消耗端（热阻 16），按网络 HP 无限超频出粒子；无压力罐时不占用。 */
    PARTICLE_SOURCE("particle_source", 16.0D, 0.0D, 20),
    /** 辐照仓：吃任意已接入的粒子流（中子 / 质子 / 铀离子 / 铅离子……），按配方表分派；兼做增殖包层（锂粉 → 氚）。 */
    IRRADIATION_CHAMBER("irradiation_chamber", 16.0D, 1024.0D, 300),
    /** 对撞机：唯一要求<strong>两路束流同时供能</strong>的机器（束-束对撞），运行时会喷出 μ 子流。 */
    COLLIDER("collider", 32.0D, 2048.0D, 600),
    /** ADS 加速器驱动次临界系统：质子流 + 次锕系 / 高放废料 → 稳定核素，并向外供蒸汽。 */
    ADS("ads", 32.0D, 2048.0D, 600),

    /** 湮灭反应堆：反物质 + 氢 → 直接向热压差网络供能（生成端，不接消耗端）。 */
    ANNIHILATION_REACTOR("annihilation_reactor", 0.0D, 0.0D, 600),

    /** 裂变堆：燃料棒常驻输入箱，按温度 + 功率向蒸汽网络输出蒸汽数据（压力管）。 */
    FISSION_REACTOR("fission_reactor", 0.0D, 0.0D, 600),
    /** 蒸汽涡轮机组：按蒸汽网络供汽升速，转速换算成热压差出力（不再产凝结水）。 */
    STEAM_TURBINE("steam_turbine_unit", 0.0D, 0.0D, 40);

    private final String id;
    private final double resistance;
    private final double baseFlowRate;
    private final int cycleTicks;

    FissionMachineKind(String id, double resistance, double baseFlowRate, int cycleTicks) {
        this.id = id;
        this.resistance = resistance;
        this.baseFlowRate = baseFlowRate;
        this.cycleTicks = cycleTicks;
    }

    /** 方块 / 贴图 / 语言键用的 id（如 {@code ore_leacher}）。 */
    public String id() {
        return id;
    }

    /** 基础热阻（占用 = 本值 × 有效热流率）；反应堆 / 汽轮机不接热压差消耗端，取 0。 */
    public double resistance() {
        return resistance;
    }

    /** 基准热流率（HP/t）：达到「1.0 倍速」所需；反应堆 / 汽轮机取 0（不接消耗端）。 */
    public double baseFlowRate() {
        return baseFlowRate;
    }

    /** 一个加工周期的刻数（1.0 倍速下）。 */
    public int cycleTicks() {
        return cycleTicks;
    }

    /** 该机种是不是「燃料循环机器」（共用方块类的机器）。 */
    public boolean isFuelMachine() {
        return this == ORE_LEACHER
                || this == CENTRIFUGE || this == CANNING_MACHINE || this == REPROCESSOR;
    }

    /** 展示机：目前没有（缪子聚变仪已于 2026-10-26 转正，有自己的点火 / 出束逻辑）。 */
    public boolean displayOnly() {
        return false;
    }
}
