package dev.znecby.stellar_evolution.thermal;

import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * <strong>热室</strong>的一条配方 —— <strong>单独的配方类型</strong>（不与熔炼 / 生物 / 石化共用）。
 *
 * <p>口径：<strong>只吃物品、不吃流体</strong>；能源一律来自<strong>热压差网络</strong>
 * （网络热流率只决定倍速，不改写产率）。
 *
 * <p>本记录只是「配方数据」，不含任何逻辑；匹配 / 扣料 / 产出都在 {@link HotChamberRecipes} 里完成。
 *
 * @param itemInputs  精确物品输入（每种带数量；均为只读模板，切勿就地修改）
 * @param tagInputs   <strong>标签输入</strong>（2026-11-08：同一功能槽可自由选择同级塑料）
 * @param itemOutputs 物品产出
 * @param duration    加工时长（tick，20 tick = 1 秒）
 * @param pressure    <strong>等压值</strong>（0 = 默认配方组；其余为互相抢料时拆出的专用组，见 {@link HotChamberRecipes} 顶部）
 */
public record HotChamberRecipe(List<ItemStack> itemInputs, List<TagInput> tagInputs,
                               List<ItemStack> itemOutputs, int duration, int pressure) {
    /** 一条「任意属于该标签的物品 × count」输入；{@code display} 只用于 JEI 图标。 */
    public record TagInput(TagKey<Item> tag, int count, ItemStack display) {
        public TagInput {
            if (count <= 0) {
                throw new IllegalArgumentException("Tag input count must be positive: " + tag);
            }
            display = display.copy();
        }
    }

    public HotChamberRecipe {
        itemInputs = List.copyOf(itemInputs);
        tagInputs = List.copyOf(tagInputs);
        itemOutputs = List.copyOf(itemOutputs);
    }

    /** 无标签输入的旧写法重载。 */
    public HotChamberRecipe(List<ItemStack> itemInputs, List<ItemStack> itemOutputs, int duration, int pressure) {
        this(itemInputs, List.of(), itemOutputs, duration, pressure);
    }

    /** 等压 = 0 的旧写法重载。 */
    public HotChamberRecipe(List<ItemStack> itemInputs, List<ItemStack> itemOutputs, int duration) {
        this(itemInputs, List.of(), itemOutputs, duration, 0);
    }

    /** 输入槽总数（精确物品 + 标签，JEI 3×3 上限用）。 */
    public int inputSlotCount() {
        return itemInputs.size() + tagInputs.size();
    }
}
