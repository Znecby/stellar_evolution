package dev.modmind.stellar_evolution.thermal;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

/**
 * Jade 物品栏显示：<strong>本模组的耐久物品按「同名」合并成一条</strong>。
 *
 * <p>Jade 默认按「物品 + 完整组件」合并，燃料棒每 tick 都在变的中子通量 / 耐久会让同一批棒子
 * 变成好几条、并随数值变化不断重排，看起来就是闪烁。这里接管本模组方块的物品列表：
 *
 * <ul>
 *   <li>本模组的<strong>有耐久物品</strong>（燃料棒 / 石墨棒）→ 只按物品合并，忽略耐久与通量，
 *       显示成「中浓缩铀燃料棒 ×3」这样一条；</li>
 *   <li>其余物品（含其它模组）→ 仍按物品 + 完整组件合并，与 Jade 默认行为一致。</li>
 * </ul>
 *
 * <p>只对本模组命名空间的方块写数据，别的方块返回 {@code null} 让 Jade 自己的实现接着处理。
 * 优先级用默认值 0（Jade 自带的兜底实现是 9999），所以会先问到这里。
 */
public final class MergedItemStorageJade implements IServerExtensionProvider<ItemStack>,
        IClientExtensionProvider<ItemStack, ItemView> {
    public static final MergedItemStorageJade INSTANCE = new MergedItemStorageJade();

    private static final Identifier UID = StellarEvolution.id("merged_item_storage");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    @Nullable
    public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block)) {
            return null;
        }
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block.getBlock());
        if (blockId == null || !StellarEvolution.MOD_ID.equals(blockId.getNamespace())) {
            return null;   // 只接管本模组的方块
        }
        ResourceHandler<ItemResource> handler = block.getLevel().getCapability(
                Capabilities.Item.BLOCK, block.getPosition(), block.getBlockState(),
                block.getBlockEntity(), null);
        if (handler == null) {
            return null;
        }
        Map<Key, ItemStack> merged = new LinkedHashMap<>();
        for (int i = 0; i < handler.size(); i++) {
            ItemResource resource = handler.getResource(i);
            if (resource.isEmpty()) {
                continue;
            }
            long amount = handler.getAmountAsLong(i);
            if (amount <= 0L) {
                continue;
            }
            ItemStack stack = resource.toStack((int) Math.min(Integer.MAX_VALUE, amount));
            if (stack.isEmpty()) {
                continue;
            }
            Key key = Key.of(stack);
            ItemStack existing = merged.get(key);
            if (existing == null) {
                merged.put(key, stack);
            } else {
                existing.setCount(existing.getCount() + stack.getCount());
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        return List.of(new ViewGroup<>(new ArrayList<>(merged.values())));
    }

    @Override
    public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
        return ClientViewGroup.map(groups, ItemView::new, null);
    }

    /** 合并用的键：本模组耐久物品只看物品本身，其余看物品 + 完整组件。 */
    private record Key(Item item, @Nullable DataComponentPatch components) {
        static Key of(ItemStack stack) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean own = id != null && StellarEvolution.MOD_ID.equals(id.getNamespace());
            if (own && stack.isDamageableItem()) {
                return new Key(stack.getItem(), null);
            }
            return new Key(stack.getItem(), stack.getComponentsPatch());
        }
    }
}
