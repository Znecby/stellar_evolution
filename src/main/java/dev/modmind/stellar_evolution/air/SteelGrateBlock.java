package dev.modmind.stellar_evolution.air;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * 不锈钢格栅：外观照原版铜格栅，作为机械管网络的<strong>虚拟液态空气源</strong>。
 *
 * <p>本身无状态、无方块实体；供气逻辑全部在
 * {@link dev.modmind.stellar_evolution.tank.GrateSource} 里按方块 id 识别。
 * 只有主世界、且至少一面贴着空气方块时才供气。
 */
public class SteelGrateBlock extends Block {
    public SteelGrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SteelGrateBlock> codec() {
        return simpleCodec(SteelGrateBlock::new);
    }
}
