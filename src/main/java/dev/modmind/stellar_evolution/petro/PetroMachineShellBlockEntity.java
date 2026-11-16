package dev.modmind.stellar_evolution.petro;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 石化机器「填充格」的方块实体：本身不存任何数据，只把物品 / 流体能力转发给机器本体 ——
 * 照生化线 {@code BioMachineShellBlockEntity} 口径。
 *
 * <p>为什么填充格也要挂方块实体：整台机器「所有部分都视作本体」—— 瞄任意一格都应看到同样的物品 / 流体存量。
 * 本实体无 ticker、不存盘：能力解析时现查「哪台机器」（反查主方块）再转发即可。
 */
public class PetroMachineShellBlockEntity extends BlockEntity {
    public PetroMachineShellBlockEntity(BlockPos pos, BlockState state) {
        super(PetroMachineBlocks.PETRO_MACHINE_SHELL_ENTITY.get(), pos, state);
    }

    /** 本填充格所属的机器主方块实体；不在世界里 / 找不到主方块则 null。 */
    @Nullable
    private PetroMachineBlockEntity machine() {
        if (level == null) {
            return null;
        }
        BlockPos origin = PetroMachineBlocks.findMachine(level, worldPosition);
        return origin != null && level.getBlockEntity(origin) instanceof PetroMachineBlockEntity be ? be : null;
    }

    /** 转发机器本体的物品能力。 */
    @Nullable
    public ResourceHandler<ItemResource> itemCapability() {
        PetroMachineBlockEntity machine = machine();
        return machine == null ? null : machine.itemCapability();
    }

    /** 转发机器本体的流体能力。 */
    @Nullable
    public ResourceHandler<FluidResource> fluidCapability() {
        PetroMachineBlockEntity machine = machine();
        return machine == null ? null : machine.fluidCapability();
    }
}
