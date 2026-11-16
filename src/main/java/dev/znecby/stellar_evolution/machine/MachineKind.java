package dev.znecby.stellar_evolution.machine;

import java.util.List;
import net.minecraft.core.Direction;

/**
 * 材料加工机械：一台机器一种，决定方块 id 与它的可动部件（动画规格）。
 *
 * <p><strong>形态口径</strong>：照 {@code engine.EngineBlocks} 的燃烧引擎 —— 都是<strong>单方块</strong>
 * （主方块 + 一圈 2px 铜框架），可动部件由 {@code client.MachineRenderer} 借「隐藏载体方块 + submitMovingBlock」画在方块内。
 *
 * <p><strong>运行方式</strong>：四台机器都是热压差消耗端，读取各自配方表、保存库存与进度；
 * 可动部件只用于表现，不参与结算。
 *
 * <p>只依赖原版类型（{@link Direction.Axis}），<strong>公共侧安全</strong>。
 */
public enum MachineKind {
    /**
     * 破碎机（颚式）：料从顶部进入两颚之间的 V 形破碎腔，偏心飞轮带动动颚往复摆动（挤压 + 排料）。
     * 侧面大飞轮＝偏心轴（自转），前端动颚＝悬挂在顶部铰座的颚板（摆动）。
     */
    CRUSHER("crusher", 240,
            MachinePart.spin("crusher_flywheel", Direction.Axis.X, 9.0F, 15.0F, 8.0F, 8.0F),
            MachinePart.swing("crusher_jaw", Direction.Axis.X, 12.0F, 3.0F, 8.0F, 12.2F, 5.4F)),
    /**
     * 压模机：四立柱机架 + 顶板（上模座），中间的滑块沿导柱下压，把模具里的料压实；立柱左面大飞轮＝飞轮储能（自转），
     * 正面压力表指针随压力摆动（摆动）。
     */
    DIE_PRESS("die_press", 320,
            MachinePart.spin("die_press_flywheel", Direction.Axis.X, 7.0F, 1.0F, 8.0F, 8.0F),
            MachinePart.slide("die_press_ram", Direction.Axis.Y, 8.0F, 1.0F),
            MachinePart.swing("die_press_needle", Direction.Axis.Z, 5.0F, 32.0F, 8.0F, 7.6F, 1.8F)),
    /**
     * 线材轧机：一座 <strong>二辊牌坊</strong> —— 两片牌坊立板 + 顶梁 + 底垫围出一个「窗口」，
     * 窗口里上下两根轧辊夹住料线（<strong>辊轴沿 X、料线沿 Z 从正面穿到背面</strong>），
     * 进出口各有一对导卫条（上/下）托住料线；右侧轴承座上挂着与辊轴同轴的传动轮（绕 X 自转）。
     * 两辊同速反向自转（上辊转速取负号＝反向）。
     */
    ROLLING_MILL("rolling_mill", 280,
            MachinePart.spin("rolling_mill_roll", Direction.Axis.X, 14.0F, 8.0F, 6.6F, 8.0F),
            MachinePart.spin("rolling_mill_roll", Direction.Axis.X, -14.0F, 0.0F, 2.8F, 0.0F, 8.0F, 6.6F, 8.0F),
            MachinePart.spin("rolling_mill_wheel", Direction.Axis.X, 9.0F, 15.0F, 8.0F, 8.0F)),
    /**
     * 钢炉：<strong>熔炉的上位机</strong> —— 3×3 料仓、9 格并行执行<strong>任意熔炉（熔炼）配方</strong>。
     * 外观：铜框架 + 钢制炉体 + 正面炉口火光，侧面鼓风机转子（自转）。
     * 周期固定 {@code 200t}（原版熔炉配方的标准时长），与其它机器一样按热压差倍速缩短。
     */
    STEEL_FURNACE("steel_furnace", 200,
            MachinePart.spin("steel_furnace_fan", Direction.Axis.X, 10.0F, 15.0F, 8.0F, 8.0F));

    private final String id;
    private final int cycleTicks;
    private final List<MachinePart> parts;

    MachineKind(String id, int cycleTicks, MachinePart... parts) {
        this.id = id;
        this.cycleTicks = cycleTicks;
        this.parts = List.of(parts);
    }

    /** 方块 id（如 {@code crusher}）。 */
    public String id() {
        return id;
    }

    /** 一个加工周期的基准刻数（1.0 倍速）。 */
    public int cycleTicks() {
        return cycleTicks;
    }

    /** 该机器的全部可动部件。 */
    public List<MachinePart> parts() {
        return parts;
    }
}
