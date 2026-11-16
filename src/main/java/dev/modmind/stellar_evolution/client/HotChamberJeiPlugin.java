package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.thermal.HotChamberRecipe;
import dev.modmind.stellar_evolution.thermal.HotChamberRecipes;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * JEI 联动：把<strong>热室</strong>配方摆成一个分类（催化剂 = 原版<strong>铜箱子</strong>）。
 *
 * <p>为什么催化剂是「铜箱子」：热室不是一个方块，而是<strong>铜箱子底部垫铜块</strong>组成的结构
 * （见 {@code thermal.HotChamber}）—— 认到的那个方块就是铜箱子。
 *
 * <p>配方是<strong>代码内置</strong>的（{@link HotChamberRecipes}），所以这里不需要数据包配方类型；
 * JEI 只把同一张表摆出来看 —— 「机器真跑的」与「JEI 显示的」永远一致。
 */
@JeiPlugin
public final class HotChamberJeiPlugin implements IModPlugin {
    /** 热室配方在 JEI 里的类型（唯一的分类）。 */
    static final IRecipeType<HotChamberRecipe> TYPE =
            IRecipeType.create(StellarEvolution.MOD_ID, "hot_chamber", HotChamberRecipe.class);

    /** 原版铜箱子全家族（未氧化 / 斑驳 / 风化 / 氧化 × 是否打蜡）—— 与 {@code CopperHeat.COPPER_CHESTS} 标签一致。 */
    private static final ItemLike[] COPPER_CHESTS = {
            Items.COPPER_CHEST, Items.EXPOSED_COPPER_CHEST, Items.WEATHERED_COPPER_CHEST, Items.OXIDIZED_COPPER_CHEST,
            Items.WAXED_COPPER_CHEST, Items.WAXED_EXPOSED_COPPER_CHEST, Items.WAXED_WEATHERED_COPPER_CHEST,
            Items.WAXED_OXIDIZED_COPPER_CHEST
    };

    @Override
    public Identifier getPluginUid() {
        // 注意：生化线的 BioJeiPlugin 已占用 `stellar_evolution:jei`、石化线占用 `jei_petro`，
        // JEI 要求插件 uid 唯一，这里另给一个。
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "jei_hot_chamber");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RecipePanelCategory<>(
                registration.getJeiHelpers(),
                TYPE,
                RecipeLayouts.HOT_CHAMBER,
                Component.translatable("jei.stellar_evolution.hot_chamber"),
                Items.COPPER_CHEST,
                // 按实际配方算高度：最多 9 种输入（3×3 槽位），避免第三行压到面板下边框
                RecipePanelCategory.heightFor(RecipeLayouts.HOT_CHAMBER, HotChamberRecipes.all())));
        StellarEvolution.LOGGER.info("JEI: registered hot chamber recipe category");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // 传可变副本：JEI 内部可能对收到的列表排序 / 改写，而 HotChamberRecipes.all() 是不可变表
        registration.addRecipes(TYPE, new ArrayList<>(HotChamberRecipes.all()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 热室认的是 `minecraft:copper_chests` 标签（含氧化 / 打蜡共 8 种），催化剂也一并给出
        for (ItemLike chest : COPPER_CHESTS) {
            registration.addCraftingStation(TYPE, chest);
        }
    }
}
