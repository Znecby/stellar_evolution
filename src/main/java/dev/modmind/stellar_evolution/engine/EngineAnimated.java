package dev.modmind.stellar_evolution.engine;

/**
 * 「有可动部件」的燃烧类发电机组方块实体：渲染器只认这个接口，
 * 三台机组（内燃 / 燃气 / 火箭）共用一套渲染（照 {@code fission.FissionAnimated} 口径）。
 */
public interface EngineAnimated {

    /** 已运行时长（tick，含 partialTick 插值）——各转子按它推算各自转角。 */
    float age(float partialTick);
}
