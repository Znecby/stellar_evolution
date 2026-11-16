package dev.znecby.stellar_evolution.stream;

/**
 * 束流网络的<strong>消费者</strong>：辐照 / 嬗变 / 对撞 / 增殖包层等吃粒子流的机器。
 *
 * <p>与旧版的关键区别：<strong>机器不再预设自己「只吃一种流」</strong>。机器旁边接了几种束流管，
 * 对应的几束网络在采集时都会把它算进需求表，各自调用 {@link #streamDemand(StreamType)}
 * 问它这一路想要多少；机器按自己的配方表决定<strong>当前只吃哪一路</strong>
 * （其余路返回 0，不参与分配），再由 {@link #streamAccept(StreamType, double)} 记账。
 *
 * <p>这样「接了什么就是什么，接了多种就是多种」——多路都在时由机器的配方优先级决定吃哪一路，
 * 不会出现两种流混进同一份进度。
 *
 * <p>网络本身仍然是<strong>单流</strong>的（一段管网只承载一种粒子）：份额由
 * {@link StreamNetwork.State#shareOf(net.minecraft.core.BlockPos)} 按
 * <strong>max-min 公平分配</strong>算出，同 tick 内所有节点读到同一份快照，互不抢跑。
 */
public interface StreamConsumer extends StreamNode {
    /**
     * 这一路束流本 tick 最多想接收的量（单位/t）；不吃这一路时返回 0（不参与分配）。
     *
     * @param type 正在采集的那张网络的粒子流类型
     */
    double streamDemand(StreamType type);

    /**
     * 实际接收到的量（单位/t），由消费者自己累计 / 参与配方判定。
     *
     * @param type   这一份额来自哪种粒子流
     * @param amount 分到的量
     */
    void streamAccept(StreamType type, double amount);
}
