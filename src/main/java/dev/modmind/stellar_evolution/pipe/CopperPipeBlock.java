package dev.modmind.stellar_evolution.pipe;

import com.mojang.serialization.MapCodec;
import dev.modmind.stellar_evolution.engine.BurningEngineBlock;
import dev.modmind.stellar_evolution.engine.HeatEngineBlock;
import dev.modmind.stellar_evolution.large.LargeMachineBlock;
import dev.modmind.stellar_evolution.large.LargeMachines;
import dev.modmind.stellar_evolution.thermal.CopperHeat;
import dev.modmind.stellar_evolution.thermal.CreativePressureBlock;
import dev.modmind.stellar_evolution.thermal.ThermalConsumer;
import dev.modmind.stellar_evolution.thermal.ThermalGenerator;
import dev.modmind.stellar_evolution.thermal.ThermalNetwork;
import dev.modmind.stellar_evolution.thermal.ThermalTransmitter;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
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
 * 铜管：纯外观的「6 面自动连接」方块，版式取自存档里的 {@code copper_pipe} 结构
 * （中心 + 6 个面邻居，即 6 面全连接的「十字」）。
 * <p>
 * 只有外观，没有方块实体与能力接口：
 * <ul>
 *   <li><strong>连接</strong>：6 个方向各一个布尔属性，值 = 该方向相邻方块是不是「可接管方块」。
 *       放置时由 {@link #getStateForPlacement} 定初值，之后靠 {@link #updateShape} 跟随邻居变化
 *       （拆掉一根，两侧的管子会立刻回退成「T / 直 / 拐」）。</li>
 *   <li><strong>手动切断</strong>：另有一套 6 个 {@code *_cut} 属性记「该端被手动切断」。
 *       手持<strong>剪刀</strong>对准某段管臂右键即切换该端连接（切 → 断、再右键 → 复）；
 *       切断会<strong>同时</strong>作用到对面的同类铜管上，邻居换成不可接管方块后标记会自动清除。</li>
 *   <li><strong>尺寸</strong>：模型是 4px 见方的细管（整方块的 1/4），居中；碰撞箱与选择框
 *       与外观<strong>完全一致</strong>：一个 4px 中心块 + 每个已连接方向各一段 4px 方的管臂
 *       （见 {@link #SHAPES}），空手点选与行走碰撞都只「摸到」管身。</li>
 *   <li><strong>互连规则</strong>（见 {@link #connectsTo}）：同类铜管、铜块、创造差压、热力引擎 / 燃烧引擎的左右端面、
 *       限流管的两个端面、<strong>大机器</strong>（生成端 / 消耗端）的底排，以及实现了
 *       {@link ThermalConsumer}/{@link ThermalGenerator} 的<strong>单方块机器</strong>（破碎机 / 基岩钻等）。</li>
 *   <li><strong>热压差网络</strong>：铜管是那套网络的<strong>传递件</strong>（实现 {@link ThermalTransmitter}），
 *       只把网络连通、自己不生成也不消耗；连通遍历按 6 面相邻走，<strong>管臂朝向不参与判定</strong>，
 *       但<strong>被剪刀切断的那一端会真正断开网络</strong>（见 {@link #isCut}）。</li>
 * </ul>
 * 模型与方块状态见 {@code assets/stellar_evolution/blockstates/copper_pipe.json}：
 * 中心块常驻 + 6 个方向各叠一个「臂」，臂模型只有一个（-Z 方向），其余方向用 {@code y/x} 旋转复用。
 */
public class CopperPipeBlock extends Block implements ThermalTransmitter {
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

    /**
     * 该管所在的热压差网络是否<strong>超载</strong>。由 {@code thermal.ThermalNetwork#syncAlarm} 在服务端同步
     * （只在状态变化时写方块，稳态零开销）。
     */
    public static final BooleanProperty OVERLOADED = BooleanProperty.create("overloaded");

    /**
     * 超载告警的<strong>闪烁相位</strong>：只在 {@link #OVERLOADED} 为 true 时有意义，
     * 由本方块自己的<strong>计划刻</strong>每 {@link #BLINK_INTERVAL} 刻翻转一次。
     * <p>
     * 为什么要做成方块状态而不是渲染器里按时间算：方块颜色是<strong>烘焙进区块网格</strong>的，
     * 不换状态就不会重新烘焙 —— 想要「闪」必须让状态真的变（这也是原版红石线变色的路子）。
     */
    public static final BooleanProperty BLINKING = BooleanProperty.create("blinking");

    /** 告警闪烁的半周期（刻）：4 刻亮、4 刻暗，约合每秒一闪多一点。 */
    public static final int BLINK_INTERVAL = 4;

    /** 可接管的铜块家族（未氧化 + 氧化/打蜡各档），管子贴上去即接管。 */
    private static final Set<Block> COPPER_BLOCKS = Set.of(
            Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
            Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER,
            Blocks.WAXED_OXIDIZED_COPPER);

    /** 固定顺序的方向表，用于把 6 个连接属性打包成一个 6 位掩码（索引对应 {@link #SHAPES} 的位）。 */
    private static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};

    /** 常驻的中心 4px 立方（与模型 {@code copper_pipe/core} 的 6..10 对齐）。 */
    private static final VoxelShape CENTER = Block.box(6.0, 6.0, 6.0, 10.0, 10.0, 10.0);

    /** 每个方向的管臂：从该方向的面起、4px 见方、伸到中心块边界（与模型 {@code copper_pipe/arm} 对齐）。 */
    private static final VoxelShape[] ARM_SHAPES = {
            Block.box(6.0, 6.0, 0.0, 10.0, 10.0, 6.0),    // NORTH (-Z)
            Block.box(10.0, 6.0, 6.0, 16.0, 10.0, 10.0),  // EAST  (+X)
            Block.box(6.0, 6.0, 10.0, 10.0, 10.0, 16.0),  // SOUTH (+Z)
            Block.box(0.0, 6.0, 6.0, 6.0, 10.0, 10.0),    // WEST  (-X)
            Block.box(6.0, 10.0, 6.0, 10.0, 16.0, 10.0),  // UP    (+Y)
            Block.box(6.0, 0.0, 6.0, 10.0, 6.0, 10.0)};   // DOWN  (-Y)

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

    public CopperPipeBlock(Properties properties) {
        super(properties);
        // BooleanProperty 的隐式默认值是 true（BooleanProperty.VALUES = [true, false]，
        // StateDefinition.any() 取每个属性的第 0 个值）。不显式登记默认态的话，
        // 新状态的 6 个连接位与 6 个切断位都会是 true；后者会让 updateShape 的切断逻辑
        // 每次都把连接强制关掉 —— 表现为：刚放下的管子连得上，隔壁那根却永远不接管。
        BlockState defaultState = defaultBlockState();
        for (BooleanProperty connection : CONNECTIONS.values()) {
            defaultState = defaultState.setValue(connection, false);
        }
        for (BooleanProperty cut : CUTS.values()) {
            defaultState = defaultState.setValue(cut, false);
        }
        defaultState = defaultState.setValue(OVERLOADED, false).setValue(BLINKING, false);
        registerDefaultState(defaultState);
    }

    @Override
    protected MapCodec<? extends CopperPipeBlock> codec() {
        return simpleCodec(CopperPipeBlock::new);
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

    /**
     * 这一端是否被剪刀<strong>手动切断</strong> —— 热压差网络的连通判定用（见 {@code ThermalNetwork}）。
     * <p>不是铜管时一律 {@code false}；方向为空（不会发生）也按未切断处理。
     */
    public static boolean isCut(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CopperPipeBlock)) {
            return false;
        }
        BooleanProperty cut = CUTS.get(direction);
        return cut != null && state.getValue(cut);
    }

    /** 选择框 = 管身形状（中心块 + 已连接的管臂）。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[mask(state)];
    }

    /** 碰撞箱与选择框一致，只按管身占位。 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[mask(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        CUTS.values().forEach(builder::add);
        builder.add(OVERLOADED, BLINKING);
    }

    /**
     * 服务端：把「本网络是否超载」写到这根管子上 —— 只在状态<strong>变化</strong>时写方块，
     * 所以稳态（一直正常或一直超载）零开销。超载时顺手起一条<b>计划刻</b>链驱动告警闪烁。
     *
     * @return 是否真的改了状态
     */
    public static boolean setOverloaded(ServerLevel level, BlockPos pos, boolean overloaded) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CopperPipeBlock) || state.getValue(OVERLOADED) == overloaded) {
            return false;
        }
        BlockState updated = state.setValue(OVERLOADED, overloaded).setValue(BLINKING, false);
        level.setBlock(pos, updated, Block.UPDATE_ALL);
        if (overloaded) {
            level.scheduleTick(pos, updated.getBlock(), BLINK_INTERVAL);
        }
        return true;
    }

    /**
     * 告警闪烁的计划刻：超载时翻转 {@link #BLINKING} 并续上下一拍；一旦不再超载就把相位归零并停链。
     * <p>
     * <strong>自检</strong>：{@link ThermalNetwork#syncAlarm} 只由消耗端在自己的 ticker 里调用，
     * 所以「消耗端被拆掉 / 负载降下来」之后没人会再来重算 —— 光靠计划刻会让铜管一直红闪。
     * 这里每拍先向网络核对一次真实超载状态：不再超载时 syncAlarm 会把本网络所有铜管复位，
     * 本管也随之停链；仍超载则继续翻相位。
     * <p>
     * 颜色由客户端的 {@code client.ThermalPipeTintSource} 按「超载 + 亮相位」给出红色，
     * 状态一变区块网格就会重新烘焙，于是看到「闪」。
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OVERLOADED)) {
            if (state.getValue(BLINKING)) {
                level.setBlock(pos, state.setValue(BLINKING, false), Block.UPDATE_ALL);
            }
            return;
        }
        // 自检：消耗端可能已经不在网络上，没人再调 syncAlarm 了
        ThermalNetwork.syncAlarm(level, pos);
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof CopperPipeBlock) || !current.getValue(OVERLOADED)) {
            return;   // 已不再超载：syncAlarm 已复位本网络全部铜管，停链
        }
        level.setBlock(pos, current.setValue(BLINKING, !current.getValue(BLINKING)), Block.UPDATE_ALL);
        level.scheduleTick(pos, current.getBlock(), BLINK_INTERVAL);
    }

    /** 放置时按「6 个邻居各自是不是可接管方块」定初始连接状态；默认态（全 false）就是一根孤立的短接头。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
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
     * 邻居变化时只改「朝向该邻居」的那一个方向位。
     * <p>
     * {@code direction} 是<strong>从本方块指向邻居</strong>的方向（原版约定，与 {@code CrossCollisionBlock} 相同）。
     * 若邻居已不可接管，则顺手清掉该端的「手动切断」标记，避免标残留导致以后放回管子却不连。
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
     * 服务端：把<strong>单方块机器</strong>四周的铜管补连一遍（老存档自愈）。
     * <p>铜管的连接位只写一次（放置 / 邻居变化），代码更新前就放好的管子不会自己重算；
     * 由机器周期性调用本方法，把「朝向这台机器」的那一端补成已连接。被剪刀切断的端不动。
     */
    public static void refreshAround(Level level, BlockPos machinePos) {
        for (Direction direction : Direction.values()) {
            BlockPos pipePos = machinePos.relative(direction);
            if (!level.hasChunkAt(pipePos)) {
                continue;
            }
            BlockState state = level.getBlockState(pipePos);
            if (!(state.getBlock() instanceof CopperPipeBlock)) {
                continue;
            }
            Direction towardMachine = direction.getOpposite();
            BooleanProperty connection = CONNECTIONS.get(towardMachine);
            BooleanProperty cut = CUTS.get(towardMachine);
            if (connection == null || cut == null || state.getValue(cut) || state.getValue(connection)) {
                continue;
            }
            level.setBlock(pipePos, state.setValue(connection, true), Block.UPDATE_ALL);
        }
    }

    /**
     * 供机器「接管口」刷新用：判断这根铜管是否真的接在该机器格上。
     * <p>旧存档里铜管放置时没刷到连接位（或机器后放）时，连接位可能是 false，但热压差网络仍按
     * 邻接算连通；这里按同一套底排规则补写连接位，避免出现「热网通了但管臂 / 端口不亮」。
     *
     * @param pipePos       铜管位置
     * @param machinePos    机器格（主方块 / 填充格 / 物流仓）
     * @param towardMachine 从铜管指向机器格的方向
     */
    public static boolean connectsToMachine(Level level, BlockPos pipePos, BlockPos machinePos, Direction towardMachine) {
        BlockState state = level.getBlockState(pipePos);
        if (!(state.getBlock() instanceof CopperPipeBlock)) {
            return false;
        }
        BooleanProperty connection = CONNECTIONS.get(towardMachine);
        BooleanProperty cut = CUTS.get(towardMachine);
        if (connection == null || cut == null || state.getValue(cut)) {
            return false;
        }
        boolean connectable = LargeMachines.pipeConnectsToMachineBottomRow(level, pipePos, machinePos, towardMachine);
        if (connectable && !state.getValue(connection)) {
            level.setBlock(pipePos, state.setValue(connection, true), Block.UPDATE_ALL);
        }
        return connectable;
    }

    /**
     * 互连规则：
     * <ul>
     *   <li>同类铜管：永远互连；</li>
     *   <li><strong>铜块</strong>（{@link #COPPER_BLOCKS} 全家族）：任意面都接管；</li>
     *   <li><strong>创造差压</strong>（{@link CreativePressureBlock}）：任意面都接管（调试电源要能怼上铜管）；</li>
     *   <li><strong>大机器</strong>：生成端与消耗端都接，接法与机械管一致——只接<strong>底排</strong>
     *       （四侧最底层 + 正下方，顶面不接）；消耗端可直接经铜管接入热压差网络。</li>
     *   <li><strong>热力引擎 / 燃烧引擎</strong>：只连左右两个端面（水平、且垂直于其正面朝向 {@code FACING} 的那根轴，
     *       两侧中心正是那两块 6×6 铜板）。</li>
     * </ul>
     * <strong>不接储罐</strong>：铜管是热压差网络，储罐不参与热网；流体物流走<strong>机械管</strong>
     * （{@link MachinePipeBlock}）。
     * <p><strong>不接铜箱子</strong>：铜箱子（含大铜箱子）不再是热压差消耗端，接网络靠<strong>底部垫铜块</strong>
     * 组成的「热室」结构（见 {@code thermal.HotChamber}），细管贴上去不生管臂。
     * <p>要连别的方块时在这里加判断即可。
     */
    private boolean connectsTo(BlockGetter level, BlockPos pipePos, BlockState neighborState, Direction direction) {
        if (neighborState.is(this)) {
            return true;
        }
        Block neighbor = neighborState.getBlock();
        if (COPPER_BLOCKS.contains(neighbor)) {
            return true;
        }
        if (neighbor instanceof CreativePressureBlock) {
            return true;   // 创造差压（调试电源）：任意面都接管
        }
        if (neighbor instanceof FlowLimiterBlock) {
            // 限流管（热压差闸门）：**只有它那两个端面**能对接 —— 管子朝它伸出臂的那一面必须落在它的流向上
            return direction.getAxis() == neighborState.getValue(FlowLimiterBlock.FACING).getAxis();
        }
        if (neighbor instanceof LargeMachineBlock) {
            // 大机器（生成端 / 消耗端）都可经铜管接入；接法与机械管一致：只接底排
            BlockPos neighborPos = pipePos.relative(direction);
            return LargeMachines.pipeConnectsToMachineBottomRow(level, pipePos, neighborPos, direction);
        }
        Direction engineFacing = null;
        if (neighbor instanceof HeatEngineBlock) {
            engineFacing = neighborState.getValue(HeatEngineBlock.FACING);
        } else if (neighbor instanceof BurningEngineBlock) {
            engineFacing = neighborState.getValue(BurningEngineBlock.FACING);
        }
        if (engineFacing != null) {
            return direction.getAxis() != Direction.Axis.Y && direction.getAxis() != engineFacing.getAxis();
        }
        // 单方块热压差机器（破碎机 / 基岩钻等）：方块实体实现了消耗端 / 生成端即可接管，
        // 否则会出现「热网已经通了、管臂却不长」的假连接外观。
        BlockEntity neighborEntity = level.getBlockEntity(pipePos.relative(direction));
        return neighborEntity instanceof ThermalConsumer || neighborEntity instanceof ThermalGenerator;
    }

    /**
     * 手持剪刀右键：切换「对准的这一端」的连接 —— 切 → 断，再右键 → 复。
     * <p>
     * 方向判定优先看命中的<strong>管臂</strong>（细管四周的面法线是垂直的，靠法线分不出是哪根臂）；
     * 瞄到中心块（或已断端没有臂）时退回用命中面的法线，这样断开后点中心块的对应面即可复原。
     * 切断会同步作用到对面的同类铜管；非剪刀一律放行。
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
            // 该端既没连上、也没被切过，无从切断
            return InteractionResult.PASS;
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

    /** 对面若是同类铜管，同步切换它这一端，避免单边断开后管臂插进对方的格子里。 */
    private static void cutNeighbor(Level level, BlockPos neighborPos, BlockState neighborState, Direction direction,
                                    boolean cutNow) {
        if (!(neighborState.getBlock() instanceof CopperPipeBlock neighborPipe)) {
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
