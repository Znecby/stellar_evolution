package dev.znecby.stellar_evolution.thermal;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 联动入口：{@code @WailaPlugin} 注解就是 Jade 的发现手段 ——
 * Jade 自己会扫各模组的注解数据（{@code ModFileScanData}）找到本类并实例化。
 *
 * <p>注册范围：热压差网络相关的全部方块（铜管 / 热力引擎 / 燃烧引擎 / 铜块 / 木桶 / 熔炉·烟熏炉·高炉 /
 * 机械土壤 / 热室 / 大型机器，以及大型机器的机械管与物流容器）
 * 共用同一套 HUD（数据 {@link ThermalJadeData} + 显示 {@link ThermalJadeProvider}），风格统一。
 *
 * <p><strong>注册在 {@code Block.class} 上</strong>（Jade 的 {@code HierarchyLookup} 会按方块类的继承链往上找，
 * 根类就是最后兜底）：因为 Jade 只按<strong>方块类</strong>注册，而原版铜块里「打蜡」那 4 种是纯 {@code Block}
 * （另外 4 种是 {@code WeatheringCopperFullBlock}）—— 没有一个共同子类能一次覆盖全 8 种标签成员。
 * 于是挂根类、再由 {@link ThermalJadeData} 按<strong>标签</strong>过滤：非热压差方块不写数据、客户端也就什么都不画。
 *
 * <p>数据与显示<strong>必须分成两个类</strong>（{@link ThermalJadeData} 服务端算、{@link ThermalJadeProvider}
 * 客户端画）：Jade 自 1.21.6 起禁止同一个类兼任数据提供者与组件提供者。
 * 所以 {@code register(...)} 跑在两边、注册数据类；{@code registerClient(...)} 只在客户端跑、注册显示类。
 *
 * <p><strong>别给它加 private 构造器</strong>：Jade 是反射实例化插件类的，拿不到无参构造会在加载期
 * 抛 {@code Failed to instantiate plugin class}，而那属于同步工作队列的致命错误，会直接把客户端打崩。
 */
@WailaPlugin(StellarEvolution.MOD_ID)
public final class ThermalJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ThermalJadeData.INSTANCE, Block.class);
        // 物品栏合并：本模组耐久物品（燃料棒 / 石墨棒）按同名合并，避免数值变动导致显示闪烁
        registration.registerItemStorage(MergedItemStorageJade.INSTANCE, BaseEntityBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ThermalJadeProvider.INSTANCE, Block.class);
        registration.registerItemStorageClient(MergedItemStorageJade.INSTANCE);
        // 热室：把 Jade 默认的对象名行（「铜箱子」）整行换成「热室」——
        // 服务端在 ThermalJadeData 里标了 HOT_CHAMBER。用「tooltip 收集完成」回调（一定在所有组件之后），
        // 免得依赖和核心对象名组件之间的先后顺序。
        registration.addTooltipCollectedCallback((box, accessor) -> {
            if (!accessor.getServerData().getBooleanOr(ThermalJadeData.HOT_CHAMBER, false)) {
                return;
            }
            Component name = Component.translatable("stellar_evolution.jade.hot_chamber");
            if (!box.getTooltip().replace(JadeIds.CORE_OBJECT_NAME, name)) {
                box.getTooltip().add(0, name);   // 兜底：拿不到那一行就直接插到最前
            }
        });
    }
}
