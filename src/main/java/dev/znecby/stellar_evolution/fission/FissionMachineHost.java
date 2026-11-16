package dev.znecby.stellar_evolution.fission;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 裂变机器的「本体能力」接口：填充格方块实体据此把物品 / 流体能力转发给主方块。
 *
 * <p>只依赖 transfer API，<strong>公共侧安全</strong>。
 */
public interface FissionMachineHost {

    /** 本体物品能力；没有物品槽的机器返回 {@code null}。 */
    @Nullable
    ResourceHandler<ItemResource> itemCapability();

    /** 本体流体能力；没有流体罐的机器返回 {@code null}。 */
    @Nullable
    ResourceHandler<FluidResource> fluidCapability();
}
