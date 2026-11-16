package dev.modmind.stellar_evolution.tank;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 储罐注册表：<strong>铜 / 钢 / 硬质</strong>三档方块 + 物品 + 共用方块实体 + 流体能力。
 * <p>
 * 三档共用同一个 {@link TankBlock} / {@link TankBlockEntity} / {@link TankMultiblock}，
 * 只有 {@link TankTier} 里的单格容量与模型贴图不同（见 {@link TankTier}）。
 * 图文资源按档位同名：{@code blockstates/copper_tank.json}、{@code models/block/copper_tank/…}。
 * <p>
 * 流体能力直接把罐实体暴露给 NeoForge 的 {@code Capabilities.Fluid.BLOCK}（管道可灌可抽）。
 */
public final class TankBlocks {
    /** 方块实体类型 id（三档共用一个类型）。 */
    public static final String TANK_NAME = "tank";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    public static final DeferredBlock<TankBlock> COPPER_TANK = tank(TankTier.COPPER);
    public static final DeferredBlock<TankBlock> STEEL_TANK = tank(TankTier.STEEL);
    public static final DeferredBlock<TankBlock> HARDENED_TANK = tank(TankTier.HARDENED);

    public static final DeferredItem<BlockItem> COPPER_TANK_ITEM = item(TankTier.COPPER, COPPER_TANK);
    public static final DeferredItem<BlockItem> STEEL_TANK_ITEM = item(TankTier.STEEL, STEEL_TANK);
    public static final DeferredItem<BlockItem> HARDENED_TANK_ITEM = item(TankTier.HARDENED, HARDENED_TANK);

    /** 全部储罐方块（创造页 / 命令按这个顺序走）。 */
    public static final List<DeferredBlock<TankBlock>> ALL =
            List.of(COPPER_TANK, STEEL_TANK, HARDENED_TANK);
    /** 全部储罐物品。 */
    public static final List<DeferredItem<BlockItem>> ALL_ITEMS =
            List.of(COPPER_TANK_ITEM, STEEL_TANK_ITEM, HARDENED_TANK_ITEM);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankBlockEntity>> TANK_ENTITY =
            BLOCK_ENTITIES.register(TANK_NAME, () -> new BlockEntityType<TankBlockEntity>(TankBlockEntity::new,
                    COPPER_TANK.get(), STEEL_TANK.get(), HARDENED_TANK.get()));

    private static DeferredBlock<TankBlock> tank(TankTier tier) {
        return BLOCKS.register(tier.id(), () -> new TankBlock(tier, BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                // 这里**不能**加 noOcclusion：罐体的顶盖 / 角柱正好压在方块边界上（cap_top 的上、四侧面都带
                // cullface），一旦让邻居不消面，紧贴罐子的方块就会与这些面同向共面 → 闪面。
                // 罐内发黑是「挡光」问题，改在 TankBlock 里用 getLightDampening()=0 + getShadeBrightness()=1 解决。
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(tier.id())))));
    }

    private static DeferredItem<BlockItem> item(TankTier tier, DeferredBlock<TankBlock> block) {
        // TankBlockItem：物品介绍里显示罐里存的流体（流体随物品搬运）
        return ITEMS.register(tier.id(), key -> new TankBlockItem(block.get(),
                new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, key))
                        .useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TankComponents.register(bus);
        bus.addListener(TankBlocks::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, TANK_ENTITY.get(), (tank, side) -> tank.fluidCapability());
    }

    private TankBlocks() {}
}
