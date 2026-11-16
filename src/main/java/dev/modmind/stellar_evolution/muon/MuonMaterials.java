package dev.modmind.stellar_evolution.muon;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 聚变线物品：聚变芯核（定位同裂变燃料棒）。
 *
 * <p>贴图由 {@code tools/gen_fission_materials.ps1} 生成；双语名在 lang 里。
 */
public final class MuonMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 氘氚聚变芯核：约束缪子流。 */
    public static final DeferredItem<MuonCoreItem> DT_FUSION_CORE = register(MuonCoreSpec.DEUTERIUM_TRITIUM);
    /** 氘氘聚变芯核。 */
    public static final DeferredItem<MuonCoreItem> DD_FUSION_CORE = register(MuonCoreSpec.DEUTERIUM_DEUTERIUM);
    /** 氘-氦3 聚变芯核。 */
    public static final DeferredItem<MuonCoreItem> DHE3_FUSION_CORE = register(MuonCoreSpec.DEUTERIUM_HELIUM3);
    /** 质子-硼11 聚变芯核。 */
    public static final DeferredItem<MuonCoreItem> PB11_FUSION_CORE = register(MuonCoreSpec.PROTON_BORON11);
    /** 星核聚变芯核（§7.12）。 */
    public static final DeferredItem<MuonCoreItem> STAR_FUSION_CORE = register(MuonCoreSpec.STAR_CORE);

    /** 全部芯核（创造页按这个顺序走）。 */
    public static final List<DeferredItem<? extends Item>> ALL = List.of(
            DT_FUSION_CORE, DD_FUSION_CORE, DHE3_FUSION_CORE, PB11_FUSION_CORE, STAR_FUSION_CORE);

    private static DeferredItem<MuonCoreItem> register(MuonCoreSpec spec) {
        return ITEMS.<MuonCoreItem>register(spec.itemId(), key -> new MuonCoreItem(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))
                .durability(spec.maxDamage()), spec));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private MuonMaterials() {}
}
