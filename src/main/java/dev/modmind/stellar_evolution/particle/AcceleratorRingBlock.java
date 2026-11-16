package dev.modmind.stellar_evolution.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * <strong>加速环段</strong>（三期 E4）：单方块、无方块实体、不 tick。
 *
 * <p>贴着对撞机放置即可被扫描计数（上限 16）；数量达到配方门槛后，对撞机解锁
 * “批量奇异物质 / 批量反物质”配方。它本身只负责“规模”，逻辑都在对撞机侧。
 */
public class AcceleratorRingBlock extends Block {
    public AcceleratorRingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends AcceleratorRingBlock> codec() {
        return simpleCodec(AcceleratorRingBlock::new);
    }
}
