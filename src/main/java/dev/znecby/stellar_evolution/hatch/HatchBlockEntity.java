package dev.znecby.stellar_evolution.hatch;

import dev.znecby.stellar_evolution.thermal.ThermalConsumer;
import dev.znecby.stellar_evolution.thermal.ThermalLoad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 机械物流仓的方块实体：只保存「被哪台多方块控制器认领」。
 *
 * <p>没有物品/流体库存、没有界面、不自动搬运；认领关系只活在内存里，
 * 区块重新加载后由控制器重新认领。
 *
 * <p><strong>热压差代理</strong>：仓室是质量投射轨道炮的<strong>热接入端口</strong> —— 铜块 / 底排铜管
 * 贴着仓室即可接入网络。网络记账时把仓室当作那台机器的消耗端入口（热阻 / 基准热流率 / 封顶都转发给控制器），
 * 这样「热阻 64」记在机器身上，而接入点仍然是机械物流仓。
 */
public class HatchBlockEntity extends BlockEntity implements ThermalConsumer {
    private @Nullable BlockPos controllerPos;
    /** 载入后只强制刷新一次邻居（不存盘）。 */
    private boolean neighborRefreshDone;

    public HatchBlockEntity(BlockPos pos, BlockState state) {
        super(HatchBlocks.HATCH_ENTITY.get(), pos, state);
    }

    /** 载入后第一次 tick：让相邻机械管按「只有覆盖层面能接」重算一次。 */
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, HatchBlockEntity hatch) {
        if (level.isClientSide() || hatch.neighborRefreshDone) {
            return;
        }
        hatch.neighborRefreshDone = true;
        state.updateNeighbourShapes(level, pos, net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    @Nullable
    public BlockPos controllerPos() {
        return controllerPos;
    }

    public void link(BlockPos controller) {
        this.controllerPos = controller.immutable();
    }

    public void unlink() {
        this.controllerPos = null;
    }

    /** 切换基底材质（控制器成型 / 失效时调用）；只在服务端真正写方块状态。 */
    public void setCasing(HatchBlock.Casing casing) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof HatchBlock && state.getValue(HatchBlock.CASING) != casing) {
            level.setBlock(worldPosition, state.setValue(HatchBlock.CASING, casing),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /** 认领它的多方块控制器（质量炮 / 焦化炉）；未认领 / 已失效返回 null。 */
    @Nullable
    private HatchOwner owner() {
        return level != null && controllerPos != null
                && level.getBlockEntity(controllerPos) instanceof HatchOwner owner
                && owner.isFormed() ? owner : null;
    }

    @Override
    public double thermalResistance() {
        HatchOwner owner = owner();
        return owner == null ? 0.0D : owner.thermalResistance();
    }

    @Override
    public double baseFlowRate() {
        HatchOwner owner = owner();
        return owner == null ? 1.0D : owner.baseFlowRate();
    }

    @Override
    public double maxUsefulFlowRate() {
        HatchOwner owner = owner();
        return owner == null ? 0.0D : owner.maxUsefulFlowRate();
    }

    /** 充能由控制器自己统一结算（多个仓室不会重复充能），这里不需要做事。 */
    @Override
    public void onThermalTick(ThermalLoad load) {
    }
}
