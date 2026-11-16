package dev.znecby.stellar_evolution.tank;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 储罐方块实体：<strong>每格 8 / 32 / 128 桶</strong>（铜 / 钢 / 硬质，见 {@link TankTier}）任意流体，
 * 可照机械动力的口径<strong>合并成大罐</strong>（上下叠、3×3，见 {@link TankMultiblock}）。
 * <p>
 * <b>多方块口径</b>：合并后只有一个「控制器」（本罐最小角那一格，{@link #isController()}）真正存流体，
 * 其余格（部件）把能力<strong>转发</strong>给控制器 —— 管道接任意一格都通，容量 = 格数 × 本档位单格容量。
 * 控制器 = {@code null} 的 {@link #controller} 表示「我就是控制器」。
 * <p>
 * <b>存盘 / 同步</b>：本体的 {@link #fluids} 走 {@code ValueIOSerializable}，尺寸（宽 / 高）与控制器坐标
 * 单独存；容量不入盘（载入时按宽高重算）。客户端靠 {@link #getUpdateTag} 拿到尺寸与流体，
 * 渲染器据此把液面画满整个大罐。
 */
public class TankBlockEntity extends BlockEntity {
    /** 本档位（铜 / 钢 / 硬质），决定单格容量。 */
    private final TankTier tier;

    /** 真正存流体的那一个罐（只有控制器会用它；部件的容量恒为 0）。 */
    private final TankInventory fluids = new TankInventory();
    /** 控制器坐标；{@code null} = 本格就是控制器。 */
    @Nullable
    private BlockPos controller;
    /** 合并后的水平边长（1 / 3 格；不做 2×2）。 */
    private int width = 1;
    /** 合并后的竖直高度（格）。 */
    private int height = 1;
    /** 是否开窗（3×3 只有四条边的中间格开窗，其余为金属板）。 */
    private boolean window = true;
    /** 需要重算连通性（放置 / 破坏 / 载入时置位，ticker 里处理，避免递归改方块）。 */
    private boolean updateConnectivity = true;

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(TankBlocks.TANK_ENTITY.get(), pos, state);
        this.tier = state.getBlock() instanceof TankBlock tank ? tank.tier() : TankTier.COPPER;
        // 单格罐先按本档位容量起步（合并后由 configure 改成 格数 × 容量）
        fluids.capacity(tier.capacityPerBlock());
    }

    /** 本档位（铜 / 钢 / 硬质）。 */
    public TankTier tier() {
        return tier;
    }

    /** 本档位单格容量（mB）。 */
    public int capacityPerBlock() {
        return tier.capacityPerBlock();
    }

    // ------------------------------------------------------------------ tick：只处理「待重算的连通性」

    public static void tick(Level level, BlockPos pos, BlockState state, TankBlockEntity tank) {
        if (!tank.updateConnectivity) {
            return;
        }
        tank.updateConnectivity = false;
        if (level instanceof ServerLevel serverLevel) {
            TankMultiblock.resolve(serverLevel, pos);
        }
    }

    /** 放置 / 破坏后请求下一 tick 重算多方块。 */
    public void scheduleConnectivity() {
        this.updateConnectivity = true;
    }

    /** 清掉「待重算」标记（重算会覆盖整片储罐，清掉可避免同一 tick 重复跑）。 */
    void clearConnectivityRequest() {
        this.updateConnectivity = false;
    }

    // ------------------------------------------------------------------ 控制器 / 尺寸

    public boolean isController() {
        return controller == null;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** 本格所属大罐的控制器；本格即控制器时返回自己；异常（控制器丢了）时返回 {@code null}。 */
    @Nullable
    public TankBlockEntity controller() {
        if (controller == null) {
            return this;
        }
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof TankBlockEntity be ? be : null;
    }

    /** 控制器坐标（{@code null} = 本格就是控制器）；多方块重算前后比对用。 */
    @Nullable
    public BlockPos controllerPos() {
        return controller;
    }

    public boolean window() {
        return window;
    }

    // ------------------------------------------------------------------ 流体能力（部件转发给控制器）

    /** 本格自己的存储（只对控制器有意义）。 */
    public FluidStacksResourceHandler rawFluids() {
        return fluids;
    }

    /** 对外流体能力 / 存储：部件转发给控制器。 */
    public FluidStacksResourceHandler fluids() {
        TankBlockEntity be = controller();
        return be != null ? be.fluids : fluids;
    }

    public ResourceHandler<FluidResource> fluidCapability() {
        return fluids();
    }

    /** 当前罐内流体（空则 {@link FluidStack#EMPTY}），供渲染器 / 命令读取。 */
    public FluidStack fluid() {
        FluidStacksResourceHandler handler = fluids();
        FluidResource resource = handler.getResource(0);
        return resource.isEmpty() ? FluidStack.EMPTY : resource.toStack(handler.getAmountAsInt(0));
    }

    // ------------------------------------------------------------------ 多方块装配（由 TankMultiblock 调用）

    /** 复位成「单格罐」：控制器 = 自己、1×1、开窗（**容量不动**，免得在重算途中出现「存量 > 容量」）。 */
    void resetMulti() {
        boolean changed = controller != null || width != 1 || height != 1 || !window;
        controller = null;
        width = 1;
        height = 1;
        window = true;
        if (changed) {
            setChanged();
        }
    }

    /**
     * 写入合并结果。
     *
     * @param controller 控制器坐标；{@code null} = 本格就是控制器
     * @param capacity   本格应持有的容量（控制器 = 格数 × 8 桶，部件 = 0）
     */
    void configure(@Nullable BlockPos controller, int width, int height, boolean window, int capacity) {
        BlockPos target = controller == null ? null : controller.immutable();
        boolean changed = !Objects.equals(this.controller, target)
                || this.width != width || this.height != height || this.window != window;
        this.controller = target;
        this.width = width;
        this.height = height;
        this.window = window;
        changed |= fluids.capacity(capacity);
        // 容量变小（例如把大罐拆小）时，多出来的那部分直接丢弃 —— 与「拆罐丢流体」同一口径；
        // 也保证「存量 ≤ 容量」这个不变量（Jade 等按 存量/容量 画进度条的外部代码靠它）
        fluids.clamp();
        if (changed) {
            setChanged();
        }
    }

    /** 清空本格存储（合并时「放不下的残留」直接丢弃）。 */
    void clearContents() {
        fluids.clear();
    }

    /** 把超出容量的那部分丢掉（用于合并收完流体的收尾）。 */
    void clampContents() {
        fluids.clamp();
    }

    /**
     * 破坏控制器前：让本格接住 {@code amount} mB 流体（最多单格容量），返回实际接住的量。
     * <p>只给「容量为 0 的部件格」临时扩到单格容量；已经是控制器的格子不动容量（避免把它自己的存量夹掉）。
     */
    int acceptForSplit(FluidResource resource, int amount) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        if (fluids.capacityValue() < capacityPerBlock()) {
            fluids.capacity(capacityPerBlock());
        }
        try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                     net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int moved = fluids.insert(0, resource, Math.min(amount, capacityPerBlock()), transaction);
            if (moved > 0) {
                transaction.commit();
            }
            return moved;
        }
    }

    /** 放下「装着流体的储罐物品」时把流体灌回本格（放下后可能立刻并罐，由多方块重算汇入控制器）。 */
    void restoreFluid(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return;
        }
        fluids.capacity(capacityPerBlock());
        try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                     net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int moved = fluids.insert(0, FluidResource.of(fluid.getFluid()), fluid.getAmount(), transaction);
            if (moved > 0) {
                transaction.commit();
            }
        }
        setChanged();
        pushUpdate();
    }

    /** 按当前尺寸刷新顶部 / 底部盖板、内部面剔除与开窗的方块状态（只改属性，不触发邻居更新）。 */
    void refreshState() {
        if (level == null) {
            return;
        }
        BlockState current = getBlockState();
        if (!(current.getBlock() instanceof TankBlock)) {
            return;
        }
        BlockPos origin = controller == null ? worldPosition : controller;
        BlockState updated = TankBlock.shape(current, worldPosition, origin, width, height, window);
        if (updated != current) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** 把尺寸 / 流体推给周围客户端（控制器需要，渲染器据此画液面）。 */
    void pushUpdate() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putChild("fluids", fluids);
        output.putInt("width", width);
        output.putInt("height", height);
        output.putBoolean("window", window);
        if (controller != null) {
            output.putLong("controller", controller.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild("fluids", fluids);
        width = Math.max(1, input.getIntOr("width", 1));
        height = Math.max(1, input.getIntOr("height", 1));
        window = input.getBooleanOr("window", true);
        long packed = input.getLongOr("controller", Long.MIN_VALUE);
        controller = packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
        fluids.capacity(isController() ? width * height * width * capacityPerBlock() : 0);
        // 兼容旧存档里可能出现的「存量 > 容量」（合并 / 拆分口径变更前留下的），顺手修掉
        fluids.clamp();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 控制器专属存储：容量可运行时改（合并 / 拆分），内容一变就标记 + 推给客户端。 */
    private final class TankInventory extends FluidStacksResourceHandler {
        private TankInventory() {
            // 占位容量，构造器随后按档位改写
            super(1, TankTier.COPPER.capacityPerBlock());
        }

        private boolean capacity(int value) {
            if (this.capacity == value) {
                return false;
            }
            this.capacity = value;
            return true;
        }

        private int capacityValue() {
            return this.capacity;
        }

        private void clear() {
            stacks.set(0, FluidStack.EMPTY);
        }

        /** 把超出容量的那部分流体丢掉（容量变小后保证「存量 ≤ 容量」）。 */
        private void clamp() {
            FluidStack stack = stacks.get(0);
            if (stack.isEmpty() || stack.getAmount() <= capacity) {
                return;
            }
            stacks.set(0, capacity <= 0 ? FluidStack.EMPTY : stack.copyWithAmount(capacity));
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
