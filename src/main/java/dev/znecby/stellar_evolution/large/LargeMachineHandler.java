package dev.znecby.stellar_evolution.large;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * 大机器<strong>物流能力的公共骨架</strong>：把「物流网络里连接的全部容器」摊平成一条
 * {@link ResourceHandler} 视图，供漏斗 / 管道（transfer API）读写。
 *
 * <p>摊平口径：每个连接容器的<strong>每个槽</strong>算一个索引；容器方向决定该索引可进 / 可出 ——
 * {@link LargeMachineIo#INPUT} 只可<strong>灌入</strong>、{@link LargeMachineIo#OUTPUT} 只可<strong>抽出</strong>。
 * 于是「大机器对外能力」= 输入箱 + 输出箱的合并视图，其它模组按标准能力接入即可。
 *
 * <p>子类只需给出「某位置的物品 / 流体能力」（{@link #handlerAt}）与「空资源」（{@link #empty}）。
 * 结果按 tick 缓存（机器 / 网络拓扑变化下一 tick 自动生效）。
 */
public abstract class LargeMachineHandler<T extends Resource> implements ResourceHandler<T> {
    /** 一个摊平后的槽：其所属容器能力 + 槽位 + 可进 / 可出。 */
    protected record Ref<T extends Resource>(ResourceHandler<T> handler, int slot, boolean insertable,
                                             boolean extractable) {}

    protected final LargeMachine machine;

    private long tick = Long.MIN_VALUE;
    /** 内部视角：含虚拟源（铜格栅），机器取料 / 放料用。 */
    private List<Ref<T>> refs = List.of();
    /** 对外视角（别的模组 / Jade）：可隐藏虚拟源，避免把「14 位数的水罐」摆给玩家看。按需构建。 */
    @Nullable
    private List<Ref<T>> externalRefs;

    protected LargeMachineHandler(LargeMachine machine) {
        this.machine = machine;
    }

    /** 某位置的资源能力（物品 / 流体）；没有则 null。 */
    @Nullable
    protected abstract ResourceHandler<T> handlerAt(Level level, BlockPos pos);

    /** 空资源（索引越界时的兜底返回）。 */
    protected abstract T empty();

    /** 这个容器要不要出现在<strong>对外</strong>能力里（默认全部出现；虚拟源可覆写成 false）。 */
    protected boolean showExternally(Level level, BlockPos pos) {
        return true;
    }

    private List<Ref<T>> refs() {
        return refs(false);
    }

    private List<Ref<T>> externalRefs() {
        return refs(true);
    }

    private List<Ref<T>> refs(boolean external) {
        Level level = machine.level();
        if (level == null) {
            return List.of();
        }
        long now = level.getGameTime();
        if (now != tick) {
            tick = now;
            refs = build(level, false);
            // 对外视角只在真的被外部能力 / Jade 查询时才构建：绝大多数 tick 只有机器自己取料。
            externalRefs = null;
        }
        if (!external) {
            return refs;
        }
        List<Ref<T>> view = externalRefs;
        if (view == null) {
            view = build(level, true);
            externalRefs = view;
        }
        return view;
    }

    private List<Ref<T>> build(Level level, boolean external) {
        List<Ref<T>> list = new ArrayList<>();
        // 双联箱 / 多方块容器可能让两个位置返回同一个能力对象；按身份去重，避免容量翻倍
        Set<ResourceHandler<T>> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (LargeMachines.Connected connected : LargeMachines.connectedContainers(level, machine)) {
            if (external && !showExternally(level, connected.pos())) {
                continue;
            }
            ResourceHandler<T> handler = handlerAt(level, connected.pos());
            if (handler == null || !seenHandlers.add(handler)) {
                continue;
            }
            boolean input = machine.ioMode(connected.pos()) == LargeMachineIo.INPUT;
            for (int i = 0; i < handler.size(); i++) {
                list.add(new Ref<>(handler, i, input, !input));
            }
        }
        return list;
    }

    @Nullable
    private Ref<T> ref(int index) {
        List<Ref<T>> list = externalRefs();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    /** 对外能力：索引空间必须与 {@link #getResource} / {@link #insert} / {@link #extract} 一致（外部视角，隐藏虚拟源）。 */
    @Override
    public int size() {
        return externalRefs().size();
    }

    /** 对外视角：容器里任意一格有资源（不含铜格栅这类虚拟源）；机器自己判断「罐里有没有水」用。 */
    public boolean hasAnyContent() {
        for (Ref<T> ref : externalRefs()) {
            if (!ref.handler().getResource(ref.slot()).isEmpty()
                    && ref.handler().getAmountAsLong(ref.slot()) > 0L) {
                return true;
            }
        }
        return false;
    }

    @Override
    public T getResource(int index) {
        Ref<T> ref = ref(index);
        return ref == null ? empty() : ref.handler().getResource(ref.slot());
    }

    @Override
    public long getAmountAsLong(int index) {
        Ref<T> ref = ref(index);
        return ref == null ? 0L : ref.handler().getAmountAsLong(ref.slot());
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        Ref<T> ref = ref(index);
        return ref == null ? 0L : ref.handler().getCapacityAsLong(ref.slot(), resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        Ref<T> ref = ref(index);
        return ref != null && ref.handler().isValid(ref.slot(), resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        Ref<T> ref = ref(index);
        if (ref == null || !ref.insertable()) {
            return 0;
        }
        return ref.handler().insert(ref.slot(), resource, amount, transaction);
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        Ref<T> ref = ref(index);
        if (ref == null || !ref.extractable()) {
            return 0;
        }
        return ref.handler().extract(ref.slot(), resource, amount, transaction);
    }

    // ------------------------------------------------------------------ 机器内部取料 / 放料
    // 对外能力的方向与机器自己相反：输入容器只可灌入、输出容器只可抽出（外部视角）；
    // 机器自己要「从输入容器取料、往输出容器放产物」，所以另给两个不受方向限制的入口。

    /** 机器自己：在<strong>输入容器</strong>里找第一个满足条件的资源（不移动）。 */
    @Nullable
    public T firstInInputs(Predicate<T> predicate) {
        for (Ref<T> ref : refs()) {
            if (!ref.insertable()) {
                continue;
            }
            T resource = ref.handler().getResource(ref.slot());
            if (!resource.isEmpty() && ref.handler().getAmountAsLong(ref.slot()) > 0 && predicate.test(resource)) {
                return resource;
            }
        }
        return null;
    }

    /** 机器自己：输入容器里当前有货的资源（每个槽一条，可能重复）。 */
    public List<T> inputResources() {
        List<T> list = new ArrayList<>();
        for (Ref<T> ref : refs()) {
            if (!ref.insertable()) {
                continue;
            }
            T resource = ref.handler().getResource(ref.slot());
            if (!resource.isEmpty() && ref.handler().getAmountAsLong(ref.slot()) > 0) {
                list.add(resource);
            }
        }
        return list;
    }

    /** 机器自己：往<strong>输入容器</strong>放资源（改燃料棒 / 石墨棒时原地写回）。 */
    public int insertIntoInputs(T resource, int amount, TransactionContext transaction) {
        int moved = 0;
        for (Ref<T> ref : refs()) {
            if (!ref.insertable()) {
                continue;
            }
            moved += ref.handler().insert(ref.slot(), resource, amount - moved, transaction);
            if (moved >= amount) {
                break;
            }
        }
        return moved;
    }

    /** 机器自己：从<strong>输入容器</strong>抽资源。 */
    public int extractFromInputs(T resource, int amount, TransactionContext transaction) {
        int moved = 0;
        for (Ref<T> ref : refs()) {
            if (!ref.insertable()) {
                continue;   // 输入容器：insertable=true
            }
            moved += ref.handler().extract(ref.slot(), resource, amount - moved, transaction);
            if (moved >= amount) {
                break;
            }
        }
        return moved;
    }

    /**
     * 机器自己：按<strong>谓词</strong>从输入容器抽资源（标签输入用，例如任意原木）。
     * <p>逐个输入槽检查当前资源是否满足谓词，满足才抽；抽够即停。
     */
    public int extractFromInputs(Predicate<T> predicate, int amount, TransactionContext transaction) {
        int moved = 0;
        for (Ref<T> ref : refs()) {
            if (!ref.insertable()) {
                continue;
            }
            T resource = ref.handler().getResource(ref.slot());
            if (resource.isEmpty() || !predicate.test(resource)) {
                continue;
            }
            moved += ref.handler().extract(ref.slot(), resource, amount - moved, transaction);
            if (moved >= amount) {
                break;
            }
        }
        return moved;
    }

    /** 机器自己：输入容器里是否有足够的某种资源（<strong>只看不取</strong>，供「催化剂」判定）。 */
    public boolean hasInInputs(T resource, int amount) {
        return countInInputs(resource) >= amount;
    }

    /** 机器自己：输入容器里某种资源的总数（<strong>只看不取</strong>；不含输出容器）。 */
    public long countInInputs(T resource) {
        long total = 0L;
        for (Ref<T> ref : refs()) {
            if (!ref.insertable()) {
                continue;
            }
            if (ref.handler().getResource(ref.slot()).equals(resource)) {
                total += ref.handler().getAmountAsLong(ref.slot());
            }
        }
        return total;
    }

    /** 机器自己：把产物放进<strong>输出容器</strong>。 */
    public int insertIntoOutputs(T resource, int amount, TransactionContext transaction) {
        int moved = 0;
        for (Ref<T> ref : refs()) {
            if (!ref.extractable()) {
                continue;   // 输出容器：extractable=true
            }
            moved += ref.handler().insert(ref.slot(), resource, amount - moved, transaction);
            if (moved >= amount) {
                break;
            }
        }
        return moved;
    }
}
