package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.equipment.ModeTool;
import dev.znecby.stellar_evolution.equipment.SteelModeSupport;
import dev.znecby.stellar_evolution.equipment.SteelToolFeature;
import dev.znecby.stellar_evolution.equipment.SteelToolModePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 钢制工具的功能配置界面（原版 {@link Screen} + 按钮）。
 *
 * <p>按工具种类显示可用功能；可多选，但主行动组与时运 / 精准各自互斥。
 * 选中的按钮用原版「按下」高亮态显示；按钮文字带等级 / 规模。
 */
public class SteelToolModeScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private final ItemStack stack;
    private final ModeTool tool;
    private int features;

    public SteelToolModeScreen(ItemStack stack) {
        super(Component.translatable("screen.stellar_evolution.steel_tool_mode"));
        this.stack = stack;
        this.tool = (ModeTool) stack.getItem();
        this.features = tool.features(stack);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 84;

        Button none = addRenderableWidget(Button.builder(
                        Component.translatable("stellar_evolution.steel_tool.mode.none"),
                        button -> apply(0))
                .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        none.setOverrideRenderHighlightedSprite(() -> this.features == 0);
        y += BUTTON_HEIGHT + GAP;

        for (SteelToolFeature feature : SteelToolFeature.supportedBy(tool.kind())) {
            Button button = addRenderableWidget(Button.builder(
                            SteelModeSupport.featureLabel(tool, stack, feature),
                            pressed -> toggle(feature))
                    .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            button.setOverrideRenderHighlightedSprite(() -> (this.features & feature.mask()) != 0);
            y += BUTTON_HEIGHT + GAP;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centerX - 50, y + 8, 100, BUTTON_HEIGHT)
                .build());
    }

    /** 切换功能；开启时清掉同组互斥项。 */
    private void toggle(SteelToolFeature feature) {
        boolean enabled = (this.features & feature.mask()) != 0;
        int next = this.features;
        if (enabled) {
            next &= ~feature.mask();
        } else {
            next |= feature.mask();
            next &= ~feature.conflictMask(tool.kind());
        }
        apply(next);
    }

    private void apply(int features) {
        this.features = SteelToolFeature.sanitize(features, tool.kind());
        ClientPacketDistributor.sendToServer(new SteelToolModePayload.Set(this.features));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        graphics.centeredText(this.font, this.title, centerX, this.height / 2 - 110, 0xFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.steel_tool_mode.hint"),
                centerX, this.height / 2 - 96, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
