package dev.modmind.stellar_evolution;

import dev.modmind.stellar_evolution.petro.FluidSproutFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 世界生成要素注册：目前只有<strong>原油喷泉</strong>（{@link FluidSproutFeature}，照 GT 现代版
 * {@code gtceu:fluid_sprout} 移植）。
 *
 * <p>矿脉（铀矿）走纯数据包（{@code minecraft:ore}），不需要这里注册。
 */
public final class StellarEvolutionWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, StellarEvolution.MOD_ID);

    /** 流体喷泉：地下原油球 + 40% 概率喷出地表 6–9 格。 */
    public static final DeferredHolder<Feature<?>, FluidSproutFeature> FLUID_SPROUT =
            FEATURES.register("fluid_sprout", FluidSproutFeature::new);

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
    }

    private StellarEvolutionWorldgen() {}
}
