package dev.znecby.stellar_evolution.stream;

/**
 * 束流网络的<strong>源</strong>：目前只有缪子聚变仪。
 *
 * <p>网络<strong>不存粒子流</strong>：每 tick 由源自己给出 {@link #streamRate()} / {@link #streamEmitted()}，
 * {@link StreamNetwork} 汇总成一份快照。
 */
public interface StreamEndpoint extends StreamNode {
    /** 本 tick 输出的粒子流速（单位/t）；不输出时为 0。 */
    double streamRate();

    /** 累计输出的粒子流总量（单位）。 */
    double streamEmitted();

    /**
     * 本机<strong>往外喷</strong>的流类型。默认与 {@link #streamType()} 相同（如粒子源）；
     * 对撞机这类「吃两路、喷一路」的机器要单独覆盖它。
     */
    default StreamType outputStreamType() {
        return streamType();
    }

    /**
     * 本机此刻/本配方<strong>是否真的有输出流</strong>。
     * <p>用于「空管贴着机器时由谁定类型」：只有真正会喷流的机器（粒子源 / 对撞机）才能给空管定型，
     * 纯消费者（辐照仓）返回 false。
     */
    default boolean emitsStream() {
        return true;
    }
}
