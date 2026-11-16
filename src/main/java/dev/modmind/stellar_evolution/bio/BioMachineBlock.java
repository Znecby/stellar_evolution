package dev.modmind.stellar_evolution.bio;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 生化线单方块机器：调浆机 / 发酵罐 / 离心机 / 灭菌罐 / 结晶机 / 生物反应器。
 * <p>
 * <strong>模型是 2×2×2</strong>（模型坐标 0..32）：整机以本方块的 <b>min 角</b> 为基点，向 +X / +Y / +Z 各外扩一格。
 * 造型 = 一圈 2px 铜框架 + 机体 + 底部 8 个铜管连接件（每面 2 个，同时把机体与铜框架连成一体）。
 * <p>
 * <strong>碰撞是「8 个整格」</strong>：本方块占 1 格，另外 7 格由不可见填充格 {@link BioMachineShellBlock} 占位
 * （见下方字段注释，不要改回超格 VoxelShape）。
 * <p>
 * <strong>朝向已取消</strong>：原版朝向是绕方块中心（8,8,8）转 90°，对超格模型会把整机甩出去，
 * 故改为固定朝北的单变体（{@code blockstates/<id>.json} 里只有空属性名）。
 * <p>
 * 每台机器有一个<b>可动部件</b>由 {@link BioMachineBlockEntity} + {@code client.BioMachineRenderer} 驱动
 * （借隐藏载体方块 {@code <id>_rotor} 的烘焙模型旋转）；<b>生物反应釜</b>是「大型机器」
 * （实现 {@link dev.modmind.stellar_evolution.large.LargeMachineBlock}，接入大机器框架），其余机器暂无机制。
 */
public class BioMachineBlock extends BaseEntityBlock implements LargeMachineBlock {
    /** 正面朝向：放置时正面朝玩家；7 个填充格与渲染可动部件都跟着转。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * <strong>碰撞交给「8 个 1×1×1」承担</strong>：本方块自己的形状就是普通整格（默认 {@code Shapes.block()}），
     * 另外 7 格由不可见填充格 {@link BioMachineShellBlock} 占位。
     * <p>
     * <b>为什么不用超格 VoxelShape</b>（曾用过 {@code 0..32}）：
     * <ul>
     *   <li>原版方块碰撞形状一般不能超过 1×1×1，超格会漏格（玩家能钻进机器）；</li>
     *   <li>更重要：<b>遮挡形状（环境光/AO）默认取自 {@code getShape}</b>，超格形状会让机器那一格被当作
     *       「一整格实心」，机器自身与周围面的环境光被算黑 —— 就是此前的「光照渲染错了」。</li>
     * </ul>
     * 模型仍是 2×2×2（模型可以超格，只是碰撞形状不超格）。
     */
    public BioMachineBlock(Properties properties) {
        super(properties);
        // BooleanProperty 的隐式默认值是 true —— 不显式登记全 false 的默认态，新放的机器一上来
        // 就是「5 个面都接着铜管」，模型会全部切成 open 态（与铜管那处的坑同源）。
        BlockState defaultState = defaultBlockState();
        defaultState = defaultState.setValue(FACING, Direction.NORTH);
        for (BooleanProperty port : BioMachineBlocks.PORT_PROPERTIES.values()) {
            defaultState = defaultState.setValue(port, false);
        }
        registerDefaultState(defaultState);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(BioMachineBlock::new);
    }

    /** 主方块自己就是机器原点（大机器框架从任意一格反查原点时用）。 */
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return pos;
    }

    /**
     * 登记 5 个「接管口」状态（北 / 东 / 南 / 西 / 下）。值由 {@link BioMachineBlocks#refreshPorts} 轮询维护，
     * blockstate 用它在该面切「铜垫 capped / 已接通 open」两套接口模型。
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        BioMachineBlocks.PORT_PROPERTIES.values().forEach(builder::add);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** 不挡光：机器占的那一格保持通透，避免机器自身/内部被压暗（与填充格一致）。 */
    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 机器**真实占据 8 格**（2×2×2）：除本格外的 7 格必须可被占用才能放置，
     * 放下后由 {@link #onPlace} 填成不可见填充格（{@link BioMachineShellBlock}），
     * 这样碰撞才是**完整 8 格**（原版方块碰撞形状一般不能超过 1×1×1）。
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!BioMachineBlocks.canOccupy(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return super.getStateForPlacement(context).setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            BioMachineBlocks.fillShells(level, pos, BioMachineBlocks.kindOf(state.getBlock()));
            BioMachineBlocks.refreshPorts(level, pos);
        }
    }

    /** 机器被移除时，把自己占的另外 7 格填充格一起清掉。 */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BioMachineBlocks.clearShells(level, pos);
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
        return new BioMachineBlockEntity(pos, state);
    }

    /** 两端都挂：客户端推进可动部件转角，服务端 {@code tick} 里直接返回（见 {@link BioMachineBlockEntity#tick}）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BioMachineBlocks.BIO_MACHINE_ENTITY.get(), BioMachineBlockEntity::tick);
    }
}
