package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.massdriver.MassDriverCosts;
import dev.znecby.stellar_evolution.network.MassDriverDimensionPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 质量投射轨道炮的<strong>维度选择</strong>界面：原版按钮列表（与钢制工具配置界面同款），
 * 每个可选维度一行，显示消耗的势能；势能不足的按钮不可点。
 *
 * <p>选中后客户端只发 {@link MassDriverDimensionPayload.Select}，扣势能与末影之眼转化都在服务端完成。
 */
public class MassDriverDimensionScreen extends Screen {
    private static final int BUTTON_WIDTH = 240;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private final BlockPos pos;
    private final double potential;
    private final List<MassDriverDimensionPayload.Entry> entries;

    public MassDriverDimensionScreen(BlockPos pos, double potential,
                                     List<MassDriverDimensionPayload.Entry> entries) {
        super(Component.translatable("screen.stellar_evolution.mass_driver_dimension"));
        this.pos = pos;
        this.potential = potential;
        this.entries = entries;
    }

    private int top() {
        int needed = 34 + this.entries.size() * (BUTTON_HEIGHT + GAP) + 32;
        return Math.max(12, (this.height - needed) / 2);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = top() + 34;
        for (MassDriverDimensionPayload.Entry entry : this.entries) {
            Button button = addRenderableWidget(Button.builder(
                            Component.translatable("screen.stellar_evolution.mass_driver_dimension.entry",
                                    MassDriverCosts.displayName(entry.id()), entry.cost()),
                            pressed -> select(entry.id()))
                    .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            button.active = this.potential >= entry.cost();
            y += BUTTON_HEIGHT + GAP;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(centerX - 50, y + 8, 100, BUTTON_HEIGHT)
                .build());
    }

    private void select(Identifier dimension) {
        ClientPacketDistributor.sendToServer(new MassDriverDimensionPayload.Select(this.pos, dimension));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = top();
        graphics.centeredText(this.font, this.title, centerX, y, 0xFFFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.mass_driver_dimension.potential",
                        (long) Math.floor(this.potential)),
                centerX, y + 14, 0xFF80FF80);
        graphics.centeredText(this.font,
                Component.translatable("screen.stellar_evolution.mass_driver_dimension.note"),
                centerX, this.height - 24, 0xFF808080);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
