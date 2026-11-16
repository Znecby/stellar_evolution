package dev.modmind.stellar_evolution.fission;

import dev.modmind.stellar_evolution.bio.FermentationKinetics;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 裂变线的一条<strong>普通配方</strong>（代码内置；照石化线 {@code PetroRecipe} 口径）。
 *
 * <p>同一份记录既给机器结算（{@link FissionRecipes}），也给 JEI / 命令展示 —— 不做「机制配方」与「展示配方」两套。
 *
 * @param id           配方 id（JEI 展示 / 调试用）
 * @param kind         属于哪个机种
 * @param ticks        一个周期的基准刻数（1.0 倍速）
 * @param itemInputs   物品输入（可多路）
 * @param fluidInputs  流体输入（可多路）
 * @param itemOutputs  物品产出（可多路）
 * @param fluidOutputs 流体产出（可多路）
 * @param requiredBand 罐装机的温度带要求（{@code null} = 不限）
 * @param minFlowRate  热流率门槛（HP/t；0 = 不限）
 */
public record FissionRecipe(String id,
                            FissionMachineKind kind,
                            int ticks,
                            List<ItemIngredient> itemInputs,
                            List<FluidIngredient> fluidInputs,
                            List<ItemIngredient> itemOutputs,
                            List<FluidIngredient> fluidOutputs,
                            @Nullable FermentationKinetics.Band requiredBand,
                            double minFlowRate) {

    /** 旧签名（无热流率门槛）。 */
    public FissionRecipe(String id, FissionMachineKind kind, int ticks,
                         List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                         List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs,
                         @Nullable FermentationKinetics.Band requiredBand) {
        this(id, kind, ticks, itemInputs, fluidInputs, itemOutputs, fluidOutputs, requiredBand, 0.0D);
    }

    /** 当前温度带能不能跑这条配方。 */
    public boolean availableIn(@Nullable FermentationKinetics.Band band) {
        return requiredBand == null || requiredBand == band;
    }

    /** 当前热流率能不能跑这条配方。 */
    public boolean availableAt(double flowRate) {
        return flowRate >= minFlowRate;
    }

    /** 计数的物品原料 / 产物。 */
    public record ItemIngredient(ItemResource resource, int count) {
        /** 给 JEI / 命令看的一格物品堆。 */
        public ItemStack stack() {
            return resource.toStack(count);
        }
    }

    /** 计量的流体原料 / 产物。 */
    public record FluidIngredient(FluidResource resource, int amount) {
        /** 给 JEI / 命令看的一格流体堆。 */
        public FluidStack stack() {
            return resource.toStack(amount);
        }
    }
}
