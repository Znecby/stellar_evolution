package dev.znecby.stellar_evolution.massdriver;

import dev.znecby.stellar_evolution.component.ComponentItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 「坍缩末影之眼」的背包工具：计数、找眼、把普通末影之眼转化成坍缩末影之眼。 */
public final class MassDriverEyes {
    /** 背包里坍缩末影之眼的数量（每枚不可堆叠，所以等于格数）。 */
    public static int count(Player player) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ComponentItems.COLLAPSED_ENDER_EYE.get())) {
                found++;
            }
        }
        return found;
    }

    /** 背包里第一枚<strong>带绑定信息</strong>的坍缩末影之眼；没有则 {@code null}。 */
    public static ItemStack firstBound(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ComponentItems.COLLAPSED_ENDER_EYE.get())
                    && stack.has(MassDriverComponents.COLLAPSED_EYE.get())) {
                return stack;
            }
        }
        return null;
    }

    /** 背包里第一个普通末影之眼的槽位；没有返回 -1。 */
    public static int findEnderEyeSlot(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.ENDER_EYE)) {
                return slot;
            }
        }
        return -1;
    }

    /** 把 {@code slot} 里的一枚普通末影之眼换成坍缩末影之眼（原槽位空出就放回原槽）。 */
    public static ItemStack convert(Player player, int slot, CollapsedEyeData data) {
        ItemStack eye = player.getInventory().getItem(slot);
        if (!eye.is(Items.ENDER_EYE)) {
            return ItemStack.EMPTY;
        }
        ItemStack collapsed = new ItemStack(ComponentItems.COLLAPSED_ENDER_EYE.get());
        collapsed.set(MassDriverComponents.COLLAPSED_EYE.get(), data);
        eye.shrink(1);
        if (eye.isEmpty()) {
            player.getInventory().setItem(slot, collapsed);
        } else if (!player.getInventory().add(collapsed)) {
            player.drop(collapsed, false);
        }
        return collapsed;
    }

    private MassDriverEyes() {}
}
