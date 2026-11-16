package dev.modmind.stellar_evolution.material;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.fission.FissionBlocks;
import dev.modmind.stellar_evolution.ore.BauxiteBlocks;
import dev.modmind.stellar_evolution.ore.MonaziteBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 矿石判定（开采钻精准采集 / 钢制工具连锁挖矿共用）。
 *
 * <p>口径：{@code c:ores} 标签 + 需求点名的特殊方块（远古残骸 / 萤石 / 镶金黑石）+ 本模组矿石。
 * 两处共用同一份实现，避免「钻头认、连锁不认」。
 */
public final class OreBlocks {
    private static final TagKey<Block> ORES =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

    public static boolean isOre(BlockState state) {
        if (state.is(ORES)) {
            return true;
        }
        Block block = state.getBlock();
        // 本模组所有 id 以 _ore 结尾的方块统一认作矿石：以后新增矿种不必再改这里
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null && id.getNamespace().equals(StellarEvolution.MOD_ID) && id.getPath().endsWith("_ore")) {
            return true;
        }
        return block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.NETHER_GOLD_ORE || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.ANCIENT_DEBRIS || block == Blocks.GLOWSTONE
                || block == Blocks.GILDED_BLACKSTONE
                || block == FissionBlocks.URANIUM_ORE_BLOCK.get()
                || block == FissionBlocks.DEEPSLATE_URANIUM_ORE_BLOCK.get()
                || block == TungstenBlocks.TUNGSTEN_ORE_BLOCK.get()
                || block == TungstenBlocks.DEEPSLATE_TUNGSTEN_ORE_BLOCK.get()
                || block == LeadBlocks.LEAD_ORE_BLOCK.get()
                || block == LeadBlocks.DEEPSLATE_LEAD_ORE_BLOCK.get()
                || block == MonaziteBlocks.MONAZITE_ORE_BLOCK.get()
                || block == MonaziteBlocks.DEEPSLATE_MONAZITE_ORE_BLOCK.get()
                || block == BauxiteBlocks.BAUXITE_ORE_BLOCK.get()
                || block == BauxiteBlocks.DEEPSLATE_BAUXITE_ORE_BLOCK.get();
    }

    private OreBlocks() {}
}
