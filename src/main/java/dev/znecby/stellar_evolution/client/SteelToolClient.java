package dev.znecby.stellar_evolution.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.equipment.ModeTool;
import dev.znecby.stellar_evolution.equipment.SteelToolFeature;
import dev.znecby.stellar_evolution.equipment.SteelToolModePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 钢制工具的客户端按键：
 * <ul>
 *   <li><strong>Alt 单击（松开时）</strong>＝循环切换模式（无 → 范围 → 连锁 → 时运 → 精准）；</li>
 *   <li><strong>Alt + 右键</strong>＝打开模式配置界面；打开后松开 Alt 不会误切换。</li>
 * </ul>
 * 键位默认左 Alt，可在「控制」里改。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID, value = Dist.CLIENT)
public final class SteelToolClient {
    public static final KeyMapping MODE_KEY = new KeyMapping(
            "key.stellar_evolution.steel_tool_mode",
            InputConstants.KEY_LALT,
            KeyMapping.Category.GAMEPLAY);

    /** 本次按住是否用于打开界面（用于抑制松开时的循环切换）。 */
    private static boolean held;
    private static boolean openedUi;

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(MODE_KEY);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!isModeKey(event)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (minecraft.screen == null && minecraft.player != null) {
                held = true;
                openedUi = false;
            }
            return;
        }
        if (event.getAction() != GLFW.GLFW_RELEASE) {
            return;
        }
        boolean shouldCycle = held && !openedUi && minecraft.screen == null && minecraft.player != null;
        held = false;
        openedUi = false;
        if (shouldCycle) {
            cycleMode();
        }
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || !isModeKeyDown(minecraft)) {
            return;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof ModeTool tool)) {
            return;
        }
        event.setCanceled(true);
        openedUi = true;
        minecraft.setScreen(new SteelToolModeScreen(stack));
    }

    /** 按键映射命中（兼容 Alt 这类修饰键）。 */
    private static boolean isModeKey(InputEvent.Key event) {
        if (MODE_KEY.matches(event.getKeyEvent())) {
            return true;
        }
        return MODE_KEY.getKey().getType() == InputConstants.Type.KEYSYM
                && event.getKey() == MODE_KEY.getKey().getValue();
    }

    /** 按键是否按住（Alt 不一定走普通 KeyMapping 状态，补一层 GLFW 查询）。 */
    private static boolean isModeKeyDown(Minecraft minecraft) {
        if (MODE_KEY.isDown()) {
            return true;
        }
        return MODE_KEY.getKey().getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(minecraft.getWindow(), MODE_KEY.getKey().getValue());
    }

    private static void cycleMode() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof ModeTool tool)) {
            return;
        }
        int next = SteelToolFeature.nextPreset(tool.kind(), tool.features(stack));
        ClientPacketDistributor.sendToServer(new SteelToolModePayload.Set(next));
    }

    private SteelToolClient() {}
}
