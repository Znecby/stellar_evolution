package dev.modmind.stellar_evolution.welding;

import dev.modmind.stellar_evolution.air.AirFluids;
import dev.modmind.stellar_evolution.component.ComponentItems;
import dev.modmind.stellar_evolution.material.AlloyMaterials;
import dev.modmind.stellar_evolution.material.IntermediateItems;
import dev.modmind.stellar_evolution.material.LeadMaterials;
import dev.modmind.stellar_evolution.material.MaterialItems;
import dev.modmind.stellar_evolution.material.TungstenMaterials;
import dev.modmind.stellar_evolution.petro.PetroMaterials;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 焊接机的<strong>内置配方表</strong>（代码内置，与材料加工机械 / 石化线同一口径）。
 *
 * <p>一条配方：
 * <ul>
 *   <li><strong>最多 3 件物品输入</strong>（<strong>固定顺序</strong>，例如槽 0 钨板 / 槽 1 铜板 / 槽 2 钨板）；</li>
 *   <li><strong>最多 1 种流体输入</strong>（从机器<strong>正下方储罐</strong>抽，空配方表示不需要流体）；</li>
 *   <li>产物固定 1 份，放进<strong>正上方容器</strong>；</li>
 *   <li>{@code duration} 为 1.0 倍速下的周期刻数。</li>
 * </ul>
 * 不支持的组合返回 {@code null}；机器一次只做一条配方（不并行）。
 */
public final class WeldingRecipes {
    /** 一条焊接配方：物品输入 + 可选流体 + 产物 + 周期 + 最低 / 基准热流率。 */
    public record Recipe(List<ItemStack> items, FluidStack fluid, ItemStack output, int duration,
                         double minFlowRate, double baseFlowRate) {}

    /** 命中结果：配方 + 「每个配方输入对应哪个料仓格」（重复材料不会错扣）。 */
    public record Match(Recipe recipe, int[] slots) {}

    private static List<Recipe> recipes;

    private static void init() {
        if (recipes != null) {
            return;
        }
        List<Recipe> list = new ArrayList<>();
        // 钨板 + 铜板 + 钨板 → 坚固钨焊接板（2026-11-08 起耗助焊剂 25 mB，不再耗氧）
        list.add(new Recipe(
                List.of(
                        new ItemStack(TungstenMaterials.TUNGSTEN_PLATE.get()),
                        new ItemStack(MaterialItems.COPPER_PLATE.get()),
                        new ItemStack(TungstenMaterials.TUNGSTEN_PLATE.get())),
                new FluidStack(WeldingFluids.FLUX.source().get(), 25),
                new ItemStack(WeldingMaterials.RUGGED_TUNGSTEN_WELDING_PLATE.get()),
                200, 64.0D, 64.0D));
        // 坚固铅焊接板：铅板 ×2 + 铜板（2026-11-08）
        list.add(new Recipe(
                List.of(
                        new ItemStack(LeadMaterials.LEAD_PLATE.get()),
                        new ItemStack(MaterialItems.COPPER_PLATE.get()),
                        new ItemStack(LeadMaterials.LEAD_PLATE.get())),
                new FluidStack(WeldingFluids.FLUX.source().get(), 25),
                new ItemStack(WeldingMaterials.RUGGED_LEAD_WELDING_PLATE.get()),
                200, 64.0D, 64.0D));
        // 坚固高速钢焊接板：高速钢板 ×2 + 铜板（氩弧焊，2026-11-10 改氩气）
        list.add(new Recipe(
                List.of(
                        new ItemStack(AlloyMaterials.HIGH_SPEED_STEEL.plate.get()),
                        new ItemStack(MaterialItems.COPPER_PLATE.get()),
                        new ItemStack(AlloyMaterials.HIGH_SPEED_STEEL.plate.get())),
                new FluidStack(AirFluids.ARGON.source().get(), 25),
                new ItemStack(WeldingMaterials.RUGGED_HIGH_SPEED_STEEL_WELDING_PLATE.get()),
                1200, 512.0D, 512.0D));
        // 坚固不锈钢焊接板：不锈钢板 ×2 + 铜板（氩弧焊，2026-11-10 改氩气）
        list.add(new Recipe(
                List.of(
                        new ItemStack(AlloyMaterials.STAINLESS_STEEL.plate.get()),
                        new ItemStack(MaterialItems.COPPER_PLATE.get()),
                        new ItemStack(AlloyMaterials.STAINLESS_STEEL.plate.get())),
                new FluidStack(AirFluids.ARGON.source().get(), 25),
                new ItemStack(WeldingMaterials.RUGGED_STAINLESS_STEEL_WELDING_PLATE.get()),
                1200, 512.0D, 512.0D));
        // 控制模块：电阻 ×2 + 铜板 ×1 + 电容 ×2（2026-11-08 起耗助焊剂 25 mB，不再耗醋酸）
        list.add(new Recipe(
                List.of(
                        new ItemStack(IntermediateItems.RESISTOR.get(), 2),
                        new ItemStack(MaterialItems.COPPER_PLATE.get()),
                        new ItemStack(IntermediateItems.CAPACITOR.get(), 2)),
                new FluidStack(WeldingFluids.FLUX.source().get(), 25),
                new ItemStack(IntermediateItems.CONTROL_MODULE.get()),
                200, 128.0D, 128.0D));
        // 电路板：电容 ×4 + 电阻 ×4 + 电路基板 ×1（2026-11-08）
        list.add(new Recipe(
                List.of(
                        new ItemStack(IntermediateItems.CAPACITOR.get(), 4),
                        new ItemStack(IntermediateItems.RESISTOR.get(), 4),
                        new ItemStack(PetroMaterials.CIRCUIT_SUBSTRATE.get())),
                new FluidStack(WeldingFluids.FLUX.source().get(), 25),
                new ItemStack(ComponentItems.CIRCUIT_BOARD.get()),
                200, 128.0D, 128.0D));
        recipes = List.copyOf(list);
    }

