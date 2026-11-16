package dev.znecby.stellar_evolution.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 裂变机器「填充格」的方块实体：不存数据，只把物品 / 流体能力转发给主方块（照石化线口径）。
 */
public class FissionMachineShellBlockEntity extends BlockEntity {
    public FissionMachineShellBlockEntity(BlockPos pos, BlockState state) {
        super(FissionMachineStructure.SHELL_ENTITY.get(), pos, state);
    }

    /** 本填充格所属机器的本体；找不到则为 {@code null}。 */
    @Nullable
    private FissionMachineHost host() {
        if (level == null) {
            return null;
        }
        BlockPos origin = FissionMachineStructure.findMachine(level, worldPosition);
        return origin != null && level.getBlockEntity(origin) instanceof FissionMachineHost machine ? machine : null;
    }

    @Nullable
    public ResourceHandler<ItemResource> itemCapability() {
        FissionMachineHost host = host();
        return host == null ? null : host.itemCapability();
    }

    @Nullable
    public ResourceHandler<FluidResource> fluidCapability() {
        FissionMachineHost host = host();
        return host == null ? null : host.fluidCapability();
    }
}
