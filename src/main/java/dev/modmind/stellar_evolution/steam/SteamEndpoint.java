package dev.modmind.stellar_evolution.steam;

import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * 蒸汽网络的<strong>端点</strong>（反应堆 = 生成端、汽轮机 = 消耗端）。
 *
 * <p>压力管只负责把端点连成一张网络：{@link SteamNetwork} 从管子出发做 6 面广度优先，
 * 沿途碰到「属于实现本接口的机器」的任意一格就把它记成端点。端点自己<strong>不占管道格</strong>，
 * 也不改变管道拓扑。
 *
 * <p>实现方必须是机器<strong>主方块</strong>的方块实体（2×2×2 的另外 7 格由
 * {@code LargeMachines.originOf} 反查回主方块）。
 */
public interface SteamEndpoint {
    /** 端点状态（Jade / 粒子 / 诊断用）。 */
    enum SteamStatus {
        /** 没起蒸汽 / 没在跑。 */
        IDLE,
        /** 生成端：旁边没有压力管，蒸汽无法输出。 */
        NO_PIPE,
        /** 生成端：网络里没有消耗端 → 末端排气，蒸汽全部丢弃。 */
        VENTING,
        /** 生成端：正常送给网络里的汽轮机。 */
        DELIVERING,
        /** 消耗端：没有压力管 / 没有蒸汽。 */
        NO_STEAM,
        /** 消耗端：可能没到 100 RPM（还不输出）。 */
        SPINNING_UP,
        /** 消耗端：正常运转。 */
        RUNNING,
        /** 消耗端：网络有汽，但被更近的机组优先分走 / 份额为 0。 */
        STARVED
    }

    /** 是生成端（反应堆）还是消耗端（汽轮机）。 */
    boolean steamGenerator();

    /** 生成端：本 tick 能送进网络的蒸汽（消耗端恒 {@link SteamState#NONE}）。 */
    default SteamState steamOutput() {
        return SteamState.NONE;
    }

    /** 消耗端：本 tick 想要的蒸汽量（mB/t）；生成端恒 0。 */
    default double steamDemand() {
        return 0.0D;
    }

    /** 消耗端：在给定蒸汽下“跑满该档上限”需要的蒸汽；默认等于当前需求。 */
    default double steamMaxDemand(SteamState steam) {
        return steamDemand();
    }

    /** 这台机器占的全部格（2×2×2 的 8 格），用于「哪根压力管贴着它」。 */
    List<BlockPos> steamCells();

    /** 机器原点（主方块位置）。 */
    BlockPos steamOrigin();

    /** 当前状态快照。 */
    SteamStatus steamStatus();
}
