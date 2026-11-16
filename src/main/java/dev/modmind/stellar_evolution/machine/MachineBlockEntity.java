package dev.modmind.stellar_evolution.machine;

import dev.modmind.stellar_evolution.pipe.CopperPipeBlock;
import dev.modmind.stellar_evolution.thermal.CopperHeat;
import dev.modmind.stellar_evolution.thermal.ThermalConsumer;
import dev.modmind.stellar_evolution.thermal.ThermalLoad;
import dev.modmind.stellar_evolution.thermal.ThermalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 材料加工机械方块实体（三台共用）：热压差网络的<strong>消耗端</strong> + 「只对正上方容器动手」的加工机。
 *
 * <p><strong>数值</strong>（照口头需求 2026-10-16）：基础热阻 {@value #THERMAL_RESISTANCE}、基准热流率
 * {@value #BASE_FLOW_RATE} HP/t ⇒ <strong>64 HP/t 时占用 128 DP</strong>（1.0 倍速）；热流率越高越快，
 * 每刻最多完成一次（{@link #maxUsefulFlowRate()} 封顶，与木桶 / 机械土壤同口径）。
 *
 * <p><strong>加工口径</strong>：机内 <strong>9 格（3×3）料仓</strong>是<strong>输入</strong>（右键复用投掷器界面），
 * <strong>正上方容器</strong>是<strong>输出</strong>（必须是容器，否则整机不工作）；压模机另有 1 格<strong>模具槽</strong>：
 * <ol>
 *   <li>每个周期把机内料仓里<strong>每一格</strong>可加工的原料各处理 1 次（最多 9 件，照 HBM 粉碎机的多格并行口径）；</li>
 *   <li>某一格的产物在上方容器放不下 → <strong>这一格跳过</strong>，原料不消耗，等下一周期；</li>
 *   <li>有料且有热压差时逐刻推进 {@value #CYCLE_TICKS} 刻的周期，<strong>只有这时才播动画</strong>；</li>
 *   <li>周期完成：逐格扣料、把产物并入上方容器（按格序，先放先占）。</li>
 * </ol>
 * 接入方式与引擎 / 大机器一致：方块实体实现 {@link ThermalConsumer}，<strong>贴着铜管或铜块</strong>就在网络里。
 */
public class MachineBlockEntity extends BlockEntity implements ThermalConsumer, Container {
    /** 内部料仓容量：3×3 = 9，和投掷器界面一致。 */
    public static final int SLOTS = 9;

    private final Handler itemHandler = new Handler(SLOTS);
    /** 容器与物品能力共用同一份列表（照乏燃料池 / 反应仓口径）。 */
    private final NonNullList<ItemStack> items = itemHandler.sharedList();
    /** 压模机专用模具槽（1 格；其它机器不使用）。 */
    private final SimpleContainer mold = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            MachineBlockEntity.this.setChanged();
        }
    };

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

    /** 基础热阻（占用 = 本值 × 有效热流率）。 */
    public static final double THERMAL_RESISTANCE = 2.0D;
    /** 基准热流率（HP/t）：达到 1.0 倍速所需；本值下占用正好 128 DP。 */
    public static final double BASE_FLOW_RATE = 64.0D;
    /** 一个加工周期的基准刻数（1.0 倍速）。 */
    public static final double CYCLE_TICKS = 200.0D;

    /** 钢炉的熔炼配方查询器（带「上一条配方」缓存，稳态一次 id 比对）。 */
    private static final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> SMELTING_CHECK =
            RecipeManager.createCheck(RecipeType.SMELTING);

    /** 机器状态（Jade 用；{@link #ordinal()} 会被写进 NBT，<strong>改动顺序即改动协议</strong>）。 */
    public enum State {
        /** 顶部没有容器 —— 整机不工作。 */
        NO_CONTAINER,
        /** 有容器但没有能开工的原料（没料 / 产物放不下）。 */
        WAITING,
        /** 有料但当前没有热压差 —— 不额外提示（热压差 HUD 自己会报「未热压」）。 */
        NO_HEAT,
        /** 正在加工（Jade 画绿色「配方」槽）。 */
        WORKING
    }

    /** 已运行时长（tick）：只在<strong>加工中</strong>推进，供渲染器算动件位置。 */
    private float age;
    /** 上一 tick 的运行时长，供插值。 */
    private float lastAge;
    /** 服务端权威：当前是否在加工（同步给客户端，决定要不要播动画）。 */
    private boolean working;
    /** 当前状态（Jade 用；不存盘）。 */
    private State machineState = State.NO_CONTAINER;
    /** 当前周期已推进的刻数（不存盘：重进世界重新计时，与其它机器一致）。 */
    private double progress;
    /** Jade：本周期剩余刻数（{@code -1} = 没在加工）。 */
    private int jadeRemaining = -1;
    /** Jade：本周期进度 0..1。 */
    private double jadeProgress;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(MachineBlocks.MACHINE_ENTITY.get(), pos, state);
    }

    /** 本机机种（由方块实例决定）。 */
    public MachineKind kind() {
        return MachineBlocks.kindOf(getBlockState().getBlock());
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        if (level.isClientSide()) {
            // 「配方进行时才播动画」：working 由服务端算好同步过来
            machine.lastAge = machine.age;
            if (machine.working) {
                machine.age += 1.0F;
            }
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            // 老存档自愈：旧铜管的连接位不会因代码更新重算，每秒补一次管臂
            if (serverLevel.getGameTime() % 20L == 0L) {
                CopperPipeBlock.refreshAround(serverLevel, pos);
            }
            machine.onThermalTick(ThermalNetwork.loadAt(serverLevel, pos));
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

    /** 满速上限：每刻最多完成一次（周期按机种 ⇒ 再高的热流率也只是「每刻一件」）。 */
    @Override
    public double maxUsefulFlowRate() {
        return BASE_FLOW_RATE * kind().cycleTicks();
    }

    /** 消耗端自驱：取网络账本 → 找容器 → 找活 → 推进周期。 */
    @Override
    public void onThermalTick(ThermalLoad load) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ThermalNetwork.syncAlarm(serverLevel, getBlockPos());

        // 上方落点：原版容器或其它模组的物品能力（精妙存储 / 背包等）
        BlockPos outputPos = getBlockPos().above();
        if (!CopperHeat.isItemTarget(serverLevel, outputPos)) {
            stop(State.NO_CONTAINER);
            return;
        }
        if (!hasWork(serverLevel, outputPos)) {
            stop(State.WAITING);
            return;
        }
        double speed = load.speedFor(BASE_FLOW_RATE);
        if (speed <= 0.0D) {
            stop(State.NO_HEAT);
            return;
        }

        machineState = State.WORKING;
        double cycle = kind().cycleTicks();
        jadeRemaining = (int) Math.ceil(Math.max(0.0D, cycle - progress) / speed);
        jadeProgress = Math.min(1.0D, progress / cycle);
        setWorking(true);

        progress += speed;
        if (progress >= cycle) {
            progress -= cycle;
            if (progress > cycle) {
                progress = cycle;   // 超速时不留欠账，避免一个周期里连做多件
            }
            craftAll(serverLevel, outputPos);
        }
    }

    /**
     * 机内有没有「至少一格」能开工：该机种有配方、数量够，且上方容器放得下产物
     * （放不下就停手，免得白转一个周期）。
     */
    private boolean hasWork(Level level, BlockPos outputPos) {
        MachineKind kind = kind();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack input = items.get(i);
            if (input.isEmpty()) {
                continue;
            }
            MachineRecipes.Entry match = matchFor(level, kind, input);
            if (match == null || input.getCount() < match.inputCount()) {
                continue;
            }
            if (CopperHeat.canFitAt(level, outputPos, match.output())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一次周期把料仓里<strong>每一格</strong>可加工的原料各处理一次（最多 {@value #SLOTS} 件）：
     * 按格序逐格检查，产物放不下就跳过该格、不扣料；产物立刻并入上方容器，
     * 后续格子看到的剩余空间就是扣掉前面产物之后的真实空间。
     */
    private void craftAll(Level level, BlockPos outputPos) {
        MachineKind kind = kind();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack input = items.get(i);
            if (input.isEmpty()) {
                continue;
            }
            MachineRecipes.Entry match = matchFor(level, kind, input);
            if (match == null || input.getCount() < match.inputCount()) {
                continue;
            }
            ItemStack output = match.output();
            if (output.isEmpty() || !CopperHeat.canFitAt(level, outputPos, output)) {
                continue;
            }
            ItemStack removed = removeItem(i, match.inputCount());
            if (removed.getCount() < match.inputCount()) {
                // 理论上不会发生（上面已核过数量）；保险起见把扣掉的部分还回料仓
                CopperHeat.addToContainer(this, removed);
                continue;
            }
            CopperHeat.insertAt(level, outputPos, output.copy());
        }
        setChanged();
    }

    /** 某格当前配方的匹配结果：钢炉走原版熔炼，其它查内置表（压模机带上模具槽）。 */
    @Nullable
    private MachineRecipes.Entry matchFor(Level level, MachineKind kind, ItemStack input) {
        if (kind != MachineKind.STEEL_FURNACE) {
            return MachineRecipes.find(kind, input, mold.getItem(0));
        }
        if (input.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        SingleRecipeInput probe = new SingleRecipeInput(input.copyWithCount(1));
        ItemStack output = SMELTING_CHECK.getRecipeFor(probe, serverLevel)
                .map(holder -> holder.value().assemble(probe))
                .orElse(ItemStack.EMPTY);
        return output.isEmpty() ? null : new MachineRecipes.Entry(output, 1);
    }

    /** 压模机模具槽（Menu 用）；其它机种不会打开这个界面。 */
    public Container moldContainer() {
        return mold;
    }

    /** 停手：进度清零、Jade 不再画配方槽，并把状态记下来。 */
    private void stop(State newState) {
        machineState = newState;
        progress = 0.0D;
        jadeRemaining = -1;
        jadeProgress = 0.0D;
        setWorking(false);
    }

    /** 「是否在加工」变化时才写盘 + 发包（客户端据此决定动画播不播）。 */
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

    /** 供渲染器取插值后的运行时长。 */
    public float age(float partialTick) {
        return Mth.lerp(partialTick, lastAge, age);
    }

    /** 当前状态（Jade 用）。 */
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

    // ------------------------------------------------------------------ 内部料仓（Container）

    public ResourceHandler<ItemResource> itemCapability() {
        return itemHandler;
    }

    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
        Containers.dropContents(level, pos, mold);
    }

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
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
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

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("working", working);
        ContainerHelper.saveAllItems(output, items);
        output.storeNullable("mold", ItemStack.CODEC, mold.isEmpty() ? null : mold.getItem(0));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        working = input.getBooleanOr("working", false);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, items);
        mold.setItem(0, input.read("mold", ItemStack.CODEC).orElse(ItemStack.EMPTY));
    }

    /** 整份带去客户端：进世界 / 区块加载时同步「是否在加工」（供动画）。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
