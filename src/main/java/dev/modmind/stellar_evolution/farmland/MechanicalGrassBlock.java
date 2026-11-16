package dev.modmind.stellar_evolution.farmland;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <strong>机械草地</strong>：铜框架 + 草方块材质的一台设备（不掉落、不受重力），底下连铜块即接入热压差网络
 * （<strong>32 DP @ 8 HP</strong>：基础热阻 4 × 网络热流率 8）。<strong>专用种树</strong>：
 * <ul>
 *   <li>上面种<strong>树苗</strong>（任意树种），通电后每个周期（200t / 倍速）以
 *       <strong>25% 起步、每次失败 +5%</strong>的累计概率尝试让它「直接长成大树」，成功即重置回 25%；</li>
 *   <li>长成后按<strong>固定规模表</strong>（每种树苗的原木 / 树叶数，见 {@link MechanicalTreeHarvest}）逐块结算收获
 *       （同种树苗 / 木棍 / 苹果）收进<strong>底部容器</strong>；</li>
 *   <li><strong>必须底部有容器</strong>才工作（缺容器时 Jade 显示「底部缺失容器」）；</li>
 *   <li><strong>上面的树苗永不自己长大</strong>：Mixin 拦掉了原版树苗的生长（随机刻 / 骨粉都无效），
 *       所以不会长成真树、也不会把本方块顶成泥土（见 {@code mixin.SaplingBlockMixin}）。</li>
 * </ul>
 * 全程<strong>不写真实方块</strong>（不生成也不拆除树），所以不会卡顿。
 */
public class MechanicalGrassBlock extends MechanicalSoilBlock {
    public static final MapCodec<MechanicalGrassBlock> CODEC = simpleCodec(MechanicalGrassBlock::new);

    public MechanicalGrassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<MechanicalGrassBlock> codec() {
        return CODEC;
    }

    /** 树苗能种上来：树苗原版走 {@code supports_vegetation}，机械草地不在其中，这里直接放行树苗。 */
    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soilPosition, Direction facing, BlockState plant) {
        if (facing == Direction.UP && plant.getBlock() instanceof SaplingBlock) {
            return TriState.TRUE;
        }
        return TriState.DEFAULT;
    }
}
