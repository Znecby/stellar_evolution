package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.air.AirBlocks;
import dev.modmind.stellar_evolution.coal.CokeOvenBlocks;
import dev.modmind.stellar_evolution.petro.PetroMachineBlocks;
import dev.modmind.stellar_evolution.petro.PetroMachineKind;
import dev.modmind.stellar_evolution.petro.PetroRecipe;
import dev.modmind.stellar_evolution.petro.PetroRecipes;
import java.util.ArrayList;
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
 * JEI 联动：把每个机种的配方挂成独立分类（催化剂 = 对应机器）—— 照生化线 {@code BioJeiPlugin}。
 *
 * <p>配方是<strong>代码内置</strong>的（{@link PetroRecipes}），所以这里不需要数据包配方类型；
 * JEI 只负责把同一张表摆出来看 —— 于是「机器真跑的」与「JEI 显示的」永远一致。
 */
@JeiPlugin
public final class PetroJeiPlugin implements IModPlugin {
    private static final Map<PetroMachineKind, IRecipeType<PetroRecipe>> TYPES = new EnumMap<>(PetroMachineKind.class);

    /** 不锈钢格栅专用的「液态空气分馏」分类：U 键直接跳到这一页。 */
    private static final IRecipeType<PetroRecipe> AIR_SEPARATION =
            IRecipeType.create(StellarEvolution.MOD_ID, "air_separation", PetroRecipe.class);

    static {
        for (PetroMachineKind kind : PetroMachineKind.values()) {
            TYPES.put(kind, IRecipeType.create(StellarEvolution.MOD_ID, kind.id(), PetroRecipe.class));
        }
    }

    /** 该机种配方在 JEI 里的配方类型。 */
    static IRecipeType<PetroRecipe> typeOf(PetroMachineKind kind) {
        return TYPES.get(kind);
    }

    /** 该机种对应的机器物品（JEI 分类图标 / 催化剂）。 */
    static ItemLike machineItem(PetroMachineKind kind) {
        return switch (kind) {
            case DISTILLATION_TOWER -> PetroMachineBlocks.DISTILLATION_TOWER_ITEM.get();
            case CRACKING_FURNACE -> PetroMachineBlocks.CRACKING_FURNACE_ITEM.get();
            case REFORMER -> PetroMachineBlocks.REFORMER_ITEM.get();
            case POLYMERIZER -> PetroMachineBlocks.POLYMERIZER_ITEM.get();
            case GASIFIER -> PetroMachineBlocks.GASIFIER_ITEM.get();
            case SYNTHESIS_TOWER -> PetroMachineBlocks.SYNTHESIS_TOWER_ITEM.get();
            case COKE_OVEN -> CokeOvenBlocks.CONTROLLER_ITEM.get();
        };
    }

    @Override
    public Identifier getPluginUid() {
        // 注意：生化线的 BioJeiPlugin 已占用 `stellar_evolution:jei`，这里必须另给一个（JEI 要求插件 uid 唯一）
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "jei_petro");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        for (PetroMachineKind kind : PetroMachineKind.values()) {
            List<PetroRecipe> recipes = new ArrayList<>();
            for (PetroRecipe recipe : PetroRecipes.samples()) {
                if (recipe.kind() == kind) {
                    recipes.add(recipe);
                }
            }
            registration.addRecipeCategories(new RecipePanelCategory<>(
                    helpers,
                    typeOf(kind),
                    RecipeLayouts.PETRO,
                    Component.translatable("jei.stellar_evolution." + kind.id()),
                    machineItem(kind),
                    RecipePanelCategory.heightFor(RecipeLayouts.PETRO, recipes)));
        }
        List<PetroRecipe> airRecipes = airSeparationRecipes();
        registration.addRecipeCategories(new RecipePanelCategory<>(
                helpers,
                AIR_SEPARATION,
                RecipeLayouts.PETRO,
                Component.translatable("jei.stellar_evolution.air_separation"),
                AirBlocks.STEEL_GRATE_ITEM.get(),
                RecipePanelCategory.heightFor(RecipeLayouts.PETRO, airRecipes)));
        StellarEvolution.LOGGER.info("JEI: registered {} petro recipe categories",
                PetroMachineKind.values().length + 1);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (PetroMachineKind kind : PetroMachineKind.values()) {
            List<PetroRecipe> recipes = new ArrayList<>();
            for (PetroRecipe recipe : PetroRecipes.samples()) {
                if (recipe.kind() == kind) {
                    recipes.add(recipe);
                }
            }
            registration.addRecipes(typeOf(kind), recipes);
        }
        registration.addRecipes(AIR_SEPARATION, airSeparationRecipes());

        // 「化工流程总览」信息页：对焦化炉控制器按 U 查看整条煤 / 木化工主线
        registration.addIngredientInfo(CokeOvenBlocks.CONTROLLER_ITEM.get(),
                Component.translatable("jei.stellar_evolution.chem_overview.0"),
                Component.translatable("jei.stellar_evolution.chem_overview.1"),
                Component.translatable("jei.stellar_evolution.chem_overview.2"),
                Component.translatable("jei.stellar_evolution.chem_overview.3"),
                Component.translatable("jei.stellar_evolution.chem_overview.4"),
                Component.translatable("jei.stellar_evolution.chem_overview.5"),
                Component.translatable("jei.stellar_evolution.chem_overview.6"),
                Component.translatable("jei.stellar_evolution.chem_overview.7"),
                Component.translatable("jei.stellar_evolution.chem_overview.8"),
                Component.translatable("jei.stellar_evolution.chem_overview.9"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (PetroMachineKind kind : PetroMachineKind.values()) {
            registration.addCraftingStation(typeOf(kind), machineItem(kind));
        }
        registration.addCraftingStation(AIR_SEPARATION, AirBlocks.STEEL_GRATE_ITEM.get());
        registration.addCraftingStation(AIR_SEPARATION, PetroMachineBlocks.DISTILLATION_TOWER_ITEM.get());
    }

    /** 只取液态空气分馏这一行。 */
    private static List<PetroRecipe> airSeparationRecipes() {
        return PetroRecipes.samples().stream()
                .filter(recipe -> "separate_air".equals(recipe.id()))
                .toList();
    }
}
