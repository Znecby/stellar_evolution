package dev.modmind.stellar_evolution.fission;

import com.mojang.serialization.Codec;
import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 裂变线<strong>数据组件</strong>。
 *
 * <p>{@link #FUEL_FLUX}：燃料棒的<strong>中子通量</strong>（0–100），存在物品上、随反应堆运行增长；
 * 通量越高功率越大，超过 {@link FuelRodItem#HOT_FLUX} 后棒体进入「高温」状态。
 */
public final class FissionComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StellarEvolution.MOD_ID);

    /** 燃料棒中子通量（0–100）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> FUEL_FLUX =
            COMPONENTS.registerComponentType("fuel_flux",
                    builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    /**
     * 乏燃料余热（0–{@link SpentFuelHeat#MAX}）：反应堆产出时写入，只有乏燃料池能降低；
     * 高于 {@link SpentFuelHeat#HOT} 时物品显示「自燃」，拿进背包会着火 + 凋零。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> SPENT_HEAT =
            COMPONENTS.registerComponentType("spent_heat",
                    builder -> builder.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    /** 浸出渣来源类型（B0 两段浸出；没有该组件的旧残渣不再产出）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LeachResidueType>> LEACH_RESIDUE_TYPE =
            COMPONENTS.registerComponentType("leach_residue_type",
                    builder -> builder.persistent(LeachResidueType.CODEC)
                            .networkSynchronized(ByteBufCodecs.idMapper(
                                    index -> LeachResidueType.values()[index],
                                    LeachResidueType::ordinal)));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private FissionComponents() {}
}
