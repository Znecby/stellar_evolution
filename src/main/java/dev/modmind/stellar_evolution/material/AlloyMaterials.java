package dev.modmind.stellar_evolution.material;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * <strong>合金材料</strong>（2026-11-01）：6 种合金，每种 7 种形态
 * （锭 / 粒 / 板 / 杆 / 线 / 齿轮 / 块）。
 *
 * <p><strong>产线</strong>：只做「配料粉 → 锭」这一段 —— 6 条配方都在<strong>热室</strong>
 * （{@link dev.modmind.stellar_evolution.thermal.HotChamberRecipes}），
 * <strong>出锭数 = 投料粉的总份数</strong>（质量守恒，例如高速钢 8 + 4 + 12 + 2 = 26 份 → 26 锭）。
 * 锭 → 粒 / 块 / 板 / 杆 / 线 / 齿轮走原版工作台与切石机（数据包配方，照钨 / 铅口径）。
 *
 * <p><strong>2026-11-03 简化</strong>：高速钢-M 并入高速钢；钛钼铬 → 钛合金；锆基非晶删除；
 * 新增铝镁合金。合金粉仍不恢复（无产出、无用途）。
 *
 * <p>贴图由 {@code tools/gen_alloy_materials.ps1} 生成（灰度模板上色 + 金属块模板）；双语名在 lang 里。
 */
public final class AlloyMaterials {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 高速钢：钨 8 + 铬 4 + 钢 12 + 钒 2 → 26 锭。 */
    public static final Alloy HIGH_SPEED_STEEL =
            new Alloy("high_speed_steel", "高速钢", "High-Speed Steel", 124, 134, 152);
    /** 不锈钢：铁 8 + 铬 3 + 镍 1 → 12 锭。 */
    public static final Alloy STAINLESS_STEEL =
            new Alloy("stainless_steel", "不锈钢", "Stainless Steel", 200, 206, 214);
    /** 钨铜硬质合金：铜 3 + 钨 14 → 17 锭。 */
    public static final Alloy TUNGSTEN_COPPER_HARD_ALLOY =
            new Alloy("tungsten_copper_hard_alloy", "钨铜硬质合金", "Tungsten-Copper Hard Alloy", 126, 112, 98);
    /** 镍基高温合金：镍 7 + 铬 3 + 钼 2 → 12 锭。 */
    public static final Alloy NICKEL_SUPERALLOY =
            new Alloy("nickel_superalloy", "镍基高温合金", "Nickel Superalloy", 172, 174, 162);
    /** 钛合金：钛 8 + 铝 4 + 钒 2 → 14 锭。 */
    public static final Alloy TITANIUM_ALLOY =
            new Alloy("titanium_alloy", "钛合金", "Titanium Alloy", 150, 164, 180);
    /** 铝镁合金：铝 8 + 镁 2 → 10 锭。 */
    public static final Alloy ALUMINUM_MAGNESIUM_ALLOY =
            new Alloy("aluminum_magnesium_alloy", "铝镁合金", "Aluminum-Magnesium Alloy", 196, 200, 206);

    /** 全部合金（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<Alloy> ALL = List.of(
            HIGH_SPEED_STEEL, STAINLESS_STEEL,
            TUNGSTEN_COPPER_HARD_ALLOY, NICKEL_SUPERALLOY,
            TITANIUM_ALLOY, ALUMINUM_MAGNESIUM_ALLOY);

    /**
     * 一种合金：7 种形态共用同一 id 前缀与同一个基色（贴图由脚本按基色上色）。
     */
    public static final class Alloy {
        private final String id;
        private final String zh;
        private final String en;
        private final int rgb;

        /** 合金锭：热室由配料粉合成。 */
        public final DeferredItem<Item> ingot;
        public final DeferredItem<Item> nugget;
        public final DeferredItem<Item> plate;
        public final DeferredItem<Item> rod;
        public final DeferredItem<Item> wire;
        public final DeferredItem<Item> gear;
        /** 9 锭存储块。 */
        public final DeferredBlock<Block> block;
        public final DeferredItem<BlockItem> blockItem;

        private Alloy(String id, String zh, String en, int r, int g, int b) {
            this.id = id;
            this.zh = zh;
            this.en = en;
            this.rgb = (r << 16) | (g << 8) | b;
            this.ingot = item(id + "_ingot");
            this.nugget = item(id + "_nugget");
            this.plate = item(id + "_plate");
            this.rod = item(id + "_rod");
            this.wire = item(id + "_wire");
            this.gear = item(id + "_gear");
            this.block = BLOCKS.register(id + "_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(id + "_block")))));
            this.blockItem = ITEMS.register(id + "_block", key -> new BlockItem(block.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));
        }

        public String id() {
            return id;
        }

        public String zh() {
            return zh;
        }

        public String en() {
            return en;
        }

        /** 基色（0xRRGGBB），生成脚本与贴图共用。 */
        public int rgb() {
            return rgb;
        }

        /** 创造页顺序：锭 → 粒 → 板 → 杆 → 线 → 齿轮 → 块。 */
        public List<DeferredItem<? extends Item>> items() {
            return List.of(ingot, nugget, plate, rod, wire, gear, blockItem);
        }
    }

    private static DeferredItem<Item> item(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private AlloyMaterials() {}
}
