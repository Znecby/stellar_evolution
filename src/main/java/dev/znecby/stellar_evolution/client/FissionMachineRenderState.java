package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.fission.FissionMachinePart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 裂变机器的渲染状态（26.x 新体系：先抽状态，再在 {@code submit} 里画）—— 照石化线口径。
 * <ul>
 *   <li>{@link #parts}：该机器的可动部件规格；</li>
 *   <li>{@link #age}：运行时长（含 partialTick 插值）；</li>
 *   <li>{@link #rotors}：与 {@code parts} 一一对应的移动方块渲染状态（可空）。</li>
 * </ul>
 */
public class FissionMachineRenderState extends BlockEntityRenderState {
    public List<FissionMachinePart> parts = List.of();
    public float age;
    public final List<@Nullable MovingBlockRenderState> rotors = new ArrayList<>();
    /** 缪子聚变仪：球壳内部要额外画一颗"太阳"。 */
    public boolean muonCore;
    /** 整机朝向对应的 y 旋转角（与 blockstate 的 facing 变体一致）。 */
    public float yaw;
}
