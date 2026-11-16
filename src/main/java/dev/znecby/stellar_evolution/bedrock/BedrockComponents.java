package dev.znecby.stellar_evolution.bedrock;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 基岩线<strong>数据组件</strong>。
 *
 * <p>{@link #BEDROCK_STAGE}：{@code bedrock_residue} 的段位（S1–S4），
 * 用来让五段萃取按顺序匹配，而不新增 4 个残渣物品。
 */
public final class BedrockComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StellarEvolution.MOD_ID);

    /** 基岩浸出渣段位。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BedrockStage>> BEDROCK_STAGE =
            COMPONENTS.registerComponentType("bedrock_stage",
                    builder -> builder.persistent(BedrockStage.CODEC)
                            .networkSynchronized(ByteBufCodecs.idMapper(
                                    index -> BedrockStage.values()[index],
                                    BedrockStage::ordinal)));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private BedrockComponents() {}
}
