package dev.modmind.stellar_evolution.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 开采钻的渲染状态（26.x 新体系：先把方块实体抽成状态，再在 {@code submit} 里画）。
 * <ul>
 *   <li>{@link #facingY}：正面朝向对应的「方块状态 y 旋转角度」（北 0 / 东 90 / 南 180 / 西 270）；</li>
 *   <li>{@link #age}：运行时长（含 {@code partialTick} 插值），钻杆与绞盘按它推算转角；</li>
 *   <li>{@link #shaft}：中央钻杆的移动方块渲染状态（借用隐藏方块的烘焙模型）；</li>
 *   <li>{@link #winch}：正面绞盘的移动方块渲染状态。</li>
 * </ul>
 */
public class DrillRenderState extends BlockEntityRenderState {
    public float facingY;
    public float age;
    public @Nullable MovingBlockRenderState shaft;
    public @Nullable MovingBlockRenderState winch;
}
