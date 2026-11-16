package dev.modmind.stellar_evolution.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * <strong>同位素电源（RTG）</strong>方块：单方块、恒功率热压差生成端（见
 * {@link IsotopeGeneratorBlockEntity}）。自发光，方便在基地外找到。
 */
public class IsotopeGeneratorBlock extends BaseEntityBlock {
    public static final MapCodec<IsotopeGeneratorBlock> CODEC = simpleCodec(IsotopeGeneratorBlock::new);

    public IsotopeGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IsotopeGeneratorBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
