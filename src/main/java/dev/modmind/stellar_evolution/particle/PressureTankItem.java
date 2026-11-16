package dev.modmind.stellar_evolution.particle;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * <strong>压力罐</strong>：粒子源的燃料罐，<strong>堆叠 1 + 耐久制</strong>（{@value #MAX_DAMAGE}）。
 *
 * <p>罐上带 {@link ParticleComponents#PARTICLE_TYPE} 决定装的是什么粒子；没有组件 = 空罐。
 * 粒子源每 20t 磨损一次，耐久归零后组件被移除、罐变成空罐（可回收重装）。
 */
public class PressureTankItem extends Item {
    /** 满耐久。 */
    public static final int MAX_DAMAGE = 1_000;

    public PressureTankItem(Properties properties) {
        super(properties);
    }

    /** 罐里装的粒子类型；空罐返回 null。 */
    public static ParticleType particleOf(ItemStack stack) {
        return stack.get(ParticleComponents.PARTICLE_TYPE.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        ParticleType type = particleOf(stack);
        if (type == null) {
            tooltip.accept(Component.translatable("item.stellar_evolution.pressure_tank.tooltip.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.translatable("item.stellar_evolution.pressure_tank.tooltip.type",
                        Component.translatable(type.nameKey()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("item.stellar_evolution.pressure_tank.tooltip.output",
                        Component.translatable(type.stream().nameKey()), type.rate())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.translatable("item.stellar_evolution.pressure_tank.tooltip.usage")
                .withStyle(ChatFormatting.GRAY));
    }
}
