package dev.modmind.stellar_evolution.machine;

import dev.modmind.stellar_evolution.bedrock.BedrockMaterials;
import dev.modmind.stellar_evolution.bio.BiomassSources;
import dev.modmind.stellar_evolution.bio.BioMaterials;
import dev.modmind.stellar_evolution.coal.CoalMaterials;
import dev.modmind.stellar_evolution.fission.FissionBlocks;
import dev.modmind.stellar_evolution.material.AlloyMaterials;
import dev.modmind.stellar_evolution.material.IntermediateItems;
import dev.modmind.stellar_evolution.material.LeadBlocks;
import dev.modmind.stellar_evolution.material.LeadMaterials;
import dev.modmind.stellar_evolution.material.MaterialItems;
import dev.modmind.stellar_evolution.material.TungstenBlocks;
import dev.modmind.stellar_evolution.material.TungstenMaterials;
import dev.modmind.stellar_evolution.moon.MoonOreBlocks;
import dev.modmind.stellar_evolution.ore.BauxiteBlocks;
import dev.modmind.stellar_evolution.ore.BauxiteMaterials;
import dev.modmind.stellar_evolution.ore.MonaziteBlocks;
import dev.modmind.stellar_evolution.ore.MonaziteMaterials;
import dev.modmind.stellar_evolution.ore.OreMaterials;
import dev.modmind.stellar_evolution.ore.SulfurBlocks;
import dev.modmind.stellar_evolution.ore.TitaniumMaterials;
import dev.modmind.stellar_evolution.petro.PetroMaterials;
import dev.modmind.stellar_evolution.space.SpaceMaterials;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 材料加工机械的三张<strong>内置配方表</strong>（代码内置、不进数据包，与压板配方 / 石化配方同一口径）。
 *
 * <p>口径（2026-11-16 校正）：机器读取内部 9 格料仓，产物输出到正上方容器：
 * <ul>
 *   <li><strong>粉碎</strong>（破碎机）：金属矿 → 对应<strong>粗矿 ×3</strong>；粗矿 → 对应<strong>粉 ×2</strong>；
 *       无粗矿的矿（下界金矿 / 远古残骸 / 非金属）直接出<strong>粉（或煤 / 红石）×2</strong>；
 *       <strong>精砂 ×2 → 粉 ×1</strong>（选冶线取粉口）；石头一族走<strong>链式</strong>：石头 → 圆石 → 沙砾 → 沙。</li>
 *   <li><strong>压模</strong>（压模机）：必须装对应<strong>模具</strong>；板模具 → 锭出<strong>板 ×1</strong>，
 *       杆模具 → 锭 ×2 出<strong>杆 ×2</strong>，齿轮模具 → 锭 ×2 出<strong>齿轮 ×1</strong>。</li>
 *   <li><strong>轧线</strong>（线材轧机）：锭 → 对应<strong>线 ×8</strong>。</li>
 * </ul>
 *
 * <p><strong>材料范围</strong>：铜 / 铁 / 金 / 下界合金 / 钨 / 铅 / 钢 / 6 种合金 / 稀土合金，
 * 与数据包里的切石机板 / 杆 / 线配方逐条对齐；压模额外提供齿轮压制。
 * 另接两条已有物品链：<strong>任意原木 → 木质纤维 ×4</strong>（原版 {@code minecraft:logs} 标签）、
 * <strong>粗铜 → 铜粉 ×2</strong>；以及<strong>植物类物品 → 生物质</strong>（标签
 * {@code stellar_evolution:biomass_sources}，单件产量见 {@link BiomassSources}，小麦块固定 24）。
 *
 * <p>数值与「切石机」那套完全对齐（1 锭 → 1 板 / 1 锭 → 2 杆 / 1 锭 → 8 线），方便玩家对照。
 */
public final class MachineRecipes {

