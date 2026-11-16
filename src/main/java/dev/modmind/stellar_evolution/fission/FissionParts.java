package dev.modmind.stellar_evolution.fission;

import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 裂变线<strong>专属高级组件</strong>：只用于六台裂变机器的合成，不进入生化 / 石化 / 通用机器。
 *
 * <p>大部分在热室制作，部分通用件（耐蚀内衬 / 离心转子 / 精密装料头）走工作台；
 * 依赖钢、钨、PTFE、锆合金、石墨、碳化硼、芯片与密封件，
 * 但不依赖任何裂变机器成品，避免循环。
 */
public final class FissionParts {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);

    /** 钢化玻璃：石英玻璃热室回火，辐射屏蔽层 / 蒸汽阀组的视镜与层压材料。 */
    public static final DeferredItem<Item> TEMPERED_GLASS = register("tempered_glass");
    /** 耐蚀内衬：酸浸槽 / 氟化炉的耐硝酸、耐氟化氢内衬。 */
    public static final DeferredItem<Item> CORROSION_LINER = register("corrosion_liner");
    /** 辐射屏蔽层：钨板 + 钢化玻璃 + 钢板 + 丁腈的复合屏蔽。 */
    public static final DeferredItem<Item> RADIATION_SHIELDING = register("radiation_shielding");
    /** 离心转子：离心机的高速同位素分离转子。 */
    public static final DeferredItem<Item> CENTRIFUGE_ROTOR = register("centrifuge_rotor");
    /** 精密装料头：罐装机的芯块装管 / 封口机构。 */
    public static final DeferredItem<Item> FUEL_LOADING_HEAD = register("fuel_loading_head");
    /** 中子反射模块：裂变堆的中子反射 / 慢化组件。 */
    public static final DeferredItem<Item> REFLECTOR_MODULE = register("reflector_module");
    /** 控制棒驱动组件：裂变堆的控制棒插入 / 紧急停堆机构。 */
    public static final DeferredItem<Item> CONTROL_ROD_DRIVE = register("control_rod_drive");
    /** 涡轮扇叶：汽轮机的钨基高温叶片。 */
    public static final DeferredItem<Item> TURBINE_BLADE = register("turbine_blade");
    /** 蒸汽阀组：汽轮机的高压蒸汽调节 + 视镜。 */
    public static final DeferredItem<Item> STEAM_VALVE_MANIFOLD = register("steam_valve_manifold");

    /** 创造页 / 生成脚本 / lang 对齐按这个顺序走。 */
    public static final List<DeferredItem<? extends Item>> ALL = List.of(
            TEMPERED_GLASS, CORROSION_LINER, RADIATION_SHIELDING,
            CENTRIFUGE_ROTOR, FUEL_LOADING_HEAD,
            REFLECTOR_MODULE, CONTROL_ROD_DRIVE,
            TURBINE_BLADE, STEAM_VALVE_MANIFOLD);

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, key -> new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, key))));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private FissionParts() {}
}
