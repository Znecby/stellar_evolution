package dev.modmind.stellar_evolution.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 燃烧类发电机组的渲染状态（26.x 新体系：先抽状态，再在 {@code submit} 里画）。
 * <ul>
 *   <li>{@link #rotors}：该机组要画的旋转件规格（目前各一个）；</li>
 *   <li>{@link #yaw}：整机朝向对应的「方块状态 y 旋转角」（北 0 / 东 90 / 南 180 / 西 270）；</li>
 *   <li>{@link #age}：运行时长（含 partialTick 插值）；</li>
 *   <li>{@link #moving}：与 {@link #rotors} 一一对应的移动方块渲染状态（可空）。</li>
 * </ul>
 */
public class EngineMachineRenderState extends BlockEntityRenderState {
    public List<EngineRotor> rotors = List.of();
    public float yaw;
    public float age;
    public final List<@Nullable MovingBlockRenderState> moving = new ArrayList<>();
}
