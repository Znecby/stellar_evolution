package dev.modmind.stellar_evolution.farmland;

import dev.modmind.stellar_evolution.bio.BioMaterials;
import dev.modmind.stellar_evolution.coal.CoalMaterials;
import dev.modmind.stellar_evolution.thermal.CopperHeat;
import dev.modmind.stellar_evolution.thermal.ThermalLoad;
import dev.modmind.stellar_evolution.thermal.ThermalNetwork;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * <strong>机械土壤</strong>（机械耕地 / 机械沙地 / 机械灵魂沙地 / 机械草地）共用的方块实体：热压差网络的<strong>消耗端</strong>
 * （基础热阻 2、基准热流率 8 HP/t ⇒ 网络热流率 8 时占 16 DP、8HP）。
 *
 * <p>四种土壤共用同一套「催熟 → 自动收获」逻辑，只是<strong>能种什么</strong>由各自的方块标签决定
 * （耕地走 {@code supports_crops}、沙地走 {@code supports_sugar_cane} / {@code supports_cactus}，
 * 灵魂沙地走 {@code supports_nether_wart}）。所以它们共用同一个方块实体类型，也共用这里的一套逻辑。
 *
 * <p>为什么它需要方块实体（铜箱子 / 木桶都不用）：它<strong>每个服务器刻</strong>都要自驱，
 * 而铜箱子 / 木桶是原版方块、由 {@code CopperHeatDriver} 扫方块实体 + 手动登记来驱动；
 * 土壤这种「到处都是、还带状态」的方块没法可靠地枚举出来，干脆挂个方块实体 —— ticker 自己就跑起来了、
 * 区块重载也不用重新发现。
 *
 * <p>每刻做的事：
 * <ol>
 *   <li>必须<strong>贴着铜块</strong>才算接入网络（铜块是「消耗端 ↔ 铜管」的中间导体），否则进度清零；</li>
 *   <li>读网络账本，{@code speedFor(8) &gt; 0} 才算过热 —— 过热时冒热尘／火星，并顺手 {@code syncAlarm} 让超载铜管发红闪；</li>
 *   <li>攒够 {@value #DURATION} 刻（1.0 倍速；热流率越高越快）就<strong>让上方作物成熟 / 长高一截</strong>；</li>
 *   <li><strong>本方块底下必须有容器</strong>才工作（机械耕地 / 沙地 / 灵魂沙地 / 草地一致）——
 *       缺容器时进度清零、不催熟也不收获，Jade 额外提示「底部缺失容器」；</li>
 *   <li>上方作物可收时，<strong>收获 → 产物塞进容器</strong>
 *       （单格作物顺便复种；多格植物只拆上面几节、留下根部继续长）。容器塞不下<strong>整批</strong>产物就<strong>不收获</strong>。</li>
 * </ol>
 */
public class MechanicalSoilBlockEntity extends BlockEntity {
    /** 一次「催熟 / 收割」周期所需的刻数（1.0 倍速）。 */
    public static final double DURATION = 200.0D;
    /** 肥料点数池上限。 */
    public static final int FERT_MAX = 1600;
    /** 有肥料时，每多少刻消耗 1 点。 */
    public static final int TICKS_PER_FERT_POINT = 20;
    /** 有肥料时的催熟 / 收割加速倍率。 */
    public static final double FERT_SPEED_MULT = 2.0D;

    /** 肥料折算点数（占位值、不苛求平衡）：有机肥 = 400（4 个满槽）、尿素 = 600（工业氮肥）、生物质 = 100（弱肥）。 */
    private static final java.util.Map<Item, Integer> FERTILIZERS = java.util.Map.of(
            BioMaterials.ORGANIC_FERTILIZER.get(), 400,
            CoalMaterials.UREA.get(), 600,
            BioMaterials.BIOMASS.get(), 100);
    /** 催熟循环上限（骨粉每次升 2~5 级，正常 2~3 次就满）；纯防呆。 */
    private static final int MAX_GROW_STEPS = 8;
    /** 过热粒子间隔（刻）。 */
    private static final long PARTICLE_INTERVAL = 5L;
    /** 「右键收获」作物摘完后的年龄（与甜浆果丛右键一致：留丛、回 1 龄）。 */
    private static final int PICKED_AGE = 1;
    /** 水平四向（茎在这四个方向里随机结瓜）。 */
    private static final Direction[] HORIZONTAL =
            {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** 机械草地：首次「长成大树」的概率。 */
    private static final double TREE_BASE_CHANCE = 0.25D;
    /** 机械草地：每次失败后，下一次成功概率的增量。 */
    private static final double TREE_CHANCE_STEP = 0.05D;

    /** 累计催熟进度（刻·倍速）。 */
    private double progress;
    /** 机械草地：连续失败次数（下次概率 = 25% + 5% × 次数；成功清零）。 */
    private int treeFailStreak;

    /** 肥料点数 0..{@value #FERT_MAX}。 */
    private int fertilizerPoints;
    /** 距下一次扣肥料点的刻数。 */
    private int fertTimer;
    /** 对外的 1 格虚拟肥料口：插入即折成点数，不保留物品。 */
    private final FertilizerSink fertilizerSink = new FertilizerSink();

    public MechanicalSoilBlockEntity(BlockPos pos, BlockState state) {
        super(FarmlandBlocks.MECHANICAL_SOIL_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MechanicalSoilBlockEntity soil) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos copperPos = CopperHeat.adjacentCopperBlock(serverLevel, pos);
        if (copperPos == null) {
            soil.progress = 0.0D;   // 没贴铜块：不算接入网络
            return;
        }
        ThermalLoad load = ThermalNetwork.loadAt(serverLevel, copperPos);
        ThermalNetwork.syncAlarm(serverLevel, copperPos);   // 超载时让本网络的铜管发红闪
        double speed = load.speedFor(CopperHeat.BASE_FLOW_RATE);
        if (speed <= 0.0D) {
            soil.progress = 0.0D;   // 不过热：停手
            return;
        }
        if (!hasOutputContainer(serverLevel, pos)) {
            soil.progress = 0.0D;   // 底部没容器：不工作（Jade 提示「底部缺失容器」）
            return;
        }
        if (serverLevel.getGameTime() % PARTICLE_INTERVAL == 0L) {
            CopperHeat.spawnHeatParticles(serverLevel, pos);
        }

        // 肥料：瓶里有点数时加速催熟 / 收割，并按刻扣点（没有内部物品槽）。
        double soilSpeed = speed;
        if (soil.fertilizerPoints > 0) {
            soilSpeed *= FERT_SPEED_MULT;
            if (++soil.fertTimer >= TICKS_PER_FERT_POINT) {
                soil.fertTimer -= TICKS_PER_FERT_POINT;
                soil.fertilizerPoints--;
            }
        } else {
            soil.fertTimer = 0;
        }

        soil.progress += soilSpeed;
        if (soil.progress < DURATION) {
            return;
        }
        soil.progress -= DURATION;
        ripen(serverLevel, soil);
    }

    /**
     * 让上方作物「成熟 / 长高一截」，并按类型处理收获。
     * <ul>
     *   <li><strong>{@link CropBlock}</strong>（小麦/胡萝卜/土豆/甜菜、以及绝大多数模组作物）：
     *       骨粉催满龄 → 成熟且底下有容器时收割 + 复种；</li>
     *   <li><strong>多格植物</strong>（{@link SugarCaneBlock} 甘蔗 / {@link CactusBlock} 仙人掌 / {@link GrowingPlantBlock} 海带等）
     *       + 底下有容器 → 拆掉根部之上的那几节、产物进容器（根部继续长，实现「全部自动收」）；</li>
     *   <li><strong>西瓜 / 南瓜茎（{@link StemBlock}）</strong>：催熟到满龄并尝试结瓜 ——
     *       若土壤底下<strong>有容器</strong>，把瓜「截胡」成对应方块物品塞进容器（世界里不留瓜）；没容器则正常生成；</li>
     *   <li><strong>「右键收获」作物</strong>（{@link FarmlandBlocks#RIGHT_CLICK_HARVEST}，如甜浆果丛）：
     *       成熟 → 产物进容器、回退到 {@value #PICKED_AGE} 龄；</li>
     *   <li><strong>单格 AGE 作物</strong>（如下界疣 {@code NetherWartBlock}）：成熟 → 收割 + 复种。</li>
     * </ul>
     */
    private static void ripen(ServerLevel level, MechanicalSoilBlockEntity soil) {
        BlockPos landPos = soil.getBlockPos();
        if (level.getBlockState(landPos).getBlock() instanceof MechanicalGrassBlock) {
            ripenTree(level, soil);   // 机械草地：走「查表长树」分支
            return;
        }
        BlockPos cropPos = landPos.above();
        Container container = outputContainer(level, landPos);

        if (level.getBlockState(cropPos).getBlock() instanceof CropBlock) {
            growToMature(level, cropPos);
            BlockState cropState = level.getBlockState(cropPos);
            if (container != null && cropState.getBlock() instanceof CropBlock crop && crop.isMaxAge(cropState)) {
                harvest(level, cropPos, cropState, container);
            }
            return;
        }

        growSpecial(level, cropPos, container);   // 茎（含结瓜截胡）/ 骨粉 / 随机刻 通用生长

        if (container == null) {
            return;   // 没容器：不自动收，玩家自己来
        }
        BlockState now = level.getBlockState(cropPos);
        Block block = now.getBlock();
        if (isTallPlant(block)) {
            harvestTall(level, cropPos, now, container);   // 多格植物：拆上面几节
            return;
        }
        if (now.is(FarmlandBlocks.RIGHT_CLICK_HARVEST)) {
            harvestRightClick(level, cropPos, container);  // 右键收获作物
            return;
        }
        if (block instanceof StemBlock) {
            return;   // 茎本身不收获：瓜已在上面的随机刻里被截胡
        }
        if (isMature(now)) {
            harvest(level, cropPos, now, container);       // 单格 AGE 作物（下界疣等）
        }
    }

    /**
     * <strong>机械草地</strong>的「虚拟长树」：上面是树苗 + 底部有容器时，按「25% 起步、每次失败 +5%」的累计概率
     * 尝试长成大树；成功即按影子层统计出的原木 / 树叶数逐块滚收获战利品表（树苗 / 木棍 / 苹果）塞进容器，并把概率重置。
     * 全程<strong>不写真实方块</strong>（见 {@link MechanicalTreeHarvest}）；树苗原地保留，下一轮继续长。
     */
    private static void ripenTree(ServerLevel level, MechanicalSoilBlockEntity soil) {
        BlockPos saplingPos = soil.getBlockPos().above();
        BlockState sapling = level.getBlockState(saplingPos);
        if (!MechanicalTreeHarvest.isTree(sapling)) {
            soil.treeFailStreak = 0;   // 上面不是树苗：清零
            return;
        }
        Container container = outputContainer(level, soil.getBlockPos());
        if (container == null) {
            return;   // 必须底部有容器才工作（Jade 会提示「底部缺失容器」）
        }
        double chance = Math.min(1.0D, TREE_BASE_CHANCE + TREE_CHANCE_STEP * soil.treeFailStreak);
        if (level.getRandom().nextDouble() >= chance) {
            soil.treeFailStreak++;
            soil.setChanged();
            return;
        }

        MechanicalTreeHarvest.Tree tree = MechanicalTreeHarvest.treeFor(sapling);
        List<ItemStack> drops = MechanicalTreeHarvest.rollHarvest(sapling, tree, level.getRandom());
        if (drops.isEmpty() || !canFitAll(container, drops)) {
            return;   // 容器放不下：这次不结算（树苗原地保留），下次再试
        }
        soil.treeFailStreak = 0;   // 成功重置
        soil.setChanged();
        insertHarvest(container, drops);
    }

    /** 用「骨粉」把作物催到成熟（对任意 {@link CropBlock} 通用：每次 2~5 级，循环几次即满）。 */
    private static void growToMature(ServerLevel level, BlockPos cropPos) {
        for (int step = 0; step < MAX_GROW_STEPS; step++) {
            BlockState state = level.getBlockState(cropPos);
            if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state)) {
                return;
            }
            crop.performBonemeal(level, level.getRandom(), cropPos, state);
        }
    }

    /**
     * 催熟<strong>非 {@link CropBlock} 的作物</strong>：
     * <ol>
     *   <li><strong>茎</strong>（{@link StemBlock}）→ 走 {@link #tickStem}（可拦截结瓜）；</li>
     *   <li>能骨粉且未满（{@link BonemealableBlock#isValidBonemealTarget}）→ 调 {@link BonemealableBlock#performBonemeal}
     *       （甜浆果丛、海带等）；</li>
     *   <li>否则只要该方块<strong>会随机刻</strong>（{@link BlockState#isRandomlyTicking()}）→ 派发一次
     *       {@link BlockState#randomTick}（甘蔗 / 仙人掌 / 下界疣长高长龄）。</li>
     * </ol>
     * 三者都不满足（例如摆在上面的其实是花、草）时直接返回，不做任何事。
     */
    private static void growSpecial(ServerLevel level, BlockPos cropPos, Container container) {
        for (int step = 0; step < MAX_GROW_STEPS; step++) {
            BlockState state = level.getBlockState(cropPos);
            if (state.isAir()) {
                return;   // 中途被拆了
            }
            if (state.getBlock() instanceof StemBlock) {
                tickStem(level, cropPos, state, container);
            } else if (state.getBlock() instanceof BonemealableBlock bonemeal
                    && bonemeal.isValidBonemealTarget(level, cropPos, state)) {
                bonemeal.performBonemeal(level, level.getRandom(), cropPos, state);
            } else if (state.isRandomlyTicking()) {
                state.randomTick(level, cropPos, level.getRandom());
            } else {
                return;   // 既不能骨粉、也不随机刻：不是可生长的作物
            }
        }
    }

    /**
     * 茎的一拍：先照常用骨粉 / 随机刻推进，然后<strong>把这一拍新结出的瓜收走</strong>。
     * <p>
     * 只有<strong>土壤底下有容器</strong>时才「截胡」：{@link StemBlock#randomTick} 会把瓜放到茎四周（同层）的
     * 空地上；这里在派发前后对比那 4 个位置，凡是<strong>从空气变成非空气</strong>的就是刚结出的瓜 ——
     * 立即拆掉并把「对应方块物品」塞进容器（世界里看不到瓜、也不掉地上）；容器塞不下则留着，下次再收。
     * 没容器时完全走原版逻辑（瓜正常长在世界里）。
     */
    private static void tickStem(ServerLevel level, BlockPos cropPos, BlockState state, Container container) {
        boolean canBonemeal = state.getBlock() instanceof BonemealableBlock bonemeal
                && bonemeal.isValidBonemealTarget(level, cropPos, state);

        if (container == null) {
            if (canBonemeal) {
                ((BonemealableBlock) state.getBlock()).performBonemeal(level, level.getRandom(), cropPos, state);
            } else {
                state.randomTick(level, cropPos, level.getRandom());
            }
            return;
        }

        BlockPos[] neighbours = new BlockPos[4];
        boolean[] wasAir = new boolean[4];
        for (int i = 0; i < 4; i++) {
            neighbours[i] = cropPos.relative(HORIZONTAL[i]);
            wasAir[i] = level.getBlockState(neighbours[i]).isAir();
        }

        if (canBonemeal) {
            ((BonemealableBlock) state.getBlock()).performBonemeal(level, level.getRandom(), cropPos, state);
        } else {
            state.randomTick(level, cropPos, level.getRandom());
        }

        for (int i = 0; i < 4; i++) {
            if (!wasAir[i]) {
                continue;
            }
            BlockState grown = level.getBlockState(neighbours[i]);
            if (grown.isAir()) {
                continue;
            }
            ItemStack fruit = new ItemStack(grown.getBlock().asItem());
            if (fruit.isEmpty() || !CopperHeat.canFit(container, fruit, -1)) {
                continue;   // 放不下 / 没有对应物品：留在世界里
            }
            level.removeBlock(neighbours[i], false);
            insertHarvest(container, List.of(fruit));
        }
    }

    /** 是不是「多格植物」（甘蔗 / 仙人掌 / 海带这类竖直连成一列的作物）。 */
    private static boolean isTallPlant(Block block) {
        return block instanceof SugarCaneBlock || block instanceof CactusBlock || block instanceof GrowingPlantBlock;
    }

    /** 上下两格是不是同一株植物（用于沿竖直方向找这一列）。<strong>仙人掌花算仙人掌的一部分</strong>（它会盖在顶上挡生长）。 */
    private static boolean samePlant(Block a, Block b) {
        if (a instanceof SugarCaneBlock) {
            return b instanceof SugarCaneBlock;
        }
        if (a instanceof CactusBlock) {
            return b instanceof CactusBlock || b == Blocks.CACTUS_FLOWER;
        }
        if (a instanceof GrowingPlantBlock) {
            return b instanceof GrowingPlantBlock;
        }
        return a == b;
    }

    /**
     * <strong>多格植物</strong>的自动收获：把<strong>根部（长在土壤上的那一格）之上</strong>的所有同株方块拆掉，
     * 产物塞进容器；<strong>根部保留</strong>继续长（甘蔗 / 仙人掌 / 海带都是这个口径）。
     * 仙人掌顶上的<strong>仙人掌花</strong>也算同株（见 {@link #samePlant}）—— 一并收掉，免得它盖住顶端挡住生长。
     * <p>
     * 先取好整批掉落并确认容器放得下，再<strong>从上往下</strong>拆 —— 海带的 body 方块在「上方被拆空」时会自动
     * 变回 head（见 {@code GrowingPlantBodyBlock#updateShape}），所以拆完最顶上那节后根部会重新可生长。
     */
    private static void harvestTall(ServerLevel level, BlockPos cropPos, BlockState baseState, Container container) {
        Block baseBlock = baseState.getBlock();
        List<BlockPos> segments = new ArrayList<>();
        for (int i = 1; ; i++) {
            BlockPos p = cropPos.above(i);
            if (!samePlant(baseBlock, level.getBlockState(p).getBlock())) {
                break;
            }
            segments.add(p);
        }
        if (segments.isEmpty()) {
            return;   // 只有根部，没有可收的部分
        }
        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos p : segments) {
            for (ItemStack drop : Block.getDrops(level.getBlockState(p), level, p, null)) {
                if (!drop.isEmpty()) {
                    drops.add(drop);
                }
            }
        }
        if (drops.isEmpty() || !canFitAll(container, drops)) {
            return;   // 放不下整批产物：留着，别往地上丢
        }
        for (int i = segments.size() - 1; i >= 0; i--) {
            level.removeBlock(segments.get(i), false);
        }
        insertHarvest(container, drops);
    }

    /**
     * 「右键收获」作物（{@link FarmlandBlocks#RIGHT_CLICK_HARVEST}，如甜浆果丛）的自动收获：
     * 年龄到顶（成熟）+ 底下有容器 + 产物塞得下 → 取掉落塞进容器，并把年龄回退到 {@value #PICKED_AGE}
     * （与甜浆果丛右键「摘完留丛、回 1 龄」一致）；否则不动，交给玩家右键。
     */
    private static void harvestRightClick(ServerLevel level, BlockPos cropPos, Container container) {
        BlockState state = level.getBlockState(cropPos);
        if (!state.is(FarmlandBlocks.RIGHT_CLICK_HARVEST)) {
            return;
        }
        Property<?> ageProperty = state.getBlock().getStateDefinition().getProperty("age");
        if (!(ageProperty instanceof IntegerProperty age)
                || state.getValue(age) < Collections.max(age.getPossibleValues())) {
            return;   // 没有 age 属性 / 还没成熟
        }
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : Block.getDrops(state, level, cropPos, null)) {
            if (!drop.isEmpty()) {
                drops.add(drop);
            }
        }
        if (drops.isEmpty() || !canFitAll(container, drops)) {
            return;   // 放不下整批产物：留着，玩家自己右键
        }
        level.setBlock(cropPos, state.setValue(age, PICKED_AGE), Block.UPDATE_ALL);
        insertHarvest(container, drops);
    }

    /**
     * 单格作物是否「成熟」：{@link CropBlock} 用自带的 {@code isMaxAge}；其它（如下界疣）用 {@code age} 属性是否到顶。
     * 没有 {@code age} 属性的方块一律视为「不成熟」（不会被自动收获）。
     */
    private static boolean isMature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        Property<?> ageProperty = state.getBlock().getStateDefinition().getProperty("age");
        return ageProperty instanceof IntegerProperty age
                && state.getValue(age) >= Collections.max(age.getPossibleValues());
    }

    /**
     * 单格作物的收割：取掉落 → <strong>先确认整批产物都塞得进容器</strong> → 从掉落里找回可复种的种子
     * （小麦/甜菜是 seeds 物品、胡萝卜/土豆/下界疣就是自己）→ 清空作物格 → 种回 0 龄 → 产物进容器。
     * <p>
     * <strong>容器塞不下、或找不到可复种的种子时都不收割</strong>（保持原地不动）—— 免得往地上狂丢物品，
     * 也免得把没收成种子的作物直接拆没。
     */
    private static void harvest(ServerLevel level, BlockPos cropPos, BlockState cropState, Container container) {
        List<ItemStack> drops = Block.getDrops(cropState, level, cropPos, null);
        if (!canFitAll(container, drops)) {
            return;   // 容器放不下整批产物：这次干脆不收
        }
        // 从掉落里找回同种作物的种子（小麦/甜菜是 seeds 物品，胡萝卜/土豆/下界疣就是自己）
        ItemStack seed = ItemStack.EMPTY;
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && drop.getItem() instanceof BlockItem item && item.getBlock() == cropState.getBlock()) {
                seed = drop;
                break;
            }
        }
        if (seed.isEmpty()) {
            return;   // 找不到复种种子：不拆，免得把作物弄没
        }
        level.removeBlock(cropPos, false);
        level.setBlock(cropPos, cropState.getBlock().defaultBlockState(), Block.UPDATE_ALL);
        seed.shrink(1);
        insertHarvest(container, drops);
    }

    /**
     * 把一批收割产物塞进容器（容器放不下就不给，遵守「不往地上丢物品」的既有口径）。
     * <p>生物质改由粉碎机粉碎作物获取，土壤不再额外折算 {@code biomass}。
     */
    private static void insertHarvest(Container container, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                CopperHeat.addToContainer(container, drop);
            }
        }
        container.setChanged();
    }

    /**
     * 这些掉落能不能<strong>全部</strong>塞进容器（空槽会被逐份消耗、不重复计算）。
     * 判定口径与 {@link CopperHeat#addToContainer} 一致：先并同类堆、再填空槽。
     */
    public static boolean canFitAll(Container container, List<ItemStack> drops) {
        int size = container.getContainerSize();
        List<ItemStack> simulated = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            simulated.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            int remaining = drop.getCount();
            int max = drop.getMaxStackSize();
            // 先并进已有的同类堆
            for (int i = 0; i < size && remaining > 0; i++) {
                ItemStack stack = simulated.get(i);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, drop)) {
                    continue;
                }
                int move = Math.min(max - stack.getCount(), remaining);
                if (move <= 0) {
                    continue;
                }
                stack.grow(move);
                remaining -= move;
            }
            // 再开新槽（真正写进模拟表，后续同类堆可以并进来）
            for (int i = 0; i < size && remaining > 0; i++) {
                if (!simulated.get(i).isEmpty()) {
                    continue;
                }
                ItemStack placed = drop.copyWithCount(Math.min(max, remaining));
                simulated.set(i, placed);
                remaining -= placed.getCount();
            }
            if (remaining > 0) {
                return false;   // 这一类就放不下了
            }
        }
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("progress", this.progress);
        output.putInt("tree_fail_streak", this.treeFailStreak);
        output.putInt("fertilizer_points", this.fertilizerPoints);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("progress", 0.0D);
        this.treeFailStreak = input.getIntOr("tree_fail_streak", 0);
        this.fertilizerPoints = Math.max(0, Math.min(FERT_MAX, input.getIntOr("fertilizer_points", 0)));
    }

    // ------------------------------------------------------------------ 肥料（点数；没有内部物品槽）

    /** 肥料点数（Jade 用）。 */
    public int fertilizerPoints() {
        return this.fertilizerPoints;
    }

    /** 这件物品算不算肥料（客户端预判 / 手持右键用）。 */
    public static boolean isFertilizer(ItemStack stack) {
        return !stack.isEmpty() && FERTILIZERS.containsKey(stack.getItem());
    }

    /** 点数还没满、且这件物品是肥料 → 右键可以吃（点数的溢出部分直接丢弃，不做部分返还）。 */
    public boolean canAcceptFertilizer(ItemStack stack) {
        return !stack.isEmpty() && this.fertilizerPoints < FERT_MAX && FERTILIZERS.containsKey(stack.getItem());
    }

    /** 玩家手持肥料右键：直接把 1 个折成点数（不占槽）。 */
    public boolean addFertilizer(ItemStack held) {
        if (!canAcceptFertilizer(held)) {
            return false;
        }
        int value = FERTILIZERS.get(held.getItem());
        held.shrink(1);
        this.fertilizerPoints = Math.min(FERT_MAX, this.fertilizerPoints + value);
        this.setChanged();
        return true;
    }

    /**
     * 机械土壤底部的「产物容器」：必须是 {@link Container}，且不能是另一块机械土壤
     * （机械土壤本身只有肥料口，不是产物容器）。
     */
    public static Container outputContainer(Level level, BlockPos soilPos) {
        if (level.getBlockEntity(soilPos.below()) instanceof Container container
                && !(container instanceof MechanicalSoilBlockEntity)) {
            return container;
        }
        return null;
    }

    public static boolean hasOutputContainer(Level level, BlockPos soilPos) {
        return outputContainer(level, soilPos) != null;
    }

    /** 对外的物品能力：1 格虚拟肥料口，插入即折成点数，不保留物品、不允许抽取。 */
    public ResourceHandler<ItemResource> itemCapability() {
        return this.fertilizerSink;
    }

    /**
     * 虚拟肥料口（<strong>没有内部槽</strong>）：
     * <ul>
     *   <li>{@code getResource/getAmount} 永远是空 / 0 —— 插入的肥料立刻变成点数；</li>
     *   <li>{@code insert} 只接受能完整折进点数池的肥料，返回实际吃下的数量；</li>
     *   <li>{@code extract} 永远 0；</li>
     *   <li>{@link SnapshotJournal} 保证管道事务回滚时点数也回滚。</li>
     * </ul>
     */
    private final class FertilizerSink extends SnapshotJournal<Integer> implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0L;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            if (resource.isEmpty()) {
                return fertilizerPoints >= FERT_MAX ? 0L : 64L;
            }
            int value = valueOf(resource);
            if (value <= 0 || fertilizerPoints >= FERT_MAX) {
                return 0L;
            }
            return Math.min(64, ceilDiv(FERT_MAX - fertilizerPoints, value));
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return valueOf(resource) > 0 && fertilizerPoints < FERT_MAX;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (index != 0 || amount <= 0 || fertilizerPoints >= FERT_MAX) {
                return 0;
            }
            int value = valueOf(resource);
            if (value <= 0) {
                return 0;
            }
            // 允许最后一颗把点数顶满（多出的点数丢弃），但不允许在已经满时再吃。
            int fit = ceilDiv(FERT_MAX - fertilizerPoints, value);
            int accepted = Math.min(amount, fit);
            if (accepted <= 0) {
                return 0;
            }
            if (transaction != null) {
                updateSnapshots(transaction);
            }
            fertilizerPoints = Math.min(FERT_MAX, fertilizerPoints + accepted * value);
            return accepted;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;   // 只进不出
        }

        @Override
        protected Integer createSnapshot() {
            return fertilizerPoints;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            fertilizerPoints = snapshot;
        }

        @Override
        protected void onRootCommit(Integer original) {
            setChanged();
        }

        private int valueOf(ItemResource resource) {
            if (resource == null || resource.isEmpty()) {
                return 0;
            }
            Integer value = FERTILIZERS.get(resource.toStack().getItem());
            return value == null ? 0 : value;
        }

        private int ceilDiv(int numerator, int denominator) {
            return (numerator + denominator - 1) / denominator;
        }
    }
}
