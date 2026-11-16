package dev.modmind.stellar_evolution.massdriver;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 质量投射轨道炮的<strong>维度消耗</strong>：固定数值，按原版三维 / 其它维度区分。 */
public final class MassDriverCosts {
    public static final int OVERWORLD = 1000;
    public static final int NETHER = 2000;
    public static final int END = 3000;
    public static final int OTHER = 5000;

    /** 目标维度消耗的势能。 */
    public static int cost(ResourceKey<Level> dimension) {
        if (Level.OVERWORLD.equals(dimension)) {
            return OVERWORLD;
        }
        if (Level.NETHER.equals(dimension)) {
            return NETHER;
        }
        if (Level.END.equals(dimension)) {
            return END;
        }
        return OTHER;
    }

    /** 维度显示名：走原版 {@code dimension.<namespace>.<path>} 翻译键。 */
    public static Component displayName(Identifier dimension) {
        return Component.translatable("dimension." + dimension.getNamespace() + "." + dimension.getPath());
    }

    private MassDriverCosts() {}
}
