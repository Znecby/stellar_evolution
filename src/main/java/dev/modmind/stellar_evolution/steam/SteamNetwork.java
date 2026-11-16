package dev.modmind.stellar_evolution.steam;

import dev.modmind.stellar_evolution.large.LargeMachines;
import dev.modmind.stellar_evolution.pipe.PressurePipeBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;

/**
 * <strong>蒸汽网络</strong>：以压力管（{@link PressurePipeBlock}）为载体的连通聚合 + 每 tick 只算一次的缓存。
 *
 * <p>连通规则：从压力管做 6 面广度优先，<strong>只走压力管</strong>；管子的任意一面贴着
 * 「实现了 {@link SteamEndpoint} 的机器（任意一格）」就算该端点接入。端点不占管道格、也不继续导通。
 *
 * <p>网络<strong>不存蒸汽</strong>（蒸汽是每 tick 生成 / 消耗的数据）：反应堆在自己的 tick 里给出
 * {@link SteamEndpoint#steamOutput()}，汽轮机给出 {@link SteamEndpoint#steamMaxDemand(SteamState)}，
 * 本类把产出汇总成一份 {@link State}，并按<strong>「离生成端越近越优先、先供满速、多余的才流向下一台」</strong>
 * 把蒸汽分给各消耗端（见 {@link State#allocationFor} / {@link State#remaining}）。
 * 同一 tick 内同一张网络只算一次（按 {@link Level} 弱引用缓存）。
 *
 * <p><strong>排气</strong>：网络里没有消耗端时，端点的蒸汽全部丢弃；末端压力管会喷白色蒸汽粒子
 * （{@link #spawnVentParticles}）。末端 = 只有一条连接（或没有连接）的管子，取它第一个没接东西的面。
 */
public final class SteamNetwork {
    /** 一个排气口：末端压力管的位置 + 朝外的面。 */
    public record Vent(BlockPos pos, Direction face) {}

    /**
     * 一张蒸汽网络的快照。
     *
     * @param pipes      管道格
     * @param generators 生成端机器原点
     * @param consumers  消耗端机器原点
     * @param steam      网络里的蒸汽（按生成端输出量加权混合）
     * @param demand     所有消耗端本 tick 的**当前**需求合计（mB/t）
     * @param vents      末端排气口
     * @param allocations 按「就近优先、先供满速」分给每台消耗端的蒸汽（mB/t）
     * @param allocated  本 tick 实际分出去的蒸汽合计（mB/t）
     */
    public record State(Set<BlockPos> pipes, List<BlockPos> generators, List<BlockPos> consumers,
                        SteamState steam, double demand, List<Vent> vents,
                        Map<BlockPos, Double> allocations, double allocated) {
        /** 网络里没有消耗端 → 蒸汽只能末端排气（有蒸汽时才算排气）。 */
        public boolean venting() {
            return !pipes.isEmpty() && consumers.isEmpty() && !steam.isEmpty();
        }

        /** 这台消耗端本 tick 分到的蒸汽；0 = 被更近的机组优先拿走了。 */
        public double allocationFor(BlockPos origin) {
            return allocations.getOrDefault(origin, 0.0D);
        }

        /** 分完以后还剩多少蒸汽（没有消耗端 / 全部满速时就是剩余量）。 */
        public double remaining() {
            return Math.max(0.0D, steam.amount() - allocated);
        }
    }

    private static final State EMPTY =
            new State(Set.of(), List.of(), List.of(), SteamState.NONE, 0.0D, List.of(), Map.of(), 0.0D);

