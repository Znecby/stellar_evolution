package dev.modmind.stellar_evolution.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 储罐的渲染状态：罐里现在是什么流体、有多少，以及所属大罐的尺寸（宽 / 高，格）。
 * <p>
 * 只有控制器会真正画（{@link #active}），部件不画 —— 但部件也不该让渲染器白跑，所以先判这个开关。
 */
public class TankRenderState extends BlockEntityRenderState {
    public FluidStack fluid = FluidStack.EMPTY;
    /** 本格是否是所属大罐的控制器（只有控制器画整罐的流体）。 */
    public boolean active;
    /** 大罐水平边长（格）。 */
    public int width = 1;
    /** 大罐高度（格）。 */
    public int height = 1;
    /** 本档位单格容量（mB）—— 铜 8 000 / 钢 32 000 / 硬质 128 000。 */
    public int capacityPerBlock = 8_000;
}
