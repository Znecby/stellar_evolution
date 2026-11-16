package dev.modmind.stellar_evolution.muon;

import dev.modmind.stellar_evolution.stream.StreamType;
import net.minecraft.world.item.ItemStack;

/**
 * 聚变芯核规格（纯数据）：决定「核心」名与「约束」（可输出的粒子流类型），以及芯核耐久。
 *
 * <p>定位同裂变燃料棒：放进<strong>反应仓</strong>、由机器原地消耗耐久。目前有氘氚 / 氘氘 / 氘-氦3 / 质子-硼11 四种。
 */
public enum MuonCoreSpec {
    /** 氘氚芯核：约束 = 缪子流；点火难度基准。 */
    DEUTERIUM_TRITIUM("dt_fusion_core", "stellar_evolution.muon.core.dt",
            StreamType.MUON, 24_000, 0.2D, 1.0D),
    /** 氘氘芯核：燃料最易得，点火略难、寿命更长。 */
    DEUTERIUM_DEUTERIUM("dd_fusion_core", "stellar_evolution.muon.core.dd",
            StreamType.MUON, 32_000, 0.15D, 1.15D),
    /** 氘-氦3 芯核：需要氦-3，点火更难、寿命更长。 */
    DEUTERIUM_HELIUM3("dhe3_fusion_core", "stellar_evolution.muon.core.dhe3",
            StreamType.MUON, 40_000, 0.12D, 1.30D),
    /** 质子-硼11 芯核：终局芯核，点火最难、寿命最长。 */
    PROTON_BORON11("pb11_fusion_core", "stellar_evolution.muon.core.pb11",
            StreamType.MUON, 48_000, 0.10D, 1.55D),
    /** 星核聚变芯核：星核粒 + 氘氚芯核，寿命最长、点火最难。 */
    STAR_CORE("star_fusion_core", "stellar_evolution.muon.core.star",
            StreamType.MUON, 64_000, 0.08D, 1.80D);

    private final String itemId;
    private final String displayKey;
    private final StreamType stream;
    private final int maxDamage;
    private final double wearPerTick;
    private final double ignitionScale;

    MuonCoreSpec(String itemId, String displayKey, StreamType stream, int maxDamage,
                 double wearPerTick, double ignitionScale) {
        this.itemId = itemId;
        this.displayKey = displayKey;
        this.stream = stream;
        this.maxDamage = maxDamage;
        this.wearPerTick = wearPerTick;
        this.ignitionScale = ignitionScale;
    }

    /** 物品 id。 */
    public String itemId() {
        return itemId;
    }

    /** 核心显示名翻译键（如「氘氚」）。 */
    public String displayKey() {
        return displayKey;
    }

    /** 该芯核约束的粒子流类型。 */
    public StreamType stream() {
        return stream;
    }

    /** 耐久上限。 */
    public int maxDamage() {
        return maxDamage;
    }

    /** 点火运行时每 tick 消耗的耐久（小数累积）。 */
    public double wearPerTick() {
        return wearPerTick;
    }

    /** 点火阶段的 HP / 热阻倍率；已点火后的维持档不乘这个系数。 */
    public double ignitionScale() {
        return ignitionScale;
    }

    /** 从物品堆取规格；不是聚变芯核时返回 null。 */
    public static MuonCoreSpec of(ItemStack stack) {
        return stack.getItem() instanceof MuonCoreItem core ? core.spec() : null;
    }
}
