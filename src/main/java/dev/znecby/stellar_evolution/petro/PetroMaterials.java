package dev.znecby.stellar_evolution.petro;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 石化线的<strong>物品类物质</strong>（《石化线》O0 ①；清单见 §4.3 / §4.4）。
 *
 * <p>原料是<strong>原油</strong>（世界生成的石油湖，见 {@link PetroBlocks}）；本类只放其余物品：
 * <ul>
 *   <li><strong>塑料</strong>：电木（酚醛树脂）/ 有机玻璃（PMMA）/ 聚四氟乙烯（PTFE）/ 尼龙（PA6）——
 *       各占一个功能位（耐热绝缘 / 透明 / 耐腐蚀 / 耐磨结构）；</li>
 *   <li><strong>橡胶</strong>：丁苯 SBR / 丁腈 NBR / 氯丁 CR / 硅橡胶——各占一个功能位
 *       （耐磨通用 / 耐油 / 耐候 / 耐高温）；</li>
 *   <li><strong>辅料</strong>：石油焦（焦化副产）、硫粉（分馏副产，硫化辅料）；</li>
 *   <li><strong>制品</strong>：电路板基材（电木工作台压板，其余塑料直接进配方）。</li>
 * </ul>
 * <p>贴图由 {@code tools/gen_petro_materials.ps1} 生成（16×16 程序化图标，零外部素材）；双语名在 lang 里。
 */
public final class PetroMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    // ---- 塑料（聚合釜：单体 → 树脂）
    /** 电木（酚醛树脂）：耐热绝缘，电路基材。 */
    public static final DeferredItem<Item> BAKELITE = register("bakelite");
    /** 有机玻璃（PMMA）：透明件 / 透明板材。 */
    public static final DeferredItem<Item> PMMA = register("pmma");
    /** 聚四氟乙烯（PTFE）：耐腐蚀管道 / 密封。 */
    public static final DeferredItem<Item> PTFE = register("ptfe");
    /** 尼龙（PA6）：耐磨结构件 / 齿轮。 */
    public static final DeferredItem<Item> NYLON = register("nylon");
    // ---- 橡胶（聚合釜：单体 → 生胶）
    /** 丁苯橡胶（SBR）：耐磨通用，耐磨橡胶板。 */
    public static final DeferredItem<Item> SBR = register("sbr");
    /** 丁腈橡胶（NBR）：耐油密封。 */
    public static final DeferredItem<Item> NBR = register("nbr");
    /** 氯丁橡胶（CR）：耐候软管。 */
    public static final DeferredItem<Item> CR = register("cr");
    /** 硅橡胶：耐高温密封。 */
    public static final DeferredItem<Item> SILICONE_RUBBER = register("silicone_rubber");
    // ---- 辅料
    /** 石油焦：焦化副产（碳源 / 燃料）。 */
    public static final DeferredItem<Item> PETROLEUM_COKE = register("petroleum_coke");
    /** 硫粉：分馏副产，硫化辅料。 */
    public static final DeferredItem<Item> SULFUR_DUST = register("sulfur_dust");
    // ---- 制品（仅电路板基材保留；其余塑料直接进配方，2026-11-08）
    /** 电路板基材：电木工作台压制件。 */
    public static final DeferredItem<Item> CIRCUIT_SUBSTRATE = register("circuit_substrate");
    // ---- 石化 3（辐照仓）：高性能变体
    /** 交联塑料：质子束交联的生物塑料（目前无下游，保留物品）。 */
    public static final DeferredItem<Item> CROSSLINKED_PLASTIC = register("crosslinked_plastic");
    /** 离子径迹膜：铅离子打孔 + 蚀刻的 PTFE 膜（过滤 / 分离材料）。 */
    public static final DeferredItem<Item> TRACK_ETCH_MEMBRANE = register("track_etch_membrane");
    /** 放射性接枝塑料：放射性离子接枝的塑料（目前无下游，保留物品）。 */
    public static final DeferredItem<Item> GRAFTED_PLASTIC = register("grafted_plastic");
    /** 手性聚合物：极化质子束处理电木得到的高端材料（精密伺服件）。 */
    public static final DeferredItem<Item> CHIRAL_POLYMER = register("chiral_polymer");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            BAKELITE, PMMA, PTFE, NYLON,
            SBR, NBR, CR, SILICONE_RUBBER,
            PETROLEUM_COKE, SULFUR_DUST,
            CIRCUIT_SUBSTRATE,
            CROSSLINKED_PLASTIC, TRACK_ETCH_MEMBRANE, GRAFTED_PLASTIC, CHIRAL_POLYMER);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private PetroMaterials() {}
}
