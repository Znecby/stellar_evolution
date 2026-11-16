package dev.modmind.stellar_evolution.petro;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.Direction;

/**
 * 石化机器：一台机器一种，决定方块 id、动画部件（3 个可动部件）与「基础热阻」。
 *
 * <p><strong>形态口径</strong>：照生化线 {@code BioMachineKind} —— 六台机器<strong>共用</strong>一个方块类
 * （{@link PetroMachineBlock}）与方块实体（{@link PetroMachineBlockEntity}），机种由注册表
 * （{@link PetroMachineBlocks#kindOf}）按方块实例区分；机器是 <strong>2×2×2 整机</strong>（含 7 格填充格），
 * 并接入「大机器框架」（热压差消耗端 + 铜箱子 / 储罐物流）。
 *
 * <p>{@link #COKE_OVEN} 是<strong>例外</strong>：它只借用本枚举当配方类型，本体是 {@code coal} 包的
 * 3×3×4 砖块多方块，不参与 2×2×2 的方块 / 填充格 / 载体注册。
 *
 * <p><strong>负载口径</strong>：机种之间的差异用<strong>基础热阻</strong>表达（占用 DP = 热阻 × 有效热流率，
 * 见 {@code ThermalLoad#occupancy()}）—— 热阻大的机器同样倍速下更吃容量。**不设热流率门槛**：
 * 门槛会在低热流率处形成「停 → 直接 N 倍速」的跳变，破坏线性加速；负载差异改由热阻承担。
 *
 * <p>只依赖原版类型，**公共侧安全**（客户端渲染器按 {@link #parts()} 取载体方块画动画）。
 */
public enum PetroMachineKind {
    /** 分馏塔：原油 → 五路馏分；重载（热阻 4 = 标准负载的 2 倍，吃容量）。 */
    DISTILLATION_TOWER("distillation_tower", 4.0D, 256.0D, 400,
            new PetroMachinePart("distillation_tower_fan", Direction.Axis.Y, 3.0F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("distillation_tower_valve", Direction.Axis.Y, 5.0F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("distillation_tower_gauge", Direction.Axis.Z, 4.5F, true, 30.0F, 16F, 16F, 3.35F)),
    /** 裂解炉：石脑油 / 重油 → 烯烃；最重载（热阻 8 = 标准负载的 4 倍，吃容量最狠）。 */
    CRACKING_FURNACE("cracking_furnace", 8.0D, 448.0D, 600,
            new PetroMachinePart("cracking_furnace_fan", Direction.Axis.Y, 6.0F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("cracking_furnace_handwheel", Direction.Axis.Z, 2.0F, false, 0F, 16F, 16F, 3.35F),
            new PetroMachinePart("cracking_furnace_dial", Direction.Axis.Z, 3.5F, true, 34.0F, 8F, 16F, 3.35F)),
    /** 重整器：石脑油 → 苯 / 氢气；重油 → 润滑油；柴油 + 氢气 → 低硫油。标准负载（热阻 2）。 */
    REFORMER("reformer", 2.0D, 256.0D, 500,
            new PetroMachinePart("reformer_agitator", Direction.Axis.Y, 2.5F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("reformer_valve", Direction.Axis.Y, 5.0F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("reformer_valve_wheel", Direction.Axis.Z, 2.5F, false, 0F, 16F, 16F, 3.35F)),
    /** 聚合釜：单体 → 塑料 / 橡胶（八行）。标准负载（热阻 2）。 */
    POLYMERIZER("polymerizer", 2.0D, 192.0D, 400,
            new PetroMachinePart("polymerizer_agitator", Direction.Axis.Y, 2.5F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("polymerizer_coupling", Direction.Axis.Y, 6.0F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("polymerizer_drain_valve", Direction.Axis.Y, 5.0F, false, 0F, 16F, 16F, 16F)),
    /** 气化炉（煤化工 C 线）：煤 / 焦炭 / 煤焦油 / CO₂ → 合成气；最重载（热阻 8）。 */
    GASIFIER("gasifier", 8.0D, 480.0D, 700,
            new PetroMachinePart("gasifier_fan", Direction.Axis.Y, 3.0F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("gasifier_valve", Direction.Axis.Y, 5.0F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("gasifier_gauge", Direction.Axis.Z, 4.5F, true, 30.0F, 16F, 16F, 3.35F)),
    /** 合成塔（煤化工 C 线）：甲醇 / 氨水 / 尿素 / MTO；重载（热阻 4）。 */
    SYNTHESIS_TOWER("synthesis_tower", 4.0D, 384.0D, 600,
            new PetroMachinePart("synthesis_tower_agitator", Direction.Axis.Y, 2.5F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("synthesis_tower_valve", Direction.Axis.Y, 5.0F, false, 0F, 16F, 16F, 16F),
            new PetroMachinePart("synthesis_tower_gauge", Direction.Axis.Z, 2.5F, false, 0F, 16F, 16F, 3.35F)),
    /**
     * 焦化炉（煤化工 C 线）：煤 / 原木绝氧干馏。
     *
     * <p><strong>只借用本枚举做配方类型</strong>：本体是 {@code coal} 包里的 3×3×4 砖块多方块
     * （{@code CokeOvenControllerBlockEntity}），不是 2×2×2 石化机器，因此
     * {@link #parts()} 为空、{@code PetroMachineBlocks} 也不会为它注册方块。
     */
    COKE_OVEN("coke_oven_controller", 4.0D, 320.0D, 400);

    private final String id;
    private final double thermalResistance;
    private final double baseFlowRate;
    private final int cycleTicks;
    private final List<PetroMachinePart> parts;

    PetroMachineKind(String id, double thermalResistance, double baseFlowRate, int cycleTicks,
                     PetroMachinePart... parts) {
        this.id = id;
        this.thermalResistance = thermalResistance;
        this.baseFlowRate = baseFlowRate;
        this.cycleTicks = cycleTicks;
        this.parts = List.of(parts);
    }

    /** 方块 / 配方类型 id（如 {@code distillation_tower}）。 */
    public String id() {
        return id;
    }

    /** 基础热阻（DP·t/HP）：占用 = 本值 × 有效热流率；越大同样倍速下越吃容量。 */
    public double thermalResistance() {
        return thermalResistance;
    }

    /** 基准热流率（HP/t）：1.0 倍速所需；越靠后的工艺越吃热流率。 */
    public double baseFlowRate() {
        return baseFlowRate;
    }

    /** 一个加工周期的基准刻数（1.0 倍速）。 */
    public int cycleTicks() {
        return cycleTicks;
    }

    /** 该机器的全部可动部件。 */
    public List<PetroMachinePart> parts() {
        return parts;
    }

    /** 机种 id 小写（配方类型 id / JEI 分类 uid 用）。 */
    public String lowerId() {
        return id.toLowerCase(Locale.ROOT);
    }
}
