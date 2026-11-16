package dev.znecby.stellar_evolution.thermal;

/**
 * <strong>生成端</strong>（热压差网络的生产者）：往网络里<strong>塞容量</strong>，并给出一个<strong>固定热流率</strong>。
 *
 * <p>接入方式：让方块的 {@code BlockEntity} 实现本接口即可，网络的连通遍历会自动把它认成生成端
 * （遍历逻辑见 {@link ThermalNetwork}）。与机械动力的「动力源」同义：
 * 容量＝它能拖动多少消耗端，热流率＝它能跑多快。
 *
 * <p>单位：容量 <strong>DP（差压）</strong>，热流率 <strong>HP/t（热流率/刻）</strong>。
 */
public interface ThermalGenerator {
    /** 提供的差压容量（DP），进网络时<strong>累加</strong>。 */
    double capacity();

    /** 固定热流率（HP/t）；网络中多个生成端时取<strong>最高值</strong>作为网络热流率。 */
    double flowRate();
}
