package dev.znecby.stellar_evolution.tank;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.air.AirFluids;
import dev.znecby.stellar_evolution.large.LargeMachine;
import dev.znecby.stellar_evolution.large.LargeMachineIo;
import dev.znecby.stellar_evolution.large.LargeMachines;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * 大机器物流网络的<strong>虚拟流体源</strong>：原版铜格栅（水）与不锈钢格栅（液态空气）。
 *
 * <ul>
 *   <li><strong>方块本身含水</strong>（原版 waterlogged）：对机器表现为一个 <b>16 B 容量</b>的容器，
 *       每秒尝试补 <b>8 B</b>；3×3×3（含对角、含自身）里每多一个<strong>水源</strong>，
 *       <strong>容量与补水速率都 ×2</strong>（不设上限，超出 long 时按 long 上限）。抽取按事务回滚，机器干跑不会偷水。</li>
 *   <li><strong>本身不含水</strong>：变成泵 —— 每 10 tick 在半径 16 格内由近及远找流体源，
 *       灌进网络里<strong>输入模式</strong>的储罐；整 1000 mB 灌进去才挖掉源方块（参考 Create 动力泵）。</li>
 *   <li><strong>不锈钢格栅</strong>：只供液态空气，<strong>仅主世界</strong>、且任意一面贴着空气方块时生效；
 *       基础 16 B / 8 B/s，每多一个<strong>暴露空气面</strong>容量与产率都 ×2（最多 6 面 = ×64）；不跑水泵。</li>
 * </ul>
 */
public final class GrateSource {
    /** 虚拟容量：16 B。 */
    public static final int CAPACITY = 16_000;
    /** 基础补水速率：每秒 8 B。 */
    public static final double MB_PER_SECOND = 8_000.0D;
    /** 泵扫描半径（格）。 */
    public static final int PUMP_RADIUS = 16;
    /** 每格源方块对应的流体量。 */
    public static final int PUMP_AMOUNT = 1_000;
    /** 泵节流：每 10 tick 抽一格。 */
    private static final int PUMP_INTERVAL = 10;

    /** 每世界一份：格栅位置 → 虚拟缓冲。 */
    private static final Map<Level, Map<BlockPos, Buffer>> BUFFERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** 每世界一份：已接入网络、需要跑泵逻辑的格栅位置。 */
    private static final Map<Level, Set<BlockPos>> REGISTERED =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 虚拟源类型：原版铜格栅 = 水；不锈钢格栅 = 液态空气。 */
    public enum Kind { WATER, AIR }