    private static final class Cache {
        long tick = Long.MIN_VALUE;
        final Map<BlockPos, State> states = new HashMap<>();
    }

    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());

    /** 这一格（压力管 / 端点机器）所在网络的快照；同一 tick 内同一网络只算一次。 */
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
            for (BlockPos generator : state.generators()) {
                cache.states.put(generator, state);
            }
            for (BlockPos consumer : state.consumers()) {
                cache.states.put(consumer, state);
            }
            return state;
        }
    }

    /** 这台机器旁边有没有压力管（生成端「无法输出蒸汽」的判定）。 */
    public static boolean hasAdjacentPipe(Level level, SteamEndpoint endpoint) {
        for (BlockPos cell : endpoint.steamCells()) {
            for (Direction direction : Direction.values()) {
                if (level.getBlockState(cell.relative(direction)).getBlock() instanceof PressurePipeBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 在末端排气口喷白色蒸汽粒子（只在服务端调用；调用方自行节流）。 */
    public static void spawnVentParticles(ServerLevel level, State state) {
        if (!state.venting()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (Vent vent : state.vents()) {
            BlockPos pos = vent.pos();
            Direction face = vent.face();
            double x = pos.getX() + 0.5D + face.getStepX() * 0.55D;
            double y = pos.getY() + 0.5D + face.getStepY() * 0.55D;
            double z = pos.getZ() + 0.5D + face.getStepZ() * 0.55D;
            level.sendParticles(ParticleTypes.CLOUD,
                    x + (random.nextDouble() - 0.5D) * 0.25D,
                    y + (random.nextDouble() - 0.5D) * 0.25D,
                    z + (random.nextDouble() - 0.5D) * 0.25D,
                    2, 0.05D, 0.05D, 0.05D, 0.02D);
        }
    }

    private static State collect(Level level, BlockPos origin) {
        Set<BlockPos> startPipes = new LinkedHashSet<>();
        if (level.getBlockState(origin).getBlock() instanceof PressurePipeBlock) {
            startPipes.add(origin);
        }
        SteamEndpoint originEndpoint = endpointAt(level, origin);
        if (originEndpoint != null) {
            collectAdjacentPipes(level, originEndpoint, startPipes);
        }
        if (startPipes.isEmpty()) {
            return EMPTY;
        }

        Set<BlockPos> pipes = new LinkedHashSet<>(startPipes);
        Set<BlockPos> generators = new LinkedHashSet<>();
        Set<BlockPos> consumers = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>(startPipes);
        while (!frontier.isEmpty()) {
            BlockPos pipe = frontier.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pipe.relative(direction);
                if (level.getBlockState(neighbor).getBlock() instanceof PressurePipeBlock) {
                    if (pipes.add(neighbor)) {
                        frontier.add(neighbor);
                    }
                    continue;
                }
                SteamEndpoint endpoint = endpointAt(level, neighbor);
                if (endpoint != null) {
                    if (endpoint.steamGenerator()) {
                        generators.add(endpoint.steamOrigin());
                    } else {
                        consumers.add(endpoint.steamOrigin());
                    }
                }
            }
        }

        SteamState steam = SteamState.NONE;
        for (BlockPos generator : generators) {
            SteamEndpoint endpoint = endpointAt(level, generator);
            if (endpoint != null) {
                steam = steam.blend(endpoint.steamOutput());
            }
        }
        double demand = 0.0D;
        for (BlockPos consumer : consumers) {
            SteamEndpoint endpoint = endpointAt(level, consumer);
            if (endpoint != null) {
                demand += Math.max(0.0D, endpoint.steamDemand());
            }
        }

        // 分汽：按「离最近生成端越近越优先」排序，每台先按“满速需求”吃饱，剩下的才给下一台。
        Map<BlockPos, Double> allocations = new HashMap<>();
        double remaining = Math.max(0.0D, steam.amount());
        if (remaining > 0.0D && !consumers.isEmpty()) {
            List<BlockPos> ordered = new ArrayList<>(consumers);
            ordered.sort(Comparator.comparingDouble((BlockPos pos) -> distanceSqrToGenerators(pos, generators))
                    .thenComparingLong(BlockPos::asLong));
            for (BlockPos consumer : ordered) {
                SteamEndpoint endpoint = endpointAt(level, consumer);
                if (endpoint == null) {
                    continue;
                }
                double maxDemand = Math.max(0.0D, endpoint.steamMaxDemand(steam));
                double allocation = Math.min(remaining, maxDemand);
                if (allocation > 0.0D) {
                    allocations.put(consumer, allocation);
                    remaining -= allocation;
                }
                if (remaining <= 0.0D) {
                    break;
                }
            }
        }
        double allocated = Math.max(0.0D, steam.amount() - remaining);
        return new State(Collections.unmodifiableSet(pipes), List.copyOf(generators), List.copyOf(consumers),
                steam, demand, vents(level, pipes), Map.copyOf(allocations), allocated);
    }

    private static double distanceSqrToGenerators(BlockPos pos, Iterable<BlockPos> generators) {
        double best = Double.MAX_VALUE;
        for (BlockPos generator : generators) {
            best = Math.min(best, pos.distSqr(generator));
        }
        return best == Double.MAX_VALUE ? 0.0D : best;
    }

    /** 端点 2×2×2 的任意一格旁边贴着的压力管。 */
    private static void collectAdjacentPipes(Level level, SteamEndpoint endpoint, Set<BlockPos> out) {
        for (BlockPos cell : endpoint.steamCells()) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = cell.relative(direction);
                if (level.getBlockState(neighbor).getBlock() instanceof PressurePipeBlock) {
                    out.add(neighbor);
                }
            }
        }
    }

    /** 末端排气口：只连了 ≤1 个东西的管子，取第一个空面。 */
    private static List<Vent> vents(Level level, Set<BlockPos> pipes) {
        List<Vent> vents = new ArrayList<>();
        for (BlockPos pipe : pipes) {
            int links = 0;
            Direction open = null;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pipe.relative(direction);
                if (level.getBlockState(neighbor).getBlock() instanceof PressurePipeBlock
                        || endpointAt(level, neighbor) != null) {
                    links++;
                } else if (open == null) {
                    open = direction;
                }
            }
            if (links <= 1 && open != null) {
                vents.add(new Vent(pipe, open));
            }
        }
        return List.copyOf(vents);
    }

    /** 这一格是不是某台蒸汽端点的任意一格；是则返回它的方块实体。 */
    private static SteamEndpoint endpointAt(Level level, BlockPos pos) {
        BlockPos origin = LargeMachines.originOf(level, pos);
        if (origin == null) {
            return null;
        }
        return level.getBlockEntity(origin) instanceof SteamEndpoint endpoint ? endpoint : null;
    }

    private SteamNetwork() {}
}
