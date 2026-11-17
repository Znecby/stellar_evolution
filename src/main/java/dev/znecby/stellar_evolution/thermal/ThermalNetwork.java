package dev.znecby.stellar_evolution.thermal;

import dev.znecby.stellar_evolution.large.LargeMachine;
import dev.znecby.stellar_evolution.large.LargeMachines;
import dev.znecby.stellar_evolution.pipe.CopperPipeBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 热压差网络的<strong>连通聚合</strong>：把「一张网络」里的生成端 / 消耗端 / 传递件找出来，算成一册
 * {@link ThermalLoad}。
 *
 * <p><strong>连通规则</strong>：从起始格做 <strong>6 面相邻</strong>的广度优先遍历，只有下列格子算成员 ——
 * <ul>
 *   <li>{@link ThermalTransmitter 传递件}（铜管）：只导通、不记账；</li>
 *   <li>铜块（中间导体）与限流管（{@link ThermalGate} 闸门）：只导通、不记账；</li>
 *   <li>贴着铜块的木桶 / 熔炉系列 / 机械土壤、热室：按消耗端记账；</li>
 *   <li>实现了 {@link ThermalGenerator}/{@link ThermalConsumer} 的机器方块实体：按角色记账；</li>
 *   <li>大型机器本体与填充格：消耗端可贴铜块，也可经底排铜管直接接入；生成端同样可经铜管接入。</li>
 * </ul>
 * 所有成员都会继续向 6 面扩散 —— 框架<strong>没有</strong>「叶子消耗端不再导通」的特判。
 * 遍历到别的方块即断路；传递件的<strong>管臂朝向不做判定</strong>（老存档 / 未刷新管臂仍按方块相邻导通），
 * 但<strong>被剪刀手动切断的那一端会真正断开</strong>（见 {@link CopperPipeBlock#isCut}）——
 * 外观断开了，网络也必须断开，否则「剪了还通」不符合直觉。
 *
 * <p><strong>性能</strong>：{@link #loadAt} 做了<strong>每 tick 每网络只算一次</strong>的缓存 ——
 * 同一 tick 内，同一网络的每个成员位置拿到的都是同一份聚合结果。
 * 缓存按 {@link Level} 弱引用保存，不会拖住卸载的世界。
 *
 * <p>框架<strong>不含中央 ticker</strong>：消耗端在自己的 ticker 里调 {@link #loadAt} 即可；
 * 没有消耗端的网络不做任何计算。
 */
public final class ThermalNetwork {
    /** 一次扫描的结果：账本 + 三类成员的数量（供查看命令展示）。 */
    public record Scan(ThermalLoad load, int generatorCount, int consumerCount, int transmitterCount) {}

    /**
     * 内部聚合结果：账本 + 各类数量 + 成员位置 + 各位置的「热流率上限」。
     *
     * @param members    全部网络成员（传递件 + 机器），用于把结果写进同一 tick 的缓存
     * @param connectors 其中的<strong>传递件</strong>（铜管）位置，用于同步超载告警
     * @param flowCaps   位置 → 该处可用热流率的上限（限流管造成的；{@code ∞} = 不封）
     * @param views      位置 → 该处的「账本视图」（共享同一张账本，只把热流率按位置封顶）
     */
    private record Aggregate(ThermalLoad load, int generatorCount, int consumerCount, Set<BlockPos> members,
                             Set<BlockPos> connectors, Map<BlockPos, Double> flowCaps,
                             Map<BlockPos, ThermalLoad> views) {

        /** 取「{@code pos} 这个位置看到的账本」：同一张账本，热流率按位置封顶。 */
        ThermalLoad loadAt(BlockPos pos) {
            Double cap = flowCaps.get(pos);
            if (cap == null || cap <= 0.0D || cap.isInfinite()) {
                return load;
            }
            return views.computeIfAbsent(pos, ignored -> load.view(cap));
        }
    }

    /** 一帧 BFS 的一步：当前格 + 「走到它时身上的热流率上限」（起点为 {@code ∞}）。 */
    private record Step(BlockPos pos, double cap) {}

    /** 待登记的消耗端（先记位置，等热流率上限松弛完再登记 —— 上限可能被更宽的路径放宽）。 */
    private record RawConsumer(BlockPos pos, double resistance, double maxUsefulFlowRate) {}

    /** 每世界一份、按 tick 失效的缓存。 */
    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Cache {
        long tick = Long.MIN_VALUE;
        final Map<BlockPos, Aggregate> aggregates = new HashMap<>();
        /** 本 tick 已经同步过告警的网络（按聚合结果实例去重：同一网络的所有成员共享同一个实例）。 */
        final Set<Aggregate> alarmSynced = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * 扫描 {@code origin} 所在的热压差网络（不查缓存，趟一遍全图）——查看命令用。
     * 若 {@code origin} 既不是传递件也不是机器，返回空账本。
     */
    public static Scan scan(Level level, BlockPos origin) {
        Aggregate aggregate = collect(level, origin);
        return new Scan(aggregate.load(), aggregate.generatorCount(), aggregate.consumerCount(),
                aggregate.connectors().size());
    }

    /**
     * 取 {@code pos} 所在网络的账本（同一 tick 内同一网络只算一次）——<strong>消耗端每 tick 调这个</strong>。
     * <p>返回的是<strong>这个位置看到的账本</strong>：容量 / 占用 / 超载是全网的（同一张账本），
     * 只有<strong>热流率</strong>会被限流管按位置压低（见 {@link ThermalGate}）。
     * 若 {@code pos} 不在任何网络里，返回空账本（容量 / 热流率 / 占用都是 0，倍速因此为 0）。
     */
    public static ThermalLoad loadAt(Level level, BlockPos pos) {
        return aggregateAt(level, pos).loadAt(pos);
    }

    /**
     * 服务端：把本网络的<strong>超载状态</strong>推给它的连接件（铜管），超载时它们会发红闪。
     * <p>
     * 只在状态<strong>变化</strong>时写方块（见 {@link CopperPipeBlock#setOverloaded}），稳态零开销。
     * 谁该调它：消耗端在自己的 ticker 里、或任何想刷新告警的地方 —— 网络本身没有中央 ticker。
     *
     * @return 实际被改动的连接件数量
     */
    public static int syncAlarm(ServerLevel level, BlockPos pos) {
        Cache cache = CACHES.computeIfAbsent(level, ignored -> new Cache());
        Aggregate aggregate;
        synchronized (cache) {
            aggregate = aggregateIn(cache, level, pos);
            // 同一 tick 内同一张网络只同步一次：多台消耗端各自调用，但超载状态是全网一致的。
            // 写方块放在锁外，避免在持有缓存锁时触发邻居更新。
            if (!cache.alarmSynced.add(aggregate)) {
                return 0;
            }
        }
        boolean overloaded = aggregate.load().overloaded();
        int changed = 0;
        for (BlockPos connector : aggregate.connectors()) {
            if (CopperPipeBlock.setOverloaded(level, connector, overloaded)) {
                changed++;
            }
        }
        return changed;
    }

    /** 清空缓存（按 tick 会自动失效；保留给调试用）。 */
    public static void invalidate(Level level) {
        CACHES.remove(level);
    }

    /** 取（必要时算并缓存）{@code pos} 所在网络的聚合结果。 */
    private static Aggregate aggregateAt(Level level, BlockPos pos) {
        Cache cache = CACHES.computeIfAbsent(level, ignored -> new Cache());
        synchronized (cache) {
            return aggregateIn(cache, level, pos);
        }
    }

    /** 在已加锁的缓存里取聚合结果；跨 tick 时整表清空。 */
    private static Aggregate aggregateIn(Cache cache, Level level, BlockPos pos) {
        long now = level.getGameTime();
        if (cache.tick != now) {
            cache.tick = now;
            cache.aggregates.clear();
            cache.alarmSynced.clear();
        }
        Aggregate cached = cache.aggregates.get(pos);
        if (cached != null) {
            return cached;
        }
        Aggregate aggregate = collect(level, pos);
        for (BlockPos member : aggregate.members()) {
            cache.aggregates.put(member, aggregate);
        }
        // 起点不在网络里时也要给出结果，避免同一 tick 反复重算同一个「无网络」位置。
        cache.aggregates.put(pos, aggregate);
        return aggregate;
    }

    private static Aggregate collect(Level level, BlockPos origin) {
        ThermalLoad load = new ThermalLoad();
        Set<BlockPos> members = new LinkedHashSet<>();
        Set<BlockPos> connectors = new LinkedHashSet<>();
        List<RawConsumer> rawConsumers = new ArrayList<>();
        Set<BlockPos> generatorPositions = new HashSet<>();
        int generators = 0;
        int consumers = 0;

        if (!isNetworkPart(level, origin)) {
            return new Aggregate(load, 0, 0, members, connectors, Map.of(), Map.of());
        }

        // ---- 第 1 趟：连通 + 角色（6 面 BFS；每个位置**访问即记账**，只记一次；闸门照常导通 = 同一张网络）----
        Deque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin);
        frontier.add(origin);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            members.add(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ThermalGenerator generator) {
                load.addGenerator(generator.capacity(), generator.flowRate());
                generatorPositions.add(pos);
                generators++;
            } else if (blockEntity instanceof LargeMachine machine) {
                // 大机器框架：只有「大型机器」算消耗端；贴铜块或底排铜管都算接入。
                // 它仍像普通机器一样导通 —— 否则它在自己格子上的 loadAt 只会扫到自己、拿不到网络账本
                // （铜块 / 铜管接在网络里，必须从机器这格走出去才能扫到）。
                if (machine.isLargeMachine() && LargeMachines.touchesCopperHeat(level, machine)
                        && blockEntity instanceof ThermalConsumer consumer) {
                    rawConsumers.add(new RawConsumer(pos, consumer.thermalResistance(),
                            consumer.maxUsefulFlowRate()));
                    consumers++;
                }
            } else if (blockEntity instanceof ThermalConsumer consumer) {
                rawConsumers.add(new RawConsumer(pos, consumer.thermalResistance(),
                        consumer.maxUsefulFlowRate()));
                consumers++;
            } else if (HotChamber.isWorkingCell(level, pos)) {
                // 热室（底部垫铜块的铜箱子）：结构完整时按**一台**消耗端记账 ——
                // 双联大铜箱子只认代表格那一半，所以整箱只算 1 份热耗（合并库存 54 格）。
                rawConsumers.add(new RawConsumer(pos, HotChamber.RESISTANCE, HotChamber.maxUsefulFlowRate(level, pos)));
                consumers++;
            } else if (CopperHeat.isAttachedConsumer(level, pos)) {
                // 贴着铜块的木桶 / 熔炉系列 / 机械土壤：这才是消耗端（铜块本身只当导体，不记账）。
                double maxFlow = level instanceof ServerLevel serverLevel
                        ? CopperHeat.maxUsefulFlowRate(serverLevel, pos) : 0.0D;
                rawConsumers.add(new RawConsumer(pos, CopperHeat.resistanceAt(level, pos), maxFlow));
                consumers++;
            } else if (level.getBlockState(pos).getBlock() instanceof ThermalTransmitter) {
                connectors.add(pos);
            }
            // 闸门（限流管）走到这里什么都不记：它**不是消耗端也不是生成端**、不占 DP；
            // 它只在第 2 趟里给「朝它下游走的那一步」压一道热流率上限。
            // 铜块也什么都不记：它是「消耗端 ↔ 铜管」的中间导体，只负责把网络连通。

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (visited.contains(neighbor)) {
                    continue;
                }
                if (!isNetworkPart(level, neighbor)) {
                    visited.add(neighbor);   // 非网络成员：记一笔，免得反复判定
                    continue;
                }
                if (isCutBetween(level, pos, direction, neighbor)) {
                    continue;   // 这一端被剪刀切断：不标记 visited，允许从别的路径绕到它
                }
                visited.add(neighbor);
                frontier.add(neighbor);
            }
        }

        // ---- 第 2 趟：热流率上限（**从生成端出发**按「跨过闸门朝下游」逐格收窄）----
        // 必须从生成端起铺：热流率是「从生成端流出来的」，从别处起铺会因为起点自带「不限」而丢掉闸门的封顶。
        // 松弛式：更宽的路径可以放宽某格的上限（并联闸门取较宽、串联取最小）；反向穿过闸门不封顶。
        Map<BlockPos, Double> flowCaps = new HashMap<>();
        Deque<Step> capFrontier = new ArrayDeque<>();
        for (BlockPos generatorPos : generatorPositions) {
            flowCaps.put(generatorPos, Double.POSITIVE_INFINITY);
            capFrontier.add(new Step(generatorPos, Double.POSITIVE_INFINITY));
        }
        while (!capFrontier.isEmpty()) {
            Step step = capFrontier.poll();
            BlockPos pos = step.pos();
            Double recorded = flowCaps.get(pos);
            if (recorded != null && recorded > step.cap()) {
                continue;   // 旧状态：已有更宽的路走到它
            }
            double cap = recorded == null ? step.cap() : recorded;
            ThermalGate gate = level.getBlockEntity(pos) instanceof ThermalGate found ? found : null;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!isNetworkPart(level, neighbor)) {
                    continue;
                }
                if (isCutBetween(level, pos, direction, neighbor)) {
                    continue;   // 剪刀切断的那一端：热流率也不从这里过
                }
                double nextCap = cap;
                if (gate != null && direction == gate.outputFace() && gate.flowLimit() > 0.0D) {
                    nextCap = Math.min(nextCap, gate.flowLimit());
                }
                Double previous = flowCaps.get(neighbor);
                if (previous == null || nextCap > previous) {
                    flowCaps.put(neighbor, nextCap);
                    capFrontier.add(new Step(neighbor, nextCap));
                }
            }
        }

        // ---- 登记消耗端：每台机器用「它所在位置」的上限（限流管下游的那些会被压低）----
        for (RawConsumer raw : rawConsumers) {
            Double cap = flowCaps.get(raw.pos());
            double flowCap = cap == null || cap.isInfinite() ? 0.0D : cap;
            load.addConsumer(raw.resistance(), raw.maxUsefulFlowRate(), flowCap);
        }

        return new Aggregate(load, generators, consumers, members, connectors, flowCaps, new HashMap<>());
    }

    /**
     * 相邻两格之间能不能传热：铜管被剪刀<strong>手动切断</strong>的那一端断开。
     * <p>只认「切断」标记，不认管臂朝向 —— 老存档的管臂可能没刷新，但仍应按方块相邻导通。
     * 两侧都查（{@code CopperPipeBlock#cutNeighbor} 会同步对侧标记），任一标记为切断即断路。
     */
    private static boolean isCutBetween(Level level, BlockPos pos, Direction direction, BlockPos neighbor) {
        return CopperPipeBlock.isCut(level, pos, direction)
                || CopperPipeBlock.isCut(level, neighbor, direction.getOpposite());
    }

    /** 这一格能不能算网络成员：传递件、铜块（中间导体）、<strong>热室</strong>（铜箱子垫铜块）、贴着铜块的消耗端、或实现了热压差角色的机器方块实体。 */
    private static boolean isNetworkPart(Level level, BlockPos pos) {
        // 闸门（限流管）：算网络成员 —— 它照常导通（同一张网络），只在 BFS 里给「朝它下游走的那一步」加上限
        if (level.getBlockEntity(pos) instanceof ThermalGate) {
            return true;
        }
        if (level.getBlockState(pos).getBlock() instanceof ThermalTransmitter) {
            // 铜管是传递件：只导通、不记账（机械管不是 ThermalTransmitter，根本走不到这里）
            return true;
        }
        if (CopperHeat.isCopperBlock(level, pos)) {
            return true;   // 铜块：消耗端与铜管之间的中间导体，相邻的铜块整组视作同一节点
        }
        if (HotChamber.isPart(level, pos)) {
            return true;   // 热室（铜箱子垫铜块）：经底部铜块接入的记账节点
        }
        if (CopperHeat.isAttachedConsumer(level, pos)) {
            return true;   // 贴着铜块的木桶 / 熔炉系列（记账节点；仍与其它成员一样继续导通）
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        // 大机器生成端 / 消耗端都可直接经铜管接入。
        if (blockEntity instanceof ThermalGenerator) {
            return true;
        }
        if (blockEntity instanceof LargeMachine machine) {
            // 非大型机器（同用一个方块实体的其它生化机器）一律不算网络成员
            return machine.isLargeMachine() && LargeMachines.touchesCopperHeat(level, machine);
        }
        // 大型机器的填充格：等同机器本体（这样「2×2×2 任意一格」都能连上机器）
        BlockPos largeOrigin = LargeMachines.originOf(level, pos);
        if (largeOrigin != null && level.getBlockEntity(largeOrigin) instanceof LargeMachine machine
                && machine.isLargeMachine()) {
            return machine instanceof ThermalGenerator || LargeMachines.touchesCopperHeat(level, machine);
        }
        return blockEntity instanceof ThermalConsumer;
    }

    private ThermalNetwork() {}
}
