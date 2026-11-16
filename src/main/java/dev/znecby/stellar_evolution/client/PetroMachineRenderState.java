package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.petro.PetroMachineKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 石化机器的渲染状态（26.x 新体系：先抽状态，再在 {@code submit} 里画）—— 照生化线 {@code BioMachineRenderState} 口径。
 * <ul>
 *   <li>{@link #kind}：动画规格（含各部件）；</li>
 *   <li>{@link #age}：运行时长（含 partialTick 插值），各部件按它推算各自转角；</li>
 *   <li>{@link #rotors}：与 {@code kind.parts()} 一一对应的移动方块渲染状态（可空）。</li>
 * </ul>
 */
public class PetroMachineRenderState extends BlockEntityRenderState {
    public @Nullable PetroMachineKind kind;
    public float age;
    public final List<@Nullable MovingBlockRenderState> rotors = new ArrayList<>();
    /** 整机朝向对应的 y 旋转角（与 blockstate 的 facing 变体一致）。 */
    public float yaw;
}
