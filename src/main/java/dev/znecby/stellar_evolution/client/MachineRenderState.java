package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.machine.MachineKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 材料加工机械的渲染状态（26.x 新体系：先抽状态，再在 {@code submit} 里画）—— 照 {@code PetroMachineRenderState} 口径。
 * <ul>
 *   <li>{@link #kind}：动画规格（含各部件）；</li>
 *   <li>{@link #facingY}：正面朝向对应的「方块状态 y 旋转角度」（北 0 / 东 90 / 南 180 / 西 270）；</li>
 *   <li>{@link #age}：运行时长（含 partialTick 插值），各部件按它推算各自转角 / 位移；</li>
 *   <li>{@link #rotors}：与 {@code kind.parts()} 一一对应的移动方块渲染状态（可空）。</li>
 * </ul>
 */
public class MachineRenderState extends BlockEntityRenderState {
    public @Nullable MachineKind kind;
    public float facingY;
    public float age;
    public final List<@Nullable MovingBlockRenderState> rotors = new ArrayList<>();
}