    /** 一条内置配方：产物 + 每次加工需要消耗的输入数量（默认 1；砂→粉为 2）。 */
    public record Entry(ItemStack output, int inputCount) {}

    /**
     * 压模配方：输入物品 + 消耗数量 + <strong>必需模具</strong> + 产物。
     * <p>压模机只有一个模具槽，所以同一种锭可以按「当前装的模具」分派到板 / 杆 / 齿轮。
     */
    public record PressEntry(Item input, int inputCount, Item mold, ItemStack output) {}

    /** 粉碎表（矿物 / 石头 → 产物，数量已含在 ItemStack 里）。 */
    private static Map<Item, Entry> crush;
    /** 压模表（按声明顺序匹配；模具必须与模具槽一致）。 */
    private static List<PressEntry> press;
    /** 轧线表（锭 → 线）。 */
    private static Map<Item, Entry> roll;

    /** 三张表一起懒加载：ItemStack 必须等注册表可用之后再构造。 */
    private static void init() {
        if (crush != null) {
            return;
        }
        Map<Item, Entry> crushTable = new LinkedHashMap<>();
        // ---- 粉碎：金属矿 → 粗矿 ×3 ----
        put(crushTable, Items.IRON_ORE, Items.RAW_IRON, 3);
        put(crushTable, Items.DEEPSLATE_IRON_ORE, Items.RAW_IRON, 3);
        put(crushTable, Items.GOLD_ORE, Items.RAW_GOLD, 3);
        put(crushTable, Items.DEEPSLATE_GOLD_ORE, Items.RAW_GOLD, 3);
        put(crushTable, Items.COPPER_ORE, Items.RAW_COPPER, 3);
        put(crushTable, Items.DEEPSLATE_COPPER_ORE, Items.RAW_COPPER, 3);
        // ---- 粉碎：铀矿 → 粗铀 ×3（选冶线 v6 新增）----
        put(crushTable, FissionBlocks.URANIUM_ORE_ITEM.get(), OreMaterials.RAW_URANIUM.get(), 3);
        put(crushTable, FissionBlocks.DEEPSLATE_URANIUM_ORE_ITEM.get(), OreMaterials.RAW_URANIUM.get(), 3);
        // ---- 粉碎：钨矿 → 粗钨 ×3（2026-10-24 新增）----
        put(crushTable, TungstenBlocks.TUNGSTEN_ORE_ITEM.get(), TungstenMaterials.RAW_TUNGSTEN.get(), 3);
        put(crushTable, TungstenBlocks.DEEPSLATE_TUNGSTEN_ORE_ITEM.get(), TungstenMaterials.RAW_TUNGSTEN.get(), 3);
        // ---- 粉碎：铅矿 → 粗铅 ×3（2026-10-26 新增）----
        put(crushTable, LeadBlocks.LEAD_ORE_ITEM.get(), LeadMaterials.RAW_LEAD.get(), 3);
        put(crushTable, LeadBlocks.DEEPSLATE_LEAD_ORE_ITEM.get(), LeadMaterials.RAW_LEAD.get(), 3);
        // ---- 新矿石（B2 独居石 / B3 铝土矿）：与其他金属矿一致，先出粗矿 ×3 ----
        put(crushTable, MonaziteBlocks.MONAZITE_ORE_ITEM.get(), MonaziteMaterials.RAW_MONAZITE.get(), 3);
        put(crushTable, MonaziteBlocks.DEEPSLATE_MONAZITE_ORE_ITEM.get(), MonaziteMaterials.RAW_MONAZITE.get(), 3);
        put(crushTable, BauxiteBlocks.BAUXITE_ORE_ITEM.get(), BauxiteMaterials.RAW_BAUXITE.get(), 3);
        put(crushTable, BauxiteBlocks.DEEPSLATE_BAUXITE_ORE_ITEM.get(), BauxiteMaterials.RAW_BAUXITE.get(), 3);
        // 月球矿石 → 粗矿 ×3（与主世界矿石同口径，深板岩版一致）
        put(crushTable, MoonOreBlocks.MOON_ILMENITE_ORE_ITEM.get(), TitaniumMaterials.RAW_ILMENITE.get(), 3);
        put(crushTable, MoonOreBlocks.MOON_ICE_ROCK_ORE_ITEM.get(), MonaziteMaterials.ANORTHOSITE.get(), 3);
        put(crushTable, MoonOreBlocks.MOON_GLOW_TITANIUM_ORE_ITEM.get(), MonaziteMaterials.GLOW_TITANIUM_CRYSTAL.get(), 3);
        put(crushTable, MoonOreBlocks.DEEPSLATE_MOON_ILMENITE_ORE_ITEM.get(), TitaniumMaterials.RAW_ILMENITE.get(), 3);
        put(crushTable, MoonOreBlocks.DEEPSLATE_MOON_ICE_ROCK_ORE_ITEM.get(), MonaziteMaterials.ANORTHOSITE.get(), 3);
        // 粗矿 → 重砂 / 矿粉 ×2
        put(crushTable, MonaziteMaterials.RAW_MONAZITE.get(), MonaziteMaterials.MONAZITE_SAND.get(), 2);
        put(crushTable, TitaniumMaterials.RAW_ILMENITE.get(), TitaniumMaterials.ILMENITE_SAND.get(), 2);
        put(crushTable, BauxiteMaterials.RAW_BAUXITE.get(), BauxiteMaterials.BAUXITE_POWDER.get(), 2);
        // 基岩矿 → 基岩粉 ×1（基岩线入口）
        put(crushTable, BedrockMaterials.BEDROCK_ORE.get(), BedrockMaterials.BEDROCK_POWDER.get(), 1);
        // ---- 粉碎：粗矿 → 对应粉 ×2 ----
        put(crushTable, Items.RAW_IRON, OreMaterials.IRON_DUST.get(), 2);
        put(crushTable, Items.RAW_COPPER, MaterialItems.COPPER_DUST.get(), 2);
        put(crushTable, Items.RAW_GOLD, OreMaterials.GOLD_DUST.get(), 2);
        put(crushTable, OreMaterials.RAW_URANIUM.get(), OreMaterials.URANIUM_DUST.get(), 2);
        put(crushTable, TungstenMaterials.RAW_TUNGSTEN.get(), TungstenMaterials.TUNGSTEN_DUST.get(), 2);
        put(crushTable, LeadMaterials.RAW_LEAD.get(), LeadMaterials.LEAD_DUST.get(), 2);
        // ---- 粉碎：金属锭 → 对应粉 ×1（没有粗矿 / 需要直接取粉时）----
        put(crushTable, Items.IRON_INGOT, OreMaterials.IRON_DUST.get(), 1);
        put(crushTable, Items.COPPER_INGOT, MaterialItems.COPPER_DUST.get(), 1);
        put(crushTable, Items.GOLD_INGOT, OreMaterials.GOLD_DUST.get(), 1);
        put(crushTable, OreMaterials.URANIUM_INGOT.get(), OreMaterials.URANIUM_DUST.get(), 1);
        put(crushTable, TungstenMaterials.TUNGSTEN_INGOT.get(), TungstenMaterials.TUNGSTEN_DUST.get(), 1);
        put(crushTable, LeadMaterials.LEAD_INGOT.get(), LeadMaterials.LEAD_DUST.get(), 1);
        // 钢粉：高速钢合金的必需配料（此前只有创造页，B0 补断点）
        put(crushTable, IntermediateItems.STEEL_INGOT.get(), IntermediateItems.STEEL_DUST.get(), 1);
        // ---- 粉碎：宝石 → 对应粉 ×1 ----
        put(crushTable, Items.DIAMOND, OreMaterials.DIAMOND_DUST.get(), 1);
        put(crushTable, Items.EMERALD, OreMaterials.EMERALD_DUST.get(), 1);
        put(crushTable, Items.LAPIS_LAZULI, OreMaterials.LAPIS_DUST.get(), 1);
        put(crushTable, Items.QUARTZ, OreMaterials.QUARTZ_DUST.get(), 1);
        // ---- 无粗矿：原矿直接当粗矿（下界金矿 / 远古残骸 / 非金属）----
        put(crushTable, Items.NETHER_GOLD_ORE, OreMaterials.GOLD_DUST.get(), 2);
        put(crushTable, Items.ANCIENT_DEBRIS, OreMaterials.DEBRIS_DUST.get(), 2);
        put(crushTable, Items.COAL_ORE, Items.COAL, 2);
        put(crushTable, Items.DEEPSLATE_COAL_ORE, Items.COAL, 2);
        // ---- 煤 / 木炭 → 粉（2026-11-12 煤液化前置，让破碎机进入化工主链；1 出 1）----
        put(crushTable, Items.COAL, CoalMaterials.COAL_DUST.get(), 1);
        put(crushTable, Items.CHARCOAL, CoalMaterials.CHARCOAL_DUST.get(), 1);
        put(crushTable, Items.DIAMOND_ORE, OreMaterials.DIAMOND_DUST.get(), 2);
        put(crushTable, Items.DEEPSLATE_DIAMOND_ORE, OreMaterials.DIAMOND_DUST.get(), 2);
        put(crushTable, Items.EMERALD_ORE, OreMaterials.EMERALD_DUST.get(), 2);
        put(crushTable, Items.DEEPSLATE_EMERALD_ORE, OreMaterials.EMERALD_DUST.get(), 2);
        put(crushTable, Items.LAPIS_ORE, OreMaterials.LAPIS_DUST.get(), 2);
        put(crushTable, Items.DEEPSLATE_LAPIS_ORE, OreMaterials.LAPIS_DUST.get(), 2);
        put(crushTable, Items.REDSTONE_ORE, Items.REDSTONE, 2);
        put(crushTable, Items.DEEPSLATE_REDSTONE_ORE, Items.REDSTONE, 2);
        put(crushTable, Items.NETHER_QUARTZ_ORE, OreMaterials.QUARTZ_DUST.get(), 2);
        // 下界硫磺矿 → 3 硫磺晶体（矿石进破碎机）
        put(crushTable, SulfurBlocks.SULFUR_ORE_ITEM.get(), OreMaterials.SULFUR_CRYSTAL.get(), 3);
        // 硫磺晶体：破碎 → 3 硫粉（下界硫磺矿的极简选冶）
        put(crushTable, OreMaterials.SULFUR_CRYSTAL.get(), PetroMaterials.SULFUR_DUST.get(), 3);
        // ---- 精砂 ×2 → 粉 ×1（2:1，只为取粉）----
        put2(crushTable, OreMaterials.IRON_CONCENTRATE_SAND.get(), 2, OreMaterials.IRON_DUST.get(), 1);
        put2(crushTable, OreMaterials.COPPER_CONCENTRATE_SAND.get(), 2, MaterialItems.COPPER_DUST.get(), 1);
        put2(crushTable, OreMaterials.GOLD_CONCENTRATE_SAND.get(), 2, OreMaterials.GOLD_DUST.get(), 1);
        put2(crushTable, OreMaterials.URANIUM_CONCENTRATE_SAND.get(), 2, OreMaterials.URANIUM_DUST.get(), 1);
        put2(crushTable, TungstenMaterials.TUNGSTEN_CONCENTRATE_SAND.get(), 2, TungstenMaterials.TUNGSTEN_DUST.get(), 1);
        put2(crushTable, LeadMaterials.LEAD_CONCENTRATE_SAND.get(), 2, LeadMaterials.LEAD_DUST.get(), 1);
        put2(crushTable, OreMaterials.DEBRIS_CONCENTRATE_SAND.get(), 2, OreMaterials.DEBRIS_DUST.get(), 1);
        put2(crushTable, OreMaterials.COAL_CONCENTRATE_SAND.get(), 2, Items.COAL, 1);
        put2(crushTable, OreMaterials.DIAMOND_CONCENTRATE_SAND.get(), 2, OreMaterials.DIAMOND_DUST.get(), 1);
        put2(crushTable, OreMaterials.EMERALD_CONCENTRATE_SAND.get(), 2, OreMaterials.EMERALD_DUST.get(), 1);
        put2(crushTable, OreMaterials.LAPIS_CONCENTRATE_SAND.get(), 2, OreMaterials.LAPIS_DUST.get(), 1);
        put2(crushTable, OreMaterials.REDSTONE_CONCENTRATE_SAND.get(), 2, Items.REDSTONE, 1);
        put2(crushTable, OreMaterials.QUARTZ_CONCENTRATE_SAND.get(), 2, OreMaterials.QUARTZ_DUST.get(), 1);
        // ---- 粉碎：石头一族（链式：石头 → 圆石 → 沙砾 → 沙；沙是链尾）----
        put(crushTable, Items.STONE, Items.COBBLESTONE, 1);
        put(crushTable, Items.COBBLESTONE, Items.GRAVEL, 1);
        put(crushTable, Items.GRAVEL, Items.SAND, 1);
        crush = Map.copyOf(crushTable);

        List<PressEntry> pressTable = new ArrayList<>();
        // ---- 板模具：锭 → 板 ×1 ----
        putPress(pressTable, Items.COPPER_INGOT, 1, IntermediateItems.PLATE_MOLD.get(), MaterialItems.COPPER_PLATE.get(), 1);
        putPress(pressTable, Items.IRON_INGOT, 1, IntermediateItems.PLATE_MOLD.get(), MaterialItems.IRON_PLATE.get(), 1);
        putPress(pressTable, Items.GOLD_INGOT, 1, IntermediateItems.PLATE_MOLD.get(), MaterialItems.GOLD_PLATE.get(), 1);
        putPress(pressTable, Items.NETHERITE_INGOT, 1, IntermediateItems.PLATE_MOLD.get(), MaterialItems.NETHERITE_PLATE.get(), 1);
        putPress(pressTable, TungstenMaterials.TUNGSTEN_INGOT.get(), 1, IntermediateItems.PLATE_MOLD.get(), TungstenMaterials.TUNGSTEN_PLATE.get(), 1);
        putPress(pressTable, LeadMaterials.LEAD_INGOT.get(), 1, IntermediateItems.PLATE_MOLD.get(), LeadMaterials.LEAD_PLATE.get(), 1);
        putPress(pressTable, IntermediateItems.STEEL_INGOT.get(), 1, IntermediateItems.PLATE_MOLD.get(), IntermediateItems.STEEL_PLATE.get(), 1);
        for (AlloyMaterials.Alloy alloy : AlloyMaterials.ALL) {
            putPress(pressTable, alloy.ingot.get(), 1, IntermediateItems.PLATE_MOLD.get(), alloy.plate.get(), 1);
        }
        putPress(pressTable, SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get(), 1, IntermediateItems.PLATE_MOLD.get(),
                SpaceMaterials.RARE_EARTH_ALLOY_PLATE.get(), 1);

        // ---- 杆模具：锭 ×2 → 杆 ×2（与切石机口径一致） ----
        putPress(pressTable, Items.COPPER_INGOT, 2, IntermediateItems.ROD_MOLD.get(), MaterialItems.COPPER_ROD.get(), 2);
        putPress(pressTable, Items.NETHERITE_INGOT, 2, IntermediateItems.ROD_MOLD.get(), MaterialItems.NETHERITE_ROD.get(), 2);
        putPress(pressTable, TungstenMaterials.TUNGSTEN_INGOT.get(), 2, IntermediateItems.ROD_MOLD.get(), TungstenMaterials.TUNGSTEN_ROD.get(), 2);
        putPress(pressTable, LeadMaterials.LEAD_INGOT.get(), 2, IntermediateItems.ROD_MOLD.get(), LeadMaterials.LEAD_ROD.get(), 2);
        putPress(pressTable, IntermediateItems.STEEL_INGOT.get(), 2, IntermediateItems.ROD_MOLD.get(), IntermediateItems.STEEL_ROD.get(), 2);
        for (AlloyMaterials.Alloy alloy : AlloyMaterials.ALL) {
            putPress(pressTable, alloy.ingot.get(), 2, IntermediateItems.ROD_MOLD.get(), alloy.rod.get(), 2);
        }
        putPress(pressTable, SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get(), 2, IntermediateItems.ROD_MOLD.get(),
                SpaceMaterials.RARE_EARTH_ALLOY_ROD.get(), 2);

        // ---- 齿轮模具：锭 ×2 → 齿轮 ×1 ----
        putPress(pressTable, Items.COPPER_INGOT, 2, IntermediateItems.GEAR_MOLD.get(), MaterialItems.COPPER_GEAR.get(), 1);
        putPress(pressTable, TungstenMaterials.TUNGSTEN_INGOT.get(), 2, IntermediateItems.GEAR_MOLD.get(), TungstenMaterials.TUNGSTEN_GEAR.get(), 1);
        putPress(pressTable, LeadMaterials.LEAD_INGOT.get(), 2, IntermediateItems.GEAR_MOLD.get(), LeadMaterials.LEAD_GEAR.get(), 1);
        putPress(pressTable, IntermediateItems.STEEL_INGOT.get(), 2, IntermediateItems.GEAR_MOLD.get(), IntermediateItems.STEEL_GEAR.get(), 1);
        for (AlloyMaterials.Alloy alloy : AlloyMaterials.ALL) {
            putPress(pressTable, alloy.ingot.get(), 2, IntermediateItems.GEAR_MOLD.get(), alloy.gear.get(), 1);
        }
        press = List.copyOf(pressTable);

        Map<Item, Entry> rollTable = new LinkedHashMap<>();
        // ---- 轧线：铜 / 金 / 钨 / 铅 + 6 种合金（与切石机线材配方对齐）----
        put(rollTable, Items.COPPER_INGOT, MaterialItems.COPPER_WIRE.get(), 8);
        put(rollTable, Items.GOLD_INGOT, MaterialItems.GOLD_WIRE.get(), 8);
        put(rollTable, TungstenMaterials.TUNGSTEN_INGOT.get(), TungstenMaterials.TUNGSTEN_WIRE.get(), 8);
        put(rollTable, LeadMaterials.LEAD_INGOT.get(), LeadMaterials.LEAD_WIRE.get(), 8);
        for (AlloyMaterials.Alloy alloy : AlloyMaterials.ALL) {
            put(rollTable, alloy.ingot.get(), alloy.wire.get(), 8);
        }
        roll = Map.copyOf(rollTable);
    }

