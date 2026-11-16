package dev.znecby.stellar_evolution.bio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 生化机器「填充格」的方块实体：本身<strong>不存任何数据</strong>，只把<strong>物品 / 流体能力转发给机器本体</strong>。
 *
 * <p>为什么填充格也要挂方块实体：整台机器「所有部分都视作本体」—— 瞄**任意一格**都应看到同样的物品 / 流体存量
 * （Jade 的能力查询、其它模组的管道 / 漏斗都一样）。而能力只能挂在方块实体上：填充格原来只有碰撞（无 BE），
 * 能力查询只会报「无」，于是从填充格那一格看过去就没有物品 / 流体列表。
 *
 * <p>本实体无 ticker、不存盘：能力解析时现查「哪台机器」（反查主方块）再转发即可。
 */
public class BioMachineShellBlockEntity extends BlockEntity {
    public BioMachineShellBlockEntity(BlockPos pos, BlockState state) {
        super(BioMachineBlocks.BIO_MACHINE_SHELL_ENTITY.get(), pos, state);
    }

    /** 本填充格所属的机器主方块实体；不在世界里 / 找不到主方块则 null。 */
    @Nullable
    private BioMachineBlockEntity machine() {
        if (level == null) {
            return null;
        }
        BlockPos origin = BioMachineBlocks.findMachine(level, worldPosition);
        return origin != null && level.getBlockEntity(origin) instanceof BioMachineBlockEntity be ? be : null;
    }

    /** 转发机器本体的物品能力（非大型机器则 null）。 */
    @Nullable
    public ResourceHandler<ItemResource> itemCapability() {
        BioMachineBlockEntity machine = machine();
        return machine == null ? null : machine.itemCapability();
    }

    /** 转发机器本体的流体能力（非大型机器则 null）。 */
    @Nullable
    public ResourceHandler<FluidResource> fluidCapability() {
        BioMachineBlockEntity machine = machine();
        return machine == null ? null : machine.fluidCapability();
    }
}
