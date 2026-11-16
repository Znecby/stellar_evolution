package dev.modmind.stellar_evolution.mixin;

import dev.modmind.stellar_evolution.farmland.MechanicalGrassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让<strong>种在机械草地上的树苗永远不生长</strong>（无论随机刻还是骨粉）。
 *
 * <p>原版 {@link SaplingBlock} 的「长大」入口是 {@code advanceTree(ServerLevel, BlockPos, BlockState, RandomSource)}，
 * 随机刻与骨粉（{@code performBonemeal}）都走它。这里在方法开头判断：只要<strong>下方是机械草地</strong>就直接取消 ——
 * 于是树苗既不会长成真树，也不会把机械草顶成泥土（机械草地自己用「虚拟长树」那套来产出）。
 * <p>即使用户对着树苗打骨粉也无效（同样被拦），符合「无论什么情况都不长大」。
 */
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockMixin {
    @Inject(method = "advanceTree", at = @At("HEAD"), cancellable = true)
    private void stellar_evolution$blockGrowthOnMechanicalGrass(ServerLevel level, BlockPos pos, BlockState state,
                                                                RandomSource random, CallbackInfo ci) {
        if (level.getBlockState(pos.below()).getBlock() instanceof MechanicalGrassBlock) {
            ci.cancel();
        }
    }
}
