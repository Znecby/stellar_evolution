package dev.znecby.stellar_evolution.welding;

import dev.znecby.stellar_evolution.thermal.CopperHeat;
import dev.znecby.stellar_evolution.thermal.ThermalConsumer;
import dev.znecby.stellar_evolution.thermal.ThermalLoad;
import dev.znecby.stellar_evolution.thermal.ThermalNetwork;
import dev.znecby.stellar_evolution.tank.TankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * 焊接机方块实体：<strong>热压差网络的消耗端</strong> + 3 格料仓 + 「下方储罐 / 上方容器」的单配方加工机。
 *
 * <p>口径（照破碎机那套机器框架，但只有 1×3、不并行）：
 * <ul>
 *   <li><strong>输入</strong>：机内 3 格料仓（右键打开 1×3 界面），按 {@link WeldingRecipes} 无序匹配；</li>
 *   <li><strong>流体</strong>：必须<strong>正下方是储罐</strong>，按配方抽流体（示例：25 mB 助焊剂）；</li>
 *   <li><strong>产物</strong>：必须<strong>正上方是容器</strong>，产物直接塞进去；</li>
 *   <li><strong>一次只做一条配方</strong>，周期 {@code duration} 刻，热压差倍速同上位机器。</li>
 * </ul>
 * 数值：热阻 {@value #THERMAL_RESISTANCE}、机器基准热流率 {@value #BASE_FLOW_RATE} HP/t（与破碎机一致）；
 * 每条配方另带自己的最低 / 基准热流率，速度按配方基准计算，并 clamp 到机器满速阈值。
 */
public class WeldingMachineBlockEntity extends BlockEntity implements ThermalConsumer, Container {
    /** 机内料仓格数：1×3。 */
    public static final int SLOTS = 3;
    /** 基础热阻（占用 = 本值 × 有效热流率）。 */
    public static final double THERMAL_RESISTANCE = 2.0D;
    /** 基准热流率（HP/t）：达到 1.0 倍速所需。 */
    public static final double BASE_FLOW_RATE = 64.0D;
    /** 满速阈值：最长配方周期 200t（超过也不会更快）。 */
    public static final double MAX_USEFUL_FLOW_RATE = BASE_FLOW_RATE * 200.0D;

    /** 当前状态（Jade 用；不存盘）。 */
    public enum State {
        /** 顶部没有容器 —— 整机不工作。 */
        NO_CONTAINER,
        /** 底部没有储罐 —— 整机不工作。 */
        NO_TANK,
        /** 有储罐，但罐里没有配方所需的流体（缺流体 / 流体种类不对）。 */
        NO_FLUID,
        /** 有容器 / 储罐，但没有能开工的配方原料（没料 / 产物放不下）。 */
        WAITING,
        /** 有料但当前没有热压差 —— 不额外提示（热压差 HUD 自己会报「未热压」）。 */
        NO_HEAT,
        /** 正在加工（Jade 画绿色「配方」槽）。 */
        WORKING
    }

    private final Handler itemHandler = new Handler(SLOTS);
    /** 容器与物品能力共用同一份列表（照材料加工机械口径）。 */
    private final NonNullList<ItemStack> items = itemHandler.sharedList();

    private final class Handler extends ItemStacksResourceHandler {
        Handler(int size) {
            super(size);
        }

        NonNullList<ItemStack> sharedList() {
            return this.stacks;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    /** 当前状态（Jade 用；不存盘）。 */
    private State machineState = State.NO_CONTAINER;
    /** 当前周期已推进的刻数（不存盘：重进世界重新计时）。 */
    private double progress;
    /** 当前配方周期（0 = 没有可开工配方）。 */
    private int duration;
    /** 当前配方需要的流体量（mB；0 = 不要流体）。 */
    private int fluidRequired;
    /** 当前配方的最低热流率（HP/t；0 = 不限）。 */
    private double recipeMinFlowRate;
    /** 当前配方的 1× 基准热流率（HP/t）。 */
    private double recipeBaseFlowRate = BASE_FLOW_RATE;
    /** 正下方储罐里的流体量（mB；界面显示用）。 */
    private int fluidAmount;
    /** Jade：本周期剩余刻数（{@code -1} = 没在加工）。 */
    private int jadeRemaining = -1;
    /** Jade：本周期进度 0..1。 */
    private double jadeProgress;

    /** 界面同步数据：进度 / 周期 / 罐内流体 / 配方需求。 */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) progress;
                case 1 -> duration;
                case 2 -> fluidAmount;
                case 3 -> fluidRequired;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // 客户端只读：服务端每刻写入真实值
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public WeldingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(WeldingBlocks.WELDING_MACHINE_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WeldingMachineBlockEntity welding) {
        if (level instanceof ServerLevel serverLevel) {
            welding.onThermalTick(ThermalNetwork.loadAt(serverLevel, pos));
        }
    }

    // ------------------------------------------------------------------ ThermalConsumer

    @Override
    public double thermalResistance() {
        return THERMAL_RESISTANCE;
    }

    @Override
    public double baseFlowRate() {
        return BASE_FLOW_RATE;
    }

    @Override
    public double maxUsefulFlowRate() {
        return MAX_USEFUL_FLOW_RATE;
    }

    @Override
    public void onThermalTick(ThermalLoad load) {
        if (!(getLevel() instanceof ServerLevel level)) {
            return;
        }
        ThermalNetwork.syncAlarm(level, getBlockPos());

        duration = 0;
        fluidRequired = 0;
        fluidAmount = 0;
        recipeMinFlowRate = 0.0D;
        recipeBaseFlowRate = BASE_FLOW_RATE;

        BlockPos outputPos = getBlockPos().above();
        if (!CopperHeat.isItemTarget(level, outputPos)) {
            reset(State.NO_CONTAINER);
            return;
        }
        if (!(level.getBlockEntity(getBlockPos().below()) instanceof TankBlockEntity tank)) {
            reset(State.NO_TANK);
            return;
        }
        jadeRemaining = -1;
        jadeProgress = 0.0D;
        FluidStack tankFluid = tank.fluid();

        WeldingRecipes.Match match = WeldingRecipes.findByItems(items);
        if (match == null) {
            reset(State.WAITING);
            return;
        }
        WeldingRecipes.Recipe recipe = match.recipe();
        duration = Math.max(1, recipe.duration());
        recipeMinFlowRate = Math.max(0.0D, recipe.minFlowRate());
        recipeBaseFlowRate = Math.max(1.0D, recipe.baseFlowRate());
        fluidRequired = recipe.fluid().getAmount();
        // 只有罐里确实是配方要的流体才计入「已有」，避免拿水冒充配方流体
        fluidAmount = !recipe.fluid().isEmpty()
                && FluidStack.isSameFluidSameComponents(tankFluid, recipe.fluid())
                ? tankFluid.getAmount() : 0;

        // 流体不足 / 罐里是别的流体：界面仍显示「已有 / 需要」，但不推进
        if (!WeldingRecipes.fluidMatches(recipe, tankFluid)) {
            resetProgress(State.NO_FLUID);
            return;
        }
        if (!CopperHeat.canFitAt(level, outputPos, recipe.output())) {
            resetProgress(State.WAITING);
            return;
        }
        if (load.flowRate() < recipeMinFlowRate) {
            resetProgress(State.NO_HEAT);
            return;
        }
        // 配方决定 1× 基准；最高只到机器满速阈值（12,800 HP/t 对应的倍速），避免免费超速
        double maxSpeed = MAX_USEFUL_FLOW_RATE / recipeBaseFlowRate;
        double speed = Math.min(load.speedFor(recipeBaseFlowRate), maxSpeed);
        if (speed <= 0.0D) {
            resetProgress(State.NO_HEAT);
            return;
        }

        machineState = State.WORKING;
        progress = Math.min(duration, progress + speed);
        jadeProgress = Math.min(1.0D, progress / duration);
        jadeRemaining = speed >= duration ? 0 : (int) Math.ceil((duration - progress) / speed);
        if (progress < duration) {
            return;
        }
        progress = 0.0D;
        craft(level, outputPos, match);
        jadeProgress = 0.0D;
        jadeRemaining = speed >= duration ? 0 : (int) Math.ceil(duration / speed);
    }

    /** 完成一条配方：先抽流体（抽不到就不动料），再扣料、把产物放进上方容器。 */
    private void craft(ServerLevel level, BlockPos outputPos, WeldingRecipes.Match match) {
        if (!(level.getBlockEntity(getBlockPos().below()) instanceof TankBlockEntity tank)) {
            return;
        }
        WeldingRecipes.Recipe recipe = match.recipe();
        FluidStack required = recipe.fluid();
        if (!required.isEmpty()) {
            ResourceHandler<FluidResource> view = tank.fluids();
            boolean drained;
            try (Transaction transaction = Transaction.openRoot()) {
                drained = view.extract(0, FluidResource.of(required.getFluid()), required.getAmount(),
                        transaction) >= required.getAmount();
                if (drained) {
                    transaction.commit();
                }
            }
            if (!drained) {
                return;
            }
        }
        for (int i = 0; i < match.slots().length; i++) {
            int slot = match.slots()[i];
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                stack.shrink(match.recipe().items().get(i).getCount());
            }
        }
        CopperHeat.insertAt(level, outputPos, recipe.output().copy());
        setChanged();
    }

    private void reset(State newState) {
        resetProgress(newState);
        duration = 0;
        fluidRequired = 0;
        fluidAmount = 0;
        recipeMinFlowRate = 0.0D;
        recipeBaseFlowRate = BASE_FLOW_RATE;
    }

    private void resetProgress(State newState) {
        machineState = newState;
        progress = 0.0D;
        jadeRemaining = -1;
        jadeProgress = 0.0D;
    }

    /** Jade：当前状态（缺容器 / 缺储罐 / 缺流体 / 等待原料 / 未热压 / 加工中）。 */
    public State state() {
        return machineState;
    }

    /** Jade：本周期剩余刻数（{@code -1} = 没在加工）。 */
    public int jadeRemainingTicks() {
        return jadeRemaining;
    }

    /** Jade：本周期进度 0..1。 */
    public double jadeProgress() {
        return jadeProgress;
    }

    /** Jade：当前配方的最低热流率（HP/t；0 = 没匹配到配方）。 */
    public double recipeMinFlowRate() {
        return recipeMinFlowRate;
    }

    /** Jade：当前配方的 1× 基准热流率（HP/t）。 */
    public double recipeBaseFlowRate() {
        return recipeBaseFlowRate;
    }

    // ------------------------------------------------------------------ 界面数据 / 物品能力

    /** 菜单同步数据（服务端真实值，客户端只读）。 */
    public ContainerData data() {
        return data;
    }

    public ResourceHandler<ItemResource> itemCapability() {
        return itemHandler;
    }

    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }

    // ------------------------------------------------------------------ Container

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ 存盘

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        progress = 0.0D;
        duration = 0;
        fluidRequired = 0;
        fluidAmount = 0;
    }

    /** 整份带去客户端（打开界面 / 区块加载时至少知道方块实体存在）。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
