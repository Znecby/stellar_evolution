package dev.modmind.stellar_evolution.ore;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 微量元素粉（2026-11-03 精简后只剩 5 种）。
 *
 * <p>保留：钒（高速钢 / 钛合金）、镓（半导体）、镁（铝镁合金）、硅（晶圆线）。
 * 汞已于 2026-11-08 改为流体（见 {@link OreFluids#MERCURY}）；
 * 锗 / 硒 / 碲（半导体族只留镓）、锌 / 银 / 锑 / 铋 / 钽（无航天或工业 sink）、钪（铝镁合金不用 Al-Sc）
 * 已于 2026-11-03 删除。
 */
public final class TraceElementDusts {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 钒粉：铁渣二段 / 铀渣二段；高速钢与钛合金配料。 */
    public static final DeferredItem<Item> VANADIUM_DUST = register("vanadium_dust");
    /** 镓粉：铝土矿红泥；唯一保留的半导体元素。 */
    public static final DeferredItem<Item> GALLIUM_DUST = register("gallium_dust");
    /** 镁粉：矿物盐分选微量；铝镁合金配料。 */
    public static final DeferredItem<Item> MAGNESIUM_DUST = register("magnesium_dust");
    /** 硅粉：石英渣二段；晶圆 / 集成电路原料。 */
    public static final DeferredItem<Item> SILICON_DUST = register("silicon_dust");

    public static final List<DeferredItem<Item>> ALL = List.of(
            VANADIUM_DUST, GALLIUM_DUST, MAGNESIUM_DUST, SILICON_DUST);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private TraceElementDusts() {}
}
