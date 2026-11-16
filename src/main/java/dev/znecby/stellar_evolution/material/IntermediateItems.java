package dev.znecby.stellar_evolution.material;

import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * <strong>中间材料</strong>（第二批）：机器合成的「零件层」—— 钢系结构件 + 压模机模具 + 电气精密件。
 *
 * <p>定位：夹在「原版材料 / 铜材」和「整机」之间的中间层。产出分工：
 * <ul>
 *   <li><strong>锭 → 型材</strong>：钢板 / 钢杆、铜板 / 铜杆 / 铜线 走<strong>原版切石机</strong>
 *       （{@code data/stellar_evolution/recipe/*_stonecutting.json}）；铜齿轮 / 钢齿轮走<strong>工作台</strong>
 *       （1 锭 + 上下左右 4 板 = 1 齿轮）。<strong>刻意不放进热室</strong>：热室按「先命中先算」匹配，
 *       同一个锭出好几样必然互相遮蔽，切石机才有选单；</li>
 *   <li><strong>钢系结构件</strong>：钢锭（铁锭渗碳）→ 轴承（钢齿轮 + 钢杆）→ 机械框架（钢板 + 钢杆）；</li>
 *   <li><strong>电气精密件</strong>：电阻 / 电容 / 传感器 → 伺服机构 / 温控器 / 控制模块 ——
 *       吃<strong>铜材</strong>（{@link MaterialItems}）与<strong>原版电子料</strong>（煤 / 玻璃 / 石英 / 金锭）；</li>
 *   <li>后两类由<strong>热室</strong>（{@code thermal.HotChamber}：铜箱子底部垫铜块）加工，
 *       配方见 {@code thermal.HotChamberRecipes}。</li>
 * </ul>
 *
 * <p>贴图由 {@code tools/gen_intermediate_items.ps1} 生成：钢系平面件走材料模板上色（灰度模板 + 基色），
 * 8 件 3D 件走 3D 立方体模型（复用原版方块贴图）；压模机模具由
 * {@code tools/gen_die_press_molds.ps1} 生成（GTCEu 模具模板 + 深钢色）；双语名在 lang 里。
 */
public final class IntermediateItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    // ---- 结构机械件（钢系）
    /** 钢粉：合金配料的「钢」份（高速钢 W / M）；暂无产出途径，创造页可取。 */
    public static final DeferredItem<Item> STEEL_DUST = register("steel_dust");
    /** 钢锭：铁锭渗碳（热室）。 */
    public static final DeferredItem<Item> STEEL_INGOT = register("steel_ingot");
    /** 钢板：钢锭 → 切石机（1 锭 1 板）。 */
    public static final DeferredItem<Item> STEEL_PLATE = register("steel_plate");
    /** 钢杆：钢锭 → 切石机（1 锭 2 杆）。 */
    public static final DeferredItem<Item> STEEL_ROD = register("steel_rod");
    /** 钢齿轮：1 钢锭 + 上下左右 4 钢板 → 工作台。 */
    public static final DeferredItem<Item> STEEL_GEAR = register("steel_gear");
    // ---- 压模机模具（2026-11-13；压模机必须装对应模具，模具不消耗）
    /** 板模具：压模机压板用（深钢色工装）。 */
    public static final DeferredItem<Item> PLATE_MOLD = register("plate_mold");
    /** 杆模具：压模机压杆用。 */
    public static final DeferredItem<Item> ROD_MOLD = register("rod_mold");
    /** 齿轮模具：压模机压齿轮用。 */
    public static final DeferredItem<Item> GEAR_MOLD = register("gear_mold");
    /** 轴承：钢齿轮 + 钢杆，烧结（热室）。 */
    public static final DeferredItem<Item> BEARING = register("bearing");
    /** 机械框架：钢板 + 钢杆，钎焊（热室；整机机壳基础）。 */
    public static final DeferredItem<Item> MACHINE_FRAME = register("machine_frame");
    /** 精密重型框架：钨齿轮 / 钨板 / 下界合金板 / 下界合金杆（热室；裂变线整机专用）。 */
    public static final DeferredItem<Item> PRECISION_HEAVY_FRAME = register("precision_heavy_frame");

    // ---- 电气精密件
    /** 电阻：铜线 + 煤（碳膜）。 */
    public static final DeferredItem<Item> RESISTOR = register("resistor");
    /** 电容：铜板 + 玻璃（介质）。 */
    public static final DeferredItem<Item> CAPACITOR = register("capacitor");
    /** 传感器：铜线 + 石英 + 金锭。 */
    public static final DeferredItem<Item> SENSOR = register("sensor");
    /** 伺服机构：轴承 + 铜齿轮 + 铜线（带反馈的驱动件）。 */
    public static final DeferredItem<Item> SERVO = register("servo");
    /** 温控器：传感器 + 钢杆。 */
    public static final DeferredItem<Item> THERMOSTAT = register("thermostat");
    /** 控制模块：传感器 + 电阻 + 电容（整机控制核心）。 */
    public static final DeferredItem<Item> CONTROL_MODULE = register("control_module");

    /** 全部物品（创造页 / 生成脚本 / lang 对齐按这个顺序走）。 */
    public static final List<DeferredItem<Item>> ALL = List.of(
            STEEL_DUST, STEEL_INGOT, STEEL_PLATE, STEEL_ROD, STEEL_GEAR,
            PLATE_MOLD, ROD_MOLD, GEAR_MOLD,
            BEARING, MACHINE_FRAME,
            PRECISION_HEAVY_FRAME,
            RESISTOR, CAPACITOR, SENSOR, SERVO, THERMOSTAT, CONTROL_MODULE);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private IntermediateItems() {}
}