    private static void put(Map<Item, Entry> table, Item input, Item output, int count) {
        table.put(input, new Entry(new ItemStack(output, count), 1));
    }

    /** 需要多件输入的配方（如精砂 ×2 → 粉 ×1）。 */
    private static void put2(Map<Item, Entry> table, Item input, int inputCount, Item output, int outputCount) {
        table.put(input, new Entry(new ItemStack(output, outputCount), inputCount));
    }

    /** 一条压模配方：输入 + 数量 + 必需模具 → 产物。 */
    private static void putPress(List<PressEntry> table, Item input, int inputCount, Item mold,
                                 Item output, int outputCount) {
        table.add(new PressEntry(input, inputCount, mold, new ItemStack(output, outputCount)));
    }

    /**
     * 按「机种 + 输入物品 + 当前模具」查一条配方；查不到返回 {@code null}。
     * <p>压模机必须装对应模具（板 / 杆 / 齿轮），其它机种忽略模具。
     */
    @Nullable
    public static Entry find(MachineKind kind, ItemStack input, ItemStack mold) {
        if (input.isEmpty()) {
            return null;
        }
        init();
        if (kind == MachineKind.DIE_PRESS) {
            for (PressEntry entry : press) {
                if (entry.input() == input.getItem() && !mold.isEmpty() && mold.is(entry.mold())) {
                    return new Entry(entry.output().copy(), entry.inputCount());
                }
            }
            return null;
        }
        // 植物类 → 生物质（不同作物产量不同；小麦块单独按 24 计）
        if (kind == MachineKind.CRUSHER) {
            if (input.is(Items.HAY_BLOCK)) {
                return new Entry(new ItemStack(BioMaterials.BIOMASS.get(), BiomassSources.HAY_BLOCK_BIOMASS), 1);
            }
            if (input.is(BiomassSources.SOURCES)) {
                return new Entry(new ItemStack(BioMaterials.BIOMASS.get(), BiomassSources.biomassPerItem(input.getItem())), 1);
            }
            // 任意原木 → 木质纤维 ×4（走原版 logs 物品标签，含其它模组的原木）
            if (input.is(ItemTags.LOGS)) {
                return new Entry(new ItemStack(BioMaterials.LIGNOCELLULOSE.get(), 4), 1);
            }
        }
        Entry entry = tableFor(kind).get(input.getItem());
        return entry == null ? null : new Entry(entry.output().copy(), entry.inputCount());
    }

