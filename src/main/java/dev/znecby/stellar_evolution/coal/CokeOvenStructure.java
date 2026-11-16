package dev.znecby.stellar_evolution.coal;

import dev.znecby.stellar_evolution.hatch.HatchBlocks;
import dev.znecby.stellar_evolution.multiblock.MultiblockPattern;
import dev.znecby.stellar_evolution.multiblock.MultiblockPatternBuilder;
import net.minecraft.world.level.block.Blocks;

/**
 * 焦化炉固定图案：<strong>3 × 3 × 4</strong>（3 层砖块 + 1 层砖半砖）。
 *
 * <pre>
 * 第 4 层（y=+2）  砖半砖围一圈，中心留空 ──── 烟囱口
 * 第 3 层（y=+1）  砖块顶面，中心留空
 * 第 2 层（y= 0）  侧面正中心：正面 = 控制器，其余三面 = 机械物流仓；中心中空
 * 第 1 层（y=-1）  砖块底面
 * </pre>
 *
 * <p>外壳全部是<strong>原版红砖块</strong>（{@code minecraft:bricks}）与<strong>砖半砖</strong>
 * （{@code minecraft:brick_slab}），玩家自备；热室只做控制器。
 *
 * <p>图案按<strong>正面朝北</strong>定义，控制器在局部 (0,0,0)；局部 +Z 指向炉体内部。
 */
public final class CokeOvenStructure {
    private CokeOvenStructure() {}

    public static MultiblockPattern create() {
        return new MultiblockPatternBuilder("coke_oven")
                .whereController('C', CokeOvenBlocks.CONTROLLER.get())
                .where('B', Blocks.BRICKS)
                .where('S', Blocks.BRICK_SLAB)
                .where('P', HatchBlocks.LOGISTICS.get())
                .whereAir('.')
                .layer(
                        "BBB",
                        "BBB",
                        "BBB"
                )
                .layer(
                        "BCB",
                        "P.P",
                        "BPB"
                )
                .layer(
                        "BBB",
                        "B.B",
                        "BBB"
                )
                .layer(
                        "SSS",
                        "S.S",
                        "SSS"
                )
                .build();
    }
}
