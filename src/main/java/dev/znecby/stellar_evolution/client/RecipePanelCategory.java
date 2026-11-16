package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.stream.StreamType;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 通用 JEI 配方分类：<strong>左「输入」/ 右「产物」两块板的版面</strong>，槽位由 JEI 自己画（标准槽底），
 * 面板边框与文字由 {@link #draw} 用原版 {@code fill} / {@code text} 画 —— 不再依赖任何 UI 前置库。
 *
 * <p>与原先的 LDLib2 {@code ModularUIRecipeCategory} 相比，这里把「版面」抽成 {@link Layout}：
 * 各线只需给出「文字行 + 输入槽 + 输出槽」，其余排版（每行 3 格、间距、面板位置）全由本类负责。
 */
final class RecipePanelCategory<T> extends AbstractRecipeCategory<T> {
    /** 画布尺寸。 */
    static final int WIDTH = 150;
    static final int HEIGHT = 84;
    /** 3 行槽位（最多 9 种输入）用的画布高度：2 行文字 + 3×18 槽位 + 边距。 */
    static final int HEIGHT_THREE_ROWS = 92;

    private static final int SLOT = 18;
    private static final int SLOT_GAP = 2;
    private static final int SLOT_PITCH = SLOT + SLOT_GAP;
    private static final int SLOTS_PER_ROW = 3;
    /** 两块板的内边距。 */
    private static final int PANEL_PAD = 6;
    /** 两块板之间的空隙。 */
    private static final int PANEL_GAP = 8;
    private static final int PANEL_WIDTH = (WIDTH - PANEL_GAP) / 2;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xFF404040;

    private final Layout<T> layout;
    private final int height;

    RecipePanelCategory(IJeiHelpers helpers, IRecipeType<T> type, Layout<T> layout, Component title, ItemLike iconItem) {
        this(helpers, type, layout, title, iconItem, HEIGHT);
    }

    RecipePanelCategory(IJeiHelpers helpers, IRecipeType<T> type, Layout<T> layout, Component title, ItemLike iconItem,
                        int height) {
        super(type, title, helpers.getGuiHelper().createDrawableItemLike(iconItem), WIDTH, height);
        this.layout = layout;
        this.height = height;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        int top = bodyTop(recipe);
        place(builder, layout.inputs(recipe), RecipeIngredientRole.INPUT, 0, top);
        place(builder, layout.outputs(recipe), RecipeIngredientRole.OUTPUT, PANEL_WIDTH + PANEL_GAP, top);
    }

    @Override
    public void draw(T recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        int top = bodyTop(recipe);
        int bodyHeight = height - top;
        outline(graphics, 0, top, PANEL_WIDTH, bodyHeight);
        outline(graphics, PANEL_WIDTH + PANEL_GAP, top, PANEL_WIDTH, bodyHeight);

        int y = 1;
        for (Component line : layout.lines(recipe)) {
            graphics.text(Minecraft.getInstance().font, line, 2, y, TEXT_COLOR, false);
            y += LINE_HEIGHT;
        }
    }

    /** 正文（槽位）区域的顶部：文字行数 × 行高 + 2px 间隙。 */
    private int bodyTop(T recipe) {
        return layout.lines(recipe).size() * LINE_HEIGHT + 2;
    }

    /**
     * 按分类里<strong>实际配方</strong>算所需高度：文字行 + 最多的一侧槽位行数。
     *
     * <p>JEI 的 {@code getHeight()} 是 final，只能在构造时给定；固定 84 在「4 行文字 + 2 行槽位」
     * 时会让第二行槽位压到面板底边上（对撞机批量配方的黑线）。用这个方法按分类取最大值，
     * 既不全局加高，也不会再出现槽位溢出。
     */
    static <T> int heightFor(Layout<T> layout, List<T> recipes) {
        int needed = HEIGHT;
        for (T recipe : recipes) {
            int lines = layout.lines(recipe).size();
            int rows = Math.max(rows(layout.inputs(recipe).size()), rows(layout.outputs(recipe).size()));
            int content = lines * LINE_HEIGHT + 2;
            if (rows > 0) {
                content += PANEL_PAD + (rows - 1) * SLOT_PITCH + SLOT + PANEL_PAD;
            }
            needed = Math.max(needed, content);
        }
        return needed;
    }

    private static int rows(int count) {
        return count <= 0 ? 0 : (count + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
    }

    private static void place(IRecipeLayoutBuilder builder, List<Ingredient> ingredients,
                              RecipeIngredientRole role, int panelX, int top) {
        for (int i = 0; i < ingredients.size(); i++) {
            int x = panelX + PANEL_PAD + (i % SLOTS_PER_ROW) * SLOT_PITCH;
            int y = top + PANEL_PAD + (i / SLOTS_PER_ROW) * SLOT_PITCH;
            Ingredient ingredient = ingredients.get(i);
            IRecipeSlotBuilder slot = builder.addSlot(role, x, y).setStandardSlotBackground();
            if (!ingredient.itemList().isEmpty()) {
                slot.addItemStacks(ingredient.itemList());   // 标签：JEI 一个槽内轮换显示全部成员
            } else if (!ingredient.item().isEmpty()) {
                slot.add(ingredient.item());
            } else if (!ingredient.fluid().isEmpty()) {
                FluidStack fluid = ingredient.fluid();
                slot.add(fluid.getFluid(), fluid.getAmount());
                slot.setFluidRenderer(fluid.getAmount(), true, 16, 16);
            } else if (ingredient.stream() != null) {
                slot.add(StreamJeiPlugin.TYPE, ingredient.stream());
            }
            if (ingredient.chance() < 1.0D) {
                int percent = (int) Math.round(ingredient.chance() * 100);
                slot.addRichTooltipCallback((slotView, tooltip) ->
                        tooltip.add(Component.translatable("stellar_evolution.jei.output_chance", percent)));
            }
        }
    }

    /** 1px 描边（面板只画边框，避免盖住 JEI 自己画的槽位）。 */
    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        int color = 0xFF373737;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** 一格配方料：物品 / 物品列表（标签）/ 流体 / 粒子流（四者取其一）；{@code chance} < 1 时给槽位挂概率提示。 */
    record Ingredient(ItemStack item, FluidStack fluid, StreamType stream, List<ItemStack> itemList, double chance) {
        static Ingredient item(ItemStack stack) {
            return new Ingredient(stack, FluidStack.EMPTY, null, List.of(), 1.0D);
        }

        /** 概率产物（仅输出槽）：tooltip 里标注「产出概率 x%」。 */
        static Ingredient item(ItemStack stack, double chance) {
            return new Ingredient(stack, FluidStack.EMPTY, null, List.of(), chance);
        }

        /** 标签输入：一个槽里轮换显示全部成员。 */
        static Ingredient items(List<ItemStack> stacks) {
            return new Ingredient(ItemStack.EMPTY, FluidStack.EMPTY, null, List.copyOf(stacks), 1.0D);
        }

        static Ingredient fluid(FluidStack stack) {
            return new Ingredient(ItemStack.EMPTY, stack, null, List.of(), 1.0D);
        }

        static Ingredient stream(StreamType type) {
            return new Ingredient(ItemStack.EMPTY, FluidStack.EMPTY, type, List.of(), 1.0D);
        }
    }

    /** 一条配方的版面数据。 */
    interface Layout<T> {
        /** 顶部文字行（条件 / 时长），可以没有。 */
        default List<Component> lines(T recipe) {
            return List.of();
        }

        List<Ingredient> inputs(T recipe);

        List<Ingredient> outputs(T recipe);
    }
}
