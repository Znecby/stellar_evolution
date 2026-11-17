package dev.znecby.stellar_evolution.pipe;

import com.mojang.serialization.MapCodec;
import dev.znecby.stellar_evolution.large.LargeMachines;
import dev.znecby.stellar_evolution.stream.StreamEndpoint;
import dev.znecby.stellar_evolution.stream.StreamNode;
import dev.znecby.stellar_evolution.stream.StreamType;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * <strong>束流管</strong>：<b>粒子流网络的载体</b> —— 外形与压力管完全同口径（6×6 实心，
 * 外皮<strong>钨块</strong>、中心 2×2 <strong>磨制安山岩</strong>，见 {@code tools/gen_large_copper_pipe.ps1}）。
 *
 * <ul>
 *   <li><strong>连接</strong>：6 面各一个连接属性 + 6 个 {@code *_cut}（剪刀右键切断）。
 *       互连规则见 {@link #connectsTo}：<strong>同类型</strong>束流管 + <strong>同类型</strong>粒子流节点
 *       （{@link StreamNode}，目前是缪子聚变仪）；节点只在机器<strong>底排</strong>接管
 *       （侧面最底一层 + 正下方，顶面不接），与机械管完全一致。</li>
 *   <li><strong>单管单流</strong>：方块状态带 {@code stream}（存 {@link StreamType#ordinal()} 整数）；
 *       不同流的管子相邻也<strong>不连接</strong>。用整数而不是 EnumProperty，是为了让类型集合可以
 *       随 {@link StreamType} 增删而不用改方块状态格式（模型 / 贴图也不按流类型区分）。</li>
 *   <li><strong>只承载数据</strong>：无方块实体、无物品 / 流体能力，不进热压差 / 物流 / 蒸汽网络；
 *       粒子流数据由 {@code stream.StreamNetwork} 沿束流管做连通聚合。</li>
 * </ul>
 */
public class BeamPipeBlock extends Block {
    /** {@code stream} 属性的「空管」值：还没接到任何流，显示原版安山岩中心。 */
    public static final int EMPTY = StreamType.values().length;
    /** 粒子流类型（存 {@link StreamType#ordinal()}；{@link #EMPTY} = 空管）。 */
    public static final IntegerProperty STREAM = IntegerProperty.create("stream", 0, EMPTY);

    /** 六个方向 → 连接状态属性（与其它管道共用同一份定义，见 {@link PipeParts}）。 */
    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeParts.CONNECTIONS;

    /** 六个方向 → 「该端被手动切断」属性。 */
    public static final Map<Direction, BooleanProperty> CUTS = PipeParts.CUTS;

    /** 换流类型时最多扫多少根管子（防止误操作扫到巨型管网卡顿）。 */
    private static final int MAX_NETWORK_SCAN = 1_024;

    /** 6px 管身几何：单根管臂（剪刀瞄准）+ 64 个预合并形状（选择框 / 碰撞箱）。 */
    private static final PipeParts.ShapeSet SHAPE_SET = PipeParts.shapeSet(5.0, 11.0);

    public BeamPipeBlock(Properties properties) {
        super(properties);
        // 默认「未切断」：同类型 / 同为空的相邻管自动连成一段；类型不同时才必须用剪刀处理
        registerDefaultState(PipeParts.allDisconnected(defaultBlockState().setValue(STREAM, EMPTY)));
    }

    @Override
    protected MapCodec<? extends BeamPipeBlock> codec() {
        return simpleCodec(BeamPipeBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_SET.shapes()[PipeParts.mask(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_SET.shapes()[PipeParts.mask(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STREAM);
        PipeParts.addProperties(builder);
    }

    /** 新放的管子一律是<strong>空管 + 全部断开</strong>：类型只在用剪刀接上时继承。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(STREAM, EMPTY);
    }

    /** 环境变化时只重算连接位：<strong>不再自动继承类型</strong>（类型只在剪刀接上时传递）。 */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        BooleanProperty connection = CONNECTIONS.get(direction);
        BooleanProperty cut = CUTS.get(direction);
        if (connection == null || cut == null) {
            return state;
        }
        boolean connectable = !state.getValue(cut)
                && connectsTo(level, pos, neighborPos, state.getValue(STREAM), direction);
        return state.setValue(connection, connectable);
    }

    /**
     * 互连规则（只有<strong>未被切断</strong>的端才会连）：同类型束流管，或<strong>接受该类型</strong>的束流节点。
     * <p>束流不是流体：机器<strong>侧面任意一层</strong>都能接（2×2×2 共两层），底面从正下方接上来，顶面不接。
     */
    private static boolean connectsTo(BlockGetter level, BlockPos pipePos, BlockPos neighborPos, int streamOrdinal,
                                      Direction direction) {
        BlockState neighbor = level.getBlockState(neighborPos);
        if (neighbor.getBlock() instanceof BeamPipeBlock) {
            return neighbor.getValue(STREAM) == streamOrdinal
                    && !neighbor.getValue(CUTS.get(direction.getOpposite()));
        }
        if (streamOrdinal == EMPTY) {
            return false;   // 空管还没定型，不与机器相接
        }
        BlockPos origin = LargeMachines.originOf(level, neighborPos);
        if (origin == null || !(level.getBlockEntity(origin) instanceof StreamNode node)
                || !node.acceptsStream(StreamType.byOrdinal(streamOrdinal))) {
            return false;
        }
        return connectsToBeamMachine(pipePos, origin, direction);
    }

    /**
     * 机器接管判据（束流口径）：侧面<strong>任意一层</strong>、底面正下方可接；顶面不接。
     * <p>与机械管的「底排接管」不同 —— 束流引入口在机器中段，管段贴着机体哪一层都算接上。
     */
    private static boolean connectsToBeamMachine(BlockPos pipePos, BlockPos origin, Direction direction) {
        if (direction == Direction.DOWN) {
            return false;                                   // 机器顶面不接
        }
        if (direction == Direction.UP) {
            return pipePos.getY() == origin.getY() - 1;     // 从正下方接上来
        }
        int dy = pipePos.getY() - origin.getY();
        return dy == 0 || dy == 1;                          // 侧面两层都能接
    }

    /**
     * 手持剪刀右键：<strong>接上 / 断开</strong>对准的这一端；<strong>按住 Shift 右键</strong>：整段同类型管线换流类型。
     *
     * <p>接管规则（本批口径）：
     * <ul>
     *   <li><strong>管 ↔ 管</strong>：默认互不相连；接上时「空管」继承对方类型，两边都空则不生效，类型不同不接；</li>
     *   <li><strong>空管 ↔ 机器</strong>：只有<strong>有输出流</strong>的机器（对撞机 / 粒子源）能把空管定成它的输出流；
     *       纯消费者（辐照仓）不生效，仍是空管；</li>
     *   <li><strong>断开</strong>：有流来源的那一段保持类型，<strong>失去来源的那一段变回空管</strong>。</li>
     * </ul>
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(Items.SHEARS)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            cycleNetworkType(level, pos, state);
            level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 0.6F);
            return InteractionResult.SUCCESS;
        }
        Direction direction = aimedDirection(state, pos, hitResult);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos neighborPos = pos.relative(direction);
        if (state.getValue(CONNECTIONS.get(direction))) {
            setLink(level, pos, direction, false);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.getBlock() instanceof BeamPipeBlock) {
                setLink(level, neighborPos, direction.getOpposite(), false);
            }
            // 断开后：没有流来源的那一段变回空管
            orphanCleanup(level, pos);
            if (neighbor.getBlock() instanceof BeamPipeBlock) {
                orphanCleanup(level, neighborPos);
            }
        } else {
            tryLink(level, pos, state, direction, neighborPos);
        }
        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /** 把某一端设为「接上 / 断开」，并立刻重算该端的连接位。 */
    private static void setLink(Level level, BlockPos pos, Direction direction, boolean linked) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BeamPipeBlock)) {
            return;
        }
        boolean connectable = linked && connectsTo(level, pos, pos.relative(direction),
                state.getValue(STREAM), direction);
        level.setBlock(pos, state
                .setValue(CUTS.get(direction), !linked)
                .setValue(CONNECTIONS.get(direction), connectable), Block.UPDATE_ALL);
    }

    /**
     * 尝试把这一端接上。
     * <ul>
     *   <li>空管 ↔ 空管：直接连上（方便先把线铺好）；</li>
     *   <li>空管 ↔ 有类型管：<strong>整段连在一起的空管一起变成该类型</strong>（不用一段一段剪）；</li>
     *   <li>空管 ↔ 机器：只有「有输出流」的机器能给空管定型（对撞机 = 缪子流），纯消费者不生效；</li>
     *   <li>有类型管 ↔ 机器：类型被机器接受就直接接上。</li>
     * </ul>
     */
    private static void tryLink(Level level, BlockPos pos, BlockState state, Direction direction, BlockPos neighborPos) {
        BlockState neighbor = level.getBlockState(neighborPos);
        int type = state.getValue(STREAM);
        if (neighbor.getBlock() instanceof BeamPipeBlock) {
            int other = neighbor.getValue(STREAM);
            if (type == EMPTY && other == EMPTY) {
                setLink(level, pos, direction, true);
                setLink(level, neighborPos, direction.getOpposite(), true);
                return;
            }
            if (type != EMPTY && other != EMPTY && type != other) {
                return;                                   // 不同类型不接
            }
            int adopted = type == EMPTY ? other : type;
            // 空的那一段（整段）一次变成有类型的，边界自然接上
            if (type == EMPTY) {
                adoptComponent(level, pos, adopted);
            }
            if (other == EMPTY) {
                adoptComponent(level, neighborPos, adopted);
            }
            setLink(level, pos, direction, true);
            setLink(level, neighborPos, direction.getOpposite(), true);
            return;
        }
        BlockPos origin = LargeMachines.originOf(level, neighborPos);
        if (origin == null || !(level.getBlockEntity(origin) instanceof StreamNode node)) {
            return;
        }
        if (type == EMPTY) {
            // 规则：只有「有输出流」的机器能给空管定类型；纯消费者不生效
            if (!(level.getBlockEntity(origin) instanceof StreamEndpoint endpoint) || !endpoint.emitsStream()) {
                return;
            }
            adoptComponent(level, pos, endpoint.outputStreamType().ordinal());
            type = level.getBlockState(pos).getValue(STREAM);
        }
        if (!node.acceptsStream(StreamType.byOrdinal(type)) || !connectsToBeamMachine(pos, origin, direction)) {
            return;
        }
        setLink(level, pos, direction, true);
    }

    /** 把「从这一格出发、连着的一片空管」整段改成 {@code type}（遇到非空管或断开的端就停）。 */
    private static void adoptComponent(Level level, BlockPos start, int type) {
        Set<BlockPos> component = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        component.add(start);
        frontier.add(start);
        while (!frontier.isEmpty() && component.size() < MAX_NETWORK_SCAN) {
            BlockPos pos = frontier.poll();
            BlockState state = level.getBlockState(pos);
            for (Direction direction : PipeParts.DIRECTIONS) {
                if (!state.getValue(CONNECTIONS.get(direction))) {
                    continue;
                }
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighbor = level.getBlockState(neighborPos);
                if (neighbor.getBlock() instanceof BeamPipeBlock
                        && neighbor.getValue(STREAM) == EMPTY
                        && component.add(neighborPos)) {
                    frontier.add(neighborPos);
                }
            }
        }
        for (BlockPos pos : component) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BeamPipeBlock && state.getValue(STREAM) == EMPTY) {
                level.setBlock(pos, state.setValue(STREAM, type), Block.UPDATE_ALL);
            }
        }
    }

    /** 从这一格出发找同类型的「已连接管段」，整段没有流来源就全部变回空管。 */
    private static void orphanCleanup(Level level, BlockPos start) {
        BlockState startState = level.getBlockState(start);
        if (!(startState.getBlock() instanceof BeamPipeBlock)) {
            return;
        }
        int type = startState.getValue(STREAM);
        if (type == EMPTY) {
            return;
        }
        Set<BlockPos> component = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        component.add(start);
        frontier.add(start);
        while (!frontier.isEmpty() && component.size() < MAX_NETWORK_SCAN) {
            BlockPos pos = frontier.poll();
            BlockState state = level.getBlockState(pos);
            for (Direction direction : PipeParts.DIRECTIONS) {
                if (!state.getValue(CONNECTIONS.get(direction))) {
                    continue;
                }
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighbor = level.getBlockState(neighborPos);
                if (neighbor.getBlock() instanceof BeamPipeBlock
                        && neighbor.getValue(STREAM) == type
                        && component.add(neighborPos)) {
                    frontier.add(neighborPos);
                }
            }
        }
        if (hasStreamSource(level, component, type)) {
            return;
        }
        for (BlockPos pos : component) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BeamPipeBlock && state.getValue(STREAM) == type) {
                level.setBlock(pos, state.setValue(STREAM, EMPTY), Block.UPDATE_ALL);
            }
        }
    }

    /** 这段管网旁边有没有「往外喷这种流」的机器。 */
    private static boolean hasStreamSource(Level level, Collection<BlockPos> component, int type) {
        for (BlockPos pos : component) {
            for (Direction direction : PipeParts.DIRECTIONS) {
                BlockPos neighborPos = pos.relative(direction);
                if (level.getBlockState(neighborPos).getBlock() instanceof BeamPipeBlock) {
                    continue;
                }
                BlockPos origin = LargeMachines.originOf(level, neighborPos);
                if (origin != null && level.getBlockEntity(origin) instanceof StreamEndpoint endpoint
                        && endpoint.emitsStream() && endpoint.outputStreamType().ordinal() == type) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 把整条<strong>已连接的同类型管段</strong>换成下一种流。
     */
    private static void cycleNetworkType(Level level, BlockPos start, BlockState startState) {
        int old = startState.getValue(STREAM);
        int next = (old + 1) % (EMPTY + 1);
        Set<BlockPos> network = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        network.add(start);
        frontier.add(start);
        while (!frontier.isEmpty() && network.size() < MAX_NETWORK_SCAN) {
            BlockPos pos = frontier.poll();
            BlockState state = level.getBlockState(pos);
            for (Direction direction : PipeParts.DIRECTIONS) {
                if (!state.getValue(CONNECTIONS.get(direction))) {
                    continue;
                }
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighbor = level.getBlockState(neighborPos);
                if (neighbor.getBlock() instanceof BeamPipeBlock
                        && neighbor.getValue(STREAM) == old
                        && network.add(neighborPos)) {
                    frontier.add(neighborPos);
                }
            }
        }
        for (BlockPos pos : network) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BeamPipeBlock && state.getValue(STREAM) == old) {
                level.setBlock(pos, state.setValue(STREAM, next), Block.UPDATE_ALL);
            }
        }
    }

    private static Direction aimedDirection(BlockState state, BlockPos pos, BlockHitResult hitResult) {
        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (int i = 0; i < PipeParts.DIRECTIONS.length; i++) {
            if (state.getValue(CONNECTIONS.get(PipeParts.DIRECTIONS[i]))
                    && SHAPE_SET.arms()[i].bounds().inflate(0.01).contains(local)) {
                return PipeParts.DIRECTIONS[i];
            }
        }
        return hitResult.getDirection();
    }
}
