package dev.modmind.stellar_evolution.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 压模机界面：机内 3×3 料仓 + 左侧 1 格<strong>模具槽</strong> + 玩家背包。
 * <p>模具槽只接受 {@link MachineTags#DIE_PRESS_MOLDS}，且一次只放 1 个；
 * 配方按「当前模具」分派：板模具合板、杆模具合杆、齿轮模具合齿轮。
 */
public class DiePressMenu extends AbstractContainerMenu {
    /** 机内 3×3 料仓。 */
    public static final int MACHINE_SLOTS = MachineBlockEntity.SLOTS;
    /** 模具槽在菜单里的索引。 */
    public static final int MOLD_SLOT = MACHINE_SLOTS;
    /** 模具槽在底图上的坐标（与 3×3 第一行同高，靠左）。 */
    public static final int MOLD_X = 26;
    public static final int MOLD_Y = 17;

    private final Container container;

    /** 客户端用：占位容器。 */
    public DiePressMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS), new SimpleContainer(1));
    }

    /** 服务端用：真实方块实体料仓 + 真实模具槽。 */
    public DiePressMenu(int containerId, Inventory playerInventory, MachineBlockEntity machine) {
        this(containerId, playerInventory, machine, machine.moldContainer());
    }

    private DiePressMenu(int containerId, Inventory playerInventory, Container container, Container mold) {
        super(MachineBlocks.DIE_PRESS_MENU.get(), containerId);
        checkContainerSize(container, MACHINE_SLOTS);
        checkContainerSize(mold, 1);
        this.container = container;
        container.startOpen(playerInventory.player);

        for (int i = 0; i < MACHINE_SLOTS; i++) {
            this.addSlot(new Slot(container, i, 62 + i % 3 * 18, 17 + i / 3 * 18));
        }
        this.addSlot(new MoldSlot(mold, 0, MOLD_X, MOLD_Y));
        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int playerStart = MOLD_SLOT + 1;
        if (index < playerStart) {
            if (!this.moveItemStackTo(stack, playerStart, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(MachineTags.DIE_PRESS_MOLDS)) {
            if (!this.moveItemStackTo(stack, MOLD_SLOT, MOLD_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, MACHINE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    /** 只放模具、且只放 1 个。 */
    private static final class MoldSlot extends Slot {
        MoldSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(MachineTags.DIE_PRESS_MOLDS);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
