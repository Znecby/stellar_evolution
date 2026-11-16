package dev.znecby.stellar_evolution.particle;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 粒子线的<strong>数据组件</strong>。
 *
 * <p>{@link #PARTICLE_TYPE}：压力罐里装的粒子类型；没有这个组件的罐就是<strong>空罐</strong>。
 */
public final class ParticleComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StellarEvolution.MOD_ID);

    /** 压力罐的粒子类型（缺省 = 空罐）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ParticleType>> PARTICLE_TYPE =
            COMPONENTS.registerComponentType("particle_type",
                    builder -> builder.persistent(ParticleType.CODEC)
                            .networkSynchronized(ParticleType.STREAM_CODEC));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private ParticleComponents() {}
}
