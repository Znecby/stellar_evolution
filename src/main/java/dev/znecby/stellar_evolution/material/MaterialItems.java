package dev.znecby.stellar_evolution.material;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 材料物品：铜材质补齐原版铜锭**没有的 5 种类型**（原版铜只有 ingot / nugget / block）。
 * <p>
 * 贴图由 {@code tools/gen_copper_materials.ps1} 生成：拿「浅色灰度模板」（`模板/材料/浅色/titanium_gold_*.png`），
 * 按原版 {@code copper_ingot.png} 自身的**明暗色带**（256 级 LUT，实测 135 个不透明像素、亮度 66.9~210.3）逐像素映射上色，
 * 所以立体高光与暗部层次都保留，颜色与铜锭一致。
 * <ul>
 *   <li>{@code copper_dust} 铜粉 / {@code copper_plate} 铜板 / {@code copper_rod} 铜杆 / {@code copper_wire} 铜线 / {@code copper_gear} 铜齿轮；</li>
 *   <li>原版已有的 ingot / nugget / block **不重复注册**，直接沿用原版物品。</li>
 * </ul>
 * <p><strong>获取途径</strong>：铜板 = 加工台压板 / <strong>切石机</strong>（1 铜锭 → 1 板）；铜杆 = 切石机（1 锭 → 2 杆）；
 * 铜线 = 切石机（1 锭 → 8 线）；铜齿轮 = <strong>工作台</strong>（1 铜锭 + 上下左右 4 铜板）。
 */
public final class MaterialItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredItem<Item> COPPER_DUST = register("copper_dust");
    public static final DeferredItem<Item> COPPER_PLATE = register("copper_plate");
    public static final DeferredItem<Item> COPPER_ROD = register("copper_rod");
    public static final DeferredItem<Item> COPPER_WIRE = register("copper_wire");
    public static final DeferredItem<Item> COPPER_GEAR = register("copper_gear");

    // ---- 原版金属型材（2026-10-24 补）：铁 / 金 / 下界合金
    /** 铁板：铁锭压板 / 切石机。 */
    public static final DeferredItem<Item> IRON_PLATE = register("iron_plate");
    /** 金板：金锭压板 / 切石机。 */
    public static final DeferredItem<Item> GOLD_PLATE = register("gold_plate");
    /** 金线：金锭轧线（1 锭 → 8 线）。 */
    public static final DeferredItem<Item> GOLD_WIRE = register("gold_wire");
    /** 下界合金板：下界合金锭压板。 */
    public static final DeferredItem<Item> NETHERITE_PLATE = register("netherite_plate");
    /** 下界合金杆：下界合金板压杆（1 板 → 2 杆）。 */
    public static final DeferredItem<Item> NETHERITE_ROD = register("netherite_rod");

    // ---- 高温玻璃链（2026-10-24 补）：石英玻璃 → 钢化玻璃 ----
    /** 石英玻璃：石英 + 玻璃在热室熔制的耐酸 / 耐温玻璃。 */
    public static final DeferredItem<Item> QUARTZ_GLASS = register("quartz_glass");

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private MaterialItems() {}
}
