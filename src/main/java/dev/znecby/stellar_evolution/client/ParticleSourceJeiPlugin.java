package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.fission.FissionMachines;
import dev.znecby.stellar_evolution.particle.ParticleComponents;
import dev.znecby.stellar_evolution.particle.ParticleItems;
import dev.znecby.stellar_evolution.particle.ParticleType;
import java.util.List;
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

/** 粒子源 JEI 分类：带粒子类型的压力罐 → 空罐 + 束流。 */
@JeiPlugin
public final class ParticleSourceJeiPlugin implements IModPlugin {
    private static final IRecipeType<ParticleType> TYPE =
            IRecipeType.create(StellarEvolution.MOD_ID, "particle_source", ParticleType.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "particle_source_jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        registration.addRecipeCategories(new RecipePanelCategory<>(
                helpers,
                TYPE,
                RecipeLayouts.PARTICLE_SOURCE,
                Component.translatable("block.stellar_evolution.particle_source"),
                FissionMachines.PARTICLE_SOURCE_ITEM.get()));
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // 压力罐的「装什么粒子」只存在 particle_type 数据组件里：不注册子类型时 JEI 把所有罐当成同一个
        // ingredient，按 R / U 只会跳到表里第一条（例如永远跳到铅离子）。注册后每个粒子类型各自成一条，
        // 空罐（无组件）也与装填过的罐区分开。
        registration.registerFromDataComponentTypes(
                ParticleItems.PRESSURE_TANK.get(), ParticleComponents.PARTICLE_TYPE.get());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE, List.of(ParticleType.values()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(TYPE, FissionMachines.PARTICLE_SOURCE_ITEM.get());
    }
}
