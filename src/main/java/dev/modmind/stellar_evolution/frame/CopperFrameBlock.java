package dev.modmind.stellar_evolution.frame;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * 铜框架：纯外观的「立方体线框」方块，版式取自存档里的 {@code copper_frame} 结构
 * （{@code 3 × 3 × 3}，调色板只有 {@code copper_block} 与 {@code air}，
 * 实际是 <strong>20 个铜块 = 8 个角 + 12 条棱</strong>，也就是一个空心的立方体「笼子」）。
 * <p>
 * 与 {@link dev.modmind.stellar_evolution.pipe.CopperPipeBlock 铜管}同一路数，只有外观：
 * <ul>
 *   <li><strong>无状态</strong>：没有连接属性，也就没有 {@code blockstate} 变体；
 *       相邻两块边框天然对齐（棱都落在方块边界），摆成一排/一摞会自动接成连续框架。</li>
 *   <li><strong>尺寸</strong>：12 条棱都是 4px 见方（整方块的 1/4，与铜管管径一致），居中；
 *       但<strong>碰撞箱与选择框仍是整方块</strong>（{@code noOcclusion()} 只关掉「遮光 / 让邻居消面」，
 *       不影响默认的整方块 {@code getShape} / {@code getCollisionShape}）。</li>
 *   <li><strong>贴图</strong>：复用原版铜块（{@code minecraft:block/copper_block}），不新增贴图。</li>
 * </ul>
 * 模型见 {@code assets/stellar_evolution/models/block/copper_frame.json}：
 * 把线框拆成 <strong>4 根竖棱（从底贯到顶）+ 上下各 4 根横梁</strong>，共 12 个 element，
 * 互相只在面处相接、不重叠，避免共面 z-fighting。
 */
public class CopperFrameBlock extends Block {
    public CopperFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CopperFrameBlock> codec() {
        return simpleCodec(CopperFrameBlock::new);
    }
}
