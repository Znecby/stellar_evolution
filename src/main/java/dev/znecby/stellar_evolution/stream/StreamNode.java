package dev.znecby.stellar_evolution.stream;

import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * <strong>束流节点</strong>：接在束流管上的机器的公共部分（源与消费者都实现它）。
 *
 * <p>束流管只负责把节点连成网络：{@link StreamNetwork} 从管子出发做 6 面广度优先，
 * 沿途碰到实现本接口的机器任意一格就把它记下来。节点自己不占管道格。
 *
 * <p>实现方必须是机器<strong>主方块</strong>的方块实体（2×2×2 的另外 7 格由
 * {@code LargeMachines.originOf} 反查回主方块）。
 */
public interface StreamNode {
    /** 本机输出 / 接收的粒子流类型（束流管按它与自身类型比对）。 */
    StreamType streamType();

    /** 这台机器占的全部格（2×2×2 的 8 格），用于「哪根束流管贴着它」。 */
    List<BlockPos> streamCells();

    /** 机器原点（主方块位置）。 */
    BlockPos streamOrigin();

    /**
     * 某类型的束流管能不能接到本机（管道连接 / 视觉判定用）。
     *
     * <p>默认只认自己的 {@link #streamType()}；像辐照仓 / 对撞机这种「接什么吃什么」的机器，
     * 只要该类型出现在自己的配方里就应该能接上。
     */
    default boolean acceptsStream(StreamType type) {
        return streamType() == type;
    }
}
