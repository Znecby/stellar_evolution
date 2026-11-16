package dev.modmind.stellar_evolution.machine;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * 材料加工机械的一个「可动部件」：隐藏载体方块名 + 运动方式 + 轴 + 参数 + 枢轴 / 偏移（模型单位 0..16）。
 *
 * <p>照石化线 {@code PetroMachinePart} 口径，只多一种运动方式（{@link Motion#SLIDE}，给压机的滑块用）：
 * <ul>
 *   <li>{@link Motion#SPIN}：匀速自转，转角 = 运行时长 × {@code speed}（度/tick）；</li>
 *   <li>{@link Motion#SWING}：以 {@code speed}（度/tick）为角速度做正弦摆动，摆幅 {@code amplitude} 度；</li>
 *   <li>{@link Motion#SLIDE}：沿 {@code axis} 做正弦往复平移，位移 = {@code amplitude}（模型单位）× sin(...)。</li>
 * </ul>
 * {@code offset*} 把载体模型从「作者坐标系」平移到实际安装位置（同一份模型画多次时用，例如轧机的上下辊）；
 * {@code pivot*} 是旋转中心（模型单位）。只依赖原版 {@link Direction.Axis}，<strong>公共侧安全</strong>。
 */
public record MachinePart(String carrier, Motion motion, Direction.Axis axis, float speed, float amplitude,
                          float offsetX, float offsetY, float offsetZ,
                          float pivotX, float pivotY, float pivotZ) {

    /** 运动方式。 */
    public enum Motion {
        /** 匀速自转。 */
        SPIN,
        /** 正弦摆动（绕枢轴）。 */
        SWING,
        /** 正弦往复平移（沿轴）。 */
        SLIDE
    }

    /** 自转部件（枢轴＝转动中心，模型单位）。 */
    public static MachinePart spin(String carrier, Direction.Axis axis, float speed, float px, float py, float pz) {
        return new MachinePart(carrier, Motion.SPIN, axis, speed, 0.0F, 0.0F, 0.0F, 0.0F, px, py, pz);
    }

    /** 自转部件 + 安装偏移（同一份载体模型画在别处时用）。 */
    public static MachinePart spin(String carrier, Direction.Axis axis, float speed,
                                   float ox, float oy, float oz, float px, float py, float pz) {
        return new MachinePart(carrier, Motion.SPIN, axis, speed, 0.0F, ox, oy, oz, px, py, pz);
    }

    /** 摆动部件：{@code amplitude} 是摆幅（度），{@code speed} 是角速度（度/tick）。 */
    public static MachinePart swing(String carrier, Direction.Axis axis, float speed, float amplitude,
                                    float px, float py, float pz) {
        return new MachinePart(carrier, Motion.SWING, axis, speed, amplitude, 0.0F, 0.0F, 0.0F, px, py, pz);
    }

    /** 往复平移部件：{@code amplitude} 是行程（模型单位）。枢轴对平移无意义，仍给方块中心。 */
    public static MachinePart slide(String carrier, Direction.Axis axis, float speed, float amplitude) {
        return new MachinePart(carrier, Motion.SLIDE, axis, speed, amplitude, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 8.0F);
    }

    /**
     * 由「已插值的运行时长（tick）」算出本部件当前的状态量：
     * {@code SPIN} / {@code SWING} 返回角度（度），{@code SLIDE} 返回位移（模型单位）。
     */
    public float amount(float age) {
        return switch (motion) {
            case SPIN -> age * speed;
            case SWING, SLIDE -> amplitude * Mth.sin(age * (float) Math.toRadians(speed));
        };
    }
}
