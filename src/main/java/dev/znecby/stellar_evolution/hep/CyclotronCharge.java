package dev.znecby.stellar_evolution.hep;

/**
 * 回旋加速器的<strong>差速账本</strong>（纯数值，不依赖 Minecraft，可单测）。
 *
 * <p>口径（2026-10-29 用户定稿）：
 * <ul>
 *   <li><strong>每 tick 充能</strong>：{@code charge += 网络 HP/t ÷ 64}，封顶 = <strong>当前配方的差速需求</strong>；</li>
 *   <li>差速达到需求 → 执行配方，差速清零；</li>
 *   <li><strong>并行量</strong> = {@code floor(网络 DP / 当前配方需求)}，<strong>保底 1</strong>，再被粉量 / 输出空间 / 64 夹住；</li>
 *   <li>配方里写的差速 = 机器面板显示的「差速上限」，也是结算所需时间：{@code 秒数 = 3.2 × 需求 ÷ HP}。</li>
 * </ul>
 */
public final class CyclotronCharge {
    /** 单次最多并行 64 个产物。 */
    public static final int MAX_OPS = 64;
    /** 多少网络 HP/t 换 1 差速/t。 */
    public static final double HP_PER_STEP = 64.0D;

    /** 每 tick 充能：加网络热流率 ÷ {@value #HP_PER_STEP}，封顶当前配方需求。 */
    public static double charge(double charge, double demand, double flowRate) {
        if (demand <= 0.0D) {
            return 0.0D;
        }
        double next = Math.max(0.0D, charge) + Math.max(0.0D, flowRate) / HP_PER_STEP;
        return Math.min(demand, next);
    }

    /** 是否达到当前配方的差速需求。 */
    public static boolean ready(double charge, double demand) {
        return demand > 0.0D && charge >= demand;
    }

    /**
     * 并行量：{@code floor(网络 DP / 配方需求)}，<strong>不足 1 份时保底 1 份</strong>，
     * 再被 64、粉量、输出空间夹住。
     * <p>保底只解决「能不能跑」；想一轮多出几份仍然要堆 DP。
     * <p>输出空间不在这里算 —— 调用方用事务试放，放不下就减并行数。
     */
    public static int parallel(double networkDp, double demand, int powder, int budget) {
        if (demand <= 0.0D) {
            return 0;
        }
        int n = (int) Math.floor(Math.max(0.0D, networkDp) / demand);
        n = Math.max(1, n);
        n = Math.min(n, MAX_OPS);
        n = Math.min(n, Math.max(0, powder));
        n = Math.min(n, Math.max(0, budget));
        return Math.max(0, n);
    }

    private CyclotronCharge() {}
}
