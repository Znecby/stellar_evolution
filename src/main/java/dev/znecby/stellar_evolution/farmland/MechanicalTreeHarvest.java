package dev.znecby.stellar_evolution.farmland;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <strong>机械草地</strong>的「树木规模表」+ 收获结算。
 *
 * <p>「长成一棵树」不去跑地形特征，而是查一张<strong>固定规模表</strong>：每种树苗 → （原木数、树叶数、该树种原木、是否掉苹果）。
 * 结算分两路：
 * <ul>
 *   <li><strong>原木（主体产出）</strong>：按表里的原木数，逐个产出<strong>该树种的原木</strong>（种橡木 → 橡木原木）——
 *       所以每次收获里<strong>原木最多</strong>；</li>
 *   <li><strong>树叶（少量附加）</strong>：按表里的树叶数逐个滚一次，<strong>大多数什么都不掉</strong>
 *       （{@value #WEIGHT_NOTHING} 权重），少数给 <strong>树苗 / 木棍 / 苹果</strong>（权重各 {@value #WEIGHT_SAPLING}）；
 *       其中<strong>苹果只有「橡木类」会掉</strong>（橡木 / 深色橡木 / 苍白橡木 / 杜鹃）。</li>
 * </ul>
 *
 * <p><strong>全程不碰任何真实方块</strong>（不生成也不拆除），所以零卡顿、世界里也不会凭空多出一棵树。
 * <p>树苗取「种下去的那一种」（{@code sapling.getBlock().asItem()}）—— 种橡木只掉橡木树苗，不会串树。
 */
public final class MechanicalTreeHarvest {
    /** 树叶结算时「什么都不掉」的权重（占绝对大头，保证原木才是最多的产出、树苗/木棍很少）。 */
    private static final int WEIGHT_NOTHING = 30;
    /** 树叶结算时「同种树苗」的权重。 */
    private static final int WEIGHT_SAPLING = 1;
    /** 树叶结算时「木棍」的权重。 */
    private static final int WEIGHT_STICK = 1;
    /** 树叶结算时「苹果」的权重（仅「橡木类」才有这一项）。 */
    private static final int WEIGHT_APPLE = 1;

    /** 一次「长成」的规模：原木数 + 树叶数 + 该树种的原木物品 + 是否掉苹果（橡木类）。 */
    public record Tree(int logs, int leaves, Item log, boolean apple) {
        public int blocks() {
            return logs + leaves;
        }
    }

    /** 表里没有的树苗（含模组树苗）用这个默认规模。 */
    private static final Tree DEFAULT = new Tree(6, 40, Items.OAK_LOG, true);

    /** 树苗方块 → 规模（近似值；想调直接改这里）。 */
    private static final Map<Block, Tree> TABLE = new HashMap<>();

    static {
        TABLE.put(Blocks.OAK_SAPLING, new Tree(6, 40, Items.OAK_LOG, true));
        TABLE.put(Blocks.DARK_OAK_SAPLING, new Tree(7, 60, Items.DARK_OAK_LOG, true));
        TABLE.put(Blocks.PALE_OAK_SAPLING, new Tree(7, 60, Items.PALE_OAK_LOG, true));
        TABLE.put(Blocks.AZALEA, new Tree(6, 40, Items.OAK_LOG, true));
        TABLE.put(Blocks.FLOWERING_AZALEA, new Tree(6, 40, Items.OAK_LOG, true));
        TABLE.put(Blocks.SPRUCE_SAPLING, new Tree(8, 60, Items.SPRUCE_LOG, false));
        TABLE.put(Blocks.BIRCH_SAPLING, new Tree(7, 45, Items.BIRCH_LOG, false));
        TABLE.put(Blocks.JUNGLE_SAPLING, new Tree(12, 80, Items.JUNGLE_LOG, false));
        TABLE.put(Blocks.ACACIA_SAPLING, new Tree(6, 40, Items.ACACIA_LOG, false));
        TABLE.put(Blocks.CHERRY_SAPLING, new Tree(6, 50, Items.CHERRY_LOG, false));
        TABLE.put(Blocks.MANGROVE_PROPAGULE, new Tree(6, 50, Items.MANGROVE_LOG, false));
    }

    /** 该方块是不是树苗（机械草地「能种树」的判据）。 */
    public static boolean isTree(BlockState state) {
        return state.getBlock() instanceof SaplingBlock;
    }

    /** 该树苗「长成一次」的规模（查表；未知 / 模组树苗用 {@link #DEFAULT}）。 */
    public static Tree treeFor(BlockState sapling) {
        return TABLE.getOrDefault(sapling.getBlock(), DEFAULT);
    }

    /**
     * 结算收获：<strong>原木</strong>按规模表逐个产出该树种原木（主体）；<strong>树叶</strong>逐个滚一次，
     * 大多数不掉、少数给「同种树苗 / 木棍 / 苹果（仅橡木类）」。返回值已<strong>合并同类堆</strong>。
     */
    public static List<ItemStack> rollHarvest(BlockState sample, Tree tree, RandomSource random) {
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < tree.logs(); i++) {
            ItemStack log = new ItemStack(tree.log());
            if (!log.isEmpty()) {
                drops.add(log);
            }
        }
        Item sapling = sample.getBlock().asItem();
        int total = WEIGHT_NOTHING + WEIGHT_SAPLING + WEIGHT_STICK + (tree.apple() ? WEIGHT_APPLE : 0);
        for (int i = 0; i < tree.leaves(); i++) {
            int roll = random.nextInt(total);
            if (roll < WEIGHT_NOTHING) {
                continue;   // 大多数树叶什么都不掉
            }
            roll -= WEIGHT_NOTHING;
            ItemStack out = null;
            if (roll < WEIGHT_SAPLING) {
                out = new ItemStack(sapling);
            } else if (roll < WEIGHT_SAPLING + WEIGHT_STICK) {
                out = new ItemStack(Items.STICK);
            } else if (tree.apple()) {
                out = new ItemStack(Items.APPLE);   // 只有橡木类才有苹果
            }
            if (out != null && !out.isEmpty()) {
                drops.add(out);
            }
        }
        return compact(drops);
    }

    /** 按物品合并同类堆，再按最大堆叠切回去。 */
    private static List<ItemStack> compact(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : in) {
            if (stack.isEmpty()) {
                continue;
            }
            int remaining = stack.getCount();
            int max = stack.getMaxStackSize();
            for (ItemStack merged : out) {
                if (remaining <= 0) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(merged, stack) && merged.getCount() < max) {
                    int move = Math.min(max - merged.getCount(), remaining);
                    merged.grow(move);
                    remaining -= move;
                }
            }
            while (remaining > 0) {
                int put = Math.min(max, remaining);
                out.add(stack.copyWithCount(put));
                remaining -= put;
            }
        }
        return out;
    }

    private MechanicalTreeHarvest() {}
}
