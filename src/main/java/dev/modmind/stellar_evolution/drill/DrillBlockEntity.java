package dev.modmind.stellar_evolution.drill;

import dev.modmind.stellar_evolution.bedrock.BedrockBalance;
import dev.modmind.stellar_evolution.bedrock.BedrockMaterials;
import dev.modmind.stellar_evolution.material.OreBlocks;
import dev.modmind.stellar_evolution.pipe.CopperPipeBlock;
import dev.modmind.stellar_evolution.thermal.CopperHeat;
import dev.modmind.stellar_evolution.thermal.ThermalConsumer;
import dev.modmind.stellar_evolution.thermal.ThermalLoad;
import dev.modmind.stellar_evolution.thermal.ThermalNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * 开采钻方块实体：热压差网络的<strong>消耗端</strong> + 两种作业模式。
 *
 * <p><strong>数值（精准采集）</strong>：以 <strong>3×3 区块</strong>为基准 —— 1.0× 需要
 * {@value #ORE_BASE_FLOW_RATE} HP/t、热阻 {@value #ORE_RESISTANCE}（占用 16,384 DP，正好 2 台柴油燃烧引擎）；
 * 1×1 热流相同、热阻减半（4）；周期 {@value #ORE_CYCLE_TICKS} t/层（3×3），按 {@code N/3} 线性变长。
 * 热流按 {@code N/3} 线性增长、热阻按 {@code (N/3)²} 增长，满速上限 = 该范围基准热流的 4 倍。
 *
 * <p><strong>数值（范围破坏）</strong>：以 5×5 方块为基准 —— 基准 {@value #BASE_FLOW_RATE} HP/t、热阻
 * <strong>线性</strong> {@value #THERMAL_RESISTANCE} × (N/5)（63×63 仅 12,902 DP）、周期按面积
 * {@value #CYCLE_TICKS} × (N/5)²、满速上限 {@value #MAX_USEFUL_FLOW_RATE} HP/t（4 倍）。
 *
 * <p><strong>作业</strong>：从机器<strong>正下方一格</strong>开始，每完成一个周期下移一层，到世界底部后停机。
 * 产物全部放进<strong>正上方容器</strong>（与破碎机 / 压模机同口径），没有容器就停机等待。
 * <ul>
 *   <li><strong>范围破坏</strong>：以机器为中心 N×N 方块（N=5..63 奇数）；挖掉一切可破坏、非流体、无方块实体的方块，
 *       掉落按「无附魔下界合金镐」结算（不喷粒子、不产生经验）。</li>
 *   <li><strong>精准采集</strong>：以机器所在区块为中心 N×N 区块（N=5..15 奇数）；只采 {@code c:ores} 标签
 *       + 远古残骸 / 萤石等特殊矿石，产出<strong>方块本身</strong>（精准采集），并把矿洞<strong>回填圆石</strong>。</li>
 * </ul>
 * 两种模式都用「层内游标」把扫描摊到整个周期里（每 tick 最多 {@value #SCAN_BUDGET} 格），大范围也不会卡顿。
 * <p>模式 / 范围 / 当前层 Y 会存盘；进度与待提交方块不存盘（重进世界重扫当前层）。
 */
public class DrillBlockEntity extends BlockEntity implements ThermalConsumer {
    /** 基础热阻（5×5 时占用 = 本值 × 256 = 1024 DP）。 */
    public static final double THERMAL_RESISTANCE = 4.0D;
    /** 基准热流率（HP/t）：达到 1.0 倍速所需。 */
    public static final double BASE_FLOW_RATE = 256.0D;
    /** 一个基础周期（刻）：处理一层。 */
    public static final double CYCLE_TICKS = 200.0D;
    /** 最高可用热流率（HP/t）：4 倍速封顶。 */
    public static final double MAX_USEFUL_FLOW_RATE = 1024.0D;
    /** 基岩模式的门槛热流率（HP/t）：低于它直接停机。 */
    public static final double BEDROCK_MIN_FLOW_RATE = BedrockBalance.DRILL_MIN_FLOW;
    /** 基岩模式的最高可用热流率（HP/t）：8 倍速封顶。 */
    public static final double BEDROCK_MAX_USEFUL_FLOW_RATE = 2_048.0D;
    /** 基岩模式一个周期（刻）：基准 9600t；8 倍速下约 1200t / 矿。 */
    public static final double BEDROCK_CYCLE_TICKS = 9_600.0D;
    /** 每 tick 最多检查的方块数（把大范围的扫描摊开）。 */
    private static final int SCAN_BUDGET = 512;

    /** 范围破坏：边长（方块）最小 / 最大。 */
    public static final int AREA_MIN = 5;
    public static final int AREA_MAX = 63;
    /** 精准采集：边长（区块）最小 / 最大（公式见 {@link DrillBalance}）。 */
    public static final int ORE_MIN = DrillBalance.ORE_MIN;
    public static final int ORE_MAX = DrillBalance.ORE_MAX;
    /** 精准采集：3×3 的 1.0× 热流（HP/t）—— 单测与文档引用用。 */
    public static final double ORE_BASE_FLOW_RATE = DrillBalance.ORE_BASE_FLOW_RATE;
    /** 精准采集：3×3 的热阻。 */
    public static final double ORE_RESISTANCE = DrillBalance.ORE_RESISTANCE;
    /** 精准采集：3×3 的每层周期（刻）。 */
    public static final double ORE_CYCLE_TICKS = DrillBalance.ORE_CYCLE_TICKS;

    /** 作业模式。 */
    public enum Mode {
        /** 范围破坏：挖掉范围内所有可破坏方块。 */
        AREA,
        /** 精准采集：只采矿石、回填圆石。 */
        ORE;

        public Mode next() {
            return this == AREA ? ORE : AREA;
        }
    }

    /** 运行状态（Jade / 调试用）。 */
    public enum State {
        /** 正上方没有容器。 */
        NO_CONTAINER,
        /** 等待容器腾出空间（本层已扫完但产物放不下）。 */
        WAITING,
        /** 没有热压差。 */
        NO_HEAT,
        /** 正在工作。 */
        WORKING,
        /** 已经挖到世界底部。 */
        DONE
    }

    /** 一条待提交的记录：位置 + 扫描时的方块状态 + 是否回填圆石 + 掉落物。 */
    private record Pending(BlockPos pos, BlockState original, boolean ore, List<ItemStack> drops) {}

    /** 范围破坏用的「工具」：无附魔下界合金镐（只影响掉落判定，不受损）。 */
    private static final Item STACK_TOOL = Items.NETHERITE_PICKAXE;

    /** 当前模式。 */
    private Mode mode = Mode.AREA;
    /** 范围破坏边长（方块，奇数）。 */
    private int areaRange = AREA_MIN;
    /** 精准采集边长（区块，奇数）。 */
    private int oreRange = ORE_MIN;
    /** 当前作业层 Y；{@link Integer#MIN_VALUE} = 尚未初始化。 */
    private int layerY = Integer.MIN_VALUE;
    /** 当前周期进度（0..{@value #CYCLE_TICKS}）。 */
    private double progress;
    /** 本层已扫描到的下标。 */
    private int scanIndex;
    /** 本层待提交的方块。 */
    private final List<Pending> pending = new ArrayList<>();
    /** 当前状态。 */
    private State state = State.NO_CONTAINER;
    /** Jade：本周期剩余刻数（{@code -1} = 没在加工）。 */
    private int jadeRemaining = -1;
    /** Jade：本周期进度 0..1。 */
    private double jadeProgress;
    /** Jade：当前 HP 倍速（0 = 停机）。 */
    private double jadeSpeed;
    /** 服务端权威：是否正在工作（同步给客户端决定要不要播动画）。 */
    private boolean working;
    /** 已运行时长（tick），只在工作时推进。 */
    private float age;
    /** 上一 tick 的运行时长，供插值。 */
    private float lastAge;
    /** 上一 tick 是否处于基岩模式（只在内存里，用来在进出基岩时清进度）。 */
    private boolean wasBedrock;

    public DrillBlockEntity(BlockPos pos, BlockState state) {
        super(DrillBlocks.DRILL_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DrillBlockEntity drill) {
        drill.lastAge = drill.age;
        if (level.isClientSide()) {
            if (drill.working) {
                drill.age += 1.0F;
            }
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            // 老存档自愈：旧铜管的连接位不会因代码更新重算，每秒补一次管臂
            if (serverLevel.getGameTime() % 20L == 0L) {
                CopperPipeBlock.refreshAround(serverLevel, pos);
            }
            drill.onThermalTick(ThermalNetwork.loadAt(serverLevel, pos));
        }
    }

    // ------------------------------------------------------------------ ThermalConsumer

    @Override
    public double thermalResistance() {
        if (usesOreBalance()) {
            return DrillBalance.resistance(oreRange);
        }
        return isBedrockMode() ? THERMAL_RESISTANCE : DrillBalance.areaResistance(areaRange);
    }

    @Override
    public double baseFlowRate() {
        if (usesOreBalance()) {
            return DrillBalance.baseFlow(oreRange);
        }
        return BASE_FLOW_RATE;
    }

    /** 满速上限：精准采集 = 该范围基准热流的 4 倍；范围破坏沿用旧值；基岩 8 倍速。 */
    @Override
    public double maxUsefulFlowRate() {
        if (isBedrockMode()) {
            return BEDROCK_MAX_USEFUL_FLOW_RATE;
        }
        return usesOreBalance()
                ? DrillBalance.maxUsefulFlow(oreRange) : MAX_USEFUL_FLOW_RATE;
    }

    /** 当前模式的最大倍速（Jade 用）。 */
    public double maxSpeedMultiplier() {
        return maxUsefulFlowRate() / baseFlowRate();
    }

    /** 是否走「精准采集」的新平衡（基岩模式优先，仍用旧口径）。 */
    private boolean usesOreBalance() {
        return !isBedrockMode() && mode == Mode.ORE;
    }

    /** 当前模式每层周期（刻）：精准采集 N/3 线性；范围破坏按面积 (N/5)²。 */
    private double cycleTicks() {
        if (usesOreBalance()) {
            return DrillBalance.cycleTicks(oreRange);
        }
        return isBedrockMode() ? CYCLE_TICKS : DrillBalance.areaCycleTicks(areaRange);
    }

    /** 正下方是不是基岩：是则进入「基岩矿」固定模式（不存盘、实时判定）。 */
    public boolean isBedrockMode() {
        Level level = getLevel();
        return level != null && level.getBlockState(getBlockPos().below()).is(Blocks.BEDROCK);
    }

    /** 消耗端自驱：取网络账本 → 找容器 → 扫层 / 提交。 */
    @Override
    public void onThermalTick(ThermalLoad load) {
        if (!(getLevel() instanceof ServerLevel level)) {
            return;
        }
        ThermalNetwork.syncAlarm(level, getBlockPos());

        // 上方落点：原版容器或其它模组的物品能力（精妙存储 / 背包等）
        BlockPos outputPos = getBlockPos().above();
        if (!CopperHeat.isItemTarget(level, outputPos)) {
            pause(State.NO_CONTAINER);
            return;
        }

        boolean bedrock = isBedrockMode();
        double speed = Math.min(load.speedFor(baseFlowRate()), maxSpeedMultiplier());
        if (bedrock) {
            if (!wasBedrock) {
                wasBedrock = true;
                progress = 0.0D;
                scanIndex = 0;
                pending.clear();
            }
            if (load.flowRate() < BEDROCK_MIN_FLOW_RATE || speed <= 0.0D) {
                pause(State.NO_HEAT);
                return;
            }
            tickBedrock(level, outputPos, speed);
            return;
        }
        if (wasBedrock) {
            wasBedrock = false;
            progress = 0.0D;
            scanIndex = 0;
            pending.clear();
        }

        if (layerY == Integer.MIN_VALUE) {
            layerY = getBlockPos().getY() - 1;
        }
        if (layerY < level.getMinY()) {
            pause(State.DONE);
            return;
        }
        if (speed <= 0.0D) {
            pause(State.NO_HEAT);
            return;
        }
        jadeSpeed = speed;
        int total = layerTotal();
        double cycle = cycleTicks();
        // 本层已扫完但产物放不下：先停住，别在「工作 → 等待」之间每 tick 来回切换（会每 tick 发包）
        if (progress >= cycle && scanIndex >= total && !fits(level, outputPos)) {
            pause(State.WAITING);
            return;
        }
        state = State.WORKING;
        setWorking(true);
        if (progress >= cycle) {
            jadeRemaining = 0;
            jadeProgress = 1.0D;
        }

        if (progress >= cycle && scanIndex < total) {
            scanUpTo(level, total);   // 先把本层扫完（大范围会跨多 tick）
            if (scanIndex < total) {
                return;
            }
        }
        if (progress >= cycle) {
            if (!fits(level, outputPos)) {
                pause(State.WAITING);
                return;
            }
            commit(level, outputPos);
            return;
        }

        progress = Math.min(cycle, progress + speed);
        jadeRemaining = (int) Math.ceil(Math.max(0.0D, cycle - progress) / speed);
        jadeProgress = Math.min(1.0D, progress / cycle);
        int target = (int) Math.ceil(progress * total / cycle);
        if (scanIndex < target) {
            scanUpTo(level, target);
        }
        if (progress >= cycle && scanIndex >= total) {
            if (!fits(level, outputPos)) {
                pause(State.WAITING);
                return;
            }
            commit(level, outputPos);
        }
    }

    // ------------------------------------------------------------------ 扫描 / 提交

    /** 本层边长（方块）：范围破坏 = N，精准采集 = N 区块 × 16。 */
    private int layerSide() {
        return mode == Mode.AREA ? areaRange : oreRange * 16;
    }

    /** 本层要检查的方块数。 */
    private int layerTotal() {
        int side = layerSide();
        return side * side;
    }

    /** 层内第 index 个位置。 */
    private BlockPos positionAt(int index) {
        int side = layerSide();
        int dx = index % side;
        int dz = index / side;
        BlockPos origin = getBlockPos();
        if (mode == Mode.AREA) {
            int half = areaRange / 2;
            return new BlockPos(origin.getX() + dx - half, layerY, origin.getZ() + dz - half);
        }
        int halfChunks = oreRange / 2;
        int baseX = ((origin.getX() >> 4) - halfChunks) << 4;
        int baseZ = ((origin.getZ() >> 4) - halfChunks) << 4;
        return new BlockPos(baseX + dx, layerY, baseZ + dz);
    }

    /** 扫描到 target（层内下标上界）；每 tick 最多 {@value #SCAN_BUDGET} 格。未加载的区块直接跳过。 */
    private void scanUpTo(ServerLevel level, int target) {
        int total = layerTotal();
        int budget = SCAN_BUDGET;
        FakePlayer fake = null;
        ItemStack tool = null;
        while (scanIndex < target && scanIndex < total && budget-- > 0) {
            BlockPos pos = positionAt(scanIndex++);
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (mode == Mode.ORE) {
                if (!OreBlocks.isOre(current)) {
                    continue;
                }
                Item item = current.getBlock().asItem();
                if (item == Items.AIR) {
                    continue;
                }
                pending.add(new Pending(pos, current, true, List.of(new ItemStack(item))));
            } else {
                if (!canBreak(level, pos, current)) {
                    continue;
                }
                if (fake == null) {
                    fake = FakePlayerFactory.getMinecraft(level);
                    tool = new ItemStack(STACK_TOOL);
                }
                List<ItemStack> drops = Block.getDrops(current, level, pos, null, fake, tool);
                List<ItemStack> copy = new ArrayList<>(drops.size());
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        copy.add(drop.copy());
                    }
                }
                pending.add(new Pending(pos, current, false, copy));
            }
        }
    }

    /** 范围破坏的跳过规则：空气 / 流体 / 方块实体（刷怪笼、箱子、其它机器…）/ 不可破坏方块。 */
    private static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.liquid() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        Block block = state.getBlock();
        return block != Blocks.END_PORTAL && block != Blocks.END_GATEWAY && block != Blocks.NETHER_PORTAL;
    }

    /** 待提交产物的合计能不能塞进上方落点（原版容器在副本里模拟；模组能力用回滚事务）。 */
    private boolean fits(ServerLevel level, BlockPos outputPos) {
        List<ItemStack> drops = new ArrayList<>();
        for (Pending entry : pending) {
            drops.addAll(entry.drops());
        }
        return CopperHeat.canFitAt(level, outputPos, drops);
    }

    /** 基岩模式：固定 1×1，不破坏基岩、不递减层数；每周期产出 1 个基岩矿到上方容器。 */
    private void tickBedrock(ServerLevel level, BlockPos outputPos, double speed) {
        state = State.WORKING;
        setWorking(true);
        jadeSpeed = speed;
        progress = Math.min(BEDROCK_CYCLE_TICKS, progress + speed);
        jadeRemaining = (int) Math.ceil(Math.max(0.0D, BEDROCK_CYCLE_TICKS - progress) / speed);
        jadeProgress = Math.min(1.0D, progress / BEDROCK_CYCLE_TICKS);
        if (progress < BEDROCK_CYCLE_TICKS) {
            return;
        }
        ItemStack ore = new ItemStack(BedrockMaterials.BEDROCK_ORE.get());
        if (!CopperHeat.canFitAt(level, outputPos, ore)) {
            pause(State.WAITING);
            return;
        }
        CopperHeat.insertAt(level, outputPos, ore);
        progress = 0.0D;
        jadeProgress = 0.0D;
        jadeRemaining = 0;
        setChanged();
    }

    /** 提交本层：改方块 + 产物并入上方容器 + 下移一层。 */
    private void commit(ServerLevel level, BlockPos outputPos) {
        for (Pending entry : pending) {
            BlockPos pos = entry.pos();
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            if (level.getBlockState(pos) != entry.original()) {
                continue;   // 扫描后被别人改过：不覆盖
            }
            level.setBlock(pos, entry.ore() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            for (ItemStack drop : entry.drops()) {
                CopperHeat.insertAt(level, outputPos, drop.copy());
            }
        }
        pending.clear();
        progress = 0.0D;
        scanIndex = 0;
        layerY--;
        setChanged();
    }

    /** 暂停（不丢进度 / 待提交层）：容器缺失、满了、没热压差或到底时调用。 */
    private void pause(State newState) {
        state = newState;
        jadeRemaining = -1;
        jadeProgress = 0.0D;
        if (newState != State.WAITING) {
            jadeSpeed = 0.0D;   // 等待容器空间时保留倍速，方便 Jade 继续显示「HP 提速」
        }
        setWorking(false);
    }

    /** 「是否在工作」变化时才写盘 + 发包（客户端据此决定动画播不播）。 */
    private void setWorking(boolean now) {
        if (working == now) {
            return;
        }
        working = now;
        setChanged();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ 模式 / 范围

    public Mode mode() {
        return mode;
    }

    public State state() {
        return state;
    }

    /** 当前模式的范围值（UI 预填 / 叠加提示用）。 */
    public int rangeFor(Mode which) {
        return which == Mode.AREA ? areaRange : oreRange;
    }

    public static int minFor(Mode which) {
        return which == Mode.AREA ? AREA_MIN : ORE_MIN;
    }

    public static int maxFor(Mode which) {
        return which == Mode.AREA ? AREA_MAX : ORE_MAX;
    }

    /** 剪刀右键（未蹲下）：切换模式，并从机器下方重新开始；基岩模式下锁定，不响应。 */
    public void toggleMode() {
        if (isBedrockMode()) {
            return;
        }
        mode = mode.next();
        resetJob();
        setChanged();
    }

    /** UI 提交：写回对应范围；值变了才从机器下方重新开始；基岩模式下锁定，不响应。 */
    public void setRange(Mode which, int value) {
        if (isBedrockMode()) {
            return;
        }
        int normalized = normalizeRange(value, minFor(which), maxFor(which));
        if (which == Mode.AREA) {
            if (areaRange == normalized) {
                return;
            }
            areaRange = normalized;
        } else {
            if (oreRange == normalized) {
                return;
            }
            oreRange = normalized;
        }
        resetJob();
        setChanged();
    }

    /** 奇数化 + 夹到 [min, max]。 */
    private static int normalizeRange(int value, int min, int max) {
        int clamped = Math.max(min, Math.min(max, value));
        if ((clamped & 1) == 0) {
            clamped = clamped < max ? clamped + 1 : clamped - 1;
        }
        return clamped;
    }

    /** 从机器下方一格重新开始：清进度 / 游标 / 待提交层。 */
    private void resetJob() {
        layerY = getBlockPos().getY() - 1;
        progress = 0.0D;
        scanIndex = 0;
        pending.clear();
        state = State.NO_CONTAINER;
        jadeRemaining = -1;
        jadeProgress = 0.0D;
        jadeSpeed = 0.0D;
        setWorking(false);
    }

    // ------------------------------------------------------------------ 渲染 / 存盘

    /** Jade：本周期剩余刻数（{@code -1} = 没在加工）。 */
    public int jadeRemainingTicks() {
        return jadeRemaining;
    }

    /** Jade：本周期进度 0..1。 */
    public double jadeProgress() {
        return jadeProgress;
    }

    /** Jade：当前倍速（0 = 停机；1.0 = 该范围 1× 基准热流，精准采集为 2,048×N/3 HP/t）。 */
    public double speedMultiplier() {
        return jadeSpeed;
    }

    /** 本层已到周期末尾、但还在补扫剩余方块（大范围 / 高倍速时出现）；基岩模式不扫描。 */
    public boolean isScanning() {
        return !isBedrockMode() && state == State.WORKING
                && progress >= cycleTicks() && scanIndex < layerTotal();
    }

    /** 扫描进度 0..1（Jade 在「扫描中」时用它当进度条）。 */
    public double scanProgress() {
        int total = layerTotal();
        return total <= 0 ? 1.0D : Math.min(1.0D, (double) scanIndex / total);
    }

    /** 供渲染器取插值后的运行时长。 */
    public float age(float partialTick) {
        return Mth.lerp(partialTick, lastAge, age);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("mode", mode.ordinal());
        output.putInt("area", areaRange);
        output.putInt("ore", oreRange);
        output.putInt("layerY", layerY);
        output.putBoolean("working", working);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int modeIndex = Math.max(0, Math.min(Mode.values().length - 1, input.getIntOr("mode", 0)));
        mode = Mode.values()[modeIndex];
        areaRange = normalizeRange(input.getIntOr("area", AREA_MIN), AREA_MIN, AREA_MAX);
        oreRange = normalizeRange(input.getIntOr("ore", ORE_MIN), ORE_MIN, ORE_MAX);
        layerY = input.getIntOr("layerY", Integer.MIN_VALUE);
        working = input.getBooleanOr("working", false);
        progress = 0.0D;
        scanIndex = 0;
        pending.clear();
    }

    /** 整份带去客户端：进世界 / 区块加载时同步「是否在工作」（供动画）。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
