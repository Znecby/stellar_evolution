package dev.znecby.stellar_evolution.engine;

import com.mojang.serialization.MapCodec;
import dev.znecby.stellar_evolution.bio.BioMachineBlocks;
import dev.znecby.stellar_evolution.large.LargeMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 航空航天火箭引擎（2×2×2 大机器）：中高 DP / 中高 HP，自带液氧，真空可用。
 * <p>
 * {@link #FACING} = <strong>喷口朝向</strong>（4 套预旋转模型与尾焰都朝这一面）。放置时喷口朝玩家视线方向，
 * 不会对着自己喷；尾焰长 8 格，会把生物吹飞、点燃并直接烧死。
 */
public class RocketEngineBlock extends BaseEntityBlock implements EngineMachineBlock, LargeMachineBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RocketEngineBlock(Properties properties) {
        super(properties);
        // BooleanProperty 的隐式默认值是 true —— 不显式登记全 false 的默认态，
        // 新放的机器一上来就是「12 个接点都接着铜管」（与生化机器同一坑）。
        BlockState defaultState = defaultBlockState();
        for (BooleanProperty port : BioMachineBlocks.PORT_PROPERTIES.values()) {
            defaultState = defaultState.setValue(port, false);
        }
        registerDefaultState(defaultState);
    }

    @Override
    protected MapCodec<? extends RocketEngineBlock> codec() {
        return simpleCodec(RocketEngineBlock::new);
    }

    /** 登记朝向 + 12 个「机械管接点」（铜垫）状态，与生化 / 石化机器同一口径。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        BioMachineBlocks.PORT_PROPERTIES.values().forEach(builder::add);
    }

    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return pos;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 喷口朝玩家视线方向（背对玩家）
        Direction facing = context.getHorizontalDirection();
        if (!EngineMachineSupport.canOccupy(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            EngineMachineSupport.fillShells(level, pos, EngineMachines.ROCKET_SHELL.get());
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        EngineMachineSupport.clearShells(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RocketEngineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, EngineMachines.ROCKET_ENTITY.get(), RocketEngineBlockEntity::tick);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.getRotation(state.getValue(FACING)).rotate(state.getValue(FACING)));
    }
}
