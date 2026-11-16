package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.machine.MachineBlocks;
import dev.znecby.stellar_evolution.machine.MachineKind;
import dev.znecby.stellar_evolution.machine.MachineRecipe;
import dev.znecby.stellar_evolution.machine.MachineRecipes;
import dev.znecby.stellar_evolution.welding.WeldingBlocks;
import dev.znecby.stellar_evolution.welding.WeldingRecipes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * JEI 联动：把<strong>材料加工机械</strong>的三类配方挂成 3 个分类（催化剂 = 对应机器）——
 * 照石化线 {@code PetroJeiPlugin} / 热室 {@code HotChamberJeiPlugin}。
 *
 * <p>配方是<strong>代码内置</strong>的（{@link MachineRecipes}），所以这里不需要数据包配方类型；
 * JEI 只把同一张表摆出来看 —— 于是「机器真跑的」与「JEI 显示的」永远一致。
 *
 * <p>分类 uid 直接用机种 id（{@code crusher} / {@code die_press} / {@code rolling_mill}），
 * 标题走语言键 {@code jei.stellar_evolution.<机种 id>}。
 */
@JeiPlugin
public final class MachineJeiPlugin implements IModPlugin {
    private static final Map<MachineKind, IRecipeType<MachineRecipe>> TYPES = new EnumMap<>(MachineKind.class);

    /** 焊接机配方类型（固定顺序的多物品 + 流体）。 */
    private static final IRecipeType<WeldingRecipes.Recipe> WELDING =
            IRecipeType.create(StellarEvolution.MOD_ID, "welding", WeldingRecipes.Recipe.class);

    static {
        for (MachineKind kind : MachineKind.values()) {
            TYPES.put(kind, IRecipeType.create(StellarEvolution.MOD_ID, kind.id(),
                    MachineRecipe.class));
        }
    }

    /** 该机种配方在 JEI 里的配方类型。 */
    static IRecipeType<MachineRecipe> typeOf(MachineKind kind) {
        return TYPES.get(kind);
    }

    /** 该机种对应的机器物品（JEI 分类图标 / 催化剂）。 */
    static ItemLike machineItem(MachineKind kind) {
        return switch (kind) {
            case CRUSHER -> MachineBlocks.CRUSHER_ITEM.get();
            case DIE_PRESS -> MachineBlocks.DIE_PRESS_ITEM.get();
            case ROLLING_MILL -> MachineBlocks.ROLLING_MILL_ITEM.get();
            case STEEL_FURNACE -> MachineBlocks.STEEL_FURNACE_ITEM.get();
        };
    }

    @Override
    public Identifier getPluginUid() {
        // 注意：生化线占用 `stellar_evolution:jei`、石化线 `jei_petro`、热室 `jei_hot_chamber`，
        // JEI 要求插件 uid 唯一，这里另给一个。
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "jei_machine");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        for (MachineKind kind : MachineKind.values()) {
            if (kind == MachineKind.STEEL_FURNACE) {
                continue;   // 钢炉直接复用原版「熔炼」分类，不再单开一类
            }
            registration.addRecipeCategories(new RecipePanelCategory<>(
                    helpers,
                    typeOf(kind),
                    RecipeLayouts.MACHINE,
                    Component.translatable("jei.stellar_evolution." + kind.id()),
                    machineItem(kind)));
        }
        // 焊接机：固定顺序的多物品 + 流体
        registration.addRecipeCategories(new RecipePanelCategory<>(
                helpers,
                WELDING,
                RecipeLayouts.WELDING,
                Component.translatable("jei.stellar_evolution.welding"),
                WeldingBlocks.WELDING_MACHINE_ITEM.get()));
        StellarEvolution.LOGGER.info("JEI: registered material machine recipe categories");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (MachineKind kind : MachineKind.values()) {
            if (kind == MachineKind.STEEL_FURNACE) {
                continue;   // 配方由原版熔炼分类展示
            }
            // 传可变副本：JEI 内部可能对收到的列表排序 / 改写
            registration.addRecipes(typeOf(kind), new ArrayList<>(MachineRecipes.samplesFor(kind)));
        }
        registration.addRecipes(WELDING, new ArrayList<>(WeldingRecipes.all()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineKind kind : MachineKind.values()) {
            if (kind == MachineKind.STEEL_FURNACE) {
                // 钢炉能执行所有熔炉配方 → 作为原版「熔炼」分类的催化剂
                registration.addCraftingStation(RecipeTypes.SMELTING, machineItem(kind));
                continue;
            }
            registration.addCraftingStation(typeOf(kind), machineItem(kind));
        }
        registration.addCraftingStation(WELDING, WeldingBlocks.WELDING_MACHINE_ITEM.get());
    }
}
