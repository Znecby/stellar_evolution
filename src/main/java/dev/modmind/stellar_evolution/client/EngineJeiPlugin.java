package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.engine.EngineBlocks;
import dev.modmind.stellar_evolution.engine.EngineFuelGrade;
import dev.modmind.stellar_evolution.engine.EngineFuels;
import dev.modmind.stellar_evolution.engine.EngineMachines;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 燃烧类引擎的 JEI 联动（**整份自写**，原版绘制，无 UI 前置库）。
 *
 * <p>五个燃料分类，催化剂 = 对应机器：
 * <ul>
 *   <li><b>燃烧引擎（固体模式）</b>：列 {@link FuelValues}（= 所有原版熔炉燃料）；</li>
 *   <li><b>燃烧引擎（液体模式）</b>：只列 LOW / MEDIUM / HIGH（往复式内燃机燃料）；</li>
 *   <li><b>工业内燃机组</b>：LOW / MEDIUM / HIGH；</li>
 *   <li><b>联合燃气机组</b>：GAS / AERO；</li>
 *   <li><b>航空航天火箭引擎</b>：ROCKET（煤油氧 / 甲烷氧 / 自燃，燃料自带氧化剂）。</li>
 * </ul>
 *
 * <p><b>版面</b>：两块并排的板 —— 左板 = 燃料槽（槽上下居中），右板 = 数值文字（竖直居中、左对齐）。
 */
@JeiPlugin
public final class EngineJeiPlugin implements IModPlugin {
    /** 一条固体燃料：燃料物品 + 折算点数。 */
    public record SolidFuel(ItemStack fuel, int points) {}

    /** 一条液体燃料：流体 + 每 mB 时长 + 出力。 */
    public record LiquidFuel(FluidStack fluid, int ticksPerMb, double capacity, double flowRate) {}

