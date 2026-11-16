package dev.modmind.stellar_evolution.pipe;

import com.mojang.serialization.MapCodec;
import dev.modmind.stellar_evolution.fission.ReactionChamberBlock;
import dev.modmind.stellar_evolution.large.LargeMachineBlock;
import dev.modmind.stellar_evolution.large.LargeMachines;
import dev.modmind.stellar_evolution.tank.TankBlock;
import dev.modmind.stellar_evolution.thermal.CopperHeat;
import dev.modmind.stellar_evolution.thermal.HotChamber;
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
 * <strong>机械管</strong>：大机器<b>物流网络</b>专用的管子 —— 外形与互连手感照铜管（{@link CopperPipeBlock}），
 * 但更粗、且中心空心：
 * <ul>
 *   <li><strong>断面</strong>：外径 <b>6×6</b>、壁厚 <b>2px</b>、中心 <b>2×2 空心</b>（沿轴向贯通）；
 *       外表面用<strong>切制铜块</strong>、空心内壁与中心空腔面用<strong>磨制黑石</strong>（见 {@code tools/gen_large_copper_pipe.ps1}）。</li>
 *   <li><strong>连接</strong>：6 面各一个连接属性，值 = 该方向方块是不是「可接管」；放置时定初值，之后靠
 *       {@link #updateShape} 跟随邻居变化。另有一套 6 个 {@code *_cut} 属性记「该端被手动切断」，
 *       手持<strong>剪刀</strong>右键某段管臂即切换该端连接。</li>
 *   <li><strong>互连范围</strong>（见 {@link #connectsTo}）：<b>只与同类机械管</b>互连；另外接
 *       <b>大机器</b>（底排 / 正下方，与压力管 / 束流管相同）与<b>物流容器</b>
 *       （铜箱子=任意面、储罐=任意面、反应仓=任意面、铜格栅=任意面）。
 *       「<b>热室</b>」（底部垫铜块的铜箱子，热压差结构）例外：<b>不接机械管</b>。
 *       <b>不与铜管对接</b>、也不接铜块 / 引擎 / 创造差压。</li>
 *   <li><strong>纯物流</strong>：<b>不实现</b> {@code ThermalTransmitter} —— 不导电、不参与热压差网络，
 *       所以也没有「超载红闪」那套状态。大机器物流网络（{@code large.LargeMachines}）<b>只认本方块</b>：
 *       普通铜管不再参与大机器网络。</li>
 * </ul>
 * 模型见 {@code assets/stellar_evolution/blockstates/machine_pipe.json}：中心空腔框常驻 + 6 个方向各叠一个管臂
 * （臂模型只画 -Z 方向，其余方向用 {@code y/x} 旋转复用）。
 */
public class MachinePipeBlock extends Block {
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

    public MachinePipeBlock(Properties properties) {
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
    protected MapCodec<? extends MachinePipeBlock> codec() {
        return simpleCodec(MachinePipeBlock::new);
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

    /** 放置时按「6 个邻居各自是不是可接管方块」定初始连接状态；默认态（全 false）就是一根孤立的短接头。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 一台机器一张物流网络：这个位置六面能碰到两台不同的机器时，直接放成孤立短接头，
        // 不允许把两台机器的管系接起来（旧存档里已经连在一起的由 LargeMachines 的归属分区兜底）。
        if (LargeMachines.nearbyMachineOrigins(context.getLevel(), context.getClickedPos()).size() > 1) {
            return defaultBlockState();
        }
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTIONS.entrySet()) {
            Direction direction = entry.getKey();
            BlockPos neighborPos = context.getClickedPos().relative(direction);
            state = state.setValue(entry.getValue(),
                    connectsTo(context.getLevel(), context.getClickedPos(),
                            context.getLevel().getBlockState(neighborPos), direction));
        }
        return state;
    }

    /**
     * 放置机械管时，把贴着它的铜格栅登记进「泵模式待办」。
     * <p>格栅是原版方块、没有方块实体，泵逻辑靠 {@code GrateSource} 的登记表驱动；
     * 这里补一条「放置即登记」，避免机器空闲（不查物流能力）时格栅一直不工作。
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (dev.modmind.stellar_evolution.tank.GrateSource.isGrate(level.getBlockState(neighbor))) {
                dev.modmind.stellar_evolution.tank.GrateSource.register(level, neighbor);
            }
        }
    }

    /** 邻居变化（例如在已有机械管旁放下铜格栅）时同样登记，保证格栅第一时间进入泵逻辑。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   net.minecraft.world.level.redstone.Orientation orientation,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (dev.modmind.stellar_evolution.tank.GrateSource.isGrate(level.getBlockState(neighbor))) {
                dev.modmind.stellar_evolution.tank.GrateSource.register(level, neighbor);
            }
        }
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
        boolean connectable = connectsTo(level, pos, neighborState, direction);
        boolean cutValue = state.getValue(cut) && connectable;
        return state.setValue(cut, cutValue).setValue(connection, connectable && !cutValue);
    }

    /**
     * 互连规则（机械管 = 纯物流管）：
     * <ul>
     *   <li><strong>同类机械管</strong>：永远互连（<b>不与普通铜管对接</b> —— 两者是两套管子）；</li>
     *   <li><strong>大机器</strong>（生化 / 石化，主方块或填充格）：只接<strong>底排</strong> —— 四个水平侧面接机器
     *       最底那一层，底面从正下方接上来（机器顶面不接管）。接上后对应那个「接管口」会变成铜垫态；</li>
     *   <li><strong>物流容器</strong>：铜箱子与储罐<strong>任意面</strong>皆可接管；
     *       但<strong>热室</strong>（底部垫铜块的铜箱子，见 {@link HotChamber}）<strong>不接</strong> —— 它是热压差结构；</li>
     *   <li>其余一律不接（铜块 / 热力引擎 / 燃烧引擎 / 创造差压 / 普通铜管 都不是本管的对接面）。</li>
     * </ul>
     */
    private boolean connectsTo(BlockGetter level, BlockPos pipePos, BlockState neighborState, Direction direction) {
        if (neighborState.getBlock() instanceof MachinePipeBlock) {
            // 管-管：两边若已分别归属不同机器，就不连；这样新放置/邻居更新都会在归属边界自动断开。
            BlockPos myOwner = LargeMachines.pipeOwner(level, pipePos);
            BlockPos theirOwner = LargeMachines.pipeOwner(level, pipePos.relative(direction));
            return myOwner == null || theirOwner == null || myOwner.equals(theirOwner);
        }
        Block neighbor = neighborState.getBlock();
        if (neighborState.is(CopperHeat.COPPER_CHESTS)) {
            // 物品容器：任意面都能接管 —— 但「热室」（底部垫铜块的热压差结构）不接机械管
            return HotChamber.stateAt(level, pipePos.relative(direction)) == HotChamber.State.NONE;
        }
        if (neighbor instanceof TankBlock) {
            // 储罐：任意面都可接管（与铜箱子同口径），不再限制上下
            return true;
        }
        if (neighbor instanceof ReactionChamberBlock) {
            return true;   // 反应仓：任意面接入，作为反应堆专属燃料容器
        }
        if (dev.modmind.stellar_evolution.tank.GrateSource.isGrate(neighborState)) {
            return true;   // 原版铜格栅：机械管网络的虚拟水/流体源，任意面都能接管
        }
        if (neighbor instanceof dev.modmind.stellar_evolution.hatch.HatchBlock) {
            // 机械物流仓：只有正面（铜块覆盖层那一面）能接机械管；且必须已被多方块控制器认领
            Direction facing = neighborState.getValue(dev.modmind.stellar_evolution.hatch.HatchBlock.FACING);
            BlockPos hatchPos = pipePos.relative(direction);
            return facing == direction.getOpposite() && LargeMachines.originOf(level, hatchPos) != null;
        }
        if (neighbor instanceof LargeMachineBlock) {
            // 机器-管：若这根管已归属另一台机器，禁止再接第二台。
            BlockPos candidate = LargeMachines.originOf(level, pipePos.relative(direction));
            BlockPos owner = LargeMachines.pipeOwner(level, pipePos);
            if (owner != null && candidate != null && !owner.equals(candidate)) {
                return false;
            }
            // 所有大机器（生化 / 石化 / 裂变……）共用同一套「底排接管」规则
            return connectsToMachineBottomRow(level, pipePos, pipePos.relative(direction), direction);
        }
        return false;
    }

    /**
     * 大机器（生化 / 石化 / 裂变同规则）：只在<strong>底排</strong>接管 —— 四个水平侧面接机器最底那一层，
     * 底面从正下方接上来，顶面不接。
     * <p>机器占 2×2×2（主方块在最小角，另外 7 格是填充格），所以管子可能贴着主方块、也可能贴着填充格，
     * 两种都要能反查出机器原点。
     */
    private static boolean connectsToMachineBottomRow(BlockGetter level, BlockPos pipePos,
                                                       BlockPos neighborPos, Direction direction) {
        return LargeMachines.pipeConnectsToMachineBottomRow(level, pipePos, neighborPos, direction);
    }

    /**
     * 手持剪刀右键：切换「对准的这一端」的连接 —— 切 → 断，再右键 → 复。
     * <p>方向判定优先看命中的<strong>管臂</strong>（细管四周的面法线是垂直的，靠法线分不出是哪根臂）；
     * 瞄到中心块（或已断端没有臂）时退回用命中面的法线。切断会同步作用到对面的同类机械管；非剪刀一律放行。
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
                .setValue(connection, !cutNow && connectsTo(level, pos, neighborState, direction)), Block.UPDATE_ALL);
        cutNeighbor(level, neighborPos, neighborState, direction.getOpposite(), cutNow);
        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /** 对面若是同类机械管，同步切换它这一端，避免单边断开后管臂插进对方的格子里。 */
    private static void cutNeighbor(Level level, BlockPos neighborPos, BlockState neighborState, Direction direction,
                                    boolean cutNow) {
        if (!(neighborState.getBlock() instanceof MachinePipeBlock neighborPipe)) {
            return;
        }
        boolean connectable = neighborPipe.connectsTo(level, neighborPos,
                level.getBlockState(neighborPos.relative(direction)), direction);
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
