package dev.znecby.stellar_evolution.tank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.znecby.stellar_evolution.StellarEvolution;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * 储罐的<strong>数据组件</strong>：让「装着流体的储罐」能作为物品搬运。
 *
 * <p>{@link #FLUID}：破坏储罐时把罐里的流体写进掉落物，放下时再灌回方块实体
 * （见 {@code TankBlock#getDrops} / {@code TankBlock#setPlacedBy}）。没有这个组件的储罐物品就是空罐。
 *
 * <p><strong>为什么不用 {@code FluidStack} 当组件值</strong>：数据组件要求不可变且实现 equals / hashCode，
 * 而 26.x 的 {@code FluidStack} 没实现（启动会直接抛 IllegalArgumentException）—— 所以用本类这个
 * 纯值 record（流体 id + 数量），需要时再转回 {@link FluidStack}。
 */
public final class TankComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StellarEvolution.MOD_ID);

    /** 储罐物品里保存的流体（含种类与数量；缺省 = 空罐）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TankFluid>> FLUID =
            COMPONENTS.registerComponentType("fluid",
                    builder -> builder.persistent(TankFluid.CODEC)
                            .networkSynchronized(TankFluid.STREAM_CODEC));

    /** 物品里存的流体：流体注册名 + 数量（mB）。 */
    public record TankFluid(String fluid, int amount) {
        public static final Codec<TankFluid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("fluid").forGetter(TankFluid::fluid),
                Codec.INT.fieldOf("amount").forGetter(TankFluid::amount)
        ).apply(instance, TankFluid::new));

        public static final StreamCodec<ByteBuf, TankFluid> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TankFluid::fluid,
                ByteBufCodecs.VAR_INT, TankFluid::amount,
                TankFluid::new);

        /** 从一个 {@link FluidStack} 生成组件值。 */
        public static TankFluid of(FluidStack stack) {
            return new TankFluid(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(), stack.getAmount());
        }

        /** 转回 {@link FluidStack}（未知流体 / 数量 ≤ 0 时为空）。 */
        public FluidStack toStack() {
            Fluid found = fluidValue();
            return found == null ? FluidStack.EMPTY : new FluidStack(found, amount);
        }

        /** 供 tooltip 用：拿不到流体时返回 null。 */
        @Nullable
        public Fluid fluidValue() {
            if (amount <= 0) {
                return null;
            }
            Identifier id = Identifier.tryParse(fluid);
            if (id == null) {
                return null;
            }
            return BuiltInRegistries.FLUID.get(id)
                    .map(reference -> reference.value())
                    .filter(found -> found != Fluids.EMPTY)
                    .orElse(null);
        }
    }

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private TankComponents() {}
}
