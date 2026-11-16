package dev.znecby.stellar_evolution.client;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

/**
 * 燃烧类发电机组的一个「可动部件」：隐藏载体方块 + 旋转轴 + 转速（度/tick）+ 枢轴（模型单位 0..32）。
 *
 * <p>枢轴与轴都按「不旋转的北向模型」给；整机的四朝向由渲染器在画之前先绕机器中心反向转回来。
 */
public record EngineRotor(Block carrier, Direction.Axis axis, float speed,
                          float pivotX, float pivotY, float pivotZ) {

    /** 由「已插值的运行时长（tick）」算转角（度）。 */
    public float angle(float age) {
        return age * speed;
    }
}