    /** 固体模式分类。 */
    public static final IRecipeType<SolidFuel> SOLID =
            IRecipeType.create(StellarEvolution.MOD_ID, "engine_solid", SolidFuel.class);
    /** 燃烧引擎液体模式分类。 */
    public static final IRecipeType<LiquidFuel> LIQUID =
            IRecipeType.create(StellarEvolution.MOD_ID, "engine_liquid", LiquidFuel.class);
    /** 工业内燃机组燃料分类。 */
    public static final IRecipeType<LiquidFuel> INDUSTRIAL =
            IRecipeType.create(StellarEvolution.MOD_ID, "engine_industrial", LiquidFuel.class);
    /** 联合燃气机组燃料分类。 */
    public static final IRecipeType<LiquidFuel> GAS_TURBINE =
            IRecipeType.create(StellarEvolution.MOD_ID, "engine_gas_turbine", LiquidFuel.class);
    /** 航空航天火箭引擎燃料分类。 */
    public static final IRecipeType<LiquidFuel> ROCKET =
            IRecipeType.create(StellarEvolution.MOD_ID, "engine_rocket", LiquidFuel.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "engine_jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        registration.addRecipeCategories(
                new FuelCategory<>(helpers, SOLID, "jei.stellar_evolution.engine_solid",
                        EngineBlocks.BURNING_ENGINE_ITEM.get(),
                        recipe -> RecipePanelCategory.Ingredient.item(recipe.fuel()),
                        recipe -> List.of(Component.translatable(
                                "jei.stellar_evolution.engine_solid.value",
                                recipe.points(), recipe.points() * 20))),
                liquidCategory(helpers, LIQUID, "jei.stellar_evolution.engine_liquid",
                        EngineBlocks.BURNING_ENGINE_ITEM.get()),
                liquidCategory(helpers, INDUSTRIAL, "jei.stellar_evolution.engine_industrial",
                        EngineMachines.INDUSTRIAL_ENGINE_ITEM.get()),
                liquidCategory(helpers, GAS_TURBINE, "jei.stellar_evolution.engine_gas_turbine",
                        EngineMachines.GAS_TURBINE_ITEM.get()),
                liquidCategory(helpers, ROCKET, "jei.stellar_evolution.engine_rocket",
                        EngineMachines.ROCKET_ENGINE_ITEM.get()));
        StellarEvolution.LOGGER.info("JEI: registered 5 combustion engine fuel categories");
    }

    private static FuelCategory<LiquidFuel> liquidCategory(IJeiHelpers helpers, IRecipeType<LiquidFuel> type,
                                                           String titleKey, Item icon) {
        return new FuelCategory<>(helpers, type, titleKey, icon,
                recipe -> RecipePanelCategory.Ingredient.fluid(recipe.fluid()),
                recipe -> List.of(
                        Component.translatable("jei.stellar_evolution.engine_liquid.power",
                                (int) recipe.capacity(), (int) recipe.flowRate()),
                        Component.translatable("jei.stellar_evolution.engine_liquid.value",
                                recipe.ticksPerMb())));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SOLID, solidFuels());
        registration.addRecipes(LIQUID, liquidFuels(EngineFuels::pistonGrade));
        registration.addRecipes(INDUSTRIAL, liquidFuels(EngineFuels::pistonGrade));
        registration.addRecipes(GAS_TURBINE, liquidFuels(fuel -> isGrade(fuel,
                EngineFuelGrade.GAS, EngineFuelGrade.AERO)));
        registration.addRecipes(ROCKET, liquidFuels(fuel -> isGrade(fuel,
                EngineFuelGrade.ROCKET)));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(SOLID, EngineBlocks.BURNING_ENGINE_ITEM.get());
        registration.addCraftingStation(LIQUID, EngineBlocks.BURNING_ENGINE_ITEM.get());
        registration.addCraftingStation(INDUSTRIAL, EngineMachines.INDUSTRIAL_ENGINE_ITEM.get());
        registration.addCraftingStation(GAS_TURBINE, EngineMachines.GAS_TURBINE_ITEM.get());
        registration.addCraftingStation(ROCKET, EngineMachines.ROCKET_ENGINE_ITEM.get());
    }

    // ------------------------------------------------------------------ 条目

    /** 固体燃料：遍历当前世界的 {@link FuelValues}（拿不到就返回空表）。 */
    private static List<SolidFuel> solidFuels() {
        List<SolidFuel> out = new ArrayList<>();
        if (Minecraft.getInstance().level == null) {
            return out;
        }
        FuelValues fuels = Minecraft.getInstance().level.fuelValues();
        for (Item item : fuels.fuelItems()) {
            ItemStack stack = new ItemStack(item);
            int burn = fuels.burnDuration(stack);
            if (burn > 0) {
                out.add(new SolidFuel(stack, Math.max(1, burn / 20)));
            }
        }
        out.sort((a, b) -> Integer.compare(b.points(), a.points()));
        return out;
    }

    /** 液体燃料：按等级过滤 {@link EngineFuels}。 */
    private static List<LiquidFuel> liquidFuels(Predicate<EngineFuels.Fuel> filter) {
        List<LiquidFuel> out = new ArrayList<>();
        for (Map.Entry<Fluid, EngineFuels.Fuel> entry : EngineFuels.all().entrySet()) {
            EngineFuels.Fuel fuel = entry.getValue();
            if (!filter.test(fuel)) {
                continue;
            }
            out.add(new LiquidFuel(new FluidStack(entry.getKey(), 1000),
                    fuel.ticksPerMb(), fuel.capacity(), fuel.flowRate()));
        }
        out.sort((a, b) -> Integer.compare(b.ticksPerMb(), a.ticksPerMb()));
        return out;
    }

    private static boolean isGrade(EngineFuels.Fuel fuel, EngineFuelGrade... grades) {
        for (EngineFuelGrade grade : grades) {
            if (fuel.grade() == grade) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 分类

    /** 通用燃料分类：左板一格燃料槽，右板若干行文字。 */
    private static final class FuelCategory<T> extends AbstractRecipeCategory<T> {
        private static final int SLOT = 18;
        private static final int PAD = 4;
        private static final int GAP = 4;
        /** 左板宽 = 槽 + 左右内边距。 */
        private static final int LEFT_W = SLOT + PAD * 2;
        private static final int WIDTH = 176;
        private static final int HEIGHT = 30;
        private static final int LINE_HEIGHT = 10;
        private static final int TEXT_COLOR = 0xFF404040;

        private final Function<T, RecipePanelCategory.Ingredient> slotContent;
        private final Function<T, List<Component>> lines;

        FuelCategory(IJeiHelpers helpers, IRecipeType<T> type, String titleKey, Item icon,
                     Function<T, RecipePanelCategory.Ingredient> slotContent,
                     Function<T, List<Component>> lines) {
            super(type, Component.translatable(titleKey),
                    helpers.getGuiHelper().createDrawableItemLike(icon),
                    WIDTH, HEIGHT);
            this.slotContent = slotContent;
            this.lines = lines;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
            IRecipeSlotBuilder slot = builder
                    .addSlot(RecipeIngredientRole.INPUT, PAD, (HEIGHT - SLOT) / 2)
                    .setStandardSlotBackground();
            RecipePanelCategory.Ingredient content = slotContent.apply(recipe);
            if (!content.item().isEmpty()) {
                slot.add(content.item());
            } else if (!content.fluid().isEmpty()) {
                FluidStack fluid = content.fluid();
                slot.add(fluid.getFluid(), fluid.getAmount());
                slot.setFluidRenderer(fluid.getAmount(), true, 16, 16);
            }
        }

        @Override
        public void draw(T recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                         double mouseX, double mouseY) {
            outline(graphics, 0, 0, LEFT_W, HEIGHT);
            outline(graphics, LEFT_W + GAP, 0, WIDTH - LEFT_W - GAP, HEIGHT);

            List<Component> text = lines.apply(recipe);
            int y = (HEIGHT - text.size() * LINE_HEIGHT) / 2;
            for (Component line : text) {
                graphics.text(Minecraft.getInstance().font, line, LEFT_W + GAP + PAD, y, TEXT_COLOR, false);
                y += LINE_HEIGHT;
            }
        }

        private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
            int color = 0xFF373737;
            graphics.fill(x, y, x + w, y + 1, color);
            graphics.fill(x, y + h - 1, x + w, y + h, color);
            graphics.fill(x, y, x + 1, y + h, color);
            graphics.fill(x + w - 1, y, x + w, y + h, color);
        }
    }
}
