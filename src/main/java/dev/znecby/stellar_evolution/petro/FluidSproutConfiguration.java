package dev.znecby.stellar_evolution.petro;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.material.Fluid;

/**
 * 流体喷泉配置：字段与 GT 现代版 {@code gtceu:fluid_sprout} 一一对应
 * （{@code fluid} / {@code size} / {@code surface_offset} / {@code sprout_chance}）。
 */
public record FluidSproutConfiguration(Fluid fluid, IntProvider size, IntProvider surfaceOffset, float sproutChance)
        implements FeatureConfiguration {
    public static final Codec<FluidSproutConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidSproutConfiguration::fluid),
            IntProviders.codec(1, 64).fieldOf("size").forGetter(FluidSproutConfiguration::size),
            IntProviders.codec(0, 24).fieldOf("surface_offset").forGetter(FluidSproutConfiguration::surfaceOffset),
            Codec.FLOAT.fieldOf("sprout_chance").forGetter(FluidSproutConfiguration::sproutChance)
    ).apply(instance, FluidSproutConfiguration::new));
}
