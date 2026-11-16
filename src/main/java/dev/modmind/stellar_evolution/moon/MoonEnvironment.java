package dev.modmind.stellar_evolution.moon;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 月球环境：只做「原版属性」能表达的部分，不引入氧气 / 温度 / 辐射。
 *
 * <p>重力与坠落伤害都用原版 {@link Attributes} 修饰符：
 * 进入月球时加，离开时移除；登录 / 重生时按当前维度重新校正。
 * 氧气首版不做（见月球线讨论），所以这里没有呼吸 / 窒息逻辑。
 */
public final class MoonEnvironment {
    public static final ResourceKey<Level> MOON =
            ResourceKey.create(Registries.DIMENSION, StellarEvolution.id("moon"));

    private static final Identifier GRAVITY_ID = StellarEvolution.id("moon_gravity");
    private static final Identifier FALL_DAMAGE_ID = StellarEvolution.id("moon_fall_damage");

    /**
     * 原版重力基准 0.08；月球 1.622 m/s² ÷ 地球 9.8 m/s² ≈ 0.1655，
     * 因此用 ADD_MULTIPLIED_BASE -0.8345，得到约 0.0132。
     */
    private static final AttributeModifier MOON_GRAVITY =
            new AttributeModifier(GRAVITY_ID, -0.8345, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    /** 低重力下落更慢，但原版坠落伤害按距离算；这里把伤害压到 30%。 */
    private static final AttributeModifier MOON_FALL_DAMAGE =
            new AttributeModifier(FALL_DAMAGE_ID, -0.7, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MoonEnvironment::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(MoonEnvironment::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(MoonEnvironment::onRespawn);
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        sync(player, event.getTo());
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        sync(player, player.level().dimension());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        sync(player, player.level().dimension());
    }

    /** 幂等：先移除本模组的修饰符，再按目标维度决定是否加回。 */
    private static void sync(Player player, ResourceKey<Level> dimension) {
        boolean onMoon = dimension.equals(MOON);

        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(GRAVITY_ID);
            if (onMoon) {
                gravity.addTransientModifier(MOON_GRAVITY);
            }
        }

        AttributeInstance fallDamage = player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
        if (fallDamage != null) {
            fallDamage.removeModifier(FALL_DAMAGE_ID);
            if (onMoon) {
                fallDamage.addTransientModifier(MOON_FALL_DAMAGE);
            }
        }
    }

    private MoonEnvironment() {}
}
