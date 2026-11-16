package dev.znecby.stellar_evolution.dev;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 开发工具配置。
 *
 * <p>开发者模式默认<strong>只在开发环境开启</strong>：{@code runClient} 下为 true，
 * 发布 jar 下为 false。发布版的快速配方书不会出现在创造页，也无法打开记录界面。
 */
public final class DevConfig {
    /** 是否启用开发工具（快速配方书等）。 */
    public static final ModConfigSpec.BooleanValue DEVELOPER_MODE;

    private static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DEVELOPER_MODE = builder
                .comment("Enable developer-only tools such as the quick recipe book.",
                        "Defaults to true in the development environment and false in production jars.")
                .define("developerMode", !FMLEnvironment.isProduction());
        SPEC = builder.build();
    }

    /** 当前是否处于开发者模式。 */
    public static boolean developerMode() {
        return DEVELOPER_MODE.get();
    }

    /** 在模组构造器里注册开发工具配置（独立文件名，避免和裂变配置混在一起）。 */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "stellar_evolution-dev.toml");
    }

    private DevConfig() {}
}