    /** 全部配方（JEI / 调试用；输入与产物都是副本）。 */
    public static List<Recipe> all() {
        init();
        List<Recipe> out = new ArrayList<>(recipes.size());
        for (Recipe recipe : recipes) {
            List<ItemStack> items = new ArrayList<>(recipe.items().size());
            for (ItemStack stack : recipe.items()) {
                items.add(stack.copy());
            }
            out.add(new Recipe(List.copyOf(items), recipe.fluid().copy(), recipe.output().copy(), recipe.duration(),
                    recipe.minFlowRate(), recipe.baseFlowRate()));
        }
        return out;
    }

    /**
     * 按<strong>固定顺序</strong>匹配物品（不看流体）——示例配方必须槽 0 钨板 / 槽 1 铜板 / 槽 2 钨板。
     * <p>配方不足 3 件时，后续槽必须为空；槽内数量必须<strong>达到</strong>配方要求（多放不扣多余）。
     *
     * @param slots 料仓三格（可含空）
     * @return 命中结果；没有配方则 {@code null}
     */
    @Nullable
    public static Match findByItems(NonNullList<ItemStack> slots) {
        init();
        for (Recipe recipe : recipes) {
            if (recipe.items().size() > slots.size()) {
                continue;
            }
            boolean matched = true;
            for (int i = 0; i < slots.size(); i++) {
                ItemStack want = i < recipe.items().size() ? recipe.items().get(i) : ItemStack.EMPTY;
                if (!ItemStack.isSameItemSameComponents(slots.get(i), want)
                        || slots.get(i).getCount() < want.getCount()) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                int[] order = new int[recipe.items().size()];
                for (int i = 0; i < order.length; i++) {
                    order[i] = i;
                }
                return new Match(recipe, order);
            }
        }
        return null;
    }

    /** 配方流体需求是否满足（空需求恒满足）。 */
    public static boolean fluidMatches(Recipe recipe, FluidStack tankFluid) {
        FluidStack required = recipe.fluid();
        if (required.isEmpty()) {
            return true;
        }
        return FluidStack.isSameFluidSameComponents(tankFluid, required)
                && tankFluid.getAmount() >= required.getAmount();
    }

    private WeldingRecipes() {}
}
