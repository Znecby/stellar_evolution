package dev.modmind.stellar_evolution.fission;

/**
 * 「有可动部件」的裂变机器方块实体：渲染器只认这个接口，三台不同方块实体（燃料机器 / 反应堆 / 汽轮机）共用一套渲染。
 */
public interface FissionAnimated {

    /** 已运行时长（tick，含 partialTick 插值）——各部件按它推算各自转角。 */
    float age(float partialTick);
}
