package dev.modmind.stellar_evolution.coal;

import com.mojang.serialization.MapCodec;
import dev.modmind.stellar_evolution.large.LargeMachineBlock;
import dev.modmind.stellar_evolution.multiblock.MultiblockControllerBlock;
import dev.modmind.stellar_evolution.multiblock.MultiblockControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 焦化炉控制器：装在 3×3×4 砖块炉体某一侧面的正中心。
 *
 * <p>{@link #LIT} 表示炉子正在干馏（有热流率且料够），决定正面炉门贴图与亮度。
 */
public class CokeOvenControllerBlock extends MultiblockControllerBlock implements LargeMachineBlock {
    /** 干馏中：正面炉门发亮。 */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CokeOvenControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(CokeOvenControllerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    protected BlockEntityType<? extends MultiblockControllerBlockEntity> blockEntityType() {
        return CokeOvenBlocks.CONTROLLER_ENTITY.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CokeOvenControllerBlockEntity(pos, state);
    }

    /** 大机器框架 / Jade 从控制器本体反查原点（未成型不算机器）。 */
    @Nullable
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return level instanceof net.minecraft.world.level.Level serverLevel
                && serverLevel.getBlockEntity(pos) instanceof CokeOvenControllerBlockEntity controller
                && controller.isFormed() ? pos : null;
    }
}
