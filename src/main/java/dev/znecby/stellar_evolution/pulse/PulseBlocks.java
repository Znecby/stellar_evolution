package dev.znecby.stellar_evolution.pulse;

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
 * 脉冲核心注册表：只注册一个方块 + 对应物品（无方块实体、无能力接口、无功能）。
 * <p>
 * 贴图全部复用原版（红石块 / 铜块 / 切制铜），零新增贴图。
 */
public final class PulseBlocks {
    public static final String PULSE_CORE_NAME = "pulse_core";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredBlock<PulseCoreBlock> PULSE_CORE =
            BLOCKS.register(PULSE_CORE_NAME, () -> new PulseCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    // 结构是镂空的（方块边界上也有空气），必须 noOcclusion，否则邻居会消面出现洞
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(PULSE_CORE_NAME)))));

    public static final DeferredItem<BlockItem> PULSE_CORE_ITEM =
            ITEMS.register(PULSE_CORE_NAME, key -> new BlockItem(PULSE_CORE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private PulseBlocks() {}
}
