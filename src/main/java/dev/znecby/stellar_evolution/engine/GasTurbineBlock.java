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
 * 联合燃气机组（2×2×2 大机器）：高 HP / 中 DP，可接铜格栅取水产出蒸汽（补 DP）。
 * <p>
 * 主方块占 min 角那一格，其余 7 格由 {@link EngineMachineShellBlock} 填充。
 * 液路：整台机器任意一格贴储罐取 GAS / AERO 燃料与液氧；任意一格贴原版铜格栅供水。
 */
public class GasTurbineBlock extends BaseEntityBlock implements EngineMachineBlock, LargeMachineBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public GasTurbineBlock(Properties properties) {
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
    protected MapCodec<? extends GasTurbineBlock> codec() {
        return simpleCodec(GasTurbineBlock::new);
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
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!EngineMachineSupport.canOccupy(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            EngineMachineSupport.fillShells(level, pos, EngineMachines.GAS_TURBINE_SHELL.get());
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
        return new GasTurbineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, EngineMachines.GAS_TURBINE_ENTITY.get(), GasTurbineBlockEntity::tick);
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
