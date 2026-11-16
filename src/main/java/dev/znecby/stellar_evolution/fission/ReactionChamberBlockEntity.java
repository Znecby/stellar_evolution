package dev.znecby.stellar_evolution.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * 反应仓：<strong>反应堆专属</strong>的燃料/组件容器（54 格，大箱子界面）。
 *
 * <p>平时它就是普通容器，任何大机器都能把它当铜箱子用；但对裂变堆来说，
 * <strong>它才是唯一读取燃料棒 / 石墨棒 / 反射棒的地方</strong>：
 * 每台反应堆最多识别 1 个反应仓，>1 个会阻止反应堆工作并提示。
 *
 * <p>铜箱子里的燃料棒不再参与反应堆逻辑，放进去只会被当作普通物品。
 */
public class ReactionChamberBlockEntity extends BlockEntity implements Container {
    /** 54 格 = 大箱子（6 行 × 9 列）。 */
    public static final int SLOTS = 54;

    private final Handler itemHandler = new Handler(SLOTS);
    /**
     * 容器与物品能力<strong>共用同一份列表</strong>。
     * <p>{@code ItemStacksResourceHandler} 的构造器会复制列表，所以这里反过来：
     * 先建 handler，再把 handler 内部那份列表当作容器库存 —— 否则管道写入的是 handler 的副本，
     * 界面读到的 {@code items} 永远是空的（Jade 有物品、GUI 空白）。
     */
    private final NonNullList<ItemStack> items = itemHandler.sharedList();
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            ReactionChamberBlock.playSound(level, pos, SoundEvents.BARREL_OPEN);
            ReactionChamberBlock.setOpen(level, state, pos, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            ReactionChamberBlock.playSound(level, pos, SoundEvents.BARREL_CLOSE);
            ReactionChamberBlock.setOpen(level, state, pos, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int oldCount, int newCount) {
        }

        @Override
        public boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu
                    && menu.getContainer() == ReactionChamberBlockEntity.this;
        }
    };

    /** 与 {@link Container} 共用同一份 {@code stacks} 列表的 handler；管道 / 物流写入时也触发 {@link #setChanged()}。 */
    private final class Handler extends ItemStacksResourceHandler {
        Handler(int size) {
            super(size);
        }

        /** handler 内部那份列表 —— 交给 {@link #items} 当唯一事实源。 */
        NonNullList<ItemStack> sharedList() {
            return this.stacks;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    public ReactionChamberBlockEntity(BlockPos pos, BlockState state) {
        super(FissionReactors.REACTION_CHAMBER_ENTITY.get(), pos, state);
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (!isRemoved() && level != null) {
            openersCounter.incrementOpeners(user.getLivingEntity(), level, worldPosition, getBlockState(),
                    user.getContainerInteractionRange());
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (!isRemoved() && level != null) {
            openersCounter.decrementOpeners(user.getLivingEntity(), level, worldPosition, getBlockState());
        }
    }

    /** 反应堆直接读写这 54 格。 */
    public NonNullList<ItemStack> items() {
        return items;
    }

    /** 给机器物流网络 / 漏斗用的物品能力。 */
    public ResourceHandler<ItemResource> itemCapability() {
        return itemHandler;
    }

    public void dropContents(Level level, net.minecraft.core.BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, items);
    }
}
