package dev.modmind.stellar_evolution.particle;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 粒子线的<strong>物品</strong>：目前只有压力罐。
 *
 * <p>贴图由 {@code tools/gen_particle_items.ps1} 生成；双语名在 lang 里。
 */
public final class ParticleItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 压力罐：粒子源的燃料罐（耐久 1000，堆叠 1）。 */
    public static final DeferredItem<PressureTankItem> PRESSURE_TANK =
            ITEMS.<PressureTankItem>register("pressure_tank", key -> new PressureTankItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .durability(PressureTankItem.MAX_DAMAGE)
                    .stacksTo(1)));

    /** 束流增压模块：放进粒子源输入箱，束流强度 ×1.5、能耗门槛 ×1.5（不消耗）。 */
    public static final DeferredItem<BoosterModuleItem> BOOSTER_MODULE = ITEMS.register("booster_module",
            key -> new BoosterModuleItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, key))));

    /** 全部物品（创造页按这个顺序走）。 */
    public static final List<DeferredItem<? extends Item>> ALL = List.of(PRESSURE_TANK, BOOSTER_MODULE);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ParticleItems() {}
}
