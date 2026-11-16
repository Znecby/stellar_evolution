package dev.modmind.stellar_evolution.massdriver;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/** 质量投射轨道炮控制器：图案来自客户端保存的 114514 结构。 */
public class MassDriverControllerBlock extends MultiblockControllerBlock implements LargeMachineBlock {
    /** 成型状态：决定正面覆盖层与发光。 */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public MassDriverControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(MassDriverControllerBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    protected BlockEntityType<? extends MultiblockControllerBlockEntity> blockEntityType() {
        return MassDriverBlocks.CONTROLLER_ENTITY.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MassDriverControllerBlockEntity(pos, state);
    }

    /** 大机器框架 / Jade 从控制器本体反查原点。 */
    @Nullable
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return level instanceof net.minecraft.world.level.Level serverLevel
                && serverLevel.getBlockEntity(pos) instanceof MassDriverControllerBlockEntity controller
                && controller.isFormed() ? pos : null;
    }
}
