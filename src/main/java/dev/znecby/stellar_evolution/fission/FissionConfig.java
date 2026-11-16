package dev.znecby.stellar_evolution.fission;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 裂变线<strong>配置</strong>（《核裂变线预规划蓝图》§4.4）——熔毁的严重程度。
 *
 * <p>口径（蓝图 §4.4）：熔毁<strong>默认只「停机 + 泄漏」</strong>（可恢复）；
 * 分层球形爆炸做成<strong>可配置的困难模式</strong>，默认关闭。
 * 用 NeoForge 自带的 {@link ModConfigSpec} 注册，不依赖外部前置库。
 */
public final class FissionConfig {
    /** 熔毁时是否触发分层球形爆炸（困难模式）。 */
    public static final ModConfigSpec.BooleanValue MELTDOWN_EXPLOSION;
    /** 熔毁爆炸半径（格）。 */
    public static final ModConfigSpec.IntValue MELTDOWN_EXPLOSION_RADIUS;
    /** 是否启用氙毒（§7.8.4，默认关闭）。 */
    public static final ModConfigSpec.BooleanValue XENON_POISONING;

    private static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("meltdown");
        MELTDOWN_EXPLOSION = builder
                .comment("熔毁时是否触发分层球形爆炸（困难模式）。关闭时熔毁只停机，不破坏地形。")
                .define("explosion", false);
        MELTDOWN_EXPLOSION_RADIUS = builder
                .comment("熔毁爆炸半径（格）。仅在开启 explosion 时生效。")
                .defineInRange("explosionRadius", 16, 4, 48);
        builder.pop();
        builder.push("reactor");
        XENON_POISONING = builder
                .comment("是否启用氙毒（停堆后碘坑、重启困难）。默认关闭。")
                .define("xenonPoisoning", false);
        builder.pop();
        SPEC = builder.build();
    }

    /** 在模组构造器里注册公共配置。 */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    private FissionConfig() {}
}
