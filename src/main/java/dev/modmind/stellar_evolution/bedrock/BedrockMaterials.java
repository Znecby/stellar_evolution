package dev.modmind.stellar_evolution.bedrock;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 基岩线的<strong>物品类物质</strong>（《基岩无限资源线蓝图》§8.2）。
 *
 * <p>只有 5 件：基岩矿 / 基岩粉 / 活化基岩粉 / 基岩浸出渣（带段位组件）/ 混合硫化物精矿。
 * 其余产物全部复用已有元素粉与流体，不再新增物品。
 */
public final class BedrockMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 基岩矿：开采钻基岩模式的产物。 */
    public static final DeferredItem<Item> BEDROCK_ORE = register("bedrock_ore");
    /** 基岩粉：破碎机产物。 */
    public static final DeferredItem<Item> BEDROCK_POWDER = register("bedrock_powder");
    /** 高振基岩晶态物：γ 活化溶剂浸泡后的产物；始终带原版附魔光效。 */
    public static final DeferredItem<BedrockCrystalItem> ACTIVATED_BEDROCK_POWDER =
            ITEMS.register("activated_bedrock_powder",
                    key -> new BedrockCrystalItem(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))));
    /** 基岩浸出渣：带 {@link BedrockComponents#BEDROCK_STAGE} 段位。 */
    public static final DeferredItem<BedrockResidueItem> BEDROCK_RESIDUE = ITEMS.register("bedrock_residue",
            key -> new BedrockResidueItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))));
    /** 混合硫化物精矿：S4 硫化沉淀产物。 */
    public static final DeferredItem<Item> MIXED_SULFIDE = register("mixed_sulfide");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<? extends Item>> ALL = List.of(
            BEDROCK_ORE, BEDROCK_POWDER, ACTIVATED_BEDROCK_POWDER,
            BEDROCK_RESIDUE, MIXED_SULFIDE);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private BedrockMaterials() {}
}
