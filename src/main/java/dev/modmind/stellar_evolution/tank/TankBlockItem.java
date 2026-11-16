package dev.modmind.stellar_evolution.tank;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 储罐物品：在物品介绍里显示罐里存的流体 —— 「流体名：量 / 单格容量 mB」。
 *
 * <p>流体本身由 {@link TankComponents#FLUID} 组件携带（破坏 / 放下时由 {@link TankBlock} 读写），
 * 这样玩家可以靠搬储罐来大规模转移流体。
 */
public class TankBlockItem extends BlockItem {
    public TankBlockItem(TankBlock block, Item.Properties properties) {
        super(block, properties);
    }

    /**
     * 把流体写进储罐物品，并同步<strong>动态堆叠上限</strong>：
     * 装流体时压到 1（一叠物品共享同一份组件 = 复制流体），流体清空时恢复默认（空罐可堆 64）。
     */
    public static void applyFluid(ItemStack stack, @Nullable TankComponents.TankFluid fluid) {
        if (fluid == null || fluid.amount() <= 0) {
            stack.remove(TankComponents.FLUID.get());
            stack.remove(DataComponents.MAX_STACK_SIZE);
        } else {
            stack.set(TankComponents.FLUID.get(), fluid);
            stack.set(DataComponents.MAX_STACK_SIZE, 1);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        TankComponents.TankFluid stored = stack.get(TankComponents.FLUID.get());
        if (stored == null) {
            return;
        }
        Fluid fluid = stored.fluidValue();
        FluidStack preview = stored.toStack();
        if (fluid == null || preview.isEmpty()) {
            return;
        }
        int capacity = getBlock() instanceof TankBlock tank ? tank.capacityPerBlock() : stored.amount();
        tooltip.accept(Component.translatable("item.stellar_evolution.tank.fluid",
                        preview.getHoverName(), stored.amount(), capacity)
                .withStyle(ChatFormatting.AQUA));
    }
}
