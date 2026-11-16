package dev.modmind.stellar_evolution.thermal;

import java.util.ArrayList;
import java.util.List;

/**
 * 「热压差」网络的负荷账本 —— <strong>纯数值、不依赖 Minecraft</strong>（因此可直接写单元测试）。
 *
 * <p>设计口径（需求原文见开发文档《热压差》）：
 * <ul>
 *   <li><strong>容量（DP）</strong>：各<strong>生成端</strong>提供的差压容量<strong>相加</strong>；</li>
 *   <li><strong>热流率（HP/t）</strong>：取网络内<strong>生成端热流率的最高值</strong>，作为整个网络的热流率；</li>
 *   <li><strong>占用（DP）</strong>：Σ（消耗端<strong>基础热阻</strong> × 该机的<strong>有效热流率</strong>）——
 *       有效热流率 = min(网络热流率, 该机的「满速阈值」)；没给阈值（{@code ≤0}）就按网络原值，即「实际消耗 =
 *       基础热阻 × 热流率」，热阻越大越费。给阈值的机器（大机器并行封顶 / 机械土壤 / 木桶 / 熔炉）超过阈值后
 *       的 HP <strong>不再计 DP</strong>（倍速仍由机器自行 clamp 到「每 tick 最多一次」）。</li>
 *   <li><strong>超载</strong>：占用 &gt; 容量 → 整个网络的消耗端<strong>全部停转</strong>（对齐机械动力「过载」）；</li>
 *   <li><strong>工作倍速</strong>：热流率 / 该机<strong>基准热流率</strong>（比值制，高于基准更快、低于更慢）；
 *       超载或网络无热流率时为 0（停转）。</li>
 *   <li><strong>传递件</strong>（铜管）只传不占：既不进容量也不进占用，故本账本完全不需要它。</li>
 * </ul>
 *
 * <p>登记顺序无关：占用按「热阻 × 有效热流率」<strong>延迟计算</strong>，
 * 所以先登记消耗端、后登记生成端也能得到正确结果。
 */
public final class ThermalLoad {
    /** 差压容量合计（DP）。 */
    private double capacity;
    /** 网络热流率（HP/t）：生成端热流率的最大值。 */
    private double flowRate;
    /**
     * 「位置视图」用的热流率再封顶（HP/t）：{@code ≤0} = 不封。
     * <p>由限流管（{@link ThermalGate}）造成 —— 跨过闸门往下游的那一段网络，看到的网络热流率被压低。
     */
    private double viewFlowCap;

    /** 单个消耗端：基础热阻 + 「满速阈值」（HP/t；{@code ≤0} = 不封顶）+ 「本机位置的热流率上限」（HP/t；{@code ≤0} = 不封）。 */
    private record Consumer(double resistance, double maxUsefulFlowRate, double flowCap) {}

    /** 已登记的消耗端（占用延迟到 {@link #occupancy()} 再按热流率算，保证登记顺序无关）。 */
    private final List<Consumer> consumers = new ArrayList<>();

    /** 登记一个生成端：容量累加，热流率只取最大值。 */
    public void addGenerator(double capacity, double flowRate) {
        if (capacity > 0.0D) {
            this.capacity += capacity;
        }
        if (flowRate > this.flowRate) {
            this.flowRate = flowRate;
        }
    }

    /** 登记一个消耗端（不封顶）。 */
    public void addConsumer(double thermalResistance) {
        addConsumer(thermalResistance, 0.0D, 0.0D);
    }

    /**
     * 登记一个消耗端：累加基础热阻，并记下它的「有效热流率」上限。
     *
     * @param maxUsefulFlowRate 该机满速所需热流率（HP/t）；{@code ≤0} = 不封顶（按网络原值计）
     */
    public void addConsumer(double thermalResistance, double maxUsefulFlowRate) {
        addConsumer(thermalResistance, maxUsefulFlowRate, 0.0D);
    }

