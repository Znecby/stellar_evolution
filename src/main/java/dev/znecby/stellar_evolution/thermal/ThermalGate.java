package dev.znecby.stellar_evolution.thermal;

import net.minecraft.core.Direction;

/**
 * 热压差网络里的<strong>闸门</strong>（限流管）：<strong>不切网、不当生成 / 消耗端</strong> ——
 * 它只是让「<strong>跨过它的那一段网络</strong>」看到<strong>更低的热流率</strong>。
 *
 * <p><strong>语义（2026-10-16 定稿：同一张网络、不同的地方热流率不同）</strong>：
 * 闸门照常<strong>导通</strong>（{@link ThermalNetwork} 的 BFS 直接穿过它），所以两侧仍是
 * <strong>同一张账本</strong> —— 容量（DP）是全网生成端之和、占用与超载也全网算一次，
 * 不存在「多一份容量」这种事；闸门<strong>本身不占 DP</strong>。
 * 它唯一的作用是在 {@link ThermalNetwork} 里给热流率加一道<strong>位置上限</strong>：
 * 从闸门 {@link #outputFace()} 走出去的成员，其「可用热流率」会被
 * {@link #flowLimit()} 封一次顶（再与网络热流率、各机满速阈值取最小值）。
 *
 * <p>方向是<strong>单向</strong>的：朝下游（{@code outputFace}）走才加这道上限；
 * 反向（从下游往上游）不封顶 —— 于是「限流管只影响它下游那一段」。
 * 多个闸门串联时上限会叠加（取最小）；并联时按「任一条路能供上」取较宽的那条。
 */
public interface ThermalGate {
    /** 朝下游的那一面（管子「出水口」）—— 从这一面走出去的成员才吃 {@link #flowLimit()} 的封顶。 */
    Direction outputFace();

    /** 朝上游的那一面。 */
    default Direction inputFace() {
        return outputFace().getOpposite();
    }

    /**
     * 下游可用热流率的上限（HP/t）：{@code ≤0} = <strong>不限</strong>（等价于一根普通铜管）。
     * <p>只做封顶、不放大 —— 实际可用 = min(网络热流率, 本值)。
     */
    double flowLimit();
}
