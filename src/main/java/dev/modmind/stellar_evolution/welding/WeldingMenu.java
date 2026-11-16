package dev.modmind.stellar_evolution.welding;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 焊接机界面：<strong>1×3</strong> 料仓 + 玩家背包，外加 4 条同步数据
 * （进度 / 周期 / 罐内流体 / 配方需求）供界面画进度条与氧气量。
 */
public class WeldingMenu extends AbstractContainerMenu {
    /** 机内 3 格。 */
    public static final int SLOTS = WeldingMachineBlockEntity.SLOTS;

    private final Container container;
    private final ContainerData data;

    /** 客户端用：占位容器 + 同步数据（服务端每刻推送）。 */
    public WeldingMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(SLOTS), new SimpleContainerData(4));
    }

    /** 服务端用：真实方块实体容器 + 真实数据。 */
    public WeldingMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(WeldingBlocks.WELDING_MENU.get(), containerId);
        checkContainerSize(container, SLOTS);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);

        // 与底图（原版发射器界面）的 3×3 网格第一行对齐
        for (int i = 0; i < SLOTS; i++) {
            this.addSlot(new Slot(container, i, 62 + i * 18, 17));
        }
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addDataSlots(data);
    }

    /** 界面同步数据（客户端只读）。 */
    public ContainerData data() {
        return this.data;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < SLOTS) {
            if (!this.moveItemStackTo(stack, SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, SLOTS, false)) {
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
}
