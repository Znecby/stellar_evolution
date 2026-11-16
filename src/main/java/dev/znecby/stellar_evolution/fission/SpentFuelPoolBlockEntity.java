package dev.znecby.stellar_evolution.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * 乏燃料池方块实体：<strong>9 格（3×3）</strong>，右键复用投掷器界面。
 *
 * <p>每 20t 结算一次：先看有没有带余热的乏燃料，没有再直接返回；
 * 有则按周围 8 格水源数量降温。8 个水源满速（2 温度/秒），没有水源不降温。
 * 水源数量在放置 / 邻居变化时缓存，不在 tick 里重复扫描。
 */
public class SpentFuelPoolBlockEntity extends BlockEntity implements Container {
    /** 3×3 = 9 格，和投掷器界面一致。 */
    public static final int SLOTS = 9;

    private final Handler itemHandler = new Handler(SLOTS);
    /** 容器与物品能力共用同一份列表（照反应仓的口径）。 */
    private final NonNullList<ItemStack> items = itemHandler.sharedList();

    /** 周围 8 格水源数量（0–8），邻居变化时重算。 */
    private int waterSources;
    /** 20t 结算计数器。 */
    private int tickCounter;

    private final class Handler extends ItemStacksResourceHandler {
        Handler(int size) {
            super(size);
        }

        NonNullList<ItemStack> sharedList() {
            return this.stacks;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }

        /** 物流 / 管道也只允许乏燃料。 */
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return resource.toStack(1).getItem() instanceof SpentFuelItem;
        }
    }

    public SpentFuelPoolBlockEntity(BlockPos pos, BlockState state) {
        super(FissionPools.SPENT_FUEL_POOL_ENTITY.get(), pos, state);
    }

    /** 服务端每 tick 调用；内部按 20t 结算。 */
    public static void tick(Level level, BlockPos pos, BlockState state, SpentFuelPoolBlockEntity pool) {
        if (level.isClientSide()) {
            return;
        }
        if (++pool.tickCounter < 20) {
            return;
        }
        pool.tickCounter = 0;
        if (pool.waterSources <= 0) {
            pool.recountWater();
        }
        if (pool.waterSources <= 0) {
            return;
        }
        float cooling = SpentFuelHeat.coolingPerSecond(pool.waterSources);
        boolean changed = false;
        for (ItemStack stack : pool.items) {
            if (!(stack.getItem() instanceof SpentFuelItem)) {
                continue;
            }
            float heat = SpentFuelItem.heat(stack);
            if (heat <= 0.0F) {
                continue;
            }
            SpentFuelItem.setHeat(stack, Math.max(0.0F, heat - cooling));
            changed = true;
        }
        if (changed) {
            pool.setChanged();
        }
    }

    /** 重算同一 Y 层 3×3 环里的水源方块数量（不含自身）。 */
    public void recountWater() {
        if (level == null) {
            return;
        }
        int found = 0;
        BlockPos origin = getBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (level.getFluidState(origin.offset(dx, 0, dz)).isSource()) {
                    found++;
                }
            }
        }
        waterSources = found;
    }

    public int waterSources() {
        return waterSources;
    }

    public ResourceHandler<ItemResource> itemCapability() {
        return itemHandler;
    }

    public void dropContents(Level level, BlockPos pos) {
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

    /** 只收乏燃料；普通物品放不进去。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof SpentFuelItem;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
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
        recountWater();
    }
}
