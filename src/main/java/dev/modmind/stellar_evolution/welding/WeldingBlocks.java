package dev.modmind.stellar_evolution.welding;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 焊接机的注册表：方块 / 物品 / 方块实体 / 菜单类型。
 * <p>1×3 料仓按标准物品能力对外暴露（管道 / 漏斗可进），与材料加工机械同口径。
 */
public final class WeldingBlocks {
    /** 方块 id。 */
    public static final String ID = "welding_machine";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, StellarEvolution.MOD_ID);

    public static final DeferredBlock<WeldingMachineBlock> WELDING_MACHINE =
            BLOCKS.register(ID, () -> new WeldingMachineBlock(WeldingMachineBlock.properties(ID)));

    public static final DeferredItem<BlockItem> WELDING_MACHINE_ITEM =
            ITEMS.register(ID, key -> new BlockItem(WELDING_MACHINE.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .useBlockDescriptionPrefix()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WeldingMachineBlockEntity>>
            WELDING_MACHINE_ENTITY = BLOCK_ENTITIES.register(ID,
            () -> new BlockEntityType<>(WeldingMachineBlockEntity::new, WELDING_MACHINE.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<WeldingMenu>> WELDING_MENU =
            MENUS.register(ID, () -> new MenuType<>(WeldingMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener(WeldingBlocks::onRegisterCapabilities);
    }

    /** 1×3 料仓按标准物品能力暴露（Pipez / 漏斗 / 机械管可进）。 */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, WELDING_MACHINE_ENTITY.get(),
                (welding, side) -> welding.itemCapability());
    }

    private WeldingBlocks() {}
}
