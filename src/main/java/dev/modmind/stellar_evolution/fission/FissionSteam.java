package dev.modmind.stellar_evolution.fission;

import dev.modmind.stellar_evolution.StellarEvolution;
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
 * 裂变线<strong>蒸汽循环</strong>注册表（《核裂变线预规划蓝图》§6 / §13.3 F2）——F2b 先落蒸汽涡轮机组。
 *
 * <p>对外暴露物品 / 流体能力（物流容器），但蒸汽<strong>只走蒸汽网络</strong>
 * （压力管 → {@code steam.SteamNetwork}），不再走储罐。
 */
public final class FissionSteam {
    public static final String STEAM_TURBINE = "steam_turbine_unit";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    /** 蒸汽涡轮机组：蒸汽 → 热压差。 */
    public static final DeferredBlock<SteamTurbineBlock> STEAM_TURBINE_BLOCK =
            BLOCKS.register(STEAM_TURBINE, () -> new SteamTurbineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(STEAM_TURBINE)))));

    public static final DeferredItem<BlockItem> STEAM_TURBINE_ITEM =
            ITEMS.register(STEAM_TURBINE, key -> new BlockItem(STEAM_TURBINE_BLOCK.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamTurbineBlockEntity>> TURBINE_ENTITY =
            BLOCK_ENTITIES.register(STEAM_TURBINE, () -> new BlockEntityType<>(SteamTurbineBlockEntity::new,
                    STEAM_TURBINE_BLOCK.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(FissionSteam::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, TURBINE_ENTITY.get(),
                (turbine, side) -> turbine.itemCapability());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, TURBINE_ENTITY.get(),
                (turbine, side) -> turbine.fluidCapability());
    }

    private FissionSteam() {}
}
