package dev.znecby.stellar_evolution.component;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.massdriver.CollapsedEnderEyeItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 重工业组件物品：电动马达 / 磁通线圈 / 加热线圈 / 高频线圈 / 活塞组件 / 电路板 / 芯片 /
 * 基础流体单元 / 流体单元。
 * <p>
 * 加热线圈与高频线圈（2026-11-08）照磁通线圈的造型做，只换绕组材质：
 * 钨线（耐高温）与金线（高频低阻），铁芯换成钢锭。
 * <p>
 * 与铜材质那类「平面贴图物品」不同，这 9 件是 <strong>3D 物品模型</strong>——
 * 模型由若干立方体（{@code elements}）拼出几何形体，物品栏里能看到多个面，
 * 各面直接复用原版方块贴图（铁块 / 铜块 / 金块 / 彩色混凝土…）。
 * 模型与物品定义由 {@code tools/gen_component_models.ps1} 生成到
 * {@code assets/stellar_evolution/models/item/<id>.json} 与 {@code assets/stellar_evolution/items/<id>.json}。
 * <p>
 * 平面 16×16 图标仍留在 {@code 模板/物品/}（由 {@code tools/gen_item_icons.ps1} 生成）作为素材源。
 * <p>
 * <p>注意：电路板物品 id 用 {@code pcb}；旧的 {@code circuit_board} 装饰方块已删除。
 */
public final class ComponentItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    public static final DeferredItem<Item> ELECTRIC_MOTOR = register("electric_motor");
    public static final DeferredItem<Item> FLUX_COIL = register("flux_coil");
    /** 加热线圈：钨线绕组 + 钢芯，耐高温加热用。 */
    public static final DeferredItem<Item> HEATING_COIL = register("heating_coil");
    /** 高频线圈：金线绕组 + 钢芯，高频 / 低阻场合用。 */
    public static final DeferredItem<Item> HIGH_FREQUENCY_COIL = register("high_frequency_coil");
    public static final DeferredItem<Item> PISTON_ASSEMBLY = register("piston_assembly");
    public static final DeferredItem<Item> CIRCUIT_BOARD = register("pcb");
    public static final DeferredItem<Item> CHIP = register("chip");
    /** 基础流体单元：玻璃 + 钢/铜 + 黏液密封，T1/T2 机器用，不消耗 NBR。 */
    public static final DeferredItem<Item> BASIC_FLUID_CELL = register("basic_fluid_cell");
    /** 强化流体单元：耐压 / 耐蚀，T3/T4 机器用，需要丁腈橡胶（NBR）。 */
    public static final DeferredItem<Item> FLUID_CELL = register("fluid_cell");

    /** 坍缩末影之眼：下界之星形状 + 末影之眼配色，带原版附魔光效，不可堆叠；由质量炮维度选择生成。 */
    public static final DeferredItem<Item> COLLAPSED_ENDER_EYE = ITEMS.register("collapsed_ender_eye",
            key -> new CollapsedEnderEyeItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, key))
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ComponentItems() {}
}
