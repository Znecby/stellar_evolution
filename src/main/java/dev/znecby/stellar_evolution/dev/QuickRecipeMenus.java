package dev.znecby.stellar_evolution.dev;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 开发用菜单：快速配方添加书的 3×3 记录界面。 */
public final class QuickRecipeMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, StellarEvolution.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<QuickRecipeMenu>> QUICK_RECIPE =
            MENUS.register("quick_recipe", () -> new MenuType<>(QuickRecipeMenu::new, FeatureFlags.VANILLA_SET));

    /** 热室模式：界面相同，保存时 mode = hot_chamber。 */
    public static final DeferredHolder<MenuType<?>, MenuType<QuickRecipeMenu>> QUICK_RECIPE_HOT_CHAMBER =
            MENUS.register("quick_recipe_hot_chamber",
                    () -> new MenuType<>(QuickRecipeMenu::hotChamber, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    private QuickRecipeMenus() {}
}
