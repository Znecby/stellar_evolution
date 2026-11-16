package dev.modmind.stellar_evolution.material;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 钨（Tungsten）材料全套物品。
 *
 * <p>物品链：粗钨 → 钨粉（酸浸 / 粗钨粉碎）→ 钨锭 → 板 / 杆 / 线 / 齿轮 / 粒；
 * 另有选冶线中间物「钨精砂」。
 * <p>贴图由 {@code tools/gen_tungsten_materials.ps1} 生成：材料模板上色，金属统一冷灰蓝基色；
 * 矿石 / 深板岩矿石 / 钨块由同一脚本换色原版铁矿 / 铁块。双语名在 lang 里。
 */
public final class TungstenMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 粗钨：钨矿石 / 深板岩钨矿石的破碎产物。 */
    public static final DeferredItem<Item> RAW_TUNGSTEN = register("raw_tungsten");
    /** 钨锭：钨粉熔炼 / 精砂还原产物。 */
    public static final DeferredItem<Item> TUNGSTEN_INGOT = register("tungsten_ingot");
    /** 钨粒：9 粒 ↔ 1 锭。 */
    public static final DeferredItem<Item> TUNGSTEN_NUGGET = register("tungsten_nugget");
    /** 钨粉：粗钨粉碎 / 钨精砂酸浸产物。 */
    public static final DeferredItem<Item> TUNGSTEN_DUST = register("tungsten_dust");
    /** 钨板：钨锭压板。 */
    public static final DeferredItem<Item> TUNGSTEN_PLATE = register("tungsten_plate");
    /** 钨杆：钨板压杆（1 板 → 2 杆）。 */
    public static final DeferredItem<Item> TUNGSTEN_ROD = register("tungsten_rod");
    /** 钨线：钨锭轧线（1 锭 → 8 线）。 */
    public static final DeferredItem<Item> TUNGSTEN_WIRE = register("tungsten_wire");
    /** 钨齿轮：1 锭 + 上下左右 4 板。 */
    public static final DeferredItem<Item> TUNGSTEN_GEAR = register("tungsten_gear");
    /** 钨精砂：选冶线浮选 + 结晶中间物。 */
    public static final DeferredItem<Item> TUNGSTEN_CONCENTRATE_SAND = register("tungsten_concentrate_sand");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            RAW_TUNGSTEN, TUNGSTEN_INGOT, TUNGSTEN_NUGGET, TUNGSTEN_DUST,
            TUNGSTEN_PLATE, TUNGSTEN_ROD, TUNGSTEN_WIRE, TUNGSTEN_GEAR,
            TUNGSTEN_CONCENTRATE_SAND);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private TungstenMaterials() {}
}
