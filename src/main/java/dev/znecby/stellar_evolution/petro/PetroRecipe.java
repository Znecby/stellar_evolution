package dev.znecby.stellar_evolution.petro;

import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 石化配方在 <strong>JEI / 命令</strong>里的展示记录：机种 + 物品 / 流体输入输出 + 备注 —— 照生化线
 * {@code BioRecipe} 的口径（同一张表既给机器跑、也给 JEI 看）。
 *
 * <p><strong>注意</strong>：石化线的实际结算在 {@link PetroRecipes#craftOnce}，其产率是<strong>连续</strong>的
 * （分馏按塔温、裂解按严重度，都由网络热流率映射）；这里的记录是<strong>代表性取样</strong>
 * （塔温 / 严重度 = 0.5），用于 JEI 与 {@code /se petro recipes} 的可读展示。
 */
public record PetroRecipe(String id,
                          PetroMachineKind kind,
                          String note,
                          List<ItemIngredient> itemInputs,
                          List<FluidIngredient> fluidInputs,
                          List<ItemIngredient> itemOutputs,
                          List<FluidIngredient> fluidOutputs,
                          Item catalyst) {

    /** 无催化剂的重载（旧展示记录写法不变）。 */
    public PetroRecipe(String id, PetroMachineKind kind, String note,
                       List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                       List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs) {
        this(id, kind, note, itemInputs, fluidInputs, itemOutputs, fluidOutputs, null);
    }

    /** 计数的物品原料 / 产物；{@code tag} 非空时 JEI 槽内显示标签全部成员。 */
    public record ItemIngredient(ItemResource resource, int count, @Nullable TagKey<Item> tag) {
        public ItemIngredient(ItemResource resource, int count) {
            this(resource, count, null);
        }

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
