package dev.modmind.stellar_evolution.equipment;

/**
 * 钢制工具的种类，决定可用的功能集合。
 *
 * <ul>
 *   <li>镐：范围 / 连锁挖矿 / 时运 / 精准采集；</li>
 *   <li>斧：范围 / 伐木 / 时运 / 精准采集；</li>
 *   <li>锹：范围 / 连锁破坏 / 时运 / 精准采集；</li>
 *   <li>锄：范围耕地 / 连锁收割 / 时运 / 精准采集；</li>
 *   <li>剑：横扫。</li>
 * </ul>
 */
public enum SteelToolKind {
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SWORD
}
