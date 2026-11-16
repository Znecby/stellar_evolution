package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.welding.WeldingMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * 焊接机界面：原版发射器底图（3×3 网格只保留第一行 = 1×3）+ 进度条 + 氧气条。
 * <p>底图由 {@code tools/gen_welding_assets.ps1} 在原版 {@code dispenser.png} 上合成：
 * 抹掉多余两行格子、加两条原版风格的内嵌条。动态填充只用纯色，不会漂移。
 */
public class WeldingScreen extends AbstractContainerScreen<WeldingMenu> {
    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "textures/gui/welding_machine.png");

    /** 进度条内区（底图坐标）。 */
    private static final int BAR_X = 63;
    private static final int BAR_W = 52;
    private static final int PROGRESS_Y = 41;
    private static final int PROGRESS_H = 6;
    private static final int FLUID_Y = 53;
    private static final int FLUID_H = 6;

    public WeldingScreen(WeldingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos,
                0, 0, this.imageWidth, this.imageHeight, 256, 256);
        drawProgress(graphics);
        drawFluid(graphics);
    }

    /** 进度条：按 进度 / 周期 填橙色。 */
    private void drawProgress(GuiGraphicsExtractor graphics) {
        int progress = this.menu.data().get(0);
        int total = this.menu.data().get(1);
        if (total <= 0 || progress <= 0) {
            return;
        }
        int width = (int) Math.round(BAR_W * Math.min(1.0D, progress / (double) total));
        graphics.fill(this.leftPos + BAR_X, this.topPos + PROGRESS_Y,
                this.leftPos + BAR_X + width, this.topPos + PROGRESS_Y + PROGRESS_H, 0xFFFFA000);
    }

    /** 氧气条：按 罐内 / 需求 填淡蓝色。 */
    private void drawFluid(GuiGraphicsExtractor graphics) {
        int amount = this.menu.data().get(2);
        int required = this.menu.data().get(3);
        if (required <= 0 || amount <= 0) {
            return;
        }
        int width = (int) Math.round(BAR_W * Math.min(1.0D, amount / (double) required));
        graphics.fill(this.leftPos + BAR_X, this.topPos + FLUID_Y,
                this.leftPos + BAR_X + width, this.topPos + FLUID_Y + FLUID_H, 0xCCAAE4FF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        int amount = this.menu.data().get(2);
        int required = this.menu.data().get(3);
        graphics.text(this.font,
                Component.translatable("container.stellar_evolution.welding_machine.oxygen", amount, required),
                this.leftPos + 62, this.topPos + 62, 0xFF404040);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
