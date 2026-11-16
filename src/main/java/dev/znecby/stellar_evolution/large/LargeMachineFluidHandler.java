package dev.znecby.stellar_evolution.large;

import dev.znecby.stellar_evolution.tank.GrateSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/**
 * 大机器的<strong>流体能力</strong>：物流网络里储罐（流体容器）的合并视图。
 * 输入罐只可灌入、输出罐只可抽出（见 {@link LargeMachineHandler}）。
 */
public final class LargeMachineFluidHandler extends LargeMachineHandler<FluidResource> {
    public LargeMachineFluidHandler(LargeMachine machine) {
        super(machine);
    }

    @Nullable
    @Override
    protected ResourceHandler<FluidResource> handlerAt(Level level, BlockPos pos) {
        // 原版铜格栅没有方块实体：由本模组按「虚拟流体源」接管（含水产水）。
        if (GrateSource.isGrate(level.getBlockState(pos))) {
            return GrateSource.handler(level, pos);
        }
        return level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
    }

    @Override
    protected FluidResource empty() {
        return FluidResource.EMPTY;
    }

    /**
     * 铜格栅是<strong>虚拟流体源</strong>：机器内部可以从中抽水，但不该出现在对外能力 / Jade 的储罐列表里
     * （否则会显示成「274,877,906,943,987 mB 的水罐」这种十几位数字）。
     */
    @Override
    protected boolean showExternally(Level level, BlockPos pos) {
        return !GrateSource.isGrate(level.getBlockState(pos));
    }
}
