package dev.znecby.stellar_evolution.multiblock;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 固定多方块<strong>控制器方块实体</strong>的公共基类。
 *
 * <p>具体机器继承本类，实现 {@link #createPattern()} 并可选覆盖
 * {@link #onFormed()} / {@link #onUnformed()} / {@link #tickFormed(ServerLevel)}。
 *
 * <p><strong>校验时机</strong>：放置、旋转、世界加载、区块加载、结构内方块变化。
 * 成型后缓存结构坐标与端口；邻居变化只查变化点，不做每 tick 全量扫描。
 *
 * <p><strong>固定舱室</strong>：端口位置和角色全部写在图案里，成型时收集对应
 * {@link MultiblockPartBlockEntity}；不搜索部件、不做能力路由。
 *
 * <p><strong>大图案</strong>：一次全量校验是 O(格数)，只在事件触发时执行；结构可以跨区块，
 * 但不会常驻强制加载。
 */
public abstract class MultiblockControllerBlockEntity extends BlockEntity {
    private @Nullable MultiblockPattern pattern;

    private boolean formed;
    private boolean validationQueued;
    private boolean fullValidation = true;
    private int validationDelay;

    private @Nullable String lastError;
    private @Nullable BlockPos lastErrorPos;

    /** 成型后的世界坐标 → 期望格（用于单点失效判断）。 */
    private final Map<BlockPos, MultiblockCell> formedCells = new HashMap<>();

    private Direction lastFacing;

    protected MultiblockControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.lastFacing = facing();
    }

    // ------------------------------------------------------------------ 具体机器实现

    /** 具体机器的固定图案；只会在服务端第一次使用时调用。 */
    protected abstract MultiblockPattern createPattern();

    /** 刚成型（或重新成型）。 */
    protected void onFormed() {}

    /** 结构失效 / 控制器被拆。 */
    protected void onUnformed() {}

    /** 成型后每 tick（服务端）。 */
    protected void tickFormed(ServerLevel level) {}

    /** 未成型时每 tick（服务端）；默认什么都不做。 */
    protected void tickUnformed(ServerLevel level) {}

    /** 客户端每 tick；默认什么都不做。 */
    protected void tickClient() {}

    /** 成型后右键；默认不消费（留给具体机器开界面）。 */
    protected InteractionResult onUseFormed(Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    public void onLoad() {
        super.onLoad();
        this.lastFacing = facing();
        if (level instanceof ServerLevel serverLevel) {
            this.pattern = createPattern();
            if (this.pattern == null) {
                StellarEvolution.LOGGER.error("多方块控制器 {} 没有图案（createPattern 返回 null）", worldPosition);
            }
            // 重进世界一律重新校验；不要相信存档里的 formed
            this.formed = false;
            clearStructureState();
            MultiblockManager.get(serverLevel).register(this);
            requestValidation(true, 2);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            MultiblockManager.get(serverLevel).unregister(this);
        }
        unformInternal();
        super.setRemoved();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MultiblockControllerBlockEntity controller) {
        controller.tick(level);
    }

    private void tick(Level level) {
        if (level.isClientSide()) {
            tickClient();
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Direction current = facing();
        if (current != lastFacing) {
            lastFacing = current;
            MultiblockManager.get(serverLevel).unregister(this);
            MultiblockManager.get(serverLevel).register(this);
            requestValidation(true, 2);
        }

        if (validationQueued) {
            if (--validationDelay <= 0) {
                validationQueued = false;
                boolean full = fullValidation;
                fullValidation = false;
                if (full) {
                    validateStructure(serverLevel);
                }
            }
        }

        if (formed) {
            tickFormed(serverLevel);
        } else {
            tickUnformed(serverLevel);
        }
    }

    // ------------------------------------------------------------------ 校验

    /** 请求一次校验；{@code full=true} 会全量扫描，{@code full=false} 也按全量处理（单点路径在外部事件里）。 */
    public void requestValidation(boolean full, int delay) {
        if (full) {
            fullValidation = true;
        }
        int clamped = Math.max(1, delay);
        if (!validationQueued || clamped < validationDelay) {
            validationDelay = clamped;
            validationQueued = true;
        }
    }

    /** 全量校验：逐格比对图案；跨区块未加载时保持现状并稍后重试。 */
    private void validateStructure(ServerLevel level) {
        MultiblockPattern currentPattern = pattern();
        if (currentPattern == null) {
            fail("没有图案", worldPosition, null);
            return;
        }

        Map<BlockPos, MultiblockCell> foundCellMap = new HashMap<>(currentPattern.cells().size());
        Direction facing = facing();

        for (Map.Entry<BlockPos, MultiblockCell> entry : currentPattern.cells().entrySet()) {
            BlockPos local = entry.getKey();
            MultiblockCell expected = entry.getValue();
            BlockPos world = currentPattern.toWorld(worldPosition, facing, local);

            if (!level.isLoaded(world)) {
                // 区块没加载：不要拆结构，等区块加载事件再校验
                requestValidation(true, 20);
                return;
            }

            BlockState actual = level.getBlockState(world);
            if (!expected.matches(actual)) {
                fail("结构方块不匹配", world, expected);
                return;
            }

            if (expected instanceof MultiblockCell.ControllerCell) {
                if (actual.getBlock() != getBlockState().getBlock()) {
                    fail("控制器位置不匹配", world, expected);
                    return;
                }
            }

            foundCellMap.put(world, expected);
        }

        applyFormed(foundCellMap);
    }

    private void applyFormed(Map<BlockPos, MultiblockCell> foundCells) {
        boolean wasFormed = formed;
        boolean stateChanged = !wasFormed || lastError != null;
        formed = true;
        lastError = null;
        lastErrorPos = null;

        formedCells.clear();
        formedCells.putAll(foundCells);

        if (!wasFormed) {
            onFormed();
        }
        if (stateChanged) {
            setChanged();
            sync();
        }
    }

    private void fail(String message, BlockPos pos, @Nullable MultiblockCell expected) {
        String detail = expected == null ? message : message + "（需要 " + expected.describe() + "）";
        lastError = detail + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        lastErrorPos = pos;
        unformInternal();
        setChanged();
        sync();
    }

    private void unformInternal() {
        boolean wasFormed = formed;
        formed = false;
        formedCells.clear();
        if (wasFormed) {
            onUnformed();
        }
    }

    private void clearStructureState() {
        formedCells.clear();
    }

    // ------------------------------------------------------------------ 外部方块变化 / 区块加载

    /** 结构包围盒内某格发生变化：成型时只查这一格；未成型时安排一次全量校验。 */
    public void onExternalBlockChanged(BlockPos changed) {
        if (level == null || level.isClientSide()) {
            return;
        }
        MultiblockPattern currentPattern = pattern();
        if (currentPattern == null) {
            return;
        }
        if (!currentPattern.boundsContains(worldPosition, facing(), changed)) {
            return;
        }
        if (!formed) {
            requestValidation(true, 2);
            return;
        }

        MultiblockCell expected = formedCells.get(changed);
        if (expected == null) {
            // 在包围盒内但不在当前缓存里：安排一次全量校验兜底
            requestValidation(true, 2);
            return;
        }
        BlockState actual = level.getBlockState(changed);
        if (!expected.matches(actual)) {
            fail("结构方块被改变", changed, expected);
        }
    }

    /**
     * 方块<strong>即将</strong>变化（破坏 / 放置事件触发时，方块还没落位）。
     *
     * <p>这类事件里读到的仍是旧状态，不能按 {@link #onExternalBlockChanged} 判断，
     * 一律延迟 2 tick 做一次全量校验：拆掉结构方块会因此取消成型，补回方块会因此重新成型。
     */
    public void onExternalBlockWillChange(BlockPos changed) {
        if (level == null || level.isClientSide()) {
            return;
        }
        MultiblockPattern currentPattern = pattern();
        if (currentPattern == null || !currentPattern.boundsContains(worldPosition, facing(), changed)) {
            return;
        }
        requestValidation(true, 2);
    }

    /** 区块加载完成：重新校验，避免跨区块结构在卸载期间被误拆。 */
    public void onChunkLoaded() {
        if (level instanceof ServerLevel) {
            requestValidation(true, 2);
        }
    }

    public void onControllerRemoved() {
        unformInternal();
        setChanged();
        sync();
    }

    // ------------------------------------------------------------------ 右键

    public InteractionResult onUse(Player player, BlockHitResult hit) {
        if (formed) {
            return onUseFormed(player, hit);
        }
        if (level != null && !level.isClientSide()) {
            StellarEvolution.LOGGER.info("多方块 {} 未成型：{}", worldPosition,
                    lastError == null ? "结构未完成" : lastError);
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ 查询

    @Nullable
    public MultiblockPattern pattern() {
        if (pattern == null) {
            pattern = createPattern();
        }
        return pattern;
    }

    public boolean isFormed() {
        return formed;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(MultiblockControllerBlock.FACING)
                ? state.getValue(MultiblockControllerBlock.FACING)
                : Direction.NORTH;
    }

    @Nullable
    public String lastError() {
        return lastError;
    }

    @Nullable
    public BlockPos lastErrorPos() {
        return lastErrorPos;
    }

    /** 本控制器包围盒覆盖的区块（按当前朝向实时计算）。 */
    public Set<ChunkPos> boundsChunks() {
        MultiblockPattern currentPattern = pattern();
        return currentPattern == null ? Set.of() : currentPattern.boundsChunks(worldPosition, facing());
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("formed", formed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        formed = input.getBooleanOr("formed", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
