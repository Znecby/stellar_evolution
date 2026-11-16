package dev.znecby.stellar_evolution.material;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 铅（Lead）材料全套物品（2026-10-26，照钨矿口径）。
 *
 * <p>物品链：粗铅 → 铅粉（粗铅粉碎 / 精砂浸出）→ 铅锭 → 板 / 杆 / 线 / 齿轮 / 粒；
 * 另有选冶线中间物「铅精砂」。
 * <p>贴图由 {@code tools/gen_lead_materials.ps1} 生成：材料模板上色，金属统一冷灰蓝基色；
 * 矿石 / 深板岩矿石 / 铅块由同一脚本换色原版铁矿 / 铁块。双语名在 lang 里。
 */
public final class LeadMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 粗铅：铅矿石 / 深板岩铅矿石的破碎产物。 */
    public static final DeferredItem<Item> RAW_LEAD = register("raw_lead");
    /** 铅锭：铅粉熔炼 / 精砂还原产物。 */
    public static final DeferredItem<Item> LEAD_INGOT = register("lead_ingot");
    /** 铅粒：9 粒 ↔ 1 锭。 */
    public static final DeferredItem<Item> LEAD_NUGGET = register("lead_nugget");
    /** 铅粉：粗铅粉碎 / 铅精砂浸出产物。 */
    public static final DeferredItem<Item> LEAD_DUST = register("lead_dust");
    /** 铅板：铅锭压板。 */
    public static final DeferredItem<Item> LEAD_PLATE = register("lead_plate");
    /** 铅杆：铅板压杆（1 板 → 2 杆）。 */
    public static final DeferredItem<Item> LEAD_ROD = register("lead_rod");
    /** 铅线：铅锭轧线（1 锭 → 8 线）。 */
    public static final DeferredItem<Item> LEAD_WIRE = register("lead_wire");
    /** 铅齿轮：1 锭 + 上下左右 4 板。 */
    public static final DeferredItem<Item> LEAD_GEAR = register("lead_gear");
    /** 铅精砂：选冶线浮选 + 结晶中间物。 */
    public static final DeferredItem<Item> LEAD_CONCENTRATE_SAND = register("lead_concentrate_sand");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            RAW_LEAD, LEAD_INGOT, LEAD_NUGGET, LEAD_DUST,
            LEAD_PLATE, LEAD_ROD, LEAD_WIRE, LEAD_GEAR,
            LEAD_CONCENTRATE_SAND);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private LeadMaterials() {}
}
