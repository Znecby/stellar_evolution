package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.bedrock.BedrockComponents;
import dev.modmind.stellar_evolution.bedrock.BedrockMaterials;
import dev.modmind.stellar_evolution.fission.FissionComponents;
import dev.modmind.stellar_evolution.fission.FissionMachineKind;
import dev.modmind.stellar_evolution.fission.FissionMachines;
import dev.modmind.stellar_evolution.fission.FissionMaterials;
import dev.modmind.stellar_evolution.fission.FissionReactors;
import dev.modmind.stellar_evolution.fission.FissionRecipe;
import dev.modmind.stellar_evolution.fission.FissionRecipes;
import dev.modmind.stellar_evolution.fission.FissionSteam;
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
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * 裂变线 JEI 联动：<strong>每台机器一个分类</strong>（6 个），与生化 / 石化大机器完全同款 ——
 * 版面是两块带边框大板（{@link FissionRecipeUi}），催化剂 = 对应机器。
 *
 * <p>配方来自 {@link FissionRecipes}（机器真跑的就是它），所以 JEI 显示与机器行为永远一致。
 */
@JeiPlugin
public final class FissionJeiPlugin implements IModPlugin {
    private static final Map<FissionMachineKind, IRecipeType<FissionRecipe>> TYPES =
            new EnumMap<>(FissionMachineKind.class);

    static {
        for (FissionMachineKind kind : FissionMachineKind.values()) {
            TYPES.put(kind, IRecipeType.create(StellarEvolution.MOD_ID,
                    kind.id(), FissionRecipe.class));
        }
    }

    /** 该机种配方在 JEI 里的配方类型。 */
    static IRecipeType<FissionRecipe> typeOf(FissionMachineKind kind) {
        return TYPES.get(kind);
    }

    /** 该机种对应的机器物品（JEI 分类图标 / 催化剂）。 */
    static ItemLike machineItem(FissionMachineKind kind) {
        return switch (kind) {
            case ORE_LEACHER -> FissionMachines.ORE_LEACHER_ITEM.get();
            case CENTRIFUGE -> FissionMachines.CENTRIFUGE_ITEM.get();
            case CANNING_MACHINE -> FissionMachines.CANNING_MACHINE_ITEM.get();
            case REPROCESSOR -> FissionMachines.REPROCESSOR_ITEM.get();
            case FISSION_REACTOR -> FissionReactors.FISSION_REACTOR_ITEM.get();
            case STEAM_TURBINE -> FissionSteam.STEAM_TURBINE_ITEM.get();
            default -> throw new IllegalArgumentException("display-only kind has no JEI category: " + kind);
        };
    }

    /**
     * 这一机种要不要在 JEI 里建分类：有配方才建。
     * <p>没有配方的展示机（缪子聚变仪等）不会在 JEI 里出现空分类。
     */
    private static boolean hasCategory(FissionMachineKind kind) {
        return !FissionRecipes.forKind(kind).isEmpty();
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "fission_jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // 浸出渣 / 基岩浸出渣的变体只存在数据组件里；不注册子类型时 JEI 把它们当成同一个 ingredient，
        // R / U 只会命中表里第一条（例如永远跳到铁渣）。与压力罐的 particle_type 同一口径。
        registration.registerFromDataComponentTypes(
                FissionMaterials.LEACH_RESIDUE.get(), FissionComponents.LEACH_RESIDUE_TYPE.get());
        registration.registerFromDataComponentTypes(
                BedrockMaterials.BEDROCK_RESIDUE.get(), BedrockComponents.BEDROCK_STAGE.get());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        int registered = 0;
        for (FissionMachineKind kind : FissionMachineKind.values()) {
            if (!hasCategory(kind)) {
                continue;
            }
            List<FissionRecipe> recipes = FissionRecipes.forKind(kind);
            registration.addRecipeCategories(new RecipePanelCategory<>(
                    helpers,
                    typeOf(kind),
                    RecipeLayouts.FISSION,
                    Component.translatable("block.stellar_evolution." + kind.id()),
                    machineItem(kind),
                    RecipePanelCategory.heightFor(RecipeLayouts.FISSION, recipes)));
            registered++;
        }
        StellarEvolution.LOGGER.info("JEI: registered {} fission recipe categories", registered);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (FissionMachineKind kind : FissionMachineKind.values()) {
            if (!hasCategory(kind)) {
                continue;
            }
            List<FissionRecipe> recipes = FissionRecipes.forKind(kind);
            registration.addRecipes(typeOf(kind), recipes);
        }
        // 基岩线提示：钻头门槛 + 基础层不再产铝 + 铝只在 S2 高性能层
        registration.addIngredientInfo(BedrockMaterials.BEDROCK_ORE.get(),
                Component.translatable("jei.stellar_evolution.bedrock.0"),
                Component.translatable("jei.stellar_evolution.bedrock.1"),
                Component.translatable("jei.stellar_evolution.bedrock.2"),
                Component.translatable("jei.stellar_evolution.bedrock.3"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (FissionMachineKind kind : FissionMachineKind.values()) {
            if (!hasCategory(kind)) {
                continue;
            }
            registration.addCraftingStation(typeOf(kind), machineItem(kind));
        }
    }
}
