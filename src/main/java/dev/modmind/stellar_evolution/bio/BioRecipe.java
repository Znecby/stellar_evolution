package dev.modmind.stellar_evolution.bio;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 一条生化配方（《生化线》§5.3）：<strong>配方类型 + 物品 / 流体输入 + 物品 / 流体输出 + 条件</strong>。
 *
 * <p>本期配方<strong>代码内置</strong>（见 {@link BioRecipes}），不进数据包；JEI 直接读同一张表（见
 * {@code BioJeiPlugin}），所以「机器真跑的」与「JEI 显示的」永远是同一份数据。
 *
 * <p>条件（{@link #conditionsMet}）：
 * <ul>
 *   <li>{@link #requiredBand 温度带要求} —— 发酵罐用它把同一份进料分到沼气 / 乙醇 / 醋酸（见 {@link FermentationKinetics}）；</li>
 *   <li>{@link #minFlowRate 热流率门槛} —— 灭菌罐这类「吃热流率」的机器的额外条件。</li>
 * </ul>
 */
public record BioRecipe(String id,
                        BioRecipeType type,
                        List<ItemIngredient> itemInputs,
                        List<FluidIngredient> fluidInputs,
                        List<ItemIngredient> itemOutputs,
                        List<FluidIngredient> fluidOutputs,
                        int baseTicks,
                        @Nullable FermentationKinetics.Band requiredBand,
                        double minFlowRate,
                        @Nullable Item catalyst) {

    /** 无催化剂的旧签名（保持既有配方写法不变）。 */
    public BioRecipe(String id, BioRecipeType type,
                     List<ItemIngredient> itemInputs, List<FluidIngredient> fluidInputs,
                     List<ItemIngredient> itemOutputs, List<FluidIngredient> fluidOutputs,
                     int baseTicks, @Nullable FermentationKinetics.Band requiredBand, double minFlowRate) {
        this(id, type, itemInputs, fluidInputs, itemOutputs, fluidOutputs,
                baseTicks, requiredBand, minFlowRate, null);
    }

    /** 计数的物品原料 / 产物；{@code chance} 仅对产物有意义（<1.0 表示概率产出，默认必出）。 */
    public record ItemIngredient(ItemResource resource, int count, double chance) {
        public ItemIngredient(ItemResource resource, int count) {
            this(resource, count, 1.0D);
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

    /** 温度带 + 热流率门槛是否都满足。 */
    public boolean conditionsMet(FermentationKinetics.Band band, double flowRate) {
        if (requiredBand != null && requiredBand != band) {
            return false;
        }
        return flowRate >= minFlowRate;
    }

    /** 条件的紧凑描述（命令 / JEI 用）；无条件返回空串。 */
    public String describeConditions() {
        List<String> parts = new ArrayList<>();
        if (requiredBand != null) {
            parts.add(switch (requiredBand) {
                case ANAEROBIC -> "厌氧带（供能铜块 1 块）";
                case MESOPHILIC -> "中温带（供能铜块 2 块）";
                case THERMOPHILIC -> "高温带（供能铜块 ≥4 块）";
            });
        }
        if (minFlowRate > 0.0D) {
            parts.add("热流率 ≥" + (int) minFlowRate + " HP/t");
        }
        return String.join("，", parts);
    }

    /** 输入侧的可读描述。 */
    public String describeInputs() {
        return describe(itemInputs(), fluidInputs());
    }

    /** 输出侧的可读描述。 */
    public String describeOutputs() {
        return describe(itemOutputs(), fluidOutputs());
    }

    private static String describe(List<ItemIngredient> items, List<FluidIngredient> fluids) {
        List<String> parts = new ArrayList<>();
        for (ItemIngredient item : items) {
            String text = item.stack().getHoverName().getString() + " x" + item.count();
            if (item.chance() < 1.0D) {
                text += "（" + Math.round(item.chance() * 100) + "%）";
            }
            parts.add(text);
        }
        for (FluidIngredient fluid : fluids) {
            parts.add(fluid.stack().getHoverName().getString() + " " + fluid.amount() + "mB");
        }
        return parts.isEmpty() ? "—" : String.join(" + ", parts);
    }
}
