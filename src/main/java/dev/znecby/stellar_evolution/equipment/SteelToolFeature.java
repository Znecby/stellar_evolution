package dev.znecby.stellar_evolution.equipment;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * 钢制工具的功能开关（多选，位掩码）。
 *
 * <p>硬冲突：
 * <ul>
 *   <li><strong>主行动组</strong>：范围 / 连锁挖矿 / 伐木 / 连锁破坏 / 连锁收割，最多选一个；</li>
 *   <li><strong>附魔组</strong>：时运 / 精准采集，最多选一个；</li>
 *   <li>横扫单独给剑使用。</li>
 * </ul>
 */
public enum SteelToolFeature implements StringRepresentable {
    AREA("area", 1 << 0),
    VEIN("vein", 1 << 1),
    FELL("fell", 1 << 2),
    BULK("bulk", 1 << 3),
    HARVEST("harvest", 1 << 4),
    FORTUNE("fortune", 1 << 5),
    SILK("silk", 1 << 6),
    SWEEP("sweep", 1 << 7),
    LOOTING("looting", 1 << 8);

    /** 全部合法位。 */
    public static final int ALL = AREA.mask | VEIN.mask | FELL.mask | BULK.mask
            | HARVEST.mask | FORTUNE.mask | SILK.mask | SWEEP.mask | LOOTING.mask;

    /** 主行动组（互斥）。 */
    private static final int ACTION_MASK = AREA.mask | VEIN.mask | FELL.mask | BULK.mask | HARVEST.mask;

    private static final List<SteelToolFeature> ACTIONS =
            List.of(AREA, VEIN, FELL, BULK, HARVEST);

    private final String id;
    private final int mask;

    SteelToolFeature(String id, int mask) {
        this.id = id;
        this.mask = mask;
    }

    public String id() {
        return id;
    }

    public int mask() {
        return mask;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String nameKey() {
        return "stellar_evolution.steel_tool.mode." + id;
    }

    public Component displayName() {
        return Component.translatable(nameKey());
    }

    /** 该工具种类是否支持这个功能。 */
    public boolean supports(SteelToolKind kind) {
        return switch (kind) {
            case PICKAXE -> this == AREA || this == VEIN || this == FORTUNE || this == SILK;
            case AXE -> this == AREA || this == FELL || this == FORTUNE || this == SILK;
            case SHOVEL -> this == AREA || this == BULK || this == FORTUNE || this == SILK;
            case HOE -> this == AREA || this == HARVEST || this == FORTUNE || this == SILK;
            case SWORD -> this == SWEEP || this == LOOTING;
        };
    }

    /** 某工具种类支持的全部功能（枚举顺序）。 */
    public static List<SteelToolFeature> supportedBy(SteelToolKind kind) {
        List<SteelToolFeature> result = new ArrayList<>(4);
        for (SteelToolFeature feature : values()) {
            if (feature.supports(kind)) {
                result.add(feature);
            }
        }
        return result;
    }

    /** 某工具种类支持的位掩码。 */
    public static int maskFor(SteelToolKind kind) {
        int mask = 0;
        for (SteelToolFeature feature : values()) {
            if (feature.supports(kind)) {
                mask |= feature.mask;
            }
        }
        return mask;
    }

    /** 清掉不支持与冲突位：主行动组保留枚举顺序最前的一个，时运优先于精准。 */
    public static int sanitize(int features, SteelToolKind kind) {
        int result = features & ALL & maskFor(kind);
        int actions = result & ACTION_MASK;
        if (Integer.bitCount(actions) > 1) {
            result &= ~ACTION_MASK;
            for (SteelToolFeature feature : ACTIONS) {
                if ((actions & feature.mask) != 0) {
                    result |= feature.mask;
                    break;
                }
            }
        }
        if ((result & FORTUNE.mask) != 0) {
            result &= ~SILK.mask;
        }
        return result;
    }

    /** 开启某个功能时需要关掉的冲突位（已按工具种类过滤）。 */
    public int conflictMask(SteelToolKind kind) {
        if ((mask & ACTION_MASK) != 0) {
            return ACTION_MASK & maskFor(kind) & ~mask;
        }
        return switch (this) {
            case FORTUNE -> SILK.mask & maskFor(kind);
            case SILK -> FORTUNE.mask & maskFor(kind);
            default -> 0;
        };
    }

    /** 位掩码 → 功能列表（枚举顺序）。 */
    public static List<SteelToolFeature> fromMask(int features, SteelToolKind kind) {
        int clean = sanitize(features, kind);
        List<SteelToolFeature> result = new ArrayList<>(4);
        for (SteelToolFeature feature : values()) {
            if ((clean & feature.mask) != 0) {
                result.add(feature);
            }
        }
        return result;
    }

    /** 位掩码 → 「范围 + 时运」这样的显示文本；空掩码显示「无」。 */
    public static Component names(int features, SteelToolKind kind) {
        List<SteelToolFeature> list = fromMask(features, kind);
        if (list.isEmpty()) {
            return Component.translatable("stellar_evolution.steel_tool.mode.none");
        }
        MutableComponent result = Component.empty();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                result.append(Component.literal(" + "));
            }
            result.append(list.get(i).displayName());
        }
        return result;
    }

    /** Alt 快速切换：按工具种类生成预设并返回下一个。 */
    public static int nextPreset(SteelToolKind kind, int features) {
        List<Integer> presets = new ArrayList<>();
        presets.add(0);
        if (kind == SteelToolKind.SWORD) {
            presets.add(SWEEP.mask);
            presets.add(LOOTING.mask);
            presets.add(SWEEP.mask | LOOTING.mask);
        } else {
            for (SteelToolFeature action : ACTIONS) {
                if (!action.supports(kind)) {
                    continue;
                }
                presets.add(action.mask);
                if (FORTUNE.supports(kind)) {
                    presets.add(action.mask | FORTUNE.mask);
                }
                if (SILK.supports(kind)) {
                    presets.add(action.mask | SILK.mask);
                }
            }
            if (FORTUNE.supports(kind)) {
                presets.add(FORTUNE.mask);
            }
            if (SILK.supports(kind)) {
                presets.add(SILK.mask);
            }
        }
        int current = sanitize(features, kind);
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i) == current) {
                return presets.get((i + 1) % presets.size());
            }
        }
        return presets.getFirst();
    }
}
