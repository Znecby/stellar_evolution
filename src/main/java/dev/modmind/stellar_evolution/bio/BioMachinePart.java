package dev.modmind.stellar_evolution.bio;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * 生化机器的一个「可动部件」：隐藏载体方块名 + 旋转轴 + 转速 + 枢轴（模型单位 0..32）。
 * <p>
 * 两种运动模式：
 * <ul>
 *   <li>{@code oscillate = false}：匀速自转，转角 = {@code age × speed}（speed 单位 度/tick）；</li>
 *   <li>{@code oscillate = true}：以 {@code speed} 为角速度（度/tick）做正弦摆动，摆幅 {@code amplitude} 度
 *       （压力表指针那种）。</li>
 * </ul>
 * 只依赖原版 {@link Direction.Axis}，**公共侧安全**。
 */
public record BioMachinePart(String carrier, Direction.Axis axis, float speed, boolean oscillate, float amplitude,
                             float pivotX, float pivotY, float pivotZ) {

    /** 由「已插值的运行时长（tick）」算出本部件的转角（度）。 */
    public float angle(float age) {
        if (oscillate) {
            return amplitude * Mth.sin(age * (float) Math.toRadians(speed));
        }
        return age * speed;
    }
}
