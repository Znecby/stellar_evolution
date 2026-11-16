package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.pipe.CopperPipeBlock;
import java.util.Set;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 铜管的<strong>告警着色</strong>：所在热压差网络一旦<strong>超载</strong>，管子按闪烁相位染红。
 *
 * <p>26.x 里 {@code BlockColor} 已被 {@code BlockTintSource} 取代（{@code RegisterColorHandlersEvent.BlockTintSources}）。
 * 这里只在「超载 + 亮相位」时返回红色，其余一律白（白色 = 原样，不影响铜的原色）。
 *
 * <p><strong>颜色是烘焙进区块网格的</strong>，所以闪烁靠的是方块状态真的变化
 * （{@link CopperPipeBlock#OVERLOADED} / {@link CopperPipeBlock#BLINKING} 由服务端的计划刻翻转），
 * {@link #relevantProperties()} 把这两个属性报给网格缓存，状态一变就重新烘焙 —— 于是看到"闪"。
 * 想按时间在渲染里插值是不行的：网格不重建就不会重算顶点色。
 */
public final class ThermalPipeTintSource implements BlockTintSource {
    /** 单例（无状态）。 */
    public static final ThermalPipeTintSource INSTANCE = new ThermalPipeTintSource();

    /** 原色（不染色）。 */
    private static final int NORMAL = 0xFFFFFF;
    /** 告警红：与铜的橙褐底相乘后呈暗红，足够刺眼。 */
    private static final int ALARM = 0xFF3A3A;

    /** 无世界上下文（物品栏 / 掉落物粒子）：永远原色。 */
    @Override
    public int color(BlockState state) {
        return NORMAL;
    }

    /** 世界里：超载且处于亮相位时染红。 */
    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter getter, BlockPos pos) {
        if (state.getValue(CopperPipeBlock.OVERLOADED) && state.getValue(CopperPipeBlock.BLINKING)) {
            return ALARM;
        }
        return NORMAL;
    }

    /** 只有这两个属性会影响颜色 —— 报给网格缓存，避免连接变化等无关状态触发多余的重新烘焙。 */
    @Override
    public Set<Property<?>> relevantProperties() {
        return Set.of(CopperPipeBlock.OVERLOADED, CopperPipeBlock.BLINKING);
    }

    private ThermalPipeTintSource() {}
}
