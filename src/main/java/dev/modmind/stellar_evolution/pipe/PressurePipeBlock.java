package dev.modmind.stellar_evolution.pipe;

import com.mojang.serialization.MapCodec;
import dev.modmind.stellar_evolution.large.LargeMachines;
import dev.modmind.stellar_evolution.steam.SteamEndpoint;
import java.util.Map;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * <strong>压力管</strong>：<b>蒸汽网络的载体</b> —— 外形与连接手感照 {@link MachinePipeBlock}，
 * 但<b>实心</b>（不像机械管那样中心 2×2 空心）：中心 2×2 用<strong>原版深板岩瓦</strong>、
 * 其余外皮用<strong>原版下界合金块</strong>（见 {@code tools/gen_large_copper_pipe.ps1}）。
 * <ul>
 *   <li><strong>连接</strong>：6 面各一个连接属性，值 = 该方向方块是不是同类压力管 / 蒸汽端点；放置时定初值，之后靠
 *       {@link #updateShape} 跟随邻居变化。另有一套 6 个 {@code *_cut} 属性记「该端被手动切断」，
 *       手持<strong>剪刀</strong>右键某段管臂即切换该端连接（与机械管相同）。</li>
 *   <li><strong>互连范围</strong>（见 {@link #connectsTo}）：同类压力管互连；另外接
 *       <strong>蒸汽网络的端点</strong>（{@link SteamEndpoint}：裂变堆 / 汽轮机 / 聚变仪），
 *       接法与机械管<strong>完全一致</strong>：只在机器<strong>底排</strong>接管（侧面最底一层 + 正下方），顶面不接。
 *       不接机械管 / 铜管。</li>
 *   <li><strong>只承载蒸汽</strong>：<b>不实现</b> {@code ThermalTransmitter}、也不是
 *       {@link MachinePipeBlock} 的子类 —— 不进热压差网络、不进大机器物流网络；蒸汽数据由
 *       {@link dev.modmind.stellar_evolution.steam.SteamNetwork} 沿压力管做连通聚合。</li>
 * </ul>
 * 模型见 {@code assets/stellar_evolution/blockstates/pressure_pipe.json}：中心接头常驻 + 6 个方向各叠一个管臂
 * （臂模型只画 -Z 方向，其余方向用 {@code y/x} 旋转复用）。
 */
public class PressurePipeBlock extends Block {
    /** 六个方向 → 连接状态属性（沿用原版 {@code BlockStateProperties} 的那六个，语义与 {@code PipeBlock} 一致）。 */
    public static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN);

    /** 六个方向 → 「该端被手动切断」属性。为 true 时强制该方向不连接（剪刀右键切换）。 */
    public static final Map<Direction, BooleanProperty> CUTS = Map.of(
            Direction.NORTH, BooleanProperty.create("north_cut"),
            Direction.EAST, BooleanProperty.create("east_cut"),
            Direction.SOUTH, BooleanProperty.create("south_cut"),
            Direction.WEST, BooleanProperty.create("west_cut"),
            Direction.UP, BooleanProperty.create("up_cut"),
            Direction.DOWN, BooleanProperty.create("down_cut"));

    /** 固定顺序的方向表，用于把 6 个连接属性打包成一个 6 位掩码（索引对应 {@link #SHAPES} 的位）。 */
    private static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};

    /** 常驻的中心空腔框（外径 6px；空心只在模型上体现，碰撞按实心 6×6 算）。 */
    private static final VoxelShape CENTER = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

    /** 每个方向的管臂：从该方向的面起、6px 见方、伸到中心框边界。 */
    private static final VoxelShape[] ARM_SHAPES = {
            Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 6.0),     // NORTH (-Z)
            Block.box(10.0, 5.0, 5.0, 16.0, 11.0, 11.0),   // EAST  (+X)
            Block.box(5.0, 5.0, 10.0, 11.0, 11.0, 16.0),   // SOUTH (+Z)
            Block.box(0.0, 5.0, 5.0, 6.0, 11.0, 11.0),     // WEST  (-X)
            Block.box(5.0, 10.0, 5.0, 11.0, 16.0, 11.0),   // UP    (+Y)
            Block.box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0)};    // DOWN  (-Y)

    /** 6 位掩码 → 预合并好的形状（中心 + 各连接方向的管臂），避免每次查询都重新求并集。 */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < SHAPES.length; mask++) {
            VoxelShape shape = CENTER;
            for (int i = 0; i < DIRECTIONS.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    shape = Shapes.or(shape, ARM_SHAPES[i]);
                }
            }
            SHAPES[mask] = shape;
        }
    }

    public PressurePipeBlock(Properties properties) {
        super(properties);
        // BooleanProperty 的隐式默认值是 true（VALUES = [true, false]）—— 不显式登记默认态的话，
        // 6 个连接位与 6 个切断位都会是 true，后者会让 updateShape 每次都把连接强制关掉。
        BlockState defaultState = defaultBlockState();
        for (BooleanProperty connection : CONNECTIONS.values()) {
            defaultState = defaultState.setValue(connection, false);
        }
        for (BooleanProperty cut : CUTS.values()) {
            defaultState = defaultState.setValue(cut, false);
        }
        registerDefaultState(defaultState);
    }

    @Override
    protected MapCodec<? extends PressurePipeBlock> codec() {
        return simpleCodec(PressurePipeBlock::new);
    }

    /** 把当前连接状态打包成 6 位掩码（位序同 {@link #DIRECTIONS}），用作 {@link #SHAPES} 的索引。 */
    private static int mask(BlockState state) {
        int mask = 0;
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (state.getValue(CONNECTIONS.get(DIRECTIONS[i]))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** 选择框 = 管身形状（中心框 + 已连接的管臂）。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[mask(state)];
    }

    /** 碰撞箱与选择框一致，只按管身占位（空心只在模型上体现）。 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[mask(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        CUTS.values().forEach(builder::add);
    }

    /** 放置时按「6 个邻居各自是不是压力管 / 蒸汽端点」定初始连接状态；默认态（全 false）就是一根孤立的短接头。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
            Direction direction = entry.getKey();
            BlockPos neighborPos = context.getClickedPos().relative(direction);
            state = state.setValue(entry.getValue(),
                    connectsTo(context.getLevel(), context.getClickedPos(), neighborPos, direction));
        }
        return state;
    }

    /**
     * 邻居变化时只改「朝向该邻居」的那一个方向位。
     * <p>{@code direction} 是<strong>从本方块指向邻居</strong>的方向（原版约定）。若邻居已不可接管，
     * 则顺手清掉该端的「手动切断」标记，避免标残留导致以后放回管子却不连。
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        BooleanProperty connection = CONNECTIONS.get(direction);
        BooleanProperty cut = CUTS.get(direction);
        if (connection == null || cut == null) {
            return state;
        }
        boolean connectable = connectsTo(level, pos, neighborPos, direction);
        boolean cutValue = state.getValue(cut) && connectable;
        return state.setValue(cut, cutValue).setValue(connection, connectable && !cutValue);
    }

    /**
     * 互连规则（压力管 = 蒸汽网络载体）：同类压力管，或蒸汽端点（裂变堆 / 汽轮机 / 聚变仪）。
     * <p>端点接法与机械管<strong>完全一致</strong>：只在机器<strong>底排</strong>接管 —— 四个水平侧面接最底那一层、
     * 底面从正下方接上来，顶面不接（机器占 2×2×2，管子可能贴着主方块也可能贴着填充格，都反查回原点再判）。
     */
    private static boolean connectsTo(BlockGetter level, BlockPos pipePos, BlockPos neighborPos, Direction direction) {
        if (level.getBlockState(neighborPos).getBlock() instanceof PressurePipeBlock) {
            return true;
        }
        BlockPos origin = LargeMachines.originOf(level, neighborPos);
        if (origin == null || !(level.getBlockEntity(origin) instanceof SteamEndpoint)) {
            return false;
        }
        return connectsToMachineBottomRow(level, pipePos, origin, direction);
    }

    /** 大机器的「底排接管」判据（与 {@code MachinePipeBlock} 同口径）。 */
    private static boolean connectsToMachineBottomRow(BlockGetter level, BlockPos pipePos, BlockPos origin,
                                                      Direction direction) {
        if (direction == Direction.UP) {
            return pipePos.getY() == origin.getY() - 1;    // 从正下方接上来
        }
        if (direction.getAxis().isHorizontal()) {
            return pipePos.getY() == origin.getY();        // 侧面最底那一排
        }
        return false;                                      // 机器顶面不接管
    }

    /**
     * 手持剪刀右键：切换「对准的这一端」的连接 —— 切 → 断，再右键 → 复。
     * <p>方向判定优先看命中的<strong>管臂</strong>（细管四周的面法线是垂直的，靠法线分不出是哪根臂）；
     * 瞄到中心块（或已断端没有臂）时退回用命中面的法线。切断会同步作用到对面的同类压力管；非剪刀一律放行。
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(Items.SHEARS)) {
            return InteractionResult.PASS;
        }
        Direction direction = aimedDirection(state, pos, hitResult);
        BooleanProperty connection = CONNECTIONS.get(direction);
        BooleanProperty cut = CUTS.get(direction);
        boolean cutNow = !state.getValue(cut);
        if (cutNow && !state.getValue(connection)) {
            return InteractionResult.PASS;   // 该端既没连上、也没被切过，无从切断
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        level.setBlock(pos, state
                .setValue(cut, cutNow)
                .setValue(connection, !cutNow && connectsTo(level, pos, neighborPos, direction)), Block.UPDATE_ALL);
        cutNeighbor(level, neighborPos, neighborState, direction.getOpposite(), cutNow);
        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /** 对面若是同类压力管，同步切换它这一端，避免单边断开后管臂插进对方的格子里。 */
    private static void cutNeighbor(Level level, BlockPos neighborPos, BlockState neighborState, Direction direction,
                                    boolean cutNow) {
        if (!(neighborState.getBlock() instanceof PressurePipeBlock)) {
            return;
        }
        boolean connectable = connectsTo(level, neighborPos, neighborPos.relative(direction), direction);
        level.setBlock(neighborPos, neighborState
                .setValue(CUTS.get(direction), cutNow)
                .setValue(CONNECTIONS.get(direction), !cutNow && connectable), Block.UPDATE_ALL);
    }

    /** 判定玩家「对准的那一端」：命中管臂用该臂方向，否则退回命中面法线。 */
    private static Direction aimedDirection(BlockState state, BlockPos pos, BlockHitResult hitResult) {
        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (state.getValue(CONNECTIONS.get(DIRECTIONS[i]))
                    && ARM_SHAPES[i].bounds().inflate(0.01).contains(local)) {
                return DIRECTIONS[i];
            }
        }
        return hitResult.getDirection();
    }
}