    /** 该方块属于哪种虚拟源；不是虚拟源返回 null。 */
    @Nullable
    public static Kind kindOf(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            return null;
        }
        if ("minecraft".equals(id.getNamespace()) && id.getPath().endsWith("copper_grate")) {
            return Kind.WATER;
        }
        if (StellarEvolution.MOD_ID.equals(id.getNamespace()) && "steel_grate".equals(id.getPath())) {
            return Kind.AIR;
        }
        return null;
    }

    /** 任意虚拟源（原版铜格栅 / 不锈钢格栅）。 */
    public static boolean isGrate(BlockState state) {
        return kindOf(state) != null;
    }

    /** 钢格栅暴露在空气中的面数（0–6）；仅主世界。 */
    public static int exposedAirFaces(Level level, BlockPos pos) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return 0;
        }
        int faces = 0;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).isAir()) {
                faces++;
            }
        }
        return faces;
    }

    /** 钢格栅是否至少有一面贴着空气方块；仅主世界工作。 */
    public static boolean isExposedToAir(Level level, BlockPos pos) {
        return exposedAirFaces(level, pos) > 0;
    }

    /** 把格栅登记为「接入网络的源」，供泵逻辑遍历。 */
    public static void register(Level level, BlockPos pos) {
        java.util.Set<BlockPos> set = REGISTERED.computeIfAbsent(level, ignored -> ConcurrentHashMap.newKeySet());
        if (set.add(pos.immutable())) {
            dev.znecby.stellar_evolution.StellarEvolution.LOGGER.info(
                    "[格栅] 登记 {} 类型={} 含水={} 暴露空气={} 容量={} mB 产率={} B/s",
                    pos, kindOf(level.getBlockState(pos)), isWaterlogged(level, pos),
                    isExposedToAir(level, pos), capacityAt(level, pos), ratePerSecond(level, pos));
        }
    }

    /** 机器看到的虚拟流体能力。 */
    public static ResourceHandler<FluidResource> handler(Level level, BlockPos pos) {
        return new Handler(level, pos, buffer(level, pos));
    }

    private static Buffer buffer(Level level, BlockPos pos) {
        return BUFFERS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(pos.immutable(), ignored -> new Buffer());
    }

    /** 当前虚拟缓冲（mB）——Jade 显示用（顺带补一次水，读数才准）。 */
    public static long bufferAmount(Level level, BlockPos pos) {
        Buffer buffer = buffer(level, pos);
        refill(level, pos, buffer);
        return (long) Math.floor(buffer.amount);
    }

    /**
     * 当前<strong>容量</strong>（mB）：水格栅基础 {@link #CAPACITY}（16 B），3×3×3 内每多一个水源 ×2；
     * 钢格栅按<strong>暴露空气面数</strong>倍增（每面 ×2，最多 6 面 = ×64）。
     */
    public static long capacityAt(Level level, BlockPos pos) {
        if (kindOf(level.getBlockState(pos)) == Kind.AIR) {
            int faces = exposedAirFaces(level, pos);
            return faces <= 0 ? 0L : capacityFor(faces);
        }
        int extra = Math.max(0, countWaterSources(level, pos) - 1);
        return capacityFor(extra);
    }

    /** 当前产率（B/s）：水格栅基础 8 B/s、每多一个水源 ×2；钢格栅每多一个暴露面 ×2。 */
    public static double ratePerSecond(Level level, BlockPos pos) {
        if (kindOf(level.getBlockState(pos)) == Kind.AIR) {
            int faces = exposedAirFaces(level, pos);
            return faces <= 0 ? 0.0D : MB_PER_SECOND / 1000.0D * Math.pow(2.0D, faces);
        }
        int extra = Math.max(0, countWaterSources(level, pos) - 1);
        return MB_PER_SECOND / 1000.0D * Math.pow(2.0D, extra);
    }

    private static long capacityFor(int extra) {
        double capacity = CAPACITY * Math.pow(2.0D, extra);
        return capacity >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) capacity;
    }

    /** 方块<strong>本身</strong>是否含水（原版 waterlogged 机制：格栅的流体状态就是水）。 */
    public static boolean isWaterlogged(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && fluid.isSource();
    }

    /** 3×3×3（含斜对角）内的<strong>水源</strong>数量（决定产量倍率；流动水不算）。 */
    public static int countWaterSources(Level level, BlockPos pos) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    FluidState fluid = level.getFluidState(p);
                    if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ 缓冲

    private static final class Buffer extends SnapshotJournal<Double> {
        private double amount;
        private long lastTick = Long.MIN_VALUE;

        @Override
        protected Double createSnapshot() {
            return amount;
        }

        @Override
        protected void revertToSnapshot(Double snapshot) {
            amount = snapshot == null ? 0.0D : snapshot;
        }
    }

    private static void refill(Level level, BlockPos pos, Buffer buffer) {
        long now = level.getGameTime();
        if (buffer.lastTick == Long.MIN_VALUE) {
            buffer.lastTick = now;
            return;
        }
        long elapsed = Math.min(200L, now - buffer.lastTick);
        buffer.lastTick = now;
        if (elapsed <= 0) {
            return;
        }
        Kind kind = kindOf(level.getBlockState(pos));
        if (kind == Kind.AIR) {
            int faces = exposedAirFaces(level, pos);
            if (faces <= 0) {
                buffer.amount = 0.0D;   // 被包住 / 非主世界：不供气
                return;
            }
            double perTick = MB_PER_SECOND / 20.0D * Math.pow(2.0D, faces);
            buffer.amount = Math.min(capacityFor(faces), buffer.amount + perTick * elapsed);
            return;
        }
        if (!isWaterlogged(level, pos)) {
            buffer.amount = 0.0D;   // 本身不含水：走泵模式，缓冲清零
            return;
        }
        // 3×3×3（含斜对角、含自身）里的水源数；自身算基础那一份，多出来的每个 ×2
        int exponent = Math.max(0, countWaterSources(level, pos) - 1);
        double perTick = MB_PER_SECOND / 20.0D * Math.pow(2.0D, exponent);
        buffer.amount = Math.min(capacityFor(exponent), buffer.amount + perTick * elapsed);
    }

    private record Handler(Level level, BlockPos pos, Buffer buffer) implements ResourceHandler<FluidResource> {
        private static FluidResource water() {
            return FluidResource.of(new FluidStack(Fluids.WATER, 1));
        }

        private static FluidResource air() {
            return FluidResource.of(new FluidStack(AirFluids.LIQUID_AIR.source().get(), 1));
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            if (index != 0) {
                return FluidResource.EMPTY;
            }
            refill(level, pos, buffer);
            Kind kind = kindOf(level.getBlockState(pos));
            if (kind == Kind.AIR) {
                return isExposedToAir(level, pos) ? air() : FluidResource.EMPTY;
            }
            return isWaterlogged(level, pos) ? water() : FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            if (index != 0) {
                return 0L;
            }
            refill(level, pos, buffer);
            return (long) Math.floor(buffer.amount);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return index == 0 ? capacityAt(level, pos) : 0L;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            if (index != 0) {
                return false;
            }
            return kindOf(level.getBlockState(pos)) == Kind.AIR ? isAir(resource) : isWater(resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;   // 虚拟源：只出不进
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Kind kind = kindOf(level.getBlockState(pos));
            if (index != 0 || amount <= 0) {
                return 0;
            }
            if (kind == Kind.AIR) {
                if (!isAir(resource)) {
                    return 0;
                }
                refill(level, pos, buffer);
                if (!isExposedToAir(level, pos)) {
                    return 0;
                }
            } else {
                if (!isWater(resource)) {
                    return 0;
                }
                refill(level, pos, buffer);
                if (!isWaterlogged(level, pos)) {
                    return 0;
                }
            }
            int moved = (int) Math.min(amount, Math.floor(buffer.amount));
            if (moved <= 0) {
                return 0;
            }
            buffer.updateSnapshots(transaction);
            buffer.amount -= moved;
            return moved;
        }
    }

    private static boolean isWater(FluidResource resource) {
        return !resource.isEmpty() && resource.value() == Fluids.WATER;
    }

    private static boolean isAir(FluidResource resource) {
        return !resource.isEmpty() && resource.value() == AirFluids.LIQUID_AIR.source().get();
    }

    // ------------------------------------------------------------------ 泵模式（服务端 tick 调用）

    /** 每 10 tick 跑一遍：不含水的格栅把世界流体抽进网络输入罐。 */
    public static void pumpTick(ServerLevel level) {
        Set<BlockPos> positions = REGISTERED.get(level);
        if (positions == null || positions.isEmpty() || level.getGameTime() % PUMP_INTERVAL != 0L) {
            return;
        }
        Iterator<BlockPos> iterator = positions.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            BlockState state = level.getBlockState(pos);
            if (!isGrate(state)) {
                iterator.remove();
                continue;
            }
            if (kindOf(state) != Kind.WATER) {
                continue;   // 钢格栅只供气，不跑水泵
            }
            if (!level.isLoaded(pos) || isWaterlogged(level, pos)) {
                continue;   // 本身含水：走产水模式
            }
            pumpOnce(level, pos);
        }
    }

    private static void pumpOnce(ServerLevel level, BlockPos pos) {
        LargeMachine machine = LargeMachines.containerMachine(level, pos);
        if (machine == null) {
            return;
        }
        BlockPos sourcePos = findFluidSource(level, pos);
        if (sourcePos == null) {
            return;
        }
        FluidState fluid = level.getFluidState(sourcePos);
        FluidResource resource = FluidResource.of(new FluidStack(fluid.getType(), PUMP_AMOUNT));
        if (!deliverToTanks(level, machine, pos, resource)) {
            return;
        }
        level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** 灌进网络里「输入模式」的储罐；灌满 1000 mB 才算成功。 */
    private static boolean deliverToTanks(ServerLevel level, LargeMachine machine, BlockPos self,
                                          FluidResource resource) {
        for (LargeMachines.Connected connected : LargeMachines.connectedContainers(level, machine)) {
            if (!connected.fluid() || connected.pos().equals(self)) {
                continue;
            }
            if (machine.ioMode(connected.pos()) != LargeMachineIo.INPUT) {
                continue;
            }
            ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK,
                    connected.pos(), null);
            if (handler == null) {
                continue;
            }
            boolean filled = false;
            try (Transaction transaction = Transaction.openRoot()) {
                int moved = 0;
                for (int slot = 0; slot < handler.size() && moved < PUMP_AMOUNT; slot++) {
                    moved += handler.insert(slot, resource, PUMP_AMOUNT - moved, transaction);
                }
                if (moved >= PUMP_AMOUNT) {
                    transaction.commit();
                    filled = true;
                }
            }
            if (filled) {
                return true;
            }
        }
        return false;
    }

    /** 半径 16 内由近及远找一个流体源（按壳层扫描，湖里很快能命中）。 */
    @Nullable
    private static BlockPos findFluidSource(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int radius = 1; radius <= PUMP_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int chebyshev = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                        if (chebyshev != radius) {
                            continue;
                        }
                        cursor.setWithOffset(center, dx, dy, dz);
                        if (!level.isLoaded(cursor)) {
                            continue;
                        }
                        FluidState fluid = level.getFluidState(cursor);
                        if (!fluid.isSource() || fluid.isEmpty()) {
                            continue;
                        }
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private GrateSource() {}
}
