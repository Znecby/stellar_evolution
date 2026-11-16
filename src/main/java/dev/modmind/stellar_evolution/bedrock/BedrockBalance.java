package dev.modmind.stellar_evolution.bedrock;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基岩无限资源线的<strong>纯数值账本</strong>（不依赖任何 Minecraft 类型，配 JUnit）。
 *
 * <p>口径（2026-11-01）：
 * <ul>
 *   <li><strong>1 基岩矿 = 1 全链批次</strong>；五段萃取 + 分离后覆盖 44 种稳定元素 + Pu/Np/Am/Cm；</li>
 *   <li>段周期与门槛集中在 {@link #STAGE_TICKS} / {@link #STAGE_MIN_FLOW} 等常量；</li>
 *   <li>1 组溶剂 = 1000 mB；每批净耗 200 mB ⇒ 1 组 = 5 批；</li>
 *   <li>基础层：酸浸只出铁 / 铜 / 铅 / 硅 / 硫，生物浸出只出硼 / 锂 / 钠 / 镁 / 石墨；
 *       铝只在高性能 S2 熔盐分离层产出（3 个/批）；</li>
 * </ul>
 */
public final class BedrockBalance {
    // ---- 周期（基准刻；机器按 1.0 倍速推进，实际受 minFlowRate / 并行 8 影响）
    /** 开采钻基岩模式：9600t 基准；在 8 倍速下约 1200t / 矿。 */
    public static final int DRILL_CYCLE_TICKS = 9_600;
    /** 五段主链：9600t 基准；8 倍速下约 1200t / 批。 */
    public static final int STAGE_TICKS = 9_600;
    /** 结晶 / 分解分离：4800t 基准；8 倍速下约 600t / 批。 */
    public static final int SEPARATION_TICKS = 4_800;
    /** 离心分离：2400t 基准；4 倍速下约 600t / 批。 */
    public static final int CENTRIFUGE_SEPARATION_TICKS = 2_400;
    /** 溶剂配制：4800t 基准；8 倍速下约 600t / 批。 */
    public static final int SOLVENT_TICKS = 4_800;

    // ---- 热流率门槛（HP/t）
    /** 开采钻基岩模式门槛：4,000 HP/t（工业内燃满转；低于此不能出基岩矿）。 */
    public static final double DRILL_MIN_FLOW = 4_000.0D;
    /** 基础层 / 普通段门槛。 */
    public static final double STAGE_MIN_FLOW = 4_096.0D;
    /** S3（α 螯合）门槛：更高一档。 */
    public static final double S3_MIN_FLOW = 8_192.0D;
    /** 分离段门槛。 */
    public static final double SEPARATION_MIN_FLOW = 4_096.0D;
    /** 溶剂配制门槛。 */
    public static final double SOLVENT_MIN_FLOW = 2_048.0D;

    // ---- 溶剂
    /** 1 组溶剂体积（mB）。 */
    public static final int SOLVENT_YIELD = 1_000;
    /** 每批净耗（mB）。 */
    public static final int SOLVENT_PER_BATCH = 200;
    /** 1 组溶剂可跑批数。 */
    public static final int SOLVENT_BATCHES = SOLVENT_YIELD / SOLVENT_PER_BATCH;

    // ---- 元素清单（44 稳定元素 + 4 锕系）
    /** 44 种稳定元素（按段分组后展开）。 */
    public static final List<String> STABLE_ELEMENTS = List.of(
            "Zr", "Hf", "Ti", "Si", "Ge", "Sn", "Nb", "Ta",
            "Al", "Cr", "Mn", "Fe", "Ni", "Co", "Mo", "W", "V",
            "U", "Th", "Nd", "Sm", "Gd", "Ce", "Sc", "P",
            "Cu", "Pb", "Zn", "Ag", "Hg", "Sb", "Bi", "Se", "Te", "S",
            "Au", "Be", "B", "Li", "Na", "Mg", "Ga", "C", "Cl");

    /** 4 种锕系（以 plutonium / minor_actinides 形式产出）。 */
    public static final List<String> ACTINIDES = List.of("Pu", "Np", "Am", "Cm");

    /** S1–S4 段位 → 元素。 */
    public static final Map<BedrockStage, List<String>> STAGE_ELEMENTS;

    /** S5 生物浸出段元素（不产生残渣，单列）。 */
    public static final List<String> S5_ELEMENTS =
            List.of("Au", "Be", "B", "Li", "Na", "Mg", "Ga", "C", "Cl");

    static {
        Map<BedrockStage, List<String>> stages = new LinkedHashMap<>();
        stages.put(BedrockStage.S1, List.of("Zr", "Hf", "Ti", "Si", "Ge", "Sn", "Nb", "Ta"));
        stages.put(BedrockStage.S2, List.of("Al", "Cr", "Mn", "Fe", "Ni", "Co", "Mo", "W", "V"));
        stages.put(BedrockStage.S3, List.of("U", "Th", "Pu", "Np", "Am", "Cm",
                "Nd", "Sm", "Gd", "Ce", "Sc", "P"));
        stages.put(BedrockStage.S4, List.of("Cu", "Pb", "Zn", "Ag", "Hg", "Sb", "Bi", "Se", "Te", "S"));
        STAGE_ELEMENTS = Map.copyOf(stages);
    }

    /** 高性能层每矿产出（按元素符号；Cl 以 1000 mB 计，不在件数里）。 */
    public static final Map<String, Integer> HIGH_YIELD;

    static {
        Map<String, Integer> yield = new LinkedHashMap<>();
        // S1
        yield.put("Zr", 2);
        yield.put("Hf", 1);
        yield.put("Ti", 3);
        yield.put("Si", 8);
        yield.put("Ge", 2);
        yield.put("Sn", 2);
        yield.put("Nb", 2);
        yield.put("Ta", 1);
        // S2
        yield.put("Al", 6);
        yield.put("Cr", 3);
        yield.put("Mn", 3);
        yield.put("Fe", 10);
        yield.put("Ni", 4);
        yield.put("Co", 2);
        yield.put("Mo", 2);
        yield.put("W", 2);
        yield.put("V", 2);
        // S3
        yield.put("U", 6);
        yield.put("Th", 4);
        yield.put("Pu", 1);
        yield.put("Np", 1);
        yield.put("Am", 1);
        yield.put("Cm", 1);
        yield.put("Nd", 4);
        yield.put("Sm", 2);
        yield.put("Gd", 2);
        yield.put("Ce", 4);
        yield.put("Sc", 1);
        yield.put("P", 8);
        // S4
        yield.put("Cu", 6);
        yield.put("Pb", 4);
        yield.put("Zn", 4);
        yield.put("Ag", 2);
        yield.put("Hg", 1);
        yield.put("Sb", 1);
        yield.put("Bi", 1);
        yield.put("Se", 2);
        yield.put("Te", 1);
        yield.put("S", 10);
        // S5
        yield.put("Au", 1);
        yield.put("Be", 2);
        yield.put("B", 6);
        yield.put("Li", 6);
        yield.put("Na", 6);
        yield.put("Mg", 4);
        yield.put("Ga", 1);
        yield.put("C", 10);
        yield.put("Cl", 1);
        HIGH_YIELD = Map.copyOf(yield);
    }

    /** 全部元素（稳定 + 锕系）。 */
    public static Set<String> allElements() {
        Set<String> all = new LinkedHashSet<>(STABLE_ELEMENTS);
        all.addAll(ACTINIDES);
        return Set.copyOf(all);
    }

    /** 每矿总件数（不含 Cl 流体）。 */
    public static int totalItemsPerOre() {
        int total = 0;
        for (Map.Entry<String, Integer> entry : HIGH_YIELD.entrySet()) {
            if (!"Cl".equals(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }

    /** 全元素是否都有产出表。 */
    public static boolean coversAllElements() {
        return allElements().stream().allMatch(HIGH_YIELD::containsKey);
    }

    /** 某元素属于 S1–S4 哪一段；S5（生物段）返回 {@code null}。 */
    public static BedrockStage stageOf(String element) {
        for (Map.Entry<BedrockStage, List<String>> entry : STAGE_ELEMENTS.entrySet()) {
            if (entry.getValue().contains(element)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private BedrockBalance() {}
}
