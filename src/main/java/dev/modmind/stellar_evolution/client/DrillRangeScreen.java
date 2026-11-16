package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.drill.DrillBlockEntity;
import dev.modmind.stellar_evolution.network.DrillRangePayload;
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
 * <strong>开采钻</strong>的范围输入界面：原版文本框 + 按钮，照 {@link FlowLimiterScreen} 口径。
 *
 * <p>同一个输入框按<strong>当前模式</strong>解释数字：
 * <ul>
 *   <li>范围破坏：边长 N（方块，5–63 的奇数）；</li>
 *   <li>精准采集：边长 N（区块，5–15 的奇数）。</li>
 * </ul>
 * 提交后服务端会再夹一次范围 / 奇数化；值变了就从机器下方重新开始。
 */
public class DrillRangeScreen extends Screen {
    /** 文本框宽度。 */
    private static final int BOX_WIDTH = 90;

    private final BlockPos pos;
    private final int mode;
    private final int value;
    private final int min;
    private final int max;
    private EditBox box;

    public DrillRangeScreen(BlockPos pos, int mode, int value, int min, int max) {
        super(Component.translatable("screen.stellar_evolution.drill_range"));
        this.pos = pos;
        this.mode = mode;
        this.value = value;
        this.min = min;
        this.max = max;
    }

    private boolean oreMode() {
        return this.mode == DrillBlockEntity.Mode.ORE.ordinal();
    }

    private Component modeName() {
        return Component.translatable(oreMode()
                ? "stellar_evolution.drill.mode.ore" : "stellar_evolution.drill.mode.area");
    }

    /** 当前模式的「N×N 方块 / N×N 区块」文本（两个 %s 都填 N）。 */
    private Component unitText(int n) {
        return Component.translatable(oreMode()
                ? "stellar_evolution.drill.range.ore" : "stellar_evolution.drill.range.area", n, n);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.box = new EditBox(this.font, cx - BOX_WIDTH / 2, cy - 10, BOX_WIDTH, 20, this.title);
        this.box.setMaxLength(3);
        this.box.setValue(Integer.toString(this.value));
        this.box.setHint(Component.translatable("screen.stellar_evolution.drill_range.hint"));
        addRenderableWidget(this.box);
        setInitialFocus(this.box);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> submit())
                .bounds(cx - 60, cy + 20, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(cx + 2, cy + 20, 58, 20).build());
    }

    /** 提交：解析文本框（非法就沿用当前值），发给服务端后关界面。 */
    private void submit() {
        int parsed;
        try {
            parsed = Integer.parseInt(this.box.getValue().trim());
        } catch (NumberFormatException ignored) {
            parsed = this.value;
        }
        int clamped = Math.max(this.min, Math.min(this.max, parsed));
        ClientPacketDistributor.sendToServer(new DrillRangePayload.Set(this.pos, this.mode, clamped));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.centeredText(this.font, this.title, cx, cy - 68, 0xFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.drill_range.mode", modeName()),
                cx, cy - 54, 0xA0A0A0);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.drill_range.current", unitText(this.value)),
                cx, cy - 40, 0xA0A0A0);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.drill_range.limit", this.min, this.max),
                cx, cy + 48, 0x808080);
        graphics.centeredText(this.font,
                Component.translatable(oreMode()
                        ? "screen.stellar_evolution.drill_range.note.ore"
                        : "screen.stellar_evolution.drill_range.note.area"),
                cx, cy + 62, 0x808080);
    }

    /** 回车 = 提交。 */
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
