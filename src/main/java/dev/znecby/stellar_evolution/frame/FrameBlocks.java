package dev.znecby.stellar_evolution.frame;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 铜框架注册表：只注册一个方块 + 对应物品（无方块实体、无能力接口）。
 * <p>
 * 贴图直接复用原版铜块（{@code minecraft:block/copper_block}），不新增任何贴图资源。
 * 物品出现在本模组自己的创造页（见 {@code StellarEvolutionTabs}），不塞进原版页。
 */
public final class FrameBlocks {
    public static final String COPPER_FRAME_NAME = "copper_frame";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredBlock<CopperFrameBlock> COPPER_FRAME =
            BLOCKS.register(COPPER_FRAME_NAME, () -> new CopperFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    // 细框不该让邻居消面、也不该挡光（否则贴着实心方块会出现「洞」）
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(COPPER_FRAME_NAME)))));

    public static final DeferredItem<BlockItem> COPPER_FRAME_ITEM =
            ITEMS.register(COPPER_FRAME_NAME, key -> new BlockItem(COPPER_FRAME.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private FrameBlocks() {}
}
