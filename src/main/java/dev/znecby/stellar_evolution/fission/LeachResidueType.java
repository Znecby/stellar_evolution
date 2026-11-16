package dev.znecby.stellar_evolution.fission;

import com.mojang.serialization.Codec;
import java.util.Locale;

/**
 * 浸出渣的<strong>来源类型</strong>（B0 两段浸出）。
 *
 * <p>一段酸浸把矿线来源写进 {@link FissionComponents#LEACH_RESIDUE_TYPE}；
 * 二段酸浸按类型匹配，从而做到「一个残渣物品 + 14 种变体」，而不是 14 个残渣 id。
 */
public enum LeachResidueType {
    IRON,
    COPPER,
    GOLD,
    LEAD,
    /** 铀精砂一段酸浸的渣（钒的二段来源）。 */
    URANIUM,
    TUNGSTEN,
    COAL,
    DIAMOND,
    EMERALD,
    LAPIS,
    REDSTONE,
    QUARTZ,
    DEBRIS,
    MONAZITE;

    public static final Codec<LeachResidueType> CODEC = Codec.STRING.xmap(
            name -> valueOf(name.toUpperCase(Locale.ROOT)),
            type -> type.name().toLowerCase(Locale.ROOT));
}
