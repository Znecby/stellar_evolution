package dev.modmind.stellar_evolution.fission;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 裂变堆方块（原水冷反应堆，《核裂变线预规划蓝图》§3.3 / §13.3）——<strong>2×2×2 整机</strong>。
 *
 * <p>连续运行：输入箱里的燃料棒 / 石墨棒 / 反射棒共同决定通量、效率与三层温度；
 * 壳温 1500℃ 起累积损毁、1800℃ 瞬间熔毁；蒸汽数据经压力管网络送出。
 * 壳温超过 1500℃ 时从机械网络抽取<strong>硼酸流体</strong>注入停堆（2026-11-08 起不再用物品右键）。
 * <strong>无界面</strong>（右键不弹 GUI），也不接热压差网络。
 */
public class WaterReactorBlock extends BaseEntityBlock implements LargeMachineBlock {
    /** 正面朝向：放置时正面朝玩家；7 个填充格与渲染可动部件都跟着转。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<WaterReactorBlock> CODEC = simpleCodec(WaterReactorBlock::new);

    public WaterReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(FissionMachineStructure.withPortsOff(defaultBlockState())
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        FissionMachineStructure.addPortStates(builder);
    }

    /** 主方块自己就是机器原点。 */
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return pos;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!FissionMachineStructure.canOccupy(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return super.getStateForPlacement(context).setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            FissionMachineStructure.fillShells(level, pos);
            FissionMachineStructure.refreshPorts(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        FissionMachineStructure.clearShells(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterReactorBlockEntity(pos, state);
    }

    /** 两端都挂：客户端推进可动部件转角，服务端跑反应堆。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityTicker<T> ticker = createTickerHelper(type, FissionReactors.REACTOR_ENTITY.get(),
                WaterReactorBlockEntity::tick);
        if (ticker == null) {
            ticker = createTickerHelper(type, FissionReactors.LEGACY_REACTOR_ENTITY.get(),
                    WaterReactorBlockEntity::tick);
        }
        return ticker;
    }
}
