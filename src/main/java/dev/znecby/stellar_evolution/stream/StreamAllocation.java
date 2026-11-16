package dev.znecby.stellar_evolution.stream;

/**
 * 束流的<strong>份额分配</strong>（纯数值，不依赖 Minecraft，可单测）。
 *
 * <p>供给固定、需求各自独立时，按 <strong>max-min 公平</strong>分：先满足小需求，剩下的再平分。
 * 例：供给 10、需求 [2, 10] → [2, 8]；供给 3、需求 [10, 10, 10] → [1, 1, 1]。
 * 结果之和不会超过供给。
 */
public final class StreamAllocation {

    /** 把 {@code supply} 分给各需求；返回每个消费者的实得量（与 {@code demands} 等长）。 */
    public static double[] allocate(double supply, double[] demands) {
        int n = demands.length;
        double[] out = new double[n];
        double remaining = Math.max(0.0D, supply);
        boolean[] settled = new boolean[n];
        int active = 0;
        for (int i = 0; i < n; i++) {
            if (demands[i] > 0.0D) {
                active++;
            } else {
                settled[i] = true;
            }
        }
        while (active > 0) {
            double share = remaining / active;
            if (share <= 0.0D) {
                break;
            }
            boolean capped = false;
            for (int i = 0; i < n; i++) {
                if (settled[i] || demands[i] > share) {
                    continue;
                }
                out[i] = demands[i];
                remaining -= demands[i];
                settled[i] = true;
                active--;
                capped = true;
            }
            if (!capped) {
                for (int i = 0; i < n; i++) {
                    if (settled[i]) {
                        continue;
                    }
                    out[i] = Math.min(demands[i], share);
                    remaining -= out[i];
                    settled[i] = true;
                    active--;
                }
                break;
            }
        }
        return out;
    }

    private StreamAllocation() {}
}
