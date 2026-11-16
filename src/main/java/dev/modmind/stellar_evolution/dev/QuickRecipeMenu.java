package dev.modmind.stellar_evolution.dev;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 快速配方添加书的记录菜单：3×3 输入 + 1 个输出格。
 *
 * <p>输入槽 0–8 按“从左到右、从上到下”记录，槽 9 是输出；保存时把物品 id / 数量写进项目 {@code docs/quick_recipes}。
 * 菜单分工作台与热室两种模式，界面相同，标题 / 提示 / 保存文本里的 mode 不同。
 */
public class QuickRecipeMenu extends AbstractContainerMenu {
    /** 3×3 输入格。 */
    public static final int GRID_SIZE = 9;
    /** 输出格：工作台右侧的成品位。 */
    public static final int OUTPUT_SLOT = 9;
    /** 输入 + 输出。 */
    public static final int SLOT_COUNT = GRID_SIZE + 1;

    private final SimpleContainer container;
    private final QuickRecipeMode mode;

    public QuickRecipeMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, QuickRecipeMode.CRAFTING_TABLE);
    }

    public QuickRecipeMenu(int containerId, Inventory playerInventory, QuickRecipeMode mode) {
        this(mode == QuickRecipeMode.HOT_CHAMBER
                        ? QuickRecipeMenus.QUICK_RECIPE_HOT_CHAMBER.get()
                        : QuickRecipeMenus.QUICK_RECIPE.get(),
                containerId, playerInventory, mode);
    }

    private QuickRecipeMenu(MenuType<QuickRecipeMenu> menuType, int containerId,
                            Inventory playerInventory, QuickRecipeMode mode) {
        super(menuType, containerId);
        this.container = new SimpleContainer(SLOT_COUNT);
        this.mode = mode;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                this.addSlot(new Slot(this.container, column + row * 3, 30 + column * 18, 17 + row * 18));
            }
        }
        // 工作台右侧的输出格：记录“这个配方产出什么”
        this.addSlot(new Slot(this.container, OUTPUT_SLOT, 124, 35));
        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    /** 热室模式菜单（供第二个 MenuType 的工厂方法使用）。 */
    public static QuickRecipeMenu hotChamber(int containerId, Inventory playerInventory) {
        return new QuickRecipeMenu(containerId, playerInventory, QuickRecipeMode.HOT_CHAMBER);
    }

    public QuickRecipeMode mode() {
        return this.mode;
    }

    /** 服务端保存时读取的 3×3 容器。 */
    public Container container() {
        return this.container;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < SLOT_COUNT) {
            if (!this.moveItemStackTo(stack, SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, GRID_SIZE, false)) {
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
        return player.getMainHandItem().is(QuickRecipeItems.QUICK_RECIPE_BOOK.get())
                || player.getOffhandItem().is(QuickRecipeItems.QUICK_RECIPE_BOOK.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) {
            return;
        }
        for (int i = 0; i < this.container.getContainerSize(); i++) {
            ItemStack stack = this.container.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }
}
