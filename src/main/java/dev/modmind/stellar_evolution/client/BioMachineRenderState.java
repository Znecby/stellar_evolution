package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.bio.BioMachineKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 生化机器的渲染状态（26.x 新体系：先抽状态，再在 {@code submit} 里画）。
 * <ul>
 *   <li>{@link #kind}：动画规格（含各部件）。</li>
 *   <li>{@link #age}：运行时长（含 partialTick 插值），各部件按它推算各自转角。</li>
 *   <li>{@link #rotors}：与 {@code kind.parts()} 一一对应的移动方块渲染状态（可空）。</li>
 * </ul>
 */
public class BioMachineRenderState extends BlockEntityRenderState {
    public @Nullable BioMachineKind kind;
    public float age;
    public final List<@Nullable MovingBlockRenderState> rotors = new ArrayList<>();
    /** 罐里水的高度比例 0..1（生物反应釜据此在笼式罐体里画「升起的水」）。 */
    public float fluidFill;
    /** 整机朝向对应的 y 旋转角（与 blockstate 的 facing 变体一致）。 */
    public float yaw;
}