    /** 某机种的全部配方（<strong>JEI 摆表</strong>用；顺序 = 登记顺序，输入 / 产物都是副本，随便改）。 */
    public static List<MachineRecipe> samplesFor(MachineKind kind) {
        init();
        List<MachineRecipe> list = new ArrayList<>();
        if (kind == MachineKind.DIE_PRESS) {
            for (PressEntry entry : press) {
                list.add(new MachineRecipe(new ItemStack(entry.input(), entry.inputCount()),
                        entry.output().copy(), new ItemStack(entry.mold())));
            }
            return list;
        }
        for (Map.Entry<Item, Entry> entry : tableFor(kind).entrySet()) {
            list.add(new MachineRecipe(new ItemStack(entry.getKey(), entry.getValue().inputCount()),
                    entry.getValue().output().copy(), ItemStack.EMPTY));
        }
        // 原木 / 作物按标签合并成一条配方，JEI 槽内轮换显示全部成员
        if (kind == MachineKind.CRUSHER) {
            list.add(new MachineRecipe(new ItemStack(Items.OAK_LOG),
                    new ItemStack(BioMaterials.LIGNOCELLULOSE.get(), 4), ItemStack.EMPTY,
                    ItemTags.LOGS, null));
            list.add(new MachineRecipe(new ItemStack(Items.WHEAT),
                    new ItemStack(BioMaterials.BIOMASS.get(), 2), ItemStack.EMPTY,
                    BiomassSources.SOURCES,
                    Component.translatable("stellar_evolution.jei.machine.biomass_variable")));
            list.add(new MachineRecipe(new ItemStack(Items.HAY_BLOCK),
                    new ItemStack(BioMaterials.BIOMASS.get(), BiomassSources.HAY_BLOCK_BIOMASS)));
        }
        return list;
    }

    /** 机种 → 配方表。 */
    private static Map<Item, Entry> tableFor(MachineKind kind) {
        return switch (kind) {
            case CRUSHER -> crush;
            case DIE_PRESS -> Map.of();   // 压模走 List<PressEntry>，见 find / samplesFor
            case ROLLING_MILL -> roll;
            // 钢炉走原版熔炼配方（见 MachineBlockEntity#matchFor），没有内置表
            case STEEL_FURNACE -> Map.of();
        };
    }

    private MachineRecipes() {}
}
