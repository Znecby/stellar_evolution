package dev.modmind.stellar_evolution.thermal;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.equipment.CopperGear;
import dev.modmind.stellar_evolution.pipe.MachinePipeBlock;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 热压差网络里<strong>外部驱动型消耗端</strong>的驱动：贴着铜块的<strong>熔炉 / 烟熏炉 / 高炉</strong>
 * 与<strong>木桶</strong>通电就干活。
 *
 * <p>为什么需要外部驱动：这些都是<strong>原版方块</strong>（靠标签 / 类型识别，见 {@link CopperHeat}），
 * 熔炉虽有方块实体但不是我们自己的（挂不了自建 ticker），木桶连方块实体都没有。所以这里自己维护一份
 * 「消耗端」清单，每个服务器刻推进一次：
 * <ul>
 *   <li><strong>发现</strong>：区块加载时扫该区块的方块实体；玩家放置时即时登记；破坏时注销。清单每刻自检，失效即剔除。</li>
 *   <li><strong>过热判据</strong>：读相邻那格铜块所在网络的账本，{@code speedFor(8 HP/t) > 0} 才算「过热」；
 *       顺手 {@link ThermalNetwork#syncAlarm}，让超载时铜管发红闪。不过热一律停手、进度清零、炉火熄灭。</li>
 *   <li><strong>熔炉 / 烟熏炉 / 高炉（功能 · 网络能量代替燃料）</strong>：<strong>给原版炉子供能</strong> ——
 *       撑住火焰、把本周期总时长按倍速缩短，之后由原版 tick 推进进度、扣料、产出与给经验
 *       （所以界面上的火焰与进度箭头都正常动，见 {@link HeatCookerAccess}）。
 *       通电时把炉子点亮（{@code LIT}），断电只熄灭<strong>我们点的那把火</strong>；<strong>不吃燃料</strong>。</li>
 *   <li><strong>木桶（功能 · 钓鱼产出）</strong>：任意一个<strong>水平侧</strong>有水源方块时才干活，
 *       每 {@value #BARREL_DURATION} 刻（按倍速）产出一次钓鱼战利品、写进桶里。
 *       为满足「无视限制」——原版 {@code gameplay/fishing} 只在开阔水域才给宝藏——这里<strong>直接按原版权重
 *       （鱼 85 / 垃圾 10 / 宝藏 5）在 {@code fishing/fish}、{@code fishing/junk}、{@code fishing/treasure}
 *       三张子表里抽一张来滚</strong>，于是三种战利品都能出。</li>
 *   <li><strong>热室（{@link HotChamber}）</strong>：<strong>铜箱子底部垫铜块</strong>组成，结构完整时
 *       按 {@link HotChamberRecipes}（单独配方类型、只吃物品）推进一个周期 —— 完成即扣输入、产物并回箱内。
 *       双联大铜箱子只由<strong>代表格</strong>推进（整箱一台、共享 54 格库存）；
 *       进度只活在内存里，Jade 的绿色「配方」条经 {@link #progressAt} 向这里要快照。</li>
 * </ul>
 * 三者都<strong>不吃燃料</strong>，进度只活在内存里（按世界 + 容器坐标），区块卸载后重进从头再来。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class CopperHeatDriver {
    /** 每个服务器世界里：消耗端（熔炉 / 烟熏炉 / 高炉 / 木桶）坐标 → 累计进度（刻·倍速）。 */
    private static final Map<ServerLevel, Map<BlockPos, Double>> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 每个服务器世界里：<strong>正由热压差网络供能</strong>的熔炉系列坐标。
     * <p>只有在这张表里的炉子才会被我们熄灭 / 清进度 —— 炉子自己烧燃料时不去碰它的动画。
     */
    private static final Map<ServerLevel, Set<BlockPos>> HEATING =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 每个服务器世界里：<strong>热室</strong>（铜箱子本体）坐标 → 运行状态。
     * <p>双联大铜箱子的<strong>两格都会登记</strong>（登记时可能还不知道哪格是代表格），
     * 但每刻只由<strong>代表格</strong>推进，整箱因此只算一台。
     */
    private static final Map<ServerLevel, Map<BlockPos, ChamberState>> CHAMBERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 原版三种烹饪配方各一个查询器（带「上一条配方」缓存，稳态一次 id 比对）。 */
    private static final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> SMELTING_CHECK =
            RecipeManager.createCheck(RecipeType.SMELTING);
    private static final RecipeManager.CachedCheck<SingleRecipeInput, SmokingRecipe> SMOKING_CHECK =
            RecipeManager.createCheck(RecipeType.SMOKING);
    private static final RecipeManager.CachedCheck<SingleRecipeInput, BlastingRecipe> BLASTING_CHECK =
            RecipeManager.createCheck(RecipeType.BLASTING);

    /** 原版熔炉方块实体槽位：0 输入、1 燃料、2 输出。 */
    private static final int COOKER_INPUT_SLOT = 0;
    private static final int COOKER_RESULT_SLOT = 2;

    /** 过热粒子的发射间隔（刻）：每 5 刻冒一次，约每秒 4 次。 */
    private static final long HEAT_PARTICLE_INTERVAL = 5L;

    /** 木桶一次钓鱼产出所需的刻数（1.0 倍速）。 */
    public static final double BARREL_DURATION = 200.0D;

    /** 烹饪方块「无配方可烧」时的满速判定所采用的标准时长（刻）——原版烹饪配方基本都是 200。 */
    private static final double STANDARD_COOK_DURATION = 200.0D;

    // 原版 fishing 根表的三张子表与权重（85 / 10 / 5）；直接滚子表即可绕开宝藏的「开阔水域」条件。
    private static final ResourceKey<LootTable> FISHING_FISH = lootKey("gameplay/fishing/fish");
    private static final ResourceKey<LootTable> FISHING_JUNK = lootKey("gameplay/fishing/junk");
    private static final ResourceKey<LootTable> FISHING_TREASURE = lootKey("gameplay/fishing/treasure");
    private static final int FISH_WEIGHT = 85;
    private static final int JUNK_WEIGHT = 10;
    private static final int TREASURE_WEIGHT = 5;

    /**
     * 一台<strong>热室</strong>的运行状态（只活在内存里，区块卸载即丢）。
     * <p>{@code progress} = 累计进度（刻·倍速）；{@code remainingTicks} / {@code ratio} 是给
     * <strong>Jade 配方进度条</strong>的快照 —— 口径与大机器一致：
     * {@code remainingTicks = -1} 没在做（不画条）、{@code 0} 满速（每刻完成一条）。
     */
    private static final class ChamberState {
        double progress;
        int remainingTicks = -1;
        double ratio;

        /** 停摆（不热压 / 没匹配到配方 / 结构缺失 / 非代表格）：进度清零、Jade 不画配方条。 */
        void idle() {
            progress = 0.0D;
            remainingTicks = -1;
            ratio = 0.0D;
        }
    }

    /** 热室配方进度快照（Jade 用）：剩余刻数（{@code 0} = 满速）+ 填充比例 0..1。 */
    public record Progress(int remainingTicks, double ratio) {}

    /**
     * 取 {@code chestPos} 这台热室的配方进度快照（Jade 用）；没在做 / 没登记 / 不是代表格 → {@code null}。
     * <p>进度只活在驱动内存里（不存盘），所以只能由服务端向这里要。
     */
    @Nullable
    public static Progress progressAt(ServerLevel level, BlockPos chestPos) {
        Map<BlockPos, ChamberState> map = CHAMBERS.get(level);
        ChamberState state = map == null ? null : map.get(chestPos);
        return state == null || state.remainingTicks < 0 ? null : new Progress(state.remainingTicks, state.ratio);
    }

    // ------------------------------------------------------------------ 登记 / 注销

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        // 只扫该区块自身的方块实体：加载区块时就把世界里已存在的消耗端补齐登记
        // （机械土壤自带 BE、自驱，无需在此登记；铜箱子有 BE，热室在这里补齐）
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockPos pos = blockEntity.getBlockPos();
            if (CopperHeat.isAttachedHeatContainer(level, pos)) {
                remember(level, pos);
            }
            if (HotChamber.isPart(level, pos)) {
                rememberChamber(level, pos);
            }
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        // 放置事件在方块真正落位「之前」触发，所以判「新放的那块」要用 event.getPlacedBlock()，
        // 不能读 level.getBlockState(pos)（那时还是空气）；相邻那几格则已在世界里。
        BlockState placed = event.getPlacedBlock();
        if (isExternalConsumer(placed)) {
            // 放了消耗端：旁边只要有铜块就算接入
            if (CopperHeat.touchesCopperBlock(level, pos)) {
                remember(level, pos);
            }
        } else if (placed.is(CopperHeat.COPPER_BLOCKS)) {
            // 放了铜块：把贴着它的受热容器都登记上（此时铜块还没落位，不能靠 touchesCopperBlock 反查）
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (CopperHeat.isHeatContainer(level, neighbor) || CopperHeat.isCooker(level, neighbor)) {
                    remember(level, neighbor);
                }
                if (direction == Direction.UP && HotChamber.isChest(level, neighbor)) {
                    // 热室：铜块垫在箱子底下（只认正下方），整箱结构状态下一 tick 自检
                    rememberChamber(level, neighbor);
                }
            }
        } else if (placed.is(CopperHeat.COPPER_CHESTS)) {
            // 放了铜箱子：可能是热室的一半（自己 or 双联的另一半稍后放置时各自再登记）
            rememberChamber(level, pos);
        }
        // 无论放的是什么，都把「仍然贴着铜块」的邻居补登记回来：
        // 旧版本在机器旁拆过方块就会把消耗端静默踢出名单，这里顺手自愈。
        rememberAttachedNeighbors(level, pos);
    }

    /** 放置的这块是不是「外部驱动型消耗端」（木桶 / 熔炉·烟熏炉·高炉）。 */
    private static boolean isExternalConsumer(BlockState state) {
        return state.is(Blocks.BARREL) || state.getBlock() instanceof AbstractFurnaceBlock;
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // 只注销「被拆的这块」本身；**不能**无差别清掉 6 个邻居 —— 以前在熔炉 / 木桶旁边拆任意方块
        // （铺管道、挪铜块…）都会把它们静默踢出名单，之后除非重进世界否则再也不被驱动。
        BlockPos pos = event.getPos();
        unregister(level, pos);
        // 反过来把仍然贴着铜块的邻居补登记（真正失效的会在每刻自检里剔除）。
        rememberAttachedNeighbors(level, pos);
    }

    /** 把 {@code pos} 周围 6 格里「贴着铜块的受热容器 / 烹饪方块」补登记（自愈用）。 */
    private static void rememberAttachedNeighbors(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.isLoaded(neighbor) && CopperHeat.isAttachedHeatContainer(level, neighbor)) {
                remember(level, neighbor);
            }
        }
    }

    /** 6 个邻居所在区块是否都已加载（区块边界上的方块状态读不准，不要据此剔除）。 */
    private static boolean neighborsLoaded(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.isLoaded(pos.relative(direction))) {
                return false;
            }
        }
        return true;
    }

    /** 直接登记（不再复核方块状态）。 */
    private static void remember(ServerLevel level, BlockPos containerPos) {
        ACTIVE.computeIfAbsent(level, key -> new HashMap<>()).putIfAbsent(containerPos.immutable(), 0.0D);
    }

    /** 登记一台热室（铜箱子本体坐标，不做复核，结构状态每刻自检）。 */
    private static void rememberChamber(ServerLevel level, BlockPos chestPos) {
        CHAMBERS.computeIfAbsent(level, key -> new HashMap<>()).putIfAbsent(chestPos.immutable(), new ChamberState());
    }

    private static void unregister(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Double> map = ACTIVE.get(level);
        if (map != null) {
            map.remove(pos);
        }
        // 热室刻意**不在这里**注销：结构状态下一 tick 自检（并顺手把邻近机械管对齐），
        // 免得「拆掉垫底的铜块」时机械管收不回/放不出管臂。
    }

    /**
     * 把贴着本格（铜箱子）的<strong>机械管</strong>对齐到当前结构状态 —— 热室（{@code stateAt != NONE}）不接机械管，
     * 普通铜箱子（{@code NONE}，大机器物流容器）才接。
     *
     * <p>为什么需要手动对齐：管子的连接状态只在<strong>直接邻居</strong>变化时重算，而本结构依赖的是
     * 「箱子<strong>下面</strong>那格」—— 它离管子还隔着一格，不会自动触发。这里只在发现不一致时刷一次。
     */
    private static void refreshAdjacentPipes(ServerLevel level, BlockPos chestPos) {
        boolean wantConnect = HotChamber.stateAt(level, chestPos) == HotChamber.State.NONE;
        for (Direction direction : Direction.values()) {
            BlockPos pipePos = chestPos.relative(direction);
            BlockState pipeState = level.getBlockState(pipePos);
            if (!(pipeState.getBlock() instanceof MachinePipeBlock)) {
                continue;
            }
            Direction pipeSide = direction.getOpposite();   // 从管子指向本格的那一端
            boolean cut = pipeState.getValue(MachinePipeBlock.CUTS.get(pipeSide)) && wantConnect;
            boolean expected = wantConnect && !cut;
            if (pipeState.getValue(MachinePipeBlock.CONNECTIONS.get(pipeSide)) != expected) {
                level.getBlockState(chestPos).updateNeighbourShapes(level, chestPos, Block.UPDATE_ALL);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 每刻驱动

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickHeatContainers(level);
            tickChambers(level);
        }
    }

    /** 木桶 / 熔炉系列：每刻推进一次（贴着铜块的那些）。 */
    private static void tickHeatContainers(ServerLevel level) {
        Map<BlockPos, Double> map = ACTIVE.get(level);
        if (map == null || map.isEmpty()) {
            return;
        }
        // 迭代期间查方块可能触发同步加载区块 → ChunkEvent.Load 里的 remember() 会改这张表，
        // 所以按快照迭代，改动一律回写到 map（不能边迭代边改）。
        for (Map.Entry<BlockPos, Double> entry : List.copyOf(map.entrySet())) {
            BlockPos containerPos = entry.getKey();
            if (!level.isLoaded(containerPos)) {
                continue;   // 区块没加载：留着（进度还在），加载后再算
            }
            // 自检：消耗端还在、旁边还有铜块，否则剔除。
            // 邻居区块没加载时先别剔除 —— 边界上的铜块读成空气会被误判成「已断开」。
            if (!CopperHeat.isAttachedHeatContainer(level, containerPos)) {
                if (!neighborsLoaded(level, containerPos)) {
                    continue;
                }
                clearHeat(level, containerPos);
                map.remove(containerPos);
                continue;
            }
            BlockPos copperPos = CopperHeat.adjacentCopperBlock(level, containerPos);
            if (copperPos == null) {
                clearHeat(level, containerPos);
                map.remove(containerPos);
                continue;
            }
            map.put(containerPos, tickConsumer(level, containerPos, copperPos, entry.getValue()));
        }
    }

    /**
     * 热室：每刻推进一次（整箱一台，只有<strong>代表格</strong>那一半真正结算）。
     * 结构缺失 / 不是代表格 → 停摆且进度清零（大铜箱子只垫了一块铜块时不工作）。
     */
    private static void tickChambers(ServerLevel level) {
        Map<BlockPos, ChamberState> map = CHAMBERS.get(level);
        if (map == null || map.isEmpty()) {
            return;
        }
        // 同 tickHeatContainers：结构自检可能触发区块加载并回调 rememberChamber()，按快照迭代
        for (Map.Entry<BlockPos, ChamberState> entry : List.copyOf(map.entrySet())) {
            BlockPos chestPos = entry.getKey();
            ChamberState state = entry.getValue();
            if (!level.isLoaded(chestPos)) {
                continue;   // 区块没加载：留着（进度还在），加载后再算
            }
            refreshAdjacentPipes(level, chestPos);   // 先对齐邻近机械管（结构可能刚被改过）
            if (!HotChamber.isPart(level, chestPos)) {
                map.remove(chestPos);   // 已不是热室（铜块被拆 / 箱子没了）
                continue;
            }
            if (!HotChamber.isWorkingCell(level, chestPos)) {
                state.idle();   // 结构缺失 / 非代表格：停摆、进度清零
                continue;
            }
            tickChamber(level, chestPos, state);
        }
    }

    /**
     * 推进一台热室一 tick（就地更新 {@code state}）。
     * <p>顺带算好 Jade 的<strong>配方进度快照</strong> —— 口径照大机器：剩余时长按<strong>实际刻</strong>算
     * （进度 ÷ 倍速），倍速已经≥一个周期（每刻都能完成一条）就是「满速」({@code 0})。
     */
    private static void tickChamber(ServerLevel level, BlockPos chestPos, ChamberState state) {
        BlockPos copperPos = chestPos.below();
        ThermalLoad load = ThermalNetwork.loadAt(level, copperPos);
        ThermalNetwork.syncAlarm(level, copperPos);   // 超载时让本网络的铜管发红闪
        double speed = load.speedFor(CopperHeat.BASE_FLOW_RATE);
        int pressure = HotChamberPressureData.get(level).get(chestPos);
        Container container = speed > 0.0D ? HotChamber.containerAt(level, chestPos) : null;
        if (container == null) {
            state.idle();   // 不过热 / 箱子不在：停手，进度清零
            return;
        }
        // 铜系列装备修复优先：箱内有受损铜装备时，本周期用于修复而不是生产配方
        int repairSlot = CopperGear.findDamaged(container);
        if (repairSlot >= 0) {
            tickCopperRepair(level, chestPos, container, repairSlot, speed, state);
            return;
        }
        Optional<HotChamberRecipe> found = HotChamberRecipes.find(container, pressure);
        if (found.isEmpty()) {
            state.idle();   // 没匹配到配方（没进料、产物满、结构缺失）：停手，进度清零
            return;
        }
        HotChamberRecipe recipe = found.get();
        double duration = Math.max(1, recipe.duration());

        // 过热：冒热尘 + 少量火星（只在通电工作时；判据与 Jade 的「过热中」一致）
        if (level.getGameTime() % HEAT_PARTICLE_INTERVAL == 0L) {
            CopperHeat.spawnHeatParticles(level, chestPos);
        }

        double next = state.progress + speed;
        if (next < duration) {
            state.progress = next;
        } else {
            HotChamberRecipes.consume(container, recipe);
            HotChamberRecipes.produce(level, container, recipe);
            state.progress = next - duration;   // 每刻最多结算一条（与木桶 / 熔炉同口径）
        }
        state.remainingTicks = speed >= duration ? 0 : (int) Math.ceil((duration - state.progress) / speed);
        state.ratio = Math.min(1.0D, state.progress / duration);
    }

    /**
     * 推进铜装备修复一个周期：每 {@link CopperGear#REPAIR_DURATION} 刻（1.0 倍速）修复最大耐久的 10%。
     * 箱内有多件受损铜装备时，按槽位顺序一件一件修。
     */
    private static void tickCopperRepair(ServerLevel level, BlockPos chestPos, Container container,
                                         int slot, double speed, ChamberState state) {
        if (level.getGameTime() % HEAT_PARTICLE_INTERVAL == 0L) {
            CopperHeat.spawnHeatParticles(level, chestPos);
        }
        double duration = CopperGear.REPAIR_DURATION;
        double next = state.progress + speed;
        if (next < duration) {
            state.progress = next;
        } else {
            ItemStack stack = container.getItem(slot);
            if (CopperGear.isDamaged(stack)) {
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - CopperGear.repairAmount(stack)));
                container.setChanged();
            }
            state.progress = next - duration;
        }
        state.remainingTicks = speed >= duration ? 0 : (int) Math.ceil((duration - state.progress) / speed);
        state.ratio = Math.min(1.0D, state.progress / duration);
    }

    /** 推进一台消耗端一 tick，返回新的进度。 */
    private static double tickConsumer(ServerLevel level, BlockPos containerPos, BlockPos copperPos, double progress) {
        ThermalLoad load = ThermalNetwork.loadAt(level, copperPos);
        ThermalNetwork.syncAlarm(level, copperPos);   // 超载时让本网络的铜管发红闪
        double speed = load.speedFor(CopperHeat.BASE_FLOW_RATE);
        boolean cooker = CopperHeat.isCooker(level, containerPos);
        if (speed <= 0.0D) {
            if (cooker) {
                clearHeat(level, containerPos);   // 断电熄灭（只清我们自己点的那把火）
            }
            return 0.0D;   // 不过热：停手，进度清零
        }

        // 过热：冒热尘 + 少量火星（只在通电工作时；判据与 Jade 的「过热中」一致）
        if (level.getGameTime() % HEAT_PARTICLE_INTERVAL == 0L) {
            CopperHeat.spawnHeatParticles(level, containerPos);
        }

        if (!(level.getBlockEntity(containerPos) instanceof Container container)) {
            return 0.0D;
        }
        if (level.getBlockState(containerPos).is(Blocks.BARREL)) {
            return tickBarrel(level, containerPos, container, speed, progress);
        }
        return tickCooker(level, containerPos, container, speed);
    }

    /**
     * 熔炉 / 烟熏炉 / 高炉：<strong>给原版炉子供能</strong>，不再自己扣料。
     * <p>做法见 {@link HeatCookerAccess}：撑住火焰、把本周期总时长压到
     * {@code 配方时长 / 倍速}，之后原版 tick 自己推进进度、扣料、产出、给经验 ——
     * 于是界面上的<strong>火焰</strong>和<strong>进度箭头</strong>都正常动，服务端行为也一致。
     */
    private static double tickCooker(ServerLevel level, BlockPos pos, Container container, double speed) {
        Optional<AbstractCookingRecipe> found = cookingRecipe(level, level.getBlockState(pos),
                container.getItem(COOKER_INPUT_SLOT));
        if (found.isEmpty() || !(level.getBlockEntity(pos) instanceof HeatCookerAccess cooker)) {
            clearHeat(level, pos);
            return 0.0D;
        }
        AbstractCookingRecipe recipe = found.get();
        ItemStack output = recipe.assemble(new SingleRecipeInput(container.getItem(COOKER_INPUT_SLOT).copyWithCount(1)));
        if (output.isEmpty() || !resultFits(container, output)) {
            clearHeat(level, pos);
            return 0.0D;
        }
        int total = Math.max(1, (int) Math.ceil(Math.max(1.0D, recipe.cookingTime()) / speed));
        cooker.stellar_evolution$heat(total);
        heating(level).add(pos.immutable());
        setLit(level, pos, true);
        return 0.0D;   // 进度交给原版炉子，不再存自建进度
    }

    /** 本世界「由我们供能」的炉子集合。 */
    private static Set<BlockPos> heating(ServerLevel level) {
        return HEATING.computeIfAbsent(level, key -> new HashSet<>());
    }

    /**
     * 熄灭<strong>我们点的那把火</strong>：只有确实由热压差供过能的炉子才清火焰与进度；
     * 炉子自己烧燃料时不碰它，避免把原版燃烧打断。
     */
    private static void clearHeat(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof HeatCookerAccess cooker)) {
            return;
        }
        Set<BlockPos> heating = HEATING.get(level);
        if (heating != null && heating.remove(pos)) {
            cooker.stellar_evolution$unheat();
        }
        // 炉子自己还在烧燃料时不动它的 LIT 外观；否则清掉（也包括旧版本残留的常亮）
        if (!cooker.stellar_evolution$isBurning()) {
            setLit(level, pos, false);
        }
    }

    /** 输出槽（{@code 2}）放不放得下这份产物。 */
    private static boolean resultFits(Container container, ItemStack output) {
        ItemStack result = container.getItem(COOKER_RESULT_SLOT);
        if (result.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(result, output)) {
            return false;
        }
        return result.getCount() + output.getCount() <= result.getMaxStackSize();
    }

    /** 木桶：水平侧有水源才有产出；每 {@value #BARREL_DURATION} 刻（按倍速）滚一次钓鱼战利品写进桶里。 */
    private static double tickBarrel(ServerLevel level, BlockPos pos, Container barrel, double speed, double progress) {
        if (!hasWaterSide(level, pos)) {
            return 0.0D;
        }
        double next = progress + speed;
        if (next < BARREL_DURATION) {
            return next;
        }
        List<ItemStack> drops = rollFishing(level, pos);
        if (drops.isEmpty()) {
            return 0.0D;
        }
        ItemStack drop = drops.getFirst();
        if (drop.isEmpty() || !CopperHeat.canFit(barrel, drop, -1)) {
            return 0.0D;
        }
        CopperHeat.addToContainer(barrel, drop);
        barrel.setChanged();
        return next - BARREL_DURATION;
    }

    // ------------------------------------------------------------------ 工具

    /** 按原版烹饪方块的 {@code LIT} 状态点亮 / 熄灭（只在变化时写方块）。 */
    private static void setLit(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractFurnaceBlock) || state.getValue(AbstractFurnaceBlock.LIT) == lit) {
            return;
        }
        level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, lit), Block.UPDATE_ALL);
    }

    /**
     * <strong>烹饪方块</strong>达到满速（每刻完成一份烹饪）所需的热流率（HP/t）＝ 基准热流率 × 当前配方的
     * {@code cookingTime}。没有可烧物 / 查不到配方时按原版标准 {@value #STANDARD_COOK_DURATION} 刻算。
     * <p>Jade 拿它做「当前热流率」的封顶值（超过它再涨也不会更快）。
     */
    public static double cookerMaxFlowRate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return CopperHeat.BASE_FLOW_RATE * STANDARD_COOK_DURATION;
        }
        return cookingRecipe(level, level.getBlockState(pos), container.getItem(COOKER_INPUT_SLOT))
                .map(recipe -> CopperHeat.BASE_FLOW_RATE * Math.max(1.0D, recipe.cookingTime()))
                .orElse(CopperHeat.BASE_FLOW_RATE * STANDARD_COOK_DURATION);
    }

    /** 按方块种类查对应配方的原版烹饪配方（熔炉→熔炼、烟熏炉→烟熏、高炉→高炉）。 */
    private static Optional<AbstractCookingRecipe> cookingRecipe(ServerLevel level, BlockState state, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        SingleRecipeInput probe = new SingleRecipeInput(stack.copyWithCount(1));
        Block block = state.getBlock();
        if (block instanceof SmokerBlock) {
            return SMOKING_CHECK.getRecipeFor(probe, level).map(CopperHeatDriver::value);
        }
        if (block instanceof BlastFurnaceBlock) {
            return BLASTING_CHECK.getRecipeFor(probe, level).map(CopperHeatDriver::value);
        }
        if (block instanceof AbstractFurnaceBlock) {
            return SMELTING_CHECK.getRecipeFor(probe, level).map(CopperHeatDriver::value);
        }
        return Optional.empty();
    }

    private static AbstractCookingRecipe value(RecipeHolder<? extends AbstractCookingRecipe> holder) {
        return holder.value();
    }

    /**
     * 四个水平侧任意一侧是不是<strong>水源方块</strong>。
     *
     * <p>要点（都踩过坑）：
     * <ul>
     *   <li><strong>必须确认是真正的水方块</strong>（{@code neighbor.is(Blocks.WATER)}）：含水方块（{@code WATERLOGGED} 的台阶 /
     *       栅栏 / 楼梯…）的 {@code getFluidState()} 直接返回 {@code Fluids.WATER.getSource(false)}，
     *       光看流体状态会把它们当成水源（假水源），所以要先卡方块类型。</li>
     *   <li><strong>只认「源」</strong>（{@code isSource()}）：水方块 {@code LEVEL=0} 才是源；流动 / 下落的水
     *       （{@code isSource()} 为 false）不算 —— 需求写的是「水源方块」。</li>
     *   <li><strong>只查四个水平侧</strong>（{@code Direction.Plane.HORIZONTAL}），上下两面不算。</li>
     * </ul>
     */
    private static boolean hasWaterSide(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.is(Blocks.WATER) && neighbor.getFluidState().isSource()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 滚一次钓鱼战利品：按原版权重（85 / 10 / 5）在三张子表里抽一张来滚。
     * 直接滚子表就绕开了根表里「宝藏需要开阔水域」的条件，于是「无视限制、三种战利品都能出」。
     */
    private static List<ItemStack> rollFishing(ServerLevel level, BlockPos pos) {
        ResourceKey<LootTable> key = pickFishingTable(level.getRandom());
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .create(LootContextParamSets.FISHING);
        return table.getRandomItems(params, level.getRandom());
    }

    /** 按原版权重抽一张钓鱼子表。 */
    private static ResourceKey<LootTable> pickFishingTable(RandomSource random) {
        int roll = random.nextInt(FISH_WEIGHT + JUNK_WEIGHT + TREASURE_WEIGHT);
        if (roll < FISH_WEIGHT) {
            return FISHING_FISH;
        }
        return roll < FISH_WEIGHT + JUNK_WEIGHT ? FISHING_JUNK : FISHING_TREASURE;
    }

    private static ResourceKey<LootTable> lootKey(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("minecraft", path));
    }

    private CopperHeatDriver() {}
}
