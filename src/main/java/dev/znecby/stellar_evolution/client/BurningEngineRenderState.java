package dev.znecby.stellar_evolution.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 燃烧引擎的渲染状态（26.x 新体系：先把方块实体抽成状态，再在 {@code submit} 里画）。
 * <ul>
 *   <li>{@link #facingY}：正面朝向对应的「方块状态 y 旋转角度」（北 0 / 东 90 / 南 180 / 西 270）。</li>
 *   <li>{@link #angle}：曲轴转角（含 {@code partialTick} 插值）——同时驱动前端风扇自转与六缸活塞往复。</li>
 *   <li>{@link #rotor}：前端风扇的移动方块渲染状态（借用隐藏方块的烘焙模型）。</li>
 *   <li>{@link #piston}：单缸活塞的移动方块渲染状态（同一模型画 6 次，左右各 3 缸）。</li>
 * </ul>
 */
public class BurningEngineRenderState extends BlockEntityRenderState {
    public float facingY;
    public float angle;
    public @Nullable MovingBlockRenderState rotor;
    public @Nullable MovingBlockRenderState piston;
}
