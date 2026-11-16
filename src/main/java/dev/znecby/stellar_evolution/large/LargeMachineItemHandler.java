package dev.znecby.stellar_evolution.large;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * 大机器的<strong>物品能力</strong>：物流网络里铜箱子（物品容器）的合并视图。
 * 输入容器只可灌入、输出容器只可抽出（见 {@link LargeMachineHandler}）。
 */
public final class LargeMachineItemHandler extends LargeMachineHandler<ItemResource> {
    public LargeMachineItemHandler(LargeMachine machine) {
        super(machine);
    }

    /** 按物品标签从输入容器抽取（任意原木这类配方用）；返回实际移动数量。 */
    public int extractTagFromInputs(TagKey<Item> tag, int amount, TransactionContext transaction) {
        return extractFromInputs(resource -> resource.toStack(1).is(tag), amount, transaction);
    }

    @Nullable
    @Override
    protected ResourceHandler<ItemResource> handlerAt(Level level, BlockPos pos) {
        return LargeMachines.itemHandlerAt(level, pos);
    }

    @Override
    protected ItemResource empty() {
        return ItemResource.EMPTY;
    }
}
