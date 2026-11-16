package dev.modmind.stellar_evolution.hep;

import dev.modmind.stellar_evolution.fission.FissionMachineBlockEntity;
import dev.modmind.stellar_evolution.fission.FissionMachines;
import dev.modmind.stellar_evolution.thermal.ThermalLoad;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * 回旋加速器：<strong>不占 DP</strong> 的大机器，按当前配方差速充能、按网络 DP 决定并行量。
 *
 * <p>口径（2026-10-29 用户定稿）：
 * <ul>
 *   <li>每 tick 充能 = <strong>当前网络 HP/t ÷ 64</strong>，封顶 = <strong>当前配方差速需求</strong>；</li>
 *   <li>差速达到配方需求 → 执行一次配方，差速清零；</li>
 *   <li>并行量 = {@code floor(网络 DP / 配方需求)}，<strong>保底 1</strong>，最大 64；受粉量与输出空间限制。</li>
 * </ul>
 */
public class CyclotronBlockEntity extends FissionMachineBlockEntity {
    /** 机器状态（Jade / 调试用）。 */
    public enum State {
        IDLE,
        CHARGING,
        RUNNING,
        NO_POWER,
        OUTPUT_BLOCKED
    }

    private double charge;
    private State state = State.IDLE;
    private State lastState = State.IDLE;
    private boolean lastWorking;

    /** 展示值（Jade）：差速上限 = 当前配方需求。 */
    private double lastDemand;
    private int lastParallel;
    private int lastOps;

    public CyclotronBlockEntity(BlockPos pos, BlockState state) {
        super(FissionMachines.CYCLOTRON_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ 结算

    @Override
    public void onThermalTick(ThermalLoad load) {
        if (getLevel() == null || getLevel().isClientSide()) {
            return;
        }

        double networkDp = Math.max(0.0D, load.capacity());
        double flow = Math.max(0.0D, load.flowRate());

        // 断电 / 超载：差速清零，不结算
        if (load.overloaded() || flow <= 0.0D) {
            charge = 0.0D;
            state = State.NO_POWER;
            working = false;
            lastParallel = 0;
            lastOps = 0;
            syncState();
            return;
        }

        CyclotronRecipe recipe = currentRecipe();
        if (recipe == null) {
            charge = 0.0D;
            state = State.IDLE;
            working = false;
            lastDemand = 0.0D;
            lastParallel = 0;
            lastOps = 0;
            syncState();
            return;
        }

        double demand = recipe.demand();
        lastDemand = demand;

        // 并行量：DP / 需求，保底 1 份（充能中也先显示出来）
        int parallel = CyclotronCharge.parallel(networkDp, demand, countInput(recipe), CyclotronCharge.MAX_OPS);
        lastParallel = parallel;

        // 每 tick 按网络 HP ÷ 64 充能，封顶 = 当前配方需求
        charge = CyclotronCharge.charge(charge, demand, flow);
        if (!CyclotronCharge.ready(charge, demand)) {
            state = State.CHARGING;
            working = true;
            lastOps = 0;
            syncState();
            return;
        }

        // 达到差速上限 → 执行一次后差速清零
        int actual = 0;
        for (int k = parallel; k > 0; k--) {
            if (craft(recipe, k)) {
                actual = k;
                break;
            }
        }

        if (actual > 0) {
            charge = 0.0D;
            state = State.RUNNING;
            working = true;
            lastOps = actual;
        } else {
            // 输出放不下：差速停在需求值，等空间恢复后立即执行
            state = State.OUTPUT_BLOCKED;
            working = false;
            lastOps = 0;
        }
        syncState();
    }

    /** 当前配方：按表序取第一条「输入粉足够」的配方。 */
    private CyclotronRecipe currentRecipe() {
        for (CyclotronRecipe recipe : CyclotronRecipes.all()) {
            if (countInput(recipe) >= recipe.inputCount()) {
                return recipe;
            }
        }
        return null;
    }

    /** 基类的普通配方推进不适用；全部逻辑在 {@link #onThermalTick(ThermalLoad)}。 */
    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state, ThermalLoad load) {
        // no-op
    }

    /** 旋转动画额外判据：充能 / 运行都转。 */
    @Override
    protected boolean animating() {
        return state == State.RUNNING || state == State.CHARGING;
    }

    /** 每轮状态变化时同步一次（working 走基类存盘 / 更新包）；差速每轮变化也要 setChanged。 */
    private void syncState() {
        setChanged();
        if (state != lastState || working != lastWorking) {
            lastState = state;
            lastWorking = working;
            Level level = getLevel();
            if (level != null) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** 输入箱里某种粉的总数（只看输入容器，不含输出箱）。 */
    private int countInput(CyclotronRecipe recipe) {
        ItemResource wanted = ItemResource.of(new ItemStack(recipe.input()));
        return (int) Math.min(Integer.MAX_VALUE, items().countInInputs(wanted));
    }

    /** 事务式结算：扣粉 + 放物品 / 流体；放不下则整体回滚。 */
    private boolean craft(CyclotronRecipe recipe, int n) {
        ItemResource input = ItemResource.of(new ItemStack(recipe.input()));
        try (Transaction transaction = Transaction.openRoot()) {
            if (items().extractFromInputs(input, n, transaction) < n) {
                return false;
            }
            for (CyclotronRecipe.ItemOutput output : recipe.itemOutputs()) {
                int amount = output.count() * n;
                ItemResource resource = ItemResource.of(new ItemStack(output.item()));
                if (items().insertIntoOutputs(resource, amount, transaction) < amount) {
                    return false;
                }
            }
            for (CyclotronRecipe.FluidOutput output : recipe.fluidOutputs()) {
                int amount = output.amount() * n;
                FluidResource resource = FluidResource.of(new FluidStack(output.fluid(), output.amount()));
                if (fluids().insertIntoOutputs(resource, amount, transaction) < amount) {
                    return false;
                }
            }
            transaction.commit();
            return true;
        }
    }

    // ------------------------------------------------------------------ 状态 / 展示

    public double cyclotronCharge() {
        return charge;
    }

    public double cyclotronCap() {
        return lastDemand;
    }

    public double cyclotronDemand() {
        return lastDemand;
    }

    /** 本轮并行量（= floor(网络 DP / 配方需求)，≤64）。 */
    public int cyclotronParallel() {
        return lastParallel;
    }

    public int cyclotronOps() {
        return lastOps;
    }

    public State cyclotronState() {
        return state;
    }

    public Component cyclotronStateName() {
        return Component.translatable("stellar_evolution.jade.cyclotron.state." + state.name().toLowerCase());
    }

    // ------------------------------------------------------------------ 存盘

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("cyclotron_charge", charge);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        charge = input.getDoubleOr("cyclotron_charge", 0.0D);
    }
}
