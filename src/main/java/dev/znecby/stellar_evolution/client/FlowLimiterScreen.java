package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.network.FlowLimitPayload;
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
 * <strong>限流管</strong>的数值输入界面：原版的文本框（{@link EditBox}）+ 原版按钮，顶上一行说明。
 *
 * <p>打开时框里预填的就是<strong>输入端网络当前的热流率</strong>（没设过设定值的时候）——
 * 直接回车 / 点「完成」就等于「原样放行」；改成别的数就是给下游网络设一个上限。
 * 填 {@code 0}（或清空）= 跟随输入端。提交走 {@link FlowLimitPayload.Set}。
 */
public class FlowLimiterScreen extends Screen {
    /** 文本框宽度。 */
    private static final int BOX_WIDTH = 110;

    private final BlockPos pos;
    private final int limit;
    private final double upstreamFlow;
    private EditBox box;

    public FlowLimiterScreen(BlockPos pos, int limit, double upstreamFlow) {
        super(Component.translatable("screen.stellar_evolution.flow_limiter"));
        this.pos = pos;
        this.limit = limit;
        this.upstreamFlow = upstreamFlow;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.box = new EditBox(this.font, cx - BOX_WIDTH / 2, cy - 10, BOX_WIDTH, 20, this.title);
        this.box.setMaxLength(9);
        this.box.setValue(Integer.toString(this.limit > 0 ? this.limit : roundedUpstream()));
        this.box.setHint(Component.translatable("screen.stellar_evolution.flow_limiter.hint"));
        addRenderableWidget(this.box);
        setInitialFocus(this.box);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> submit())
                .bounds(cx - 60, cy + 20, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(cx + 2, cy + 20, 58, 20).build());
    }

    /** 提交：解析文本框（空 / 非数字 / 0 都当「跟随输入端」），发给服务端后关界面。 */
    private void submit() {
        int value;
        try {
            value = Integer.parseInt(this.box.getValue().trim());
        } catch (NumberFormatException ignored) {
            value = 0;
        }
        value = Math.max(0, Math.min(1_000_000, value));
        ClientPacketDistributor.sendToServer(new FlowLimitPayload.Set(this.pos, value));
        onClose();
    }

    private int roundedUpstream() {
        return (int) Math.round(this.upstreamFlow);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.centeredText(this.font, this.title, cx, cy - 50, 0xFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.flow_limiter.upstream", roundedUpstream()),
                cx, cy - 36, 0xA0A0A0);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.flow_limiter.note"), cx, cy + 48, 0x808080);
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
