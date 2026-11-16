package dev.znecby.stellar_evolution.ore;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 盐湖 / 矿物盐支线（B1）的物品。
 *
 * <p>入口是现有 {@code BioMaterials.MINERAL_SALT}；分解仪分选 → 硼砂 + 氯化钠，
 * 再往硼 / 钠 / 氯 / 氢氧化钠四条去向走。全部复用现有机器，不新增矿。
 * <p>硼酸已改为流体（{@link OreFluids#BORIC_ACID}，2026-11-08），这里不再注册物品。
 */
public final class SaltMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 硼砂：矿物盐分选产物，制助焊剂 / 酸浸后得硼酸流体。 */
    public static final DeferredItem<Item> BORAX = register("borax");
    /** 硼粉：硼酸流体 + 石油焦还原产物；碳化硼棒 / B-11 的原料。 */
    public static final DeferredItem<Item> BORON_DUST = register("boron_dust");
    /** 钠粉：氯化钠电解产物；钠冷 / 氢氧化钠的原料。 */
    public static final DeferredItem<Item> SODIUM_DUST = register("sodium_dust");
    /** 氯化钠：矿物盐分选产物；电解得钠 + 氯气。 */
    public static final DeferredItem<Item> SODIUM_CHLORIDE = register("sodium_chloride");
    /** 氢氧化钠：钠粉 + 水的产物；B3 铝土矿拜耳法用。 */
    public static final DeferredItem<Item> SODIUM_HYDROXIDE = register("sodium_hydroxide");

    public static final List<DeferredItem<Item>> ALL = List.of(
            BORAX, BORON_DUST, SODIUM_DUST, SODIUM_CHLORIDE, SODIUM_HYDROXIDE);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private SaltMaterials() {}
}
