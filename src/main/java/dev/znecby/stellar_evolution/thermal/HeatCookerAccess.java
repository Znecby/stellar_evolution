package dev.znecby.stellar_evolution.thermal;

/**
 * 由 Mixin 注入到原版<strong>熔炉 / 烟熏炉 / 高炉</strong>上的「供能」接口。
 *
 * <p>热压差网络代替燃料时，如果只在外围自己记进度、自己扣料，原版界面的<strong>火焰</strong>与
 * <strong>进度箭头</strong>都不会动。这里改为直接驱动原版炉子的内部字段：撑住燃烧时间、按倍速缩短
 * 本周期总时长，剩余结算（扣料、产物、经验）全部交回原版 tick。
 */
public interface HeatCookerAccess {
    /**
     * 由热压差网络点燃：火焰保持常亮，并把本周期总时长压到 {@code cookingTotalTicks}
     * （原版每 tick 推进 1，于是倍速由总时长体现）。
     */
    void stellar_evolution$heat(int cookingTotalTicks);

    /** 由热压差网络熄灭：清空火焰与本周期进度（只在我们确实供过能时调用，避免干扰炉子自己烧燃料）。 */
    void stellar_evolution$unheat();

    /** 炉子自身是否还在烧（{@code litTimeRemaining > 0}）—— 用来区分「我们点的火」与「它自己烧的燃料」。 */
    boolean stellar_evolution$isBurning();
}
