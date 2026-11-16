package dev.znecby.stellar_evolution.mixin;

import dev.znecby.stellar_evolution.thermal.HeatCookerAccess;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 给原版<strong>熔炉 / 烟熏炉 / 高炉</strong>挂上 {@link HeatCookerAccess}：
 * 让热压差网络可以直接驱动它们的火焰与烹饪进度（见 {@code CopperHeatDriver}）。
 *
 * <p>只改这四个私有计数字段，不注入任何 tick 逻辑 —— 烹饪、扣料、产物与经验仍由原版负责，
 * 所以界面动画与服务端行为天然一致。
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin implements HeatCookerAccess {
    /** 火焰显示用的「满格」燃烧时长（刻）：每 tick 由驱动重置，界面火焰因此保持常亮。 */
    private static final int STELLAR_HEAT_LIT_TICKS = 200;

    @Shadow
    private int litTimeRemaining;
    @Shadow
    private int litTotalTime;
    @Shadow
    private int cookingTimer;
    @Shadow
    private int cookingTotalTime;

    @Override
    public void stellar_evolution$heat(int cookingTotalTicks) {
        this.litTimeRemaining = STELLAR_HEAT_LIT_TICKS;
        this.litTotalTime = STELLAR_HEAT_LIT_TICKS;
        int total = Math.max(1, cookingTotalTicks);
        this.cookingTotalTime = total;
        // 倍速变快会让总时长变小；原版是「自增后 == 总时长」才结算，进度一旦超过就永远等不到。
        // 夹到 total-1，下一 tick 自增后正好命中。
        if (this.cookingTimer >= total) {
            this.cookingTimer = total - 1;
        }
    }

    @Override
    public void stellar_evolution$unheat() {
        this.litTimeRemaining = 0;
        this.litTotalTime = 0;
        this.cookingTimer = 0;
        this.cookingTotalTime = 0;
    }

    @Override
    public boolean stellar_evolution$isBurning() {
        return this.litTimeRemaining > 0;
    }
}
