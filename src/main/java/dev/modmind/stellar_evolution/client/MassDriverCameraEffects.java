package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 质量炮发射 / 抵达的<strong>镜头冲击</strong>：在 {@link ViewportEvent.ComputeCameraAngles} 里叠加随机的
 * 偏航 / 俯仰 / 翻滚抖动，并在 {@link ViewportEvent.ComputeFov} 里做一次 FOV 冲击；强度随时间平方衰减。
 *
 * <p>由 {@link dev.modmind.stellar_evolution.network.MassDriverShakePayload} 触发；纯客户端，不影响服务端。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID, value = Dist.CLIENT)
public final class MassDriverCameraEffects {
    private static final RandomSource RANDOM = RandomSource.create();

    private static int ticksLeft;
    private static int ticksTotal = 1;
    private static float strength;

    /** 触发一次冲击：同 tick 内多次触发取较强 / 较长的那次。 */
    public static void shake(int ticks, float intensity) {
        int duration = Math.max(1, ticks);
        ticksTotal = Math.max(ticksTotal, duration);
        ticksLeft = Math.max(ticksLeft, duration);
        strength = Math.max(strength, intensity);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft == 0) {
                strength = 0.0F;
                ticksTotal = 1;
            }
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ticksLeft <= 0 || strength <= 0.0F) {
            return;
        }
        float fade = ticksLeft / (float) ticksTotal;
        float amplitude = strength * fade * fade;
        event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5F) * amplitude);
        event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5F) * amplitude);
        event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5F) * amplitude * 0.7F);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ticksLeft <= 0 || strength <= 0.0F) {
            return;
        }
        float fade = ticksLeft / (float) ticksTotal;
        event.setFOV(event.getFOV() + strength * fade * 0.6F);
    }

    private MassDriverCameraEffects() {}
}
