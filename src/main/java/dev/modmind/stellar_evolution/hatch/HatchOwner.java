package dev.modmind.stellar_evolution.hatch;

/**
 * 认领机械物流仓的多方块<strong>控制器</strong>：仓室作为它的热压差接入端口。
 *
 * <p>仓室本身不占 DP：网络记账时把仓室当作那台机器的消耗端入口，
 * 热阻 / 基准热流率 / 封顶全部<strong>转发给控制器</strong>，
 * 这样「热阻 4」记在焦化炉身上，而接入点仍然是机械物流仓。
 *
 * <p>目前两个实现：质量投射轨道炮、焦化炉。
 */
public interface HatchOwner {
    /** 多方块是否已成型（未成型时仓室不参与热网记账）。 */
    boolean isFormed();

    /** 基础热阻（转发给控制器）。 */
    double thermalResistance();

    /** 基准热流率 HP/t（转发给控制器）。 */
    double baseFlowRate();

    /** 有效热流率上限 HP/t（转发给控制器）。 */
    double maxUsefulFlowRate();

    /**
     * 成型后仓室应当使用的基底材质。
     * <p><strong>每个认领仓室的控制器都必须给出</strong>（不给默认值，避免新机器漏配导致仓室永远是不锈钢）：
     * 焦化炉 → 红砖块；质量炮 → 高负荷机械方块。
     */
    HatchBlock.Casing hatchCasing();
}
