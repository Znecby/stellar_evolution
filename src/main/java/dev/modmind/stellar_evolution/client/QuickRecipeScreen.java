package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.dev.QuickRecipeMenu;
import dev.modmind.stellar_evolution.dev.QuickRecipeSavePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 快速配方添加书的记录界面：原版工作台底图 + 左侧 3×3 输入格 + 右侧输出格 + “保存”按钮。
 * <p>工作台 / 热室两种模式共用这个界面，只有标题和提示不同；保存会写 {@code docs/quick_recipes} 下的文本。
 */
public class QuickRecipeScreen extends AbstractContainerScreen<QuickRecipeMenu> {
    private static final Identifier BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");

    public QuickRecipeScreen(QuickRecipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.stellar_evolution.quick_recipe.save"),
                        button -> ClientPacketDistributor.sendToServer(
                                new QuickRecipeSavePayload(this.menu.containerId)))
                .bounds(this.leftPos + 106, this.topPos + 58, 54, 18)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos,
                0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(this.font,
                Component.translatable(this.menu.mode().hintKey()),
                this.leftPos + 8, this.topPos + 58, 0xFF808080);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
