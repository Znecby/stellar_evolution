package dev.modmind.stellar_evolution.engine;

import com.mojang.serialization.MapCodec;
import dev.modmind.stellar_evolution.bio.BioMachineBlocks;
import dev.modmind.stellar_evolution.large.LargeMachineBlock;
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
 * 工业内燃机组（2×2×2 大机器）：燃烧引擎的上位机组。
 * <p>
 * 主方块占 min 角那一格，其余 7 格由 {@link EngineMachineShellBlock} 填充（碰撞完整、不可进入）。
 * 模型坐标 0..32，正面（{@link #FACING}）朝玩家。液路：整台机器任意一格贴储罐都能取燃料与液氧；
 * 输出走热压差网络（铜块 / 铜管贴<b>任意一格</b>即可接入）。
 */
public class IndustrialEngineBlock extends BaseEntityBlock implements EngineMachineBlock, LargeMachineBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public IndustrialEngineBlock(Properties properties) {
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
    protected MapCodec<? extends IndustrialEngineBlock> codec() {
        return simpleCodec(IndustrialEngineBlock::new);
    }

    /** 登记朝向 + 12 个「机械管接点」（铜垫）状态，与生化 / 石化机器同一口径。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        BioMachineBlocks.PORT_PROPERTIES.values().forEach(builder::add);
    }

    /** 主方块自己就是机器原点。 */
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return pos;
    }

    /** 7 格必须可被占用才能放置；放下后填成不可见填充格（碰撞完整 8 格）。 */
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
            EngineMachineSupport.fillShells(level, pos, EngineMachines.INDUSTRIAL_SHELL.get());
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        EngineMachineSupport.clearShells(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /** 不挡光：机器占的那一格保持通透，避免自身 / 内部被压暗。 */
    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IndustrialEngineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, EngineMachines.INDUSTRIAL_ENTITY.get(), IndustrialEngineBlockEntity::tick);
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
