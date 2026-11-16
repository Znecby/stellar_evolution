package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.bio.BioMachineBlocks;
import dev.znecby.stellar_evolution.bio.BioRecipe;
import dev.znecby.stellar_evolution.bio.BioRecipeType;
import dev.znecby.stellar_evolution.bio.BioRecipes;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * JEI 联动：把 <strong>6 类生化配方</strong>挂成 6 个分类（催化剂 = 对应机器）。
 *
 * <p>配方是<strong>代码内置</strong>的（{@link BioRecipes}），所以这里不需要数据包配方类型；
 * JEI 只负责把同一张表摆出来看 —— 于是「机器真跑的」与「JEI 显示的」永远一致。
 * 每一类配方在 JEI 里各占一个分类，点机器（或查物品用途）就能翻到对应配方。
 */
@JeiPlugin
public final class BioJeiPlugin implements IModPlugin {
    private static final Map<BioRecipeType, IRecipeType<BioRecipe>> TYPES = new EnumMap<>(BioRecipeType.class);

    static {
        for (BioRecipeType type : BioRecipeType.values()) {
            TYPES.put(type, IRecipeType.create(StellarEvolution.MOD_ID, type.id(), BioRecipe.class));
        }
    }

    /** 该类配方在 JEI 里的配方类型。 */
    static IRecipeType<BioRecipe> typeOf(BioRecipeType type) {
        return TYPES.get(type);
    }

    /** 该类配方对应的机器物品（JEI 分类图标 / 催化剂）。 */
    static ItemLike machineItem(BioRecipeType type) {
        return switch (type) {
            case SLURRYING -> BioMachineBlocks.SLURRY_MIXER_ITEM.get();
            case FERMENTING -> BioMachineBlocks.FERMENTER_ITEM.get();
            case DECOMPOSING -> BioMachineBlocks.DECOMPOSER_ITEM.get();
            case STERILIZING -> BioMachineBlocks.STERILIZER_ITEM.get();
            case CRYSTALLIZING -> BioMachineBlocks.CRYSTALLIZER_ITEM.get();
            case BIOREACTION -> BioMachineBlocks.REACTOR_ITEM.get();
        };
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        for (BioRecipeType type : BioRecipeType.values()) {
            List<BioRecipe> recipes = BioRecipes.forType(type);
            registration.addRecipeCategories(new RecipePanelCategory<>(
                    helpers,
                    typeOf(type),
                    RecipeLayouts.BIO,
                    Component.translatable("jei.stellar_evolution." + type.id()),
                    machineItem(type),
                    RecipePanelCategory.heightFor(RecipeLayouts.BIO, recipes)));
        }
        StellarEvolution.LOGGER.info("JEI: registered {} bio recipe categories", BioRecipeType.values().length);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (BioRecipeType type : BioRecipeType.values()) {
            registration.addRecipes(typeOf(type), BioRecipes.forType(type));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (BioRecipeType type : BioRecipeType.values()) {
            registration.addCraftingStation(typeOf(type), machineItem(type));
        }
    }
}
