package dev.znecby.stellar_evolution.bio;

import java.util.List;
import net.minecraft.core.Direction;

/**
 * 生化机器的动画规格：每台机器一组 {@link BioMachinePart}（本轮每台 2 个可动部件）。
 * <p>
 * 设计约束（防穿模）：同机各部件的旋转轴与枢轴互相错开，或做成<b>同轴不同高度</b>，
 * 使任一部件在旋转扫掠时都不会撞到静止件或另一个部件。
 * <p>
 * 只依赖原版类型，**公共侧安全**。
 */
public enum BioMachineKind {
    /** 调浆机：下层十字桨叶 + 上层锚式框，同轴不同速（真实搅拌器的双级搅拌）。 */
    SLURRY_MIXER(128.0D,
            new BioMachinePart("slurry_mixer_agitator", Direction.Axis.Y, 4.0F, false, 0F, 16F, 16F, 16F),
            new BioMachinePart("slurry_mixer_anchor", Direction.Axis.Y, 3.0F, false, 0F, 16F, 16F, 16F)),
    /** 发酵罐：顶置搅拌桨 + 更高位飞轮联轴节。 */
    FERMENTER(160.0D,
            new BioMachinePart("fermenter_agitator", Direction.Axis.Y, 2.5F, false, 0F, 16F, 16F, 16F),
            new BioMachinePart("fermenter_coupling", Direction.Axis.Y, 6.0F, false, 0F, 16F, 16F, 16F)),
    /** 分解仪：卧式转鼓 + 同轴驱动皮带轮（枢轴 = 转鼓中心 y18）。 */
    DECOMPOSER(192.0D,
            new BioMachinePart("decomposer_drum", Direction.Axis.X, 14.0F, false, 0F, 16F, 18F, 16F),
            new BioMachinePart("decomposer_pulley", Direction.Axis.X, 14.0F, false, 0F, 16F, 18F, 16F)),
    /** 灭菌罐：舱门手轮（绕 Z）+ 顶部压力表指针（绕 Y 小幅摆动）。 */
    STERILIZER(224.0D,
            new BioMachinePart("sterilizer_handwheel", Direction.Axis.Z, 2.0F, false, 0F, 16F, 16F, 3.35F),
            new BioMachinePart("sterilizer_needle", Direction.Axis.Y, 6.0F, true, 34.0F, 13.7F, 29.5F, 14.0F)),
    /** 结晶机：顶部晶体簇缓慢自转 + 底部星型卸料阀。 */
    CRYSTALLIZER(256.0D,
            new BioMachinePart("crystallizer_crystal", Direction.Axis.Y, 1.0F, false, 0F, 16F, 16F, 16F),
            new BioMachinePart("crystallizer_valve", Direction.Axis.Y, 4.0F, false, 0F, 16F, 16F, 16F)),
    /** 反应器：贯通搅拌轴 + 双层桨叶（笼式罐体可见）+ 底部气体分布器。 */
    REACTOR(320.0D,
            new BioMachinePart("bioreactor_agitator", Direction.Axis.Y, 3.0F, false, 0F, 16F, 16F, 16F),
            new BioMachinePart("bioreactor_sparger", Direction.Axis.Y, 6.0F, false, 0F, 16F, 16F, 16F));

    private final double baseFlowRate;
    private final List<BioMachinePart> parts;

    BioMachineKind(double baseFlowRate, BioMachinePart... parts) {
        this.baseFlowRate = baseFlowRate;
        this.parts = List.of(parts);
    }

    /** 基准热流率（HP/t）：1.0 倍速所需；越靠后的工艺越吃热流率。 */
    public double baseFlowRate() {
        return baseFlowRate;
    }

    /** 该机器的全部可动部件。 */
    public List<BioMachinePart> parts() {
        return parts;
    }
}
