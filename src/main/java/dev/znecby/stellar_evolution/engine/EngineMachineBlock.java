package dev.znecby.stellar_evolution.engine;

/**
 * 三台燃烧类发电机组「主方块」的标记接口。
 * <p>
 * 只用于框架判归属（{@link EngineMachineSupport#findMachine} 反查原点），
 * 不承载任何机制 —— 具体行为在各自的方块 / 方块实体里。
 */
public interface EngineMachineBlock {
}
