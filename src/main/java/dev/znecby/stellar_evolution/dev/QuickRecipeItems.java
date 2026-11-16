package dev.znecby.stellar_evolution.dev;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 开发用物品：快速配方添加书。
 *
 * <p>仅用于开发期快速记录配方输入；物品带原版附魔光效，右键打开 3×3 记录界面。
 * <p><strong>以后要删除</strong>：本包（{@code dev}）与 {@code docs/quick_recipes} 都是临时开发内容。
 */
public final class QuickRecipeItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredItem<Item> QUICK_RECIPE_BOOK = ITEMS.register("quick_recipe_book",
            key -> new QuickRecipeBookItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private QuickRecipeItems() {}
}
