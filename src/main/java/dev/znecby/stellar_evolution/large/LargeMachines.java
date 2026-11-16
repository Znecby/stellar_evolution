package dev.znecby.stellar_evolution.large;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.fission.ReactionChamberBlock;
import dev.znecby.stellar_evolution.hatch.HatchBlock;
import dev.znecby.stellar_evolution.pipe.CopperPipeBlock;
import dev.znecby.stellar_evolution.pipe.MachinePipeBlock;
import dev.znecby.stellar_evolution.tank.GrateSource;
import dev.znecby.stellar_evolution.tank.TankBlock;
import dev.znecby.stellar_evolution.tank.TankBlockEntity;
import dev.znecby.stellar_evolution.thermal.CopperHeat;
import dev.znecby.stellar_evolution.thermal.HotChamber;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 大型机器框架的<strong>几何与物流工具</strong>：能源接入判定、结构接管识别、物流网络遍历。
 *
 * <p><strong>「所有部分都视作本体」</strong>：机器占的全部格（主方块 + 填充格，见 {@link LargeMachine#occupiedCells()}）
 * 一律等价 —— 铜块 / 铜管贴<strong>任意一格的任意一面</strong>都算贴在机器上，端口的「管口美术」只是外观，不参与判定。
 *
 * <p>三条规则（对应需求）：
 * <ol>
 *   <li><strong>能源接入</strong>：<strong>消耗端</strong>可贴着铜块（{@link #touchesCopperBlock}），
 *       也可经<strong>底排铜管</strong>接入（{@link #touchesCopperPipe}）；<strong>生成端</strong>（蒸汽轮机）同样两者皆可；</li>
 *   <li><strong>物流只认机械管</strong>：只有<strong>机械管</strong>（{@code pipe.MachinePipeBlock}，6×6 空心那种）
 *       算机器网络的一员；<strong>普通铜管</strong>（{@code CopperPipeBlock}，热压差网络的传递件）与铜块都<strong>不算</strong>；</li>
 *   <li><strong>物流网络</strong>：从贴着机器任意一格的<strong>机械管</strong>出发，沿机械管做 6 面广度优先，
 *       途中的<strong>铜箱子</strong>（物品）与<strong>储罐</strong>（流体）就是这台机器的输入 / 输出容器。</li>
 * </ol>
 *
 * <p><strong>性能</strong>：{@link #connectedContainers} 做了「同一 tick 同一机器只算一次」的缓存
 * （按 {@link Level} 弱引用保存），与 {@code ThermalNetwork} 同款。
 */
public final class LargeMachines {
    /** 物流网络里的一个容器：位置 + 是流体（储罐）还是物品（铜箱子）。 */
    public record Connected(BlockPos pos, boolean fluid) {}

    // ------------------------------------------------------------------ 机器识别

    /** 这格（主方块 / 填充格）属于哪台机器的原点；不属于则 null。 */
    @Nullable
    public static BlockPos originOf(BlockGetter level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof LargeMachineBlock machineBlock ? machineBlock.largeMachineOrigin(level, pos) : null;
    }

    /** 原点的方块实体是不是一台「大型机器」；不是则 null。 */
    @Nullable
    public static LargeMachine machineAt(Level level, BlockPos origin) {
        return level.getBlockEntity(origin) instanceof LargeMachine machine && machine.isLargeMachine() ? machine : null;
    }

    // ------------------------------------------------------------------ 结构 / 能源判定

    /** 若 {@code pos} 贴着某台大型机器（机器的任意一格、任意一面），返回那台机器，否则 null。 */
    @Nullable
    public static LargeMachine machineTouching(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos origin = originOf(level, pos.relative(direction));
            if (origin != null && level.getBlockEntity(origin) instanceof LargeMachine machine
                    && machine.isLargeMachine()) {
                return machine;
            }
        }
        return null;
    }

    /**
     * 机械管归属的机器：从 {@code pipePos} 出发<strong>只沿机械管</strong>走连通片，片里若有管子直接贴着机器就返回它。
     *
     * <p>刻意<strong>不借道铜块</strong>：机器上贴的<strong>铜块是供能入口</strong>（它自己与从它引出的供能管
     * 属于热压差网络，不是机器的一部分），机器网络里<strong>只有机械管</strong>。
     */
    @Nullable
    public static LargeMachine pipeComponentMachine(BlockGetter level, BlockPos pipePos) {
        if (!isPipe(level, pipePos)) {
            return null;   // 起点不是机械管（例如铜箱子下方的铜块）→ 不算机器网络
        }
        Set<BlockPos> pipes = componentPipes(level, pipePos);
        Set<BlockPos> machines = componentMachineOrigins(level, pipes);
        Map<BlockPos, BlockPos> owners = pipeOwners(level, pipes, machines);
        BlockPos owner = owners.get(pipePos);
        if (owner == null) {
            return null;
        }
        return level.getBlockEntity(owner) instanceof LargeMachine machine && machine.isLargeMachine()
                ? machine : null;
    }

    /**
     * 机械管连通片：<strong>只沿真正接通的管臂走</strong>（剪刀切断的那一端不算）。
     * <p>两侧都要接通（本管朝该方向、邻管朝回），与 {@code MachinePipeBlock.updateShape} 的维护口径一致。
     */
    public static Set<BlockPos> componentPipes(BlockGetter level, BlockPos start) {
        Set<BlockPos> pipes = new LinkedHashSet<>();
        if (!isPipe(level, start)) {
            return pipes;
        }
        Deque<BlockPos> frontier = new ArrayDeque<>();
        pipes.add(start);
        frontier.add(start);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (isPipe(level, neighbor)
                        && pipeLinked(level, pos, direction)
                        && pipeLinked(level, neighbor, direction.getOpposite())
                        && pipes.add(neighbor)) {
                    frontier.add(neighbor);
                }
            }
        }
        return pipes;
    }

    /**
     * 这根机械管在 {@code direction} 方向上是否真的接通。
     * <p>剪刀右键会把该端的 {@code CONNECTIONS} 置 false（见 {@code MachinePipeBlock#useItemOn}），
     * 网络遍历必须看这个属性，否则「切断后 Jade / 取料仍算连着机器」。
     */
    private static boolean pipeLinked(BlockGetter level, BlockPos pipePos, Direction direction) {
        BlockState state = level.getBlockState(pipePos);
        if (!(state.getBlock() instanceof MachinePipeBlock)) {
            return false;
        }
        var connection = MachinePipeBlock.CONNECTIONS.get(direction);
        return connection != null && state.getValue(connection);
    }

    /** 容器某一面上的机械管所归属的机器；管臂被剪断（朝容器这一端断开）则不算接入。 */
    @Nullable
    private static LargeMachine containerPipeMachine(Level level, BlockPos containerPos, Direction direction) {
        BlockPos pipePos = containerPos.relative(direction);
        if (!pipeLinked(level, pipePos, direction.getOpposite())) {
            return null;
        }
        return pipeComponentMachine(level, pipePos);
    }

    /** 连通片里所有大机器原点（按世界坐标排序，保证归属稳定）。 */
    public static Set<BlockPos> componentMachineOrigins(BlockGetter level, BlockPos pipePos) {
        return componentMachineOrigins(level, componentPipes(level, pipePos));
    }

    private static Set<BlockPos> componentMachineOrigins(BlockGetter level, Set<BlockPos> pipes) {
        Set<BlockPos> machines = new java.util.TreeSet<>(java.util.Comparator.comparingLong(BlockPos::asLong));
        for (BlockPos pipe : pipes) {
            for (Direction direction : Direction.values()) {
                // 该端被剪断 = 这根管不再属于那台机器
                if (!pipeLinked(level, pipe, direction)) {
                    continue;
                }
                BlockPos origin = originOf(level, pipe.relative(direction));
                if (origin != null && level.getBlockEntity(origin) instanceof LargeMachine machine
                        && machine.isLargeMachine()) {
                    machines.add(origin);
                }
            }
        }
        return machines;
    }

    /**
     * 某根机械管归属的机器原点：连通片里<strong>最近的机器</strong>；同距离按原点坐标稳定排序。
     * <p>这样即使旧存档里两台机器的管系已经连在一起，也会在这里被逻辑上切成两半。
     */
    @Nullable
    public static BlockPos pipeOwner(BlockGetter level, BlockPos pipePos) {
        if (!isPipe(level, pipePos)) {
            return null;
        }
        Set<BlockPos> pipes = componentPipes(level, pipePos);
        Set<BlockPos> machines = componentMachineOrigins(level, pipes);
        return pipeOwners(level, pipes, machines).get(pipePos);
    }

    /**
     * 放置新机械管时，六面能碰到的所有大机器（包括相邻机械管连通片里的机器）。
     * 若返回 &gt;1 台，说明这个位置会把两台机器的管系接起来，放置逻辑应拒绝连接。
     */
    public static Set<BlockPos> nearbyMachineOrigins(BlockGetter level, BlockPos pos) {
        Set<BlockPos> machines = new java.util.TreeSet<>(java.util.Comparator.comparingLong(BlockPos::asLong));
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            BlockPos origin = originOf(level, neighbor);
            if (origin != null && level.getBlockEntity(origin) instanceof LargeMachine machine
                    && machine.isLargeMachine()) {
                machines.add(origin);
            }
            if (isPipe(level, neighbor)) {
                machines.addAll(componentMachineOrigins(level, neighbor));
            }
        }
        return machines;
    }

    /**
     * 把连通片按「最近机器」分区。单机器片全部归它；多机器片用多源 BFS，
     * 距离相同按机器原点坐标排序，保证每次结果一致。
     */
    private static Map<BlockPos, BlockPos> pipeOwners(BlockGetter level, Set<BlockPos> pipes,
                                                     Set<BlockPos> machines) {
        if (pipes.isEmpty() || machines.isEmpty()) {
            return Map.of();
        }
        if (machines.size() == 1) {
            BlockPos only = machines.iterator().next();
            Map<BlockPos, BlockPos> all = new HashMap<>();
            for (BlockPos pipe : pipes) {
                all.put(pipe, only);
            }
            return all;
        }
        record Seed(BlockPos pipe, int distance, long originKey, BlockPos owner) {}
        java.util.PriorityQueue<Seed> queue = new java.util.PriorityQueue<>(
                java.util.Comparator.comparingInt(Seed::distance).thenComparingLong(Seed::originKey));
        for (BlockPos pipe : pipes) {
            for (Direction direction : Direction.values()) {
                if (!pipeLinked(level, pipe, direction)) {
                    continue;
                }
                BlockPos origin = originOf(level, pipe.relative(direction));
                if (origin != null && machines.contains(origin)) {
                    queue.add(new Seed(pipe, 0, origin.asLong(), origin));
                }
            }
        }
        Map<BlockPos, BlockPos> owners = new HashMap<>();
        while (!queue.isEmpty()) {
            Seed seed = queue.poll();
            if (owners.containsKey(seed.pipe())) {
                continue;   // 已经由更近 / 坐标更小的机器拿走
            }
            owners.put(seed.pipe(), seed.owner());
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = seed.pipe().relative(direction);
                if (pipes.contains(neighbor) && !owners.containsKey(neighbor)) {
                    queue.add(new Seed(neighbor, seed.distance() + 1, seed.originKey(), seed.owner()));
                }
            }
        }
        // 理论上连通片里每根管都能从某台机器到达；兜底给第一台，避免出现无主管
        BlockPos fallback = machines.iterator().next();
        for (BlockPos pipe : pipes) {
            owners.putIfAbsent(pipe, fallback);
        }
        return owners;
    }

    /**
     * 找出「挂着这个容器」的大型机器（与 {@link #connectedContainers} 同一口径）：
     * 铜箱子与储罐都看<strong>任意一面</strong>是不是<strong>机器管系里的机械管</strong>。
     * <p>容器与铜块都<strong>不算</strong>机器网络 —— 容器只能贴着机械管接入。
     * 例外：<strong>热室</strong>（底部垫铜块的铜箱子）是热压差结构，<strong>不属</strong>大机器物流。
     */
    @Nullable
    public static LargeMachine containerMachine(Level level, BlockPos containerPos) {
        BlockState state = level.getBlockState(containerPos);
        if (state.is(CopperHeat.COPPER_CHESTS)) {
            if (HotChamber.stateAt(level, containerPos) != HotChamber.State.NONE) {
                return null;   // 热室（底部垫铜块）：是热压差结构，不属大机器物流
            }
            for (Direction direction : Direction.values()) {
                LargeMachine machine = containerPipeMachine(level, containerPos, direction);
                if (machine != null) {
                    return machine;
                }
            }
            return null;
        }
        if (state.getBlock() instanceof ReactionChamberBlock) {
            for (Direction direction : Direction.values()) {
                LargeMachine machine = containerPipeMachine(level, containerPos, direction);
                if (machine != null) {
                    return machine;
                }
            }
            return null;
        }
        if (state.getBlock() instanceof TankBlock) {
            // 合并大罐：任意一格都要能反查出「大罐整体」挂在哪台机器上（只连一格的部件也能被 Jade / 命令识别）
            BlockPos origin = containerPos;
            int width = 1;
            int height = 1;
            if (level.getBlockEntity(containerPos) instanceof TankBlockEntity tank) {
                TankBlockEntity controller = tank.controller();
                if (controller != null) {
                    origin = controller.getBlockPos();
                    width = Math.max(1, controller.width());
                    height = Math.max(1, controller.height());
                }
            }
            // 储罐：任意一面贴着机器管系里的机械管都算接入；若同时挨着两台机器，
            // 取离储罐最近的那台，避免方向遍历顺序决定归属。
            LargeMachine best = null;
            double bestDist = Double.MAX_VALUE;
            for (int dy = 0; dy < height; dy++) {
                for (int dx = 0; dx < width; dx++) {
                    for (int dz = 0; dz < width; dz++) {
                        BlockPos cell = origin.offset(dx, dy, dz);
                        for (Direction direction : Direction.values()) {
                            LargeMachine machine = containerPipeMachine(level, cell, direction);
                            if (machine == null) {
                                continue;
                            }
                            // 用控制器位置算距离：同一大罐的任意一格都必须得到同一个归属，
                            // 否则两台机器从不同罐格接入时会把同一个罐算进两边。
                            double dist = machine.origin().distSqr(origin);
                            if (dist < bestDist) {
                                bestDist = dist;
                                best = machine;
                            }
                        }
                    }
                }
            }
            return best;
        }
        if (isItemContainer(level, containerPos)) {
            // 其它模组容器（精妙存储 / 背包等）：任意一面贴着机器管系里的机械管即接入
            for (Direction direction : Direction.values()) {
                LargeMachine machine = pipeComponentMachine(level, containerPos.relative(direction));
                if (machine != null) {
                    return machine;
                }
            }
            return null;
        }
        if (GrateSource.isGrate(state)) {
            for (Direction direction : Direction.values()) {
                LargeMachine machine = pipeComponentMachine(level, containerPos.relative(direction));
                if (machine != null) {
                    return machine;
                }
            }
            return null;
        }
        return null;
    }

    /**
     * 物流容器的<strong>记账锚点</strong>：合并大罐统一用控制器那一格。
     * <p>输入 / 输出方向是按容器坐标存的 —— 不对齐到控制器的话，剪刀右键大罐的「部件格」改了等于没改。
     */
    public static BlockPos containerAnchor(Level level, BlockPos containerPos) {
        if (level.getBlockEntity(containerPos) instanceof TankBlockEntity tank) {
            TankBlockEntity controller = tank.controller();
            if (controller != null) {
                return controller.getBlockPos().immutable();
            }
        }
        return containerPos.immutable();
    }

    /**
     * 是否是「物品容器」：铜箱子、反应仓，以及其它模组暴露 {@link Capabilities.Item#BLOCK} 的方块
     * （精妙存储 / 背包等）。
     * <p>排除大机器本体与填充格、机械管、铜管、储罐、铜格栅；原版只认铜箱子，普通箱子 / 木桶不接入。
     */
    public static boolean isItemContainer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(CopperHeat.COPPER_CHESTS) || state.getBlock() instanceof ReactionChamberBlock) {
            return true;
        }
        if (state.getBlock() instanceof LargeMachineBlock
                || state.getBlock() instanceof TankBlock
                || state.getBlock() instanceof MachinePipeBlock
                || state.getBlock() instanceof CopperPipeBlock
                || GrateSource.isGrate(state)) {
            return false;
        }
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        // 只接「外部模组」的物品容器：原版与本体方块已由上面的白名单 / 各专属分支处理，
        // 否则本体的机器（同样注册了物品能力）会被误判成物流容器。
        if (id == null || "minecraft".equals(id.getNamespace())
                || StellarEvolution.MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        return itemHandlerAt(level, pos) != null;
    }

    /**
     * 取某格的物品能力：先试无方向，再逐面试。
     * <p>精妙存储 / 背包等容器可能只在具体面注册能力，{@code null} 面拿不到。
     */
    @Nullable
    public static ResourceHandler<ItemResource> itemHandlerAt(Level level, BlockPos pos) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
        if (handler != null) {
            return handler;
        }
        for (Direction side : Direction.values()) {
            handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    /** 这台机器是否<strong>经铜块</strong>接入热压差网络：机器<strong>任意一格</strong>的<strong>任意一面</strong>贴着铜块即可。 */
    public static boolean touchesCopperBlock(BlockGetter level, LargeMachine machine) {
        return countCopperBlocks(level, machine) > 0;
    }

    /**
     * 这台机器是否<strong>经铜管</strong>接入热压差网络：机器任意一格的<strong>底排</strong>贴着铜管即可。
     * <p>接管面与 {@link CopperPipeBlock#connectsTo} 一致：四侧最底层 + 正下方，顶面不接。
     */
    public static boolean touchesCopperPipe(BlockGetter level, LargeMachine machine) {
        for (BlockPos cell : machine.occupiedCells()) {
            for (Direction direction : Direction.values()) {
                BlockPos pipePos = cell.relative(direction);
                if (level.getBlockState(pipePos).getBlock() instanceof CopperPipeBlock
                        && pipeConnectsToMachineBottomRow(level, pipePos, cell, direction.getOpposite())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 这台机器是否接了热网：铜块或底排铜管，二者其一即可。 */
    public static boolean touchesCopperHeat(BlockGetter level, LargeMachine machine) {
        return touchesCopperBlock(level, machine) || touchesCopperPipe(level, machine);
    }

    /**
     * 管道-大机器的<strong>底排接管</strong>规则（机械管 / 铜管共用）：四个水平侧面只接机器最底那一层，
     * 底面从正下方接上来，顶面不接。
     *
     * @param pipePos     管道自身位置
     * @param neighborPos 贴着的机器格（主方块或填充格）
     */
    public static boolean pipeConnectsToMachineBottomRow(BlockGetter level, BlockPos pipePos,
                                                         BlockPos neighborPos, Direction direction) {
        Block block = level.getBlockState(neighborPos).getBlock();
        if (!(block instanceof LargeMachineBlock machineBlock)) {
            return false;
        }
        BlockPos origin = machineBlock.largeMachineOrigin(level, neighborPos);
        if (origin == null) {
            return false;
        }
        if (direction == Direction.UP) {
            return pipePos.getY() == origin.getY() - 1;    // 从正下方接上来
        }
        if (direction.getAxis().isHorizontal()) {
            return pipePos.getY() == origin.getY();        // 侧面最底那一排
        }
        return false;                                      // 机器顶面不接管
    }

    /**
     * 贴在机器上的<strong>供能铜块数量</strong>（去重：同一块铜块贴着多个机器格只算一次）。
     * <p>生化机器用它分温度带：1 块厌氧 / 2 块中温 / ≥4 块高温（见 {@code FermentationKinetics#bandForCopper}）。
     */
    public static int countCopperBlocks(BlockGetter level, LargeMachine machine) {
        Set<BlockPos> cells = new HashSet<>(machine.occupiedCells());
        Set<BlockPos> copper = new HashSet<>();
        for (BlockPos cell : cells) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = cell.relative(direction);
                if (cells.contains(neighbor)) {
                    continue;   // 本体内部的格不算
                }
                if (CopperHeat.isCopperBlock(level, neighbor)) {
                    copper.add(neighbor);
                }
            }
        }
        return copper.size();
    }

    // ------------------------------------------------------------------ 物流遍历

    /** 机器网络里的导体<strong>只有机械管</strong>（{@link MachinePipeBlock}：普通铜管与铜块都不算机器网络）。 */
    private static boolean isPipe(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MachinePipeBlock;
    }

    /**
     * 这台机器的大机器物流网络里连接的全部容器（铜箱子 / 储罐）。
     * <p>同一 tick 内同一机器只算一次（缓存）。
     */
    public static List<Connected> connectedContainers(Level level, LargeMachine machine) {
        Cache cache = CACHES.computeIfAbsent(level, ignored -> new Cache());
        synchronized (cache) {
            long now = level.getGameTime();
            if (cache.tick != now) {
                cache.tick = now;
                cache.byOrigin.clear();
            }
            List<Connected> cached = cache.byOrigin.get(machine.origin());
            if (cached != null) {
                return cached;
            }
            List<Connected> result = collect(level, machine);
            cache.byOrigin.put(machine.origin().immutable(), result);
            return result;
        }
    }

    private static List<Connected> collect(Level level, LargeMachine machine) {
        Set<BlockPos> cells = new HashSet<>(machine.occupiedCells());

        // 1) 种子：贴着机器任意一格的**机械管**；广度优先**只走机械管**（普通铜管 / 铜块不入机器网络）
        Set<BlockPos> members = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        for (BlockPos cell : cells) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = cell.relative(direction);
                if (cells.contains(neighbor)) {
                    continue;
                }
                // 只有「这根管确实接着机器那一端」才算种子（剪刀切断后不再接入）
                if (isPipe(level, neighbor) && pipeLinked(level, neighbor, direction.getOpposite())
                        && members.add(neighbor)) {
                    frontier.add(neighbor);
                }
            }
        }
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (isPipe(level, neighbor)
                        && pipeLinked(level, pos, direction)
                        && pipeLinked(level, neighbor, direction.getOpposite())
                        && members.add(neighbor)) {
                    frontier.add(neighbor);
                }
            }
        }

        // 1.5) 多机器连通片：按「最近机器」逻辑分区；旧存档里已经连在一起也在这里分开。
        Set<BlockPos> machineOrigins = componentMachineOrigins(level, members);
        Map<BlockPos, BlockPos> owners = pipeOwners(level, members, machineOrigins);
        Set<BlockPos> ownedPipes = new LinkedHashSet<>();
        for (BlockPos pipe : members) {
            if (machine.origin().equals(owners.get(pipe))) {
                ownedPipes.add(pipe);
            }
        }
        members = ownedPipes;

        // 2) 扫网络成员的邻居，挑出铜箱子（物品）与储罐（流体）
        List<Connected> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> seenTanks = new HashSet<>();
        // 双联铜箱子：左右两半各自暴露**同一个合并库存**，必须只算一次，否则物品/流体能力会翻倍
        Set<Long> seenChests = new HashSet<>();
        for (BlockPos member : members) {
            for (Direction direction : Direction.values()) {
                // 管臂被剪断 → 该方向上贴着的容器不再属于这台机器的网络
                if (!pipeLinked(level, member, direction)) {
                    continue;
                }
                BlockPos neighbor = member.relative(direction);
                if (!seen.add(neighbor)) {
                    continue;
                }
                // 容器也按「最近机器」归属：一台容器只进一台机器的网络，避免边界容器被两边同时摊平。
                LargeMachine owner = containerMachine(level, neighbor);
                if (owner == null || !owner.origin().equals(machine.origin())) {
                    continue;
                }
                BlockState state = level.getBlockState(neighbor);
                if (state.is(CopperHeat.COPPER_CHESTS)) {
                    // 铜箱子：**任意面**接入 —— 六面贴到机器管系里的**机械管**即算（本循环本身就按六面扫）。
                    // 但「热室」（底部垫铜块的热压差结构）不属大机器物流，跳过。
                    if (HotChamber.stateAt(level, neighbor) == HotChamber.State.NONE) {
                        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                            Direction pair = ChestBlock.getConnectedDirection(state);
                            long key = pair == null ? neighbor.asLong()
                                    : Math.min(neighbor.asLong(), neighbor.relative(pair).asLong());
                            if (!seenChests.add(key)) {
                                continue;
                            }
                        }
                        found.add(new Connected(neighbor.immutable(), false));
                    }
                } else if (state.getBlock() instanceof ReactionChamberBlock) {
                    // 反应仓：反应堆专属燃料容器；对其它大机器就是普通 54 格容器
                    found.add(new Connected(neighbor.immutable(), false));
                } else if (state.getBlock() instanceof TankBlock) {
                    // 储罐：任意一面贴着机器管系里的机械管即接入；合并成大罐时**只算控制器那一格**，
                    // 否则部件会把同一个罐重复摊平（能力一样，容量会被算好几遍）
                    boolean touched = false;
                    for (Direction side : Direction.values()) {
                        if (members.contains(neighbor.relative(side))) {
                            touched = true;
                            break;
                        }
                    }
                    if (touched) {
                        BlockPos tankPos = neighbor.immutable();
                        if (level.getBlockEntity(tankPos) instanceof TankBlockEntity tank) {
                            TankBlockEntity controller = tank.controller();
                            if (controller != null) {
                                tankPos = controller.getBlockPos().immutable();
                            }
                        }
                        if (seenTanks.add(tankPos)) {
                            found.add(new Connected(tankPos, true));
                        }
                    }
                } else if (isItemContainer(level, neighbor)) {
                    // 其它模组物品容器（精妙存储 / 背包等）：任意一面贴着机械管即接入
                    found.add(new Connected(neighbor.immutable(), false));
                } else if (GrateSource.isGrate(state)) {
                    // 原版铜格栅：机械管网络的虚拟流体源（任意面接入）
                    GrateSource.register(level, neighbor);
                    found.add(new Connected(neighbor.immutable(), true));
                }
            }
        }
        return List.copyOf(found);
    }

    // ------------------------------------------------------------------ 缓存

    /** 每世界一份、按 tick 失效的缓存（按机器原点索引）。 */
    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Cache {
        long tick = Long.MIN_VALUE;
        final Map<BlockPos, List<Connected>> byOrigin = new HashMap<>();
    }

    private LargeMachines() {}
}
