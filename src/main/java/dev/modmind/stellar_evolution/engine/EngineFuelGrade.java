package dev.modmind.stellar_evolution.engine;

/**
 * 燃料等级：决定哪种机器能烧它。
 *
 * <ul>
 *   <li>{@link #LOW} / {@link #MEDIUM} / {@link #HIGH}：往复式内燃机（燃烧引擎、工业内燃机组）</li>
 *   <li>{@link #GAS} / {@link #AERO}：燃气轮机（气体燃料 / 航空煤油）</li>
 *   <li>{@link #ROCKET}：航空航天火箭引擎（煤油氧 / 甲烷氧 / 自燃，燃料自带氧化剂）</li>
 * </ul>
 *
 * <p>燃烧引擎保持旧行为（表里有就能烧），等级只约束三台新机器。
 * <p>丁醇 / 甲醇 / 萘已退出燃料表，只作化工中间体；燃气轮机也不再接受 MEDIUM。
 */
public enum EngineFuelGrade {
    LOW,
    MEDIUM,
    HIGH,
    AERO,
    GAS,
    ROCKET
}
