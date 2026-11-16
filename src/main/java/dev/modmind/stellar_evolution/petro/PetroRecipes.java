package dev.modmind.stellar_evolution.petro;

import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.large.LargeMachineItemHandler;
import dev.modmind.stellar_evolution.petro.PetroLine.FluidIn;
import dev.modmind.stellar_evolution.petro.PetroLine.FluidOut;
import dev.modmind.stellar_evolution.petro.PetroLine.ItemIn;
import dev.modmind.stellar_evolution.petro.PetroLine.ItemOut;
import dev.modmind.stellar_evolution.petro.PetroLine.Row;
import dev.modmind.stellar_evolution.petro.PetroRecipe.FluidIngredient;
import dev.modmind.stellar_evolution.petro.PetroRecipe.ItemIngredient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 石化线配方<strong>结算器</strong>：把 {@link PetroLine 产线配置表}喂给大机器物流能力，
 * <strong>表怎么配，机器就怎么跑</strong>（不含任何机器 / 热力 / 物流机制）。
 *
 * <p>一台机器的一次结算 = <strong>按声明顺序遍历它的行表，命中第一行能整体成立的即结算</strong>：
 * 抽输入（流体 / 物品，从输入容器取）+ 放输出（进输出容器）在<strong>同一条事务</strong>里，
 * 任一步不成立即整体回滚（{@link #canCraft} 就是「干跑不落」的那种试算）。
 *
 * <p>"命中第一行"即 HBM 的「按输入分派」：同一台机器多行 = 多路输入形态（重整器吃石脑油 / 重油 / 柴油+氢气，
 * 聚合釜按单体分派……），<strong>加材料只加表行</strong>。
 *
 * <p><strong>配方与热流率无关</strong>：产率一律照表定值，热压差网络只决定机器的<strong>倍速</strong>
 * （见 {@link PetroMachineBlockEntity#onThermalTick}）—— 这样堆热压差 = 线性加速，不会改写产率。
 */
public final class PetroRecipes {
    /** 配方基准时长（刻）；机器实际按结算节奏推进（这个只当兜底缺省）。 */
    public static final int DEFAULT_TICKS = 200;

    /**
     * 按机种结算一次：抽输入 + 放输出同事务提交。
     *
     * @param items  大机器物品能力（物流网络里铜箱子的合并视图）
     * @param fluids 大机器流体能力（物流网络里储罐的合并视图）
     * @return 是否真的结算了一次
     */
    public static boolean craftOnce(PetroMachineKind kind, LargeMachineItemHandler items,
                                    LargeMachineFluidHandler fluids) {
        return craftOnce(kind, items, fluids, true);
    }

    /**
     * 只试不落：当前料情下本机种能不能结算一次（事务开完就关，等于整体回滚）。
     * <p>机器用它判断<strong>「有没有活干」</strong> —— 没活就停在这一刻（进度清零、Jade 不画配方槽、可动部件也停）。
     */
    public static boolean canCraft(PetroMachineKind kind, LargeMachineItemHandler items,
                                   LargeMachineFluidHandler fluids) {
        return craftOnce(kind, items, fluids, false);
    }

    /**
     * @param execute {@code false} = 只试不落（不提交事务）—— 供 {@link #canCraft} 干跑
     */
    private static boolean craftOnce(PetroMachineKind kind, LargeMachineItemHandler items,
                                     LargeMachineFluidHandler fluids, boolean execute) {
        for (Row row : PetroLine.rows(kind)) {
            if (tryRow(row, items, fluids, execute)) {
                return true;   // 命中第一行
            }
        }
        return false;
    }

    /** 跑一行：抽输入 + 放产出，全在一条根事务里；{@code execute=false} 时事务开完即关（等于回滚）。 */
    private static boolean tryRow(Row row, LargeMachineItemHandler items, LargeMachineFluidHandler fluids,
                                  boolean execute) {
        return inTransaction(execute, transaction -> {
            // 催化剂：只检查输入容器里有没有，<strong>不消耗</strong>
            if (row.catalyst() != null
                    && !items.hasInInputs(ItemResource.of(new ItemStack(row.catalyst())), 1)) {
                return false;
            }
            for (FluidIn input : row.fluidIn()) {
                if (fluids.extractFromInputs(input.resource(), input.amount(), transaction) < input.amount()) {
                    return false;
                }
            }
            for (ItemIn input : row.itemIn()) {
                int moved = input.tag() == null
                        ? items.extractFromInputs(input.resource(), input.count(), transaction)
                        : items.extractTagFromInputs(input.tag(), input.count(), transaction);
                if (moved < input.count()) {
                    return false;
                }
            }
            for (FluidOut output : row.fluidOut()) {
                if (output.amount() <= 0) {
                    continue;   // 量为 0 的路视为「无需放」
                }
                if (fluids.insertIntoOutputs(output.resource(), output.amount(), transaction) < output.amount()) {
                    return false;
                }
            }
            for (ItemOut output : row.itemOut()) {
                if (items.insertIntoOutputs(output.resource(), output.count(), transaction) < output.count()) {
                    return false;
                }
            }
            return true;
        });
    }

    /** 在一条根事务里跑「抽料 + 产出」；{@code execute=false} 时事务开完即关（等于回滚），只当试算。 */
    private static boolean inTransaction(boolean execute, Predicate<TransactionContext> body) {
        try (Transaction transaction = Transaction.openRoot()) {
            if (!body.test(transaction)) {
                return false;
            }
            if (execute) {
                transaction.commit();
            }
            return true;
        }
    }

    /**
     * 该机种的配方（人类可读，供 {@code /se petro recipes} 使用）——直接取 {@link PetroLine} 每行的展示文案，
     * 与机器实际结算保持一份事实。
     */
    public static List<String> describe(PetroMachineKind kind) {
        List<String> lines = new ArrayList<>();
        for (Row row : PetroLine.rows(kind)) {
            lines.add(row.note());
        }
        return List.copyOf(lines);
    }

    /**
     * 展示用配方表（JEI 分类 / 命令）：<strong>逐行</strong>转成 {@link PetroRecipe}，
     * 与 {@link #craftOnce} 是同一份事实（同一张 {@link PetroLine 表}）。
     */
    public static List<PetroRecipe> samples() {
        List<PetroRecipe> list = new ArrayList<>();
        for (Row row : PetroLine.all()) {
            list.add(new PetroRecipe(row.id(), row.kind(), row.note(),
                    items(row.itemIn()), fluids(row.fluidIn()),
                    itemsOut(row.itemOut()), fluidsOut(row.fluidOut()), row.catalyst()));
        }
        return List.copyOf(list);
    }

    // ------------------------------------------------------------------ 展示转换

    private static List<ItemIngredient> items(List<ItemIn> inputs) {
        List<ItemIngredient> list = new ArrayList<>(inputs.size());
        for (ItemIn input : inputs) {
            list.add(new ItemIngredient(input.resource(), input.count(), input.tag()));
        }
        return list;
    }

    private static List<FluidIngredient> fluids(List<FluidIn> inputs) {
        List<FluidIngredient> list = new ArrayList<>(inputs.size());
        for (FluidIn input : inputs) {
            list.add(new FluidIngredient(input.resource(), input.amount()));
        }
        return list;
    }

    private static List<ItemIngredient> itemsOut(List<ItemOut> outputs) {
        List<ItemIngredient> list = new ArrayList<>(outputs.size());
        for (ItemOut output : outputs) {
            list.add(new ItemIngredient(output.resource(), output.count()));
        }
        return list;
    }

    private static List<FluidIngredient> fluidsOut(List<FluidOut> outputs) {
        List<FluidIngredient> list = new ArrayList<>(outputs.size());
        for (FluidOut output : outputs) {
            list.add(new FluidIngredient(output.resource(), output.amount()));
        }
        return list;
    }

    private PetroRecipes() {}
}
