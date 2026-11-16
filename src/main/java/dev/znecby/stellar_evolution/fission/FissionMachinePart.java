package dev.znecby.stellar_evolution.fission;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * 裂变机器的一个「可动部件」：隐藏载体方块名 + 旋转轴 + 转速 + 枢轴（模型单位 0..32）。
 *
 * <p>照石化线 {@code PetroMachinePart} 口径：
 * <ul>
 *   <li>{@code oscillate = false}：匀速自转，转角 = {@code age × speed}（speed 单位 度/tick）；</li>
 *   <li>{@code oscillate = true}：以 {@code speed} 为角速度（度/tick）做正弦摆动，摆幅 {@code amplitude} 度。</li>
 * </ul>
 * {@code translate = true} 时改为<strong>沿 axis 平移</strong>：位移 = {@code amplitude × sin(...)}（插栓的深入 / 抽出），
 * {@code phase} 是初相（弧度），用来让多颗插栓错开。两种模式都只依赖原版类型，<strong>公共侧安全</strong>。
 */
public record FissionMachinePart(String carrier, Direction.Axis axis, float speed, boolean oscillate, float amplitude,
                                 float pivotX, float pivotY, float pivotZ, boolean translate, float phase) {

    /** 旋转部件（旧口径）：不位移、无初相。 */
    public FissionMachinePart(String carrier, Direction.Axis axis, float speed, boolean oscillate, float amplitude,
                              float pivotX, float pivotY, float pivotZ) {
        this(carrier, axis, speed, oscillate, amplitude, pivotX, pivotY, pivotZ, false, 0F);
    }

    /** 由「已插值的运行时长（tick）」算出本部件的转角（度）。 */
    public float angle(float age) {
        if (oscillate) {
            return amplitude * Mth.sin(age * (float) Math.toRadians(speed) + phase);
        }
        return age * speed;
    }

    /** 平移部件的位移（模型单位，沿 {@link #axis()} 正方向）—— 插栓的「深入 / 抽出」。 */
    public float offset(float age) {
        return amplitude * Mth.sin(age * (float) Math.toRadians(speed) + phase);
    }
}