    /**
     * 登记一个消耗端（带「所在位置的热流率上限」）—— 限流管下游的那些机器用这条：
     * 它们能拿到的热流率会被闸门压低，占用也随之变小。
     *
     * @param flowCap 本机位置的热流率上限（HP/t）；{@code ≤0} = 不封
     */
    public void addConsumer(double thermalResistance, double maxUsefulFlowRate, double flowCap) {
        if (thermalResistance > 0.0D) {
            this.consumers.add(new Consumer(thermalResistance, maxUsefulFlowRate, flowCap));
        }
    }

    /** 某台设备<strong>实际能用到的热流率</strong>：网络热流率 ∩ 满速阈值 ∩ 本机位置上限。 */
    private double usefulFlow(Consumer consumer) {
        double flow = flowRate;
        if (consumer.maxUsefulFlowRate() > 0.0D) {
            flow = Math.min(flow, consumer.maxUsefulFlowRate());
        }
        if (consumer.flowCap() > 0.0D) {
            flow = Math.min(flow, consumer.flowCap());
        }
        return flow;
    }

    /** 差压容量合计（DP）。 */
    public double capacity() {
        return capacity;
    }

    /** 网络热流率（HP/t）；无生成端时为 0。 */
    public double flowRate() {
        return viewFlowCap > 0.0D ? Math.min(flowRate, viewFlowCap) : flowRate;
    }

    /** 消耗端实际占用的差压容量（DP）＝ Σ基础热阻 × 该机实际可用热流率。 */
    public double occupancy() {
        double total = 0.0D;
        for (Consumer consumer : consumers) {
            total += consumer.resistance() * usefulFlow(consumer);
        }
        return total;
    }

    /** 是否超载（占用 &gt; 容量）。容量为 0 且无消耗端时不算超载。 */
    public boolean overloaded() {
        return occupancy() > capacity;
    }

    /** 负载率：占用 / 容量。容量为 0 时，无占用返回 0、有占用返回 {@link Double#POSITIVE_INFINITY}。 */
    public double loadRatio() {
        if (capacity <= 0.0D) {
            return occupancy() <= 0.0D ? 0.0D : Double.POSITIVE_INFINITY;
        }
        return occupancy() / capacity;
    }

    /**
     * 某消耗端在本网络下的工作倍速：
     * 超载 → 0（停转）；本机可用热流率（已按位置封顶）为 0 → 0（停转）；
     * 否则 = 可用热流率 / 该机基准热流率。
     * <p><strong>不读</strong>该机的 {@code maxUsefulFlowRate} —— 那个值只用于封顶占用（DP）；
     * 倍速上限由各机器自己 clamp（大机器 {@code min(..., 并行)}、开采钻 4 倍等）。
     *
     * @param baseFlowRate 该机达到「1.0 倍速」所需的基准热流率（HP/t）；非正数按 1.0 倍速处理
     */
    public double speedFor(double baseFlowRate) {
        if (overloaded()) {
            return 0.0D;
        }
        double flow = flowRate();
        if (flow <= 0.0D) {
            return 0.0D;
        }
        if (baseFlowRate <= 0.0D) {
            return 1.0D;
        }
        return flow / baseFlowRate;
    }

    /**
     * 「本机位置」的视图：把网络热流率再封一次顶（限流管下游用）。
     * <p>占用 / 超载 / 容量仍按<strong>整张网络</strong>算（同一张账本），只有
     * {@link #flowRate()} / {@link #speedFor(double)} 跟着位置变 —— 这就是「同一个网络、不同地方热流率不同」。
     *
     * @param flowCap 本机位置的热流率上限（HP/t）；{@code ≤0} = 不封（直接返回本对象）
     */
    public ThermalLoad view(double flowCap) {
        if (flowCap <= 0.0D) {
            return this;
        }
        ThermalLoad view = new ThermalLoad();
        view.capacity = this.capacity;
        view.flowRate = this.flowRate;
        view.viewFlowCap = flowCap;
        view.consumers.addAll(this.consumers);
        return view;
    }
}
