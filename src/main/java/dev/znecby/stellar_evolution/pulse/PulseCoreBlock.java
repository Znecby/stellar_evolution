package dev.znecby.stellar_evolution.pulse;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * 脉冲核心（pulse core）：纯外观方块，版式取自存档结构 {@code pulse_core.nbt}（{@code 5 × 5 × 5}）。
 * <p>
 * 结构由三种原版方块拼成，缩进 1 格后仍是这三种贴图（{@code tools/gen_pulse_core.ps1} 由单元表生成模型）：
 * <ul>
 *   <li><strong>红石块</strong>（{@code redstone_block}）：8 个「角」（顶/底 × 四个 xz 角）；</li>
 *   <li><strong>铜块</strong>（{@code copper_block}）：顶/底的**风车环**（每层 4 角红石 + 8 格铜），
 *       外加中层的**中心十字**（中心 + 四个面邻居）；</li>
 *   <li><strong>切制铜楼梯</strong>（{@code cut_copper}）：上下两层各 8 处，构成一圈**螺旋桨叶**
 *       （下层 4 向 half=bottom、上层 4 向 half=top，合计 8 种朝向，共 16 处）。</li>
 * </ul>
 * 无方块实体、无方块状态、无功能：只按结构把 79 个 element 拼出来。
 * 每个结构格 = 16/5 = 3.2px；楼梯按 facing + half 展开成两块板（与原版 stairs 模型同口径）。
 */
public class PulseCoreBlock extends Block {
    public PulseCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PulseCoreBlock> codec() {
        return simpleCodec(PulseCoreBlock::new);
    }
}
