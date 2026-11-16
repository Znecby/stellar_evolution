package dev.znecby.stellar_evolution.farmland;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * <strong>机械沙地 / 机械灵魂沙地</strong>的共同基类：一台带铜框架的「土壤设备」
 * （外观 = 2px 铜框架框住对应材质的一块土，见 {@code models/block/mechanical_{sand,soul_sand}.json}）。
 * <p>
 * 与 {@link MechanicalFarmlandBlock} 的区别：耕地继承原版 {@code FarmlandBlock}（自带湿润度那套）；
 * 而沙地 / 灵魂沙地<strong>不参与重力、不参与含水</strong>，只是一台固定摆在那里的设备，所以直接继承 {@link Block}。
 * <p>
 * 它带方块实体（热压差网络的消耗端），催熟 / 自动收获逻辑全在 {@link MechanicalSoilBlockEntity#serverTick}。
 */
public abstract class MechanicalSoilBlock extends Block implements EntityBlock, MechanicalSoil {
    /** 服务端 ticker（催熟 / 自动收获），见 {@link MechanicalSoilBlockEntity#serverTick}。 */
    private static final BlockEntityTicker<MechanicalSoilBlockEntity> TICKER = MechanicalSoilBlockEntity::serverTick;

    protected MechanicalSoilBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanicalSoilBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != FarmlandBlocks.MECHANICAL_SOIL_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) TICKER;
    }
}
