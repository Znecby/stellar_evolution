package dev.modmind.stellar_evolution.hep;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/**
 * 回旋加速器的一条配方：<strong>粉 ×N + 差速需求 → 物品 / 流体</strong>。
 *
 * <p>与 {@code FissionRecipe} 分开，因为这里没有「周期」概念 —— 结算是按电荷批量做的。
 */
public record CyclotronRecipe(String id, Item input, int inputCount, double demand,
                              List<ItemOutput> itemOutputs, List<FluidOutput> fluidOutputs) {

    /** 物品产出。 */
    public record ItemOutput(Item item, int count) {}

    /** 流体产出（mB）。 */
    public record FluidOutput(Fluid fluid, int amount) {}
}
