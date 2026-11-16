package dev.znecby.stellar_evolution.large;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * 大型机器的<strong>方块侧</strong>接口：让框架能从「机器占的任意一格」（主方块或填充格）
 * 反查出机器原点，而不认识具体的机器方块类。
 *
 * <p>机器为 2×2×2 时，主方块与 7 个填充格都要实现本接口；反查结果交给
 * {@link LargeMachines#originOf} 使用。
 */
public interface LargeMachineBlock {
    /**
     * 这格属于哪台大型机器的原点；不属于则 {@code null}。
     *
     * @param level 世界
     * @param pos   机器占的某一格（主方块 / 填充格）
     */
    @Nullable
    BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos);
}
