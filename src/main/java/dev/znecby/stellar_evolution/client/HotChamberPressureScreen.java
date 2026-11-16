package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.network.HotChamberPressurePayload;
import dev.znecby.stellar_evolution.thermal.HotChamberPressureData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 热室<strong>等压值</strong>输入界面：原版文本框 + 按钮，操作口径照限流管。
 * <p>默认值 = 当前等压（新热室为 0）；提交走 {@link HotChamberPressurePayload.Set}。
 */
public class HotChamberPressureScreen extends Screen {
    private static final int BOX_WIDTH = 110;

    private final BlockPos pos;
    private final int pressure;
    private EditBox box;

    public HotChamberPressureScreen(BlockPos pos, int pressure) {
        super(Component.translatable("screen.stellar_evolution.hot_chamber_pressure"));
        this.pos = pos;
        this.pressure = pressure;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.box = new EditBox(this.font, cx - BOX_WIDTH / 2, cy - 10, BOX_WIDTH, 20, this.title);
        this.box.setMaxLength(3);
        this.box.setValue(Integer.toString(this.pressure));
        this.box.setHint(Component.translatable("screen.stellar_evolution.hot_chamber_pressure.hint"));
        addRenderableWidget(this.box);
        setInitialFocus(this.box);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> submit())
                .bounds(cx - 60, cy + 20, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(cx + 2, cy + 20, 58, 20).build());
    }

    private void submit() {
        int value;
        try {
            value = Integer.parseInt(this.box.getValue().trim());
        } catch (NumberFormatException ignored) {
            value = 0;
        }
        value = Math.max(0, Math.min(HotChamberPressureData.MAX_PRESSURE, value));
        ClientPacketDistributor.sendToServer(new HotChamberPressurePayload.Set(this.pos, value));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.centeredText(this.font, this.title, cx, cy - 50, 0xFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.hot_chamber_pressure.note"),
                cx, cy + 48, 0x808080);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
