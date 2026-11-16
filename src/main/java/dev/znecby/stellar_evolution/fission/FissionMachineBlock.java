package dev.znecby.stellar_evolution.fission;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 裂变线燃料机器方块（共用类；机种见 {@link FissionMachineKind}）。
 *
 * <p><strong>形态：2×2×2 整机</strong>（照生化 / 石化线口径）——主方块在最小角，其余 7 格由不可见填充格占位；
 * 造型 = 一圈 2px <strong>下界合金框架</strong> + 机体 + 底部连接件，每台 2～3 个可动部件。
 *
 * <p>热压差接入：方块实体实现 {@code ThermalConsumer}；只要与热压差网络（铜管 / 铜块）相邻即自动接入。
 */
public class FissionMachineBlock extends BaseEntityBlock implements LargeMachineBlock {
    /** 正面朝向：放置时正面朝玩家；7 个填充格与渲染可动部件都跟着转。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<FissionMachineBlock> CODEC = simpleCodec(FissionMachineBlock::new);

    public FissionMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(FissionMachineStructure.withPortsOff(defaultBlockState())
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** 12 个接管口状态（值由 {@link FissionMachineStructure#refreshPorts} 轮询维护）。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        FissionMachineStructure.addPortStates(builder);
    }

    /** 主方块自己就是机器原点（大机器框架从任意一格反查原点时用）。 */
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return pos;
    }

    /** 机器真实占据 8 格：除本格外的 7 格必须可被占用才能放置。 */
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

    /** 不挡光：机器占的那一格保持通透。 */
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
        if (FissionMachines.kindOf(state.getBlock()) == FissionMachineKind.MUON_FUSION) {
            return new dev.znecby.stellar_evolution.muon.MuonFusionBlockEntity(pos, state);
        }
        if (FissionMachines.kindOf(state.getBlock()) == FissionMachineKind.CYCLOTRON) {
            return new dev.znecby.stellar_evolution.hep.CyclotronBlockEntity(pos, state);
        }
        if (FissionMachines.kindOf(state.getBlock()) == FissionMachineKind.PARTICLE_SOURCE) {
            return new dev.znecby.stellar_evolution.particle.ParticleSourceBlockEntity(pos, state);
        }
        if (FissionMachines.kindOf(state.getBlock()) == FissionMachineKind.IRRADIATION_CHAMBER
                || FissionMachines.kindOf(state.getBlock()) == FissionMachineKind.COLLIDER) {
            return new dev.znecby.stellar_evolution.particle.BeamMachineBlockEntity(pos, state);
        }
        if (FissionMachines.kindOf(state.getBlock()) == FissionMachineKind.ADS) {
            return new dev.znecby.stellar_evolution.particle.AdsBlockEntity(pos, state);
        }
        if (FissionMachines.kindOf(state.getBlock()) == FissionMachineKind.ANNIHILATION_REACTOR) {
            return new dev.znecby.stellar_evolution.particle.AnnihilationReactorBlockEntity(pos, state);
        }
        return new FissionMachineBlockEntity(pos, state);
    }

    /** 两端都挂：客户端推进可动部件转角，服务端做工艺结算。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == FissionMachines.MUON_ENTITY.get()) {
            return createTickerHelper(type, FissionMachines.MUON_ENTITY.get(), FissionMachineBlockEntity::tick);
        }
        if (type == FissionMachines.CYCLOTRON_ENTITY.get()) {
            return createTickerHelper(type, FissionMachines.CYCLOTRON_ENTITY.get(), FissionMachineBlockEntity::tick);
        }
        if (type == FissionMachines.PARTICLE_SOURCE_ENTITY.get()) {
            return createTickerHelper(type, FissionMachines.PARTICLE_SOURCE_ENTITY.get(), FissionMachineBlockEntity::tick);
        }
        if (type == FissionMachines.IRRADIATION_CHAMBER_ENTITY.get()) {
            return createTickerHelper(type, FissionMachines.IRRADIATION_CHAMBER_ENTITY.get(), FissionMachineBlockEntity::tick);
        }
        if (type == FissionMachines.COLLIDER_ENTITY.get()) {
            return createTickerHelper(type, FissionMachines.COLLIDER_ENTITY.get(), FissionMachineBlockEntity::tick);
        }
        if (type == FissionMachines.ADS_ENTITY.get()) {
            return createTickerHelper(type, FissionMachines.ADS_ENTITY.get(), FissionMachineBlockEntity::tick);
        }
        if (type == FissionMachines.ANNIHILATION_ENTITY.get()) {
            return createTickerHelper(type, FissionMachines.ANNIHILATION_ENTITY.get(), FissionMachineBlockEntity::tick);
        }
        return createTickerHelper(type, FissionMachines.MACHINE_ENTITY.get(), FissionMachineBlockEntity::tick);
    }
}
