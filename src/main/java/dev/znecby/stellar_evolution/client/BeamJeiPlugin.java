package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.fission.FissionMachineKind;
import dev.znecby.stellar_evolution.fission.FissionMachines;
import dev.znecby.stellar_evolution.particle.BeamRecipes;
import java.util.List;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * 束流机器 JEI 分类：<strong>辐照仓</strong>（单路流）与<strong>对撞机</strong>（双路流进 + μ 子流出）。
 *
 * <p>两者共用一张配方表与一套版面，按机种拆成两个分类；每格都会画出它需要的那几路粒子流。
 */
@JeiPlugin
public final class BeamJeiPlugin implements IModPlugin {
    private static final IRecipeType<BeamRecipes.Recipe> IRRADIATION =
            type("irradiation");
    private static final IRecipeType<BeamRecipes.Recipe> COLLISION =
            type("collision");
    private static final IRecipeType<BeamRecipes.Recipe> ADS =
            type("ads");

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "beam_jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        registration.addRecipeCategories(
                category(helpers, IRRADIATION, FissionMachineKind.IRRADIATION_CHAMBER,
                        "block.stellar_evolution.irradiation_chamber",
                        FissionMachines.IRRADIATION_CHAMBER_ITEM),
                category(helpers, COLLISION, FissionMachineKind.COLLIDER,
                        "block.stellar_evolution.collider",
                        FissionMachines.COLLIDER_ITEM),
                category(helpers, ADS, FissionMachineKind.ADS,
                        "block.stellar_evolution.ads",
                        FissionMachines.ADS_ITEM));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(IRRADIATION, BeamRecipes.forMachine(FissionMachineKind.IRRADIATION_CHAMBER));
        registration.addRecipes(COLLISION, BeamRecipes.forMachine(FissionMachineKind.COLLIDER));
        registration.addRecipes(ADS, BeamRecipes.forMachine(FissionMachineKind.ADS));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(IRRADIATION, FissionMachines.IRRADIATION_CHAMBER_ITEM.get());
        registration.addCraftingStation(COLLISION, FissionMachines.COLLIDER_ITEM.get());
        registration.addCraftingStation(ADS, FissionMachines.ADS_ITEM.get());
    }

    private static IRecipeType<BeamRecipes.Recipe> type(String path) {
        return IRecipeType.create(StellarEvolution.MOD_ID, path, BeamRecipes.Recipe.class);
    }

    private static RecipePanelCategory<BeamRecipes.Recipe> category(
            IJeiHelpers helpers, IRecipeType<BeamRecipes.Recipe> type, FissionMachineKind kind,
            String titleKey, Supplier<? extends Item> catalyst) {
        List<BeamRecipes.Recipe> recipes = BeamRecipes.forMachine(kind);
        int height = RecipePanelCategory.heightFor(RecipeLayouts.BEAM, recipes);
        return new RecipePanelCategory<>(helpers, type, RecipeLayouts.BEAM,
                Component.translatable(titleKey), catalyst.get(), height);
    }
}
