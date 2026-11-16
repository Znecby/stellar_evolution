package dev.modmind.stellar_evolution.welding;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 焊接线的材料物品：<strong>坚固钨 / 铅 / 高速钢 / 不锈钢焊接板</strong>。
 *
 * <p>贴图由 {@code tools/gen_welding_assets.ps1} 生成：
 * 焊接板用浅色灰度模板，坚固系列按各自基色重新上色。
 */
public final class WeldingMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 坚固钨焊接板：钨板 ×2 + 铜板 + 25 mB 助焊剂。 */
    public static final DeferredItem<Item> RUGGED_TUNGSTEN_WELDING_PLATE =
            register("rugged_tungsten_welding_plate");
    /** 坚固铅焊接板：铅板 ×2 + 铜板 + 25 mB 助焊剂（2026-11-08）。 */
    public static final DeferredItem<Item> RUGGED_LEAD_WELDING_PLATE =
            register("rugged_lead_welding_plate");
    /** 坚固高速钢焊接板：高速钢板 ×2 + 铜板 + 25 mB 助焊剂（2026-11-08）。 */
    public static final DeferredItem<Item> RUGGED_HIGH_SPEED_STEEL_WELDING_PLATE =
            register("rugged_high_speed_steel_welding_plate");
    /** 坚固不锈钢焊接板：不锈钢板 ×2 + 铜板 + 25 mB 助焊剂（2026-11-08）。 */
    public static final DeferredItem<Item> RUGGED_STAINLESS_STEEL_WELDING_PLATE =
            register("rugged_stainless_steel_welding_plate");

    /** 全部物品（创造页 / 生成脚本按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            RUGGED_TUNGSTEN_WELDING_PLATE,
            RUGGED_LEAD_WELDING_PLATE, RUGGED_HIGH_SPEED_STEEL_WELDING_PLATE,
            RUGGED_STAINLESS_STEEL_WELDING_PLATE);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private WeldingMaterials() {}
}
