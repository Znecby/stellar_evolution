package dev.modmind.stellar_evolution.thermal;

/**
 * <strong>消耗端</strong>（热压差网络的消费者）：<strong>占据差压容量</strong>，并按网络热流率决定实际工作速度。
 *
 * <p>接入方式：让方块的 {@code BlockEntity} 实现本接口，并在自己的 ticker 里每 tick 调用
 * {@link ThermalNetwork#loadAt(net.minecraft.world.level.Level, net.minecraft.core.BlockPos)}
 * 拿到网络账本，然后照 {@link #onThermalTick(ThermalLoad)} 的建议行事即可 ——
 * 框架<strong>不主动驱动</strong>消耗端（没有中央 ticker），消耗端自驱更贴合「谁工作谁结算」。
 *
 * <p>典型写法：
 * <pre>{@code
 * double speed = load.speedFor(baseFlowRate());   // 超载/无生成端 → 0
 * if (speed <= 0.0D) { 停转; return; }
 * 进度 += speed;                                  // 越快的热流率，进度涨得越快
 * }</pre>
 *
 * <p>单位：基础热阻无量纲（DP·t/HP），基准热流率 <strong>HP/t</strong>。
 */
public interface ThermalConsumer {
    /**
     * <strong>基础热阻</strong>：决定实际耗能高低。
     * 实际占用 = 本值 × min(网络热流率, {@link #maxUsefulFlowRate()})（见 {@link ThermalLoad#occupancy()}）。
     */
    double thermalResistance();

    /**
     * <strong>基准热流率</strong>（HP/t）：「1.0 倍速」所需的网络热流率。
     * 网络热流率高于它→更快，低于它→更慢（见 {@link ThermalLoad#speedFor(double)}）。
     */
    double baseFlowRate();

    /**
     * 本机「有效热流率」上限（HP/t）：<strong>只用于封顶占用（DP）</strong> ——
     * 占用按 {@code min(网络热流率, 本值)} 计，超过部分不再计费。
     * <p><strong>不封顶 {@link ThermalLoad#speedFor(double)}</strong>：倍速仍是「网络热流率 / 基准」，
     * 由机器自己把倍速 clamp 到「每 tick 最多完成一次」的上限（大机器 {@code min(..., 并行)}、
     * 开采钻 4 倍、其余每 tick 只结算一次）。
     *
     * @return {@code ≤0} 表示不封顶（按网络原值计）
     */
    default double maxUsefulFlowRate() {
        return 0.0D;
    }

    /**
     * 由消耗端每 tick 自行调用（建议在服务端 ticker 里），拿到本网络的账本后决定工作与否 / 倍速。
     *
     * @param load 本消耗端所在网络的账本快照（同一 tick 内同一网络的多个消耗端拿到的是同一个实例）
     */
    void onThermalTick(ThermalLoad load);
}
