package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.fission.FissionMachines;
import dev.znecby.stellar_evolution.hep.CyclotronRecipe;
import dev.znecby.stellar_evolution.hep.CyclotronRecipes;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** 回旋加速器 JEI 分类：粉 + 差速需求 → 同位素 / 氚。 */
@JeiPlugin
public final class CyclotronJeiPlugin implements IModPlugin {
    private static final IRecipeType<CyclotronRecipe> TYPE =
            IRecipeType.create(StellarEvolution.MOD_ID, "cyclotron", CyclotronRecipe.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "cyclotron_jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        registration.addRecipeCategories(new RecipePanelCategory<>(
                helpers,
                TYPE,
                RecipeLayouts.CYCLOTRON,
                Component.translatable("block.stellar_evolution.cyclotron"),
                FissionMachines.CYCLOTRON_ITEM.get()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<CyclotronRecipe> recipes = CyclotronRecipes.all();
        if (!recipes.isEmpty()) {
            registration.addRecipes(TYPE, recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(TYPE, FissionMachines.CYCLOTRON_ITEM.get());
    }
}
