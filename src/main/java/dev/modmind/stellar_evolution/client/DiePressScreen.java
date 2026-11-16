package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.machine.DiePressMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * 压模机界面：原版发射器底图（3×3 料仓）+ 第一行左侧的<strong>模具槽</strong>。
 * <p>模具槽是额外画的（底图没有这一格）；空槽悬停时显示「只能放模具」提示。
 */
public class DiePressScreen extends AbstractContainerScreen<DiePressMenu> {
    private static final Identifier BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

    public DiePressScreen(DiePressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos,
                0, 0, this.imageWidth, this.imageHeight, 256, 256);
        // 模具槽底图：原版槽位 sprite，画在 3×3 第一行左侧
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                this.leftPos + DiePressMenu.MOLD_X - 1, this.topPos + DiePressMenu.MOLD_Y - 1, 18, 18);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (this.hoveredSlot != null && this.hoveredSlot.index == DiePressMenu.MOLD_SLOT
                && !this.hoveredSlot.hasItem()) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.translatable("container.stellar_evolution.die_press.mold_hint"), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
