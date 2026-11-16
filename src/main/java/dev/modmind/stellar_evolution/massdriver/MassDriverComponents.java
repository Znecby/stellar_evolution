package dev.modmind.stellar_evolution.massdriver;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 质量投射轨道炮的<strong>数据组件</strong>：目前只有「坍缩末影之眼」的绑定信息。 */
public final class MassDriverComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StellarEvolution.MOD_ID);

    /** 坍缩末影之眼的绑定信息（玩家 / 回溯点 / 维度 / 目标维度）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CollapsedEyeData>> COLLAPSED_EYE =
            COMPONENTS.registerComponentType("collapsed_eye",
                    builder -> builder.persistent(CollapsedEyeData.CODEC)
                            .networkSynchronized(CollapsedEyeData.STREAM_CODEC));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private MassDriverComponents() {}
}
