package dev.znecby.stellar_evolution.equipment;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 钢制工具的<strong>数据组件</strong>。
 *
 * <ul>
 *   <li>{@link #FEATURES}：功能位掩码（缺省 0），可多选；</li>
 *   <li>{@link #LEVEL}：工具等级（缺省 = 1），决定范围 / 连锁 / 时运档位。</li>
 * </ul>
 */
public final class SteelToolComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StellarEvolution.MOD_ID);

    /** 功能位掩码（{@link SteelToolFeature}）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FEATURES =
            COMPONENTS.registerComponentType("steel_tool_features",
                    builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** 工具等级（1 = 钢制基础级）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LEVEL =
            COMPONENTS.registerComponentType("steel_tool_level",
                    builder -> builder.persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private SteelToolComponents() {}
}
