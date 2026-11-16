package dev.znecby.stellar_evolution.stream;

import dev.znecby.stellar_evolution.large.LargeMachines;
import dev.znecby.stellar_evolution.pipe.BeamPipeBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <strong>束流网络</strong>：以束流管（{@link BeamPipeBlock}）为载体的连通聚合（照 {@code SteamNetwork} 口径）。
 *
 * <p>连通规则：从束流管做 6 面广度优先，<strong>只走同类型束流管</strong>；管子任意一面贴着
 * 「实现了 {@link StreamNode} 且类型一致」的机器，就算接入。
 *
 * <p>网络<strong>不存粒子流</strong>：每 tick 汇总成一份 {@link State}，源给出产出、消费者给出需求，
 * 份额由 {@link #allocate(double, double[])} 按 max-min 公平分配算出来（无中心调度）。
 * 同一 tick 内同一张网络只算一次。
 */
public final class StreamNetwork {
    /**
     * 一张束流网络的快照。
     *
     * @param pipes    管道格
     * @param sources  源机器原点
     * @param demands  消费者原点 → 本 tick 想接收的量（0 = 不参与分配）
     * @param type     粒子流类型（整张网络同一种）
     * @param rate     当前网络总输出（单位/t）
     * @param emitted  网络累计输出（单位）
     */
    public record State(Set<BlockPos> pipes, List<BlockPos> sources, Map<BlockPos, Double> demands,
                        StreamType type, double rate, double emitted) {
        /** 空网络。 */
        public static final State EMPTY =
                new State(Set.of(), List.of(), Map.of(), StreamType.MUON, 0.0D, 0.0D);

        /** 网络总需求（单位/t）。 */
        public double demand() {
            double total = 0.0D;
            for (double value : demands.values()) {
                total += value;
            }
            return total;
        }

        /** 某个消费者本 tick 能收到的量（max-min 公平分配；不在名单里 = 0）。 */
        public double shareOf(BlockPos consumerOrigin) {
            if (demands.isEmpty() || !demands.containsKey(consumerOrigin)) {
                return 0.0D;
            }
            List<BlockPos> order = new ArrayList<>(demands.keySet());
            double[] wanted = new double[order.size()];
            for (int i = 0; i < order.size(); i++) {
                wanted[i] = demands.get(order.get(i));
            }
            double[] granted = allocate(rate, wanted);
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).equals(consumerOrigin)) {
                    return granted[i];
                }
            }
            return 0.0D;
        }
    }

    private static final class Cache {
        long tick = Long.MIN_VALUE;
        final Map<BlockPos, State> states = new HashMap<>();
    }

    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());

    /** 这一格（束流管 / 端点机器）所在网络的快照；同一 tick 内同一网络只算一次。 */
    public static State stateAt(Level level, BlockPos pos) {
        Cache cache = CACHES.computeIfAbsent(level, ignored -> new Cache());
        synchronized (cache) {
            long now = level.getGameTime();
            if (cache.tick != now) {
                cache.tick = now;
                cache.states.clear();
            }
            State cached = cache.states.get(pos);
            if (cached != null) {
                return cached;
            }
            State state = collect(level, pos);
            cache.states.put(pos, state);
            for (BlockPos pipe : state.pipes()) {
                cache.states.put(pipe, state);
            }
            for (BlockPos source : state.sources()) {
                cache.states.put(source, state);
            }
            for (BlockPos consumer : state.demands().keySet()) {
                cache.states.put(consumer, state);
            }
            return state;
        }
    }

    /**
     * 按 <strong>max-min 公平</strong>把供给分给各消费者（实现见 {@link StreamAllocation}）。
     *
     * <p>例：供给 10、需求 [2, 10] → [2, 8]；供给 3、需求 [10, 10, 10] → [1, 1, 1]。
     */
    public static double[] allocate(double supply, double[] demands) {
        return StreamAllocation.allocate(supply, demands);
    }

    private static State collect(Level level, BlockPos origin) {
        StreamType type = null;
        Set<BlockPos> startPipes = new LinkedHashSet<>();
        BlockState originState = level.getBlockState(origin);
        if (originState.getBlock() instanceof BeamPipeBlock) {
            int raw = originState.getValue(BeamPipeBlock.STREAM);
            if (raw == BeamPipeBlock.EMPTY) {
                return new State(Set.of(), List.of(), Map.of(), StreamType.MUON, 0.0D, 0.0D);
            }
            type = StreamType.byOrdinal(raw);
            startPipes.add(origin);
        }
        StreamNode originNode = nodeAt(level, origin);
        if (originNode != null) {
            type = originNode.streamType();
            collectAdjacentPipes(level, originNode, startPipes);
        }
        if (type == null || startPipes.isEmpty()) {
            return new State(Set.of(), List.of(), Map.of(), type == null ? StreamType.MUON : type, 0.0D, 0.0D);
        }

        Set<BlockPos> pipes = new LinkedHashSet<>(startPipes);
        Set<BlockPos> sources = new LinkedHashSet<>();
        Map<BlockPos, Double> demands = new LinkedHashMap<>();
        Deque<BlockPos> frontier = new ArrayDeque<>(startPipes);
        while (!frontier.isEmpty()) {
            BlockPos pipe = frontier.poll();
            BlockState pipeState = level.getBlockState(pipe);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pipe.relative(direction);
                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.getBlock() instanceof BeamPipeBlock) {
                    // 不同类型不互通；**两端都必须「接上」**（束流管默认断开，剪刀接）
                    if (neighborState.getValue(BeamPipeBlock.STREAM) != type.ordinal()
                            || !pipeState.getValue(BeamPipeBlock.CONNECTIONS.get(direction))
                            || !neighborState.getValue(BeamPipeBlock.CONNECTIONS.get(direction.getOpposite()))) {
                        continue;
                    }
                    if (pipes.add(neighbor)) {
                        frontier.add(neighbor);
                    }
                    continue;
                }
                if (!pipeState.getValue(BeamPipeBlock.CONNECTIONS.get(direction))) {
                    continue;   // 管子没接到这台机器
                }
                StreamNode node = nodeAt(level, neighbor);
                if (node == null) {
                    continue;
                }
                // 一台机器可以同时是「源」和「消费者」（对撞机：吃两路、喷一路），
                // 所以这两个判断必须独立，不能互相 continue 掉。
                if (node instanceof StreamEndpoint endpoint) {
                    // 源只喂同类型网：否则网段的类型标签会说谎，两种流混进同一张网。
                    // 同时排除「有源接口但这一路并不喷」的机器（辐照仓只消费，不参与配额分母）。
                    if (endpoint.outputStreamType() == type
                            && (endpoint.streamRate() > 0.0D || endpoint.streamEmitted() > 0.0D)) {
                        sources.add(endpoint.streamOrigin());
                    }
                }
                if (node instanceof StreamConsumer consumer) {
                    // 消费者不校验类型：接了什么管就连哪张网，吃不吃哪一路由它自己的配方优先级决定
                    demands.put(consumer.streamOrigin(), Math.max(0.0D, consumer.streamDemand(type)));
                }
            }
        }

        double rate = 0.0D;
        double emitted = 0.0D;
        for (BlockPos source : sources) {
            if (level.getBlockEntity(source) instanceof StreamEndpoint endpoint) {
                rate += Math.max(0.0D, endpoint.streamRate());
                emitted += Math.max(0.0D, endpoint.streamEmitted());
            }
        }
        return new State(Collections.unmodifiableSet(pipes), List.copyOf(sources),
                Collections.unmodifiableMap(demands), type, rate, emitted);
    }

    private static void collectAdjacentPipes(Level level, StreamNode node, Set<BlockPos> out) {
        for (BlockPos cell : node.streamCells()) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = cell.relative(direction);
                BlockState state = level.getBlockState(neighbor);
                if (state.getBlock() instanceof BeamPipeBlock
                        && state.getValue(BeamPipeBlock.STREAM) == node.streamType().ordinal()
                        && state.getValue(BeamPipeBlock.CONNECTIONS.get(direction.getOpposite()))) {
                    out.add(neighbor);
                }
            }
        }
    }

    /** 这一格是不是某台束流节点的任意一格；是则返回它的方块实体。 */
    private static StreamNode nodeAt(Level level, BlockPos pos) {
        BlockPos origin = LargeMachines.originOf(level, pos);
        if (origin == null) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(origin);
        return entity instanceof StreamNode node ? node : null;
    }

    private StreamNetwork() {}
}
