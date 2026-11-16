package dev.modmind.stellar_evolution.particle;

import dev.modmind.stellar_evolution.fission.FissionMachineBlockEntity;
import dev.modmind.stellar_evolution.fission.FissionMachines;
import dev.modmind.stellar_evolution.fission.FissionMaterials;
import dev.modmind.stellar_evolution.hep.HepAdvancements;
import dev.modmind.stellar_evolution.petro.PetroFluids;
import dev.modmind.stellar_evolution.thermal.ThermalGenerator;
import dev.modmind.stellar_evolution.thermal.ThermalLoad;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * <strong>湮灭反应堆</strong>（二期 B2 终局能源）：反物质 + 氢 → 直接向热压差网络供能。
 *
 * <p>与裂变堆 / 聚变仪的区别：它<strong>不产蒸汽</strong>，而是作为 {@link ThermalGenerator}
 * 直接向网络塞容量与热流率（理论最高一档能量密度）。
 *
 * <p>燃料：输入容器里 1 个反物质 + 储罐里 1 000 氢 → 燃烧 {@value #BURN_TICKS} 刻；
 * 燃烧期间提供 {@value #CAPACITY} DP / {@value #FLOW_RATE} HP·t⁻¹，烧完自动再取一份。
 */
public class AnnihilationReactorBlockEntity extends FissionMachineBlockEntity implements ThermalGenerator {
    /** 差压容量（DP）。 */
    public static final double CAPACITY = 8_192.0D;
    /** 固定热流率（HP/t）。 */
    public static final double FLOW_RATE = 4_096.0D;
    /** 一份燃料（反物质 ×1 + 氢 1 000）的燃烧刻数。 */
    public static final int BURN_TICKS = 1_200;
    /** 稳定岛点火（鎶-304 添加剂）的产能倍率。 */
    public static final double BOOST_MULTIPLIER = 1.5D;

    private int burnTicks;
    /** 本次燃烧是否用了鎶-304 添加剂。 */
    private boolean stableIslandBoost;

    public AnnihilationReactorBlockEntity(BlockPos pos, BlockState state) {
        super(FissionMachines.ANNIHILATION_ENTITY.get(), pos, state);
    }

    @Override
    public double capacity() {
        return burnTicks > 0 ? CAPACITY * (stableIslandBoost ? BOOST_MULTIPLIER : 1.0D) : 0.0D;
    }

    @Override
    public double flowRate() {
        return burnTicks > 0 ? FLOW_RATE * (stableIslandBoost ? BOOST_MULTIPLIER : 1.0D) : 0.0D;
    }

    /** 生成端不走裂变配方推进：燃料与产能在 {@link #serverTick} 里处理。 */
    @Override
    public void onThermalTick(ThermalLoad load) {
        // no-op
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state, ThermalLoad load) {
        boolean wasWorking = working;
        if (burnTicks > 0) {
            burnTicks--;
            working = true;
        } else {
            working = refuel();
            if (working) {
                burnTicks = BURN_TICKS;
                if (level instanceof ServerLevel serverLevel) {
                    HepAdvancements.awardNearby(serverLevel, worldPosition, HepAdvancements.ANNIHILATION, 64.0D);
                }
            }
        }
        if (wasWorking != working || level.getGameTime() % 20L == 0L) {
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    /** 取一份燃料：反物质 ×1 + 氢 1 000；有鎶-304 时额外消耗 1 个，进入稳定岛点火。 */
    private boolean refuel() {
        ItemResource antimatter = ItemResource.of(new ItemStack(FissionMaterials.ANTIMATTER.get()));
        ItemResource copernicium = ItemResource.of(new ItemStack(FissionMaterials.COPERNICIUM_304.get()));
        FluidResource hydrogen = FluidResource.of(new FluidStack(PetroFluids.HYDROGEN.source().get(), 1_000));
        try (Transaction transaction = Transaction.openRoot()) {
            if (items().extractFromInputs(antimatter, 1, transaction) < 1) {
                return false;
            }
            if (fluids().extractFromInputs(hydrogen, 1_000, transaction) < 1_000) {
                return false;
            }
            stableIslandBoost = items().extractFromInputs(copernicium, 1, transaction) >= 1;
            transaction.commit();
            return true;
        }
    }

    @Override
    protected boolean animating() {
        return working;
    }

    // ------------------------------------------------------------------ 展示 / 存盘

    /** 剩余燃烧刻数（0 = 已熄火）。 */
    public int burnTicks() {
        return burnTicks;
    }

    /** 是否在燃烧。 */
    public boolean burning() {
        return burnTicks > 0;
    }

    @Override
    public int remainingRecipeTicks() {
        return burnTicks > 0 ? burnTicks : -1;
    }

    @Override
    public double recipeProgress() {
        return burnTicks / (double) BURN_TICKS;
    }

    /** 本次燃烧是否处于稳定岛点火。 */
    public boolean stableIslandBoost() {
        return stableIslandBoost;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("annihilation_burn", burnTicks);
        output.putBoolean("annihilation_boost", stableIslandBoost);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        burnTicks = input.getIntOr("annihilation_burn", 0);
        stableIslandBoost = input.getBooleanOr("annihilation_boost", false);
    }
}
