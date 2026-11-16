package dev.znecby.stellar_evolution.petro;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 石化线单方块机器的<strong>主方块</strong>（分馏塔 / 裂解炉 / 重整器 / 聚合釜 / 挤出机 / 硫化机）——
 * 照生化线 {@code BioMachineBlock} 口径。
 *
 * <p><strong>模型是 2×2×2</strong>（模型坐标 0..32）：整机以本方块的 <b>min 角</b> 为基点，向 +X / +Y / +Z 各外扩一格。
 * 造型 = 一圈 2px 铜框架 + 机体 + 底部 8 个铜管连接件。
 *
 * <p><strong>碰撞是「8 个整格」</strong>：本方块占 1 格，另外 7 格由不可见填充格 {@link PetroMachineShellBlock} 占位
 * （超格 VoxelShape 会漏格、还会把机器那格算成实心而压暗，故不用）。
 *
 * <p>每台机器 2 个可动部件由 {@link PetroMachineBlockEntity} + {@code client.PetroMachineRenderer} 驱动
 * （借隐藏载体方块的烘焙模型旋转）；机器是「大型机器」（实现 {@link LargeMachineBlock}，接入大机器框架）。
 */
public class PetroMachineBlock extends BaseEntityBlock implements LargeMachineBlock {
    /** 正面朝向：放置时正面朝玩家；7 个填充格与渲染可动部件都跟着转。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<PetroMachineBlock> CODEC = simpleCodec(PetroMachineBlock::new);

    public PetroMachineBlock(Properties properties) {
        super(properties);
        // BooleanProperty 的隐式默认值是 true —— 不显式登记全 false 的默认态，新放的机器一上来
        // 就是「12 个口都接着铜管」，模型会全部切成已接态（与铜管那处的坑同源）。
        BlockState defaultState = defaultBlockState();
        defaultState = defaultState.setValue(FACING, Direction.NORTH);
        for (BooleanProperty port : PetroMachineBlocks.PORT_PROPERTIES.values()) {
            defaultState = defaultState.setValue(port, false);
        }
        registerDefaultState(defaultState);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * 登记 12 个「接管口」状态（北 / 东 / 南 / 西各 2 + 底面 4）。值由 {@link PetroMachineBlocks#refreshPorts}
     * 轮询维护，blockstate 用它在该口切「铜垫 / 已接通」两套接口模型。
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        PetroMachineBlocks.PORT_PROPERTIES.values().forEach(builder::add);
    }

    /** 主方块自己就是机器原点（大机器框架从任意一格反查原点时用）。 */
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return pos;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** 不挡光：机器占的那一格保持通透，避免机器自身 / 内部被压暗（与填充格一致）。 */
    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 机器真实占据 8 格（2×2×2）：除本格外的 7 格必须可被占用才能放置。 */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!PetroMachineBlocks.canOccupy(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return super.getStateForPlacement(context).setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            PetroMachineBlocks.fillShells(level, pos, PetroMachineBlocks.kindOf(state.getBlock()));
            PetroMachineBlocks.refreshPorts(level, pos);
        }
    }

    /** 机器被移除时，把自己占的另外 7 格填充格一起清掉。 */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        PetroMachineBlocks.clearShells(level, pos);
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PetroMachineBlockEntity(pos, state);
    }

    /** 两端都挂：客户端推进可动部件转角，服务端 {@code tick} 里做工艺结算。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, PetroMachineBlocks.PETRO_MACHINE_ENTITY.get(), PetroMachineBlockEntity::tick);
    }
}
