package dev.znecby.stellar_evolution.thermal;

import dev.znecby.stellar_evolution.farmland.MechanicalGrassBlock;
import dev.znecby.stellar_evolution.farmland.MechanicalSoil;
import dev.znecby.stellar_evolution.farmland.MechanicalSoilBlockEntity;
import dev.znecby.stellar_evolution.large.LargeMachines;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 热压差网络里<strong>「铜制受热件」</strong>的识别、数值与共享工具：铜块是网络与消耗端之间的<strong>中间导体</strong>，
 * 消耗端必须<strong>贴着铜块</strong>接入。
 *
 * <p>拓扑：
 * <pre>
 *   引擎（生成端）—— 铜管 —— 铜块 —— 消耗端
 *                              └ 相邻的铜块互相导通，整组视作同一节点：
 *                                只要组里有一格接上了铜管（或热源），组上所有消耗端都能工作。
 * </pre>
 * <ul>
 *   <li><strong>铜块</strong>（{@code minecraft:copper} 标签，8 档含氧化 / 打蜡）：既不生成也不消耗，
 *       在网络里当<strong>导体</strong>用；普通消耗端不能直接连铜管，必须贴铜块。
 *       大机器消耗端是例外：可经<strong>底排铜管</strong>直接接入（见 {@code ThermalNetwork}）。</li>
 *   <li><strong>消耗端</strong>（普通消耗端都要贴铜块）：
 *     <ul>
 *       <li>熔炉 / 烟熏炉 / 高炉（原版烹饪方块）—— <strong>网络能量代替燃料</strong>，按各自配方烹饪；
 *          两者由 {@link CopperHeatDriver} 外部驱动（方块实体无自建 ticker）；</li>
 *       <li>木桶（{@code minecraft:barrel}）—— 钓鱼产出；同样由 {@link CopperHeatDriver} 外部驱动；</li>
 *       <li>机械土壤（机械耕地 / 沙地 / 灵魂沙地，{@link MechanicalSoil}）—— 催熟 / 自动收获，它<strong>自带方块实体</strong>自驱。</li>
 *     </ul></li>
 * </ul>
 *
 * <p><strong>注意 · 铜箱子</strong>（{@code minecraft:copper_chests}）：<strong>常规形态不是消耗端</strong> ——
 * 它是<strong>大机器物品容器</strong>（见 {@code large} 包，机械管系接铜箱子）。只有<strong>底部垫铜块</strong>
 * 组成的<strong>「热室」</strong>结构才算消耗端（见 {@link HotChamber}）：热阻 {@code HotChamber.RESISTANCE}、
 * 走<strong>单独的配方类型</strong> {@link HotChamberRecipe}（只吃物品）。
 *
 * <p>数值口径：每台消耗端基础热阻 {@value #RESISTANCE}、基准热流率 {@value #BASE_FLOW_RATE} HP/t ——
 * 于是网络热流率为 64 HP/t（1.0 倍速）时占用 {@code 128 DP}。
 */
public final class CopperHeat {
    /** 每台消耗端的基础热阻（无量纲，占用 = 本值 × 网络热流率）。 */
    public static final double RESISTANCE = 2.0D;
    /** 机械草地的基础热阻：4 ⇒ 网络热流率 64 时占用 **256 DP**（比其它消耗端费一倍）。 */
    public static final double GRASS_RESISTANCE = 4.0D;
    /** 每台消耗端的基准热流率（HP/t）：达到 1.0 倍速所需的网络热流率（2026-11-01 由 8 上调到 64）。 */
    public static final double BASE_FLOW_RATE = 64.0D;

    /** 原版铜块方块标签（{@code minecraft:copper}）。 */
    public static final TagKey<Block> COPPER_BLOCKS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "copper"));
    /** 原版铜箱子方块标签（{@code minecraft:copper_chests}）。 */
    public static final TagKey<Block> COPPER_CHESTS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "copper_chests"));

    /** {@code pos} 这格是不是铜块标签里的方块（网络的中间导体）。 */
    public static boolean isCopperBlock(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(COPPER_BLOCKS);
    }

    /**
     * {@code pos} 这格是不是<strong>受热容器</strong> —— 木桶（钓鱼产出）。
     * <p>只有木桶没有自建 ticker、靠 {@link CopperHeatDriver} 外部驱动；铜箱子（常规形态）也不是消耗端
     * —— 它是大机器物品容器，只有「热室」结构（底部垫铜块）才另走 {@link HotChamber}。
     */
    public static boolean isHeatContainer(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.BARREL);
    }

    /** {@code pos} 这格是不是<strong>原版烹饪方块</strong>（熔炉 / 烟熏炉 / 高炉）——「网络能量代替燃料」的消耗端。 */
    public static boolean isCooker(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof AbstractFurnaceBlock;
    }

    /** {@code pos} 这格是不是<strong>机械土壤</strong>（机械耕地 / 沙地 / 灵魂沙地，自带方块实体、自驱）。 */
    public static boolean isMechanicalSoil(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MechanicalSoil;
    }

    /** 热压差网络里的<strong>消耗端</strong>：木桶 / 熔炉·烟熏炉·高炉 / 机械土壤。 */
    public static boolean isConsumerDevice(BlockGetter level, BlockPos pos) {
        return isHeatContainer(level, pos) || isCooker(level, pos) || isMechanicalSoil(level, pos);
    }

    /** 该格消耗端的基础热阻：机械草地 {@value #GRASS_RESISTANCE}、其余 {@value #RESISTANCE}。 */
    public static double resistanceAt(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MechanicalGrassBlock ? GRASS_RESISTANCE : RESISTANCE;
    }

    /**
     * 该格「消耗端设备」达到<strong>满速（每刻完成一次）</strong>所需的<strong>热流率</strong>（HP/t）；
     * 返回 {@code 0} 表示不封顶。
     * <p>
     * 用于 Jade：消耗端第 2 行「当前热流率」要<strong>封顶在这个值</strong> —— 超过它再涨也不会有实际效果
     * （各设备都是「每 tick 最多完成一次」，见 {@link MechanicalSoilBlockEntity#serverTick} 与 {@link CopperHeatDriver}）。
     * 铜块是导体、不是设备，返回 0（不封顶）。
     */
    public static double maxUsefulFlowRate(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(COPPER_CHESTS)) {
            return HotChamber.maxUsefulFlowRate(level, pos);   // 热室（底部垫铜块的铜箱子）
        }
        if (state.getBlock() instanceof MechanicalSoil) {
            return BASE_FLOW_RATE * MechanicalSoilBlockEntity.DURATION;
        }
        if (state.is(Blocks.BARREL)) {
            return BASE_FLOW_RATE * CopperHeatDriver.BARREL_DURATION;
        }
        if (state.getBlock() instanceof AbstractFurnaceBlock) {
            return CopperHeatDriver.cookerMaxFlowRate(level, pos);
        }
        return 0.0D;
    }

    /** {@code pos} 这格是否与任意一格铜块相邻（6 面任意一面）。 */
    public static boolean touchesCopperBlock(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isCopperBlock(level, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    /** {@code pos} 这格是不是「贴着铜块的消耗端」—— 网络里的一台消耗端（三种都算）。 */
    public static boolean isAttachedConsumer(BlockGetter level, BlockPos pos) {
        return isConsumerDevice(level, pos) && touchesCopperBlock(level, pos);
    }

    /** 同 {@link #isAttachedConsumer}，但只认<strong>受热容器</strong>（木桶 / 熔炉·烟熏炉·高炉）——给外部驱动筛选用。 */
    public static boolean isAttachedHeatContainer(BlockGetter level, BlockPos pos) {
        return (isHeatContainer(level, pos) || isCooker(level, pos)) && touchesCopperBlock(level, pos);
    }

    /** 与这格消耗端相邻的任意一格铜块（记账锚点）；没有则返回 {@code null}。 */
    public static BlockPos adjacentCopperBlock(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (isCopperBlock(level, neighbor)) {
                return neighbor;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 共享工具（外部驱动与机械耕地都用）

    /**
     * 产物 {@code output} 放不放得下；{@code freeingSlot} 是本次会被扣掉 1 个、可能因此腾空的槽（-1 表示没有）。
     */
    public static boolean canFit(Container container, ItemStack output, int freeingSlot) {
        return canFit(container, output, freeingSlot, 1);
    }

    /**
     * 产物 {@code output} 放不放得下；{@code freeingSlot} 是本次会被扣掉 {@code freeingCount} 个、
     * 可能因此腾空的槽（-1 表示没有）。
     */
    public static boolean canFit(Container container, ItemStack output, int freeingSlot, int freeingCount) {
        int remaining = output.getCount();
        int max = output.getMaxStackSize();
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (i == freeingSlot && stack.getCount() <= freeingCount) {
                remaining -= max;                 // 该槽即将腾空
            } else if (stack.isEmpty()) {
                remaining -= max;
            } else if (ItemStack.isSameItemSameComponents(stack, output)) {
                remaining -= max - stack.getCount();
            }
        }
        return remaining <= 0;
    }

    /** 把产物并入同类堆、再填空槽（产物已确认放得下）。 */
    public static void addToContainer(Container container, ItemStack output) {
        int size = container.getContainerSize();
        int max = output.getMaxStackSize();
        int remaining = output.getCount();
        for (int i = 0; i < size && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, output)) {
                continue;
            }
            int move = Math.min(max - stack.getCount(), remaining);
            if (move <= 0) {
                continue;
            }
            ItemStack merged = stack.copy();
            merged.grow(move);
            container.setItem(i, merged);
            remaining -= move;
        }
        for (int i = 0; i < size && remaining > 0; i++) {
            if (!container.getItem(i).isEmpty()) {
                continue;
            }
            int move = Math.min(max, remaining);
            container.setItem(i, output.copyWithCount(move));
            remaining -= move;
        }
    }

    // ------------------------------------------------------------------ 物品落点（原版容器 + 模组能力）

    /**
     * 这一格能不能当<strong>物品落点</strong>：原版 {@link Container}，或其它模组暴露的物品能力
     * （精妙存储 / 精妙背包等；它们只实现 NeoForge 能力，不是原版容器）。
     */
    public static boolean isItemTarget(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container || LargeMachines.itemHandlerAt(level, pos) != null;
    }

    /** 一件产物放不放得下这一格（原版容器 / 模组能力都支持）。 */
    public static boolean canFitAt(Level level, BlockPos pos, ItemStack output) {
        return canFitAt(level, pos, List.of(output));
    }

    /**
     * 一批产物能不能全部放进这一格。
     * <p>原版容器在副本里模拟堆叠；模组能力则开一个<b>回滚事务</b>试插，数量对不上就整体撤销。
     */
    public static boolean canFitAt(Level level, BlockPos pos, List<ItemStack> drops) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            return canFitAll(container, drops);
        }
        ResourceHandler<ItemResource> handler = LargeMachines.itemHandlerAt(level, pos);
        if (handler == null) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ItemStack drop : drops) {
                if (insertAll(handler, ItemResource.of(drop), drop.getCount(), transaction) < drop.getCount()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** 把一件产物放进这一格（原版容器并入堆；模组能力提交事务）。 */
    public static boolean insertAt(Level level, BlockPos pos, ItemStack output) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            addToContainer(container, output);
            container.setChanged();
            return true;
        }
        ResourceHandler<ItemResource> handler = LargeMachines.itemHandlerAt(level, pos);
        if (handler == null) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (insertAll(handler, ItemResource.of(output), output.getCount(), transaction) <= 0) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /** 原版容器：一批产物放不放得下（在副本里模拟，不改真实库存）。 */
    public static boolean canFitAll(Container container, List<ItemStack> drops) {
        int size = container.getContainerSize();
        List<ItemStack> simulated = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            simulated.add(container.getItem(i).copy());
        }
        for (ItemStack drop : drops) {
            int left = drop.getCount();
            int max = drop.getMaxStackSize();
            for (int i = 0; i < size && left > 0; i++) {
                ItemStack stack = simulated.get(i);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, drop)) {
                    continue;
                }
                int move = Math.min(max - stack.getCount(), left);
                stack.grow(move);
                left -= move;
            }
            for (int i = 0; i < size && left > 0; i++) {
                if (!simulated.get(i).isEmpty()) {
                    continue;
                }
                int move = Math.min(max, left);
                simulated.set(i, drop.copyWithCount(move));
                left -= move;
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    private static int insertAll(ResourceHandler<ItemResource> handler, ItemResource resource, int amount,
                                 TransactionContext transaction) {
        int moved = 0;
        for (int i = 0; i < handler.size() && moved < amount; i++) {
            moved += handler.insert(i, resource, amount - moved, transaction);
        }
        return moved;
    }

    /** 过热时在方块顶上随机位置冒「热尘 + 火星」（由各消耗端在通电工作时按间隔调用）。 */
    public static void spawnHeatParticles(ServerLevel level, BlockPos pos) {
        spawnHeatParticles(level, java.util.List.of(pos));
    }

    /**
     * 大机器版：在<strong>整机顶面</strong>随机挑一格冒「热尘 + 火星」。
     * <p>只取最高一层的格（{@code y == 全机最大 y}），粒子落点在该格顶面上方 ——
     * 这样 2×2×2 大机器的热尘会均匀分布在整机顶面，而不是永远粘在主方块（min 角）那一格、
     * 甚至冒在机器内部。单方块机器只有一格，行为与旧版一致。
     */
    public static void spawnHeatParticles(ServerLevel level, java.util.List<BlockPos> cells) {
        if (cells.isEmpty()) {
            return;
        }
        int maxY = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            maxY = Math.max(maxY, cell.getY());
        }
        java.util.List<BlockPos> top = new java.util.ArrayList<>();
        for (BlockPos cell : cells) {
            if (cell.getY() == maxY) {
                top.add(cell);
            }
        }
        RandomSource random = level.getRandom();
        BlockPos cell = top.get(random.nextInt(top.size()));
        double x = cell.getX() + 0.2D + random.nextDouble() * 0.6D;
        double y = cell.getY() + 1.02D;
        double z = cell.getZ() + 0.2D + random.nextDouble() * 0.6D;
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.0D, 0.02D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.0D, 0.01D, 0.0D, 0.0D);
    }

    private CopperHeat() {}
}
