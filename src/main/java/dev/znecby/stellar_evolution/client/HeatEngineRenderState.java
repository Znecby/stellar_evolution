package dev.znecby.stellar_evolution.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 热力引擎的渲染状态（26.x 新体系：先把方块实体抽成状态，再在 {@code submit} 里画）。
 * <ul>
 *   <li>{@link #facingY}：正面朝向对应的「方块状态 y 旋转角度」（北 0 / 东 90 / 南 180 / 西 270）。</li>
 *   <li>{@link #angle}：转子绕长轴的自转角度（含 {@code partialTick} 插值）。</li>
 *   <li>{@link #rotor}：铁桨叶的移动方块渲染状态（借用隐藏方块的烘焙模型）。</li>
 * </ul>
 */
public class HeatEngineRenderState extends BlockEntityRenderState {
    public float facingY;
    public float angle;
    public @Nullable MovingBlockRenderState rotor;
}
