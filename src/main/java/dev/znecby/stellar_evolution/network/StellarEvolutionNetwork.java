package dev.znecby.stellar_evolution.network;

import dev.znecby.stellar_evolution.dev.QuickRecipeSavePayload;
import dev.znecby.stellar_evolution.equipment.SteelToolModePayload;
import java.util.function.Consumer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/**
 * 网络通道骨架。
 * <p>
 * 协议版本号用于握手校验：payload 结构发生变化时必须递增，否则新旧版本之间会被拒绝连接。
 * <p>
 * 目前注册 9 组 payload（其中快速配方书只在开发环境注册）：
 * <ul>
 *   <li>{@link FlowLimitPayload}（<strong>限流管</strong>的数值输入往返：S2C 打开输入界面 + C2S 提交设定值）；</li>
 *   <li>{@link HotChamberPressurePayload}（<strong>热室等压</strong>的输入往返：S2C 打开输入界面 + C2S 提交数值）；</li>
 *   <li>{@link DrillRangePayload}（<strong>开采钻范围</strong>的输入往返：S2C 打开输入界面 + C2S 提交数值）；</li>
 *   <li>{@link MassDriverPressurePayload}（<strong>质量炮等压</strong>的输入往返）；</li>
 *   <li>{@link MassDriverDimensionPayload}（<strong>质量炮维度选择</strong>：S2C 打开列表 + C2S 提交目标维度）；</li>
 *   <li>{@link CollapsedEyeAnimationPayload}（<strong>坍缩末影之眼回溯动画</strong>：S2C 只发给使用者）；</li>
 *   <li>{@link MassDriverShakePayload}（<strong>质量炮镜头冲击</strong>：S2C 发给附近玩家）；</li>
 *   <li>{@link QuickRecipeSavePayload}（<strong>开发用快速配方书</strong>：C2S 请求把 3×3 菜单写成 docs 文本）。</li>
 * </ul>
 * 处理端声明跑在主线程，客户端缓存因此不需要额外同步。
 */
public final class StellarEvolutionNetwork {
    /** 协议版本：v5 = 热室等压；v6 = 删除反应釜流体快照；v7 = 开采钻范围输入；v8 = 钢制工具模式；v9 = 质量炮机制；v10 = 坍缩眼回溯动画；v11 = 质量炮镜头冲击；<strong>v12 = 开发用快速配方书</strong>。 */
    public static final String PROTOCOL_VERSION = "12";

    /** 客户端界面打开器；服务端保持空实现，避免公共代码引用客户端类。 */
    private static volatile Consumer<FlowLimitPayload.Open> flowLimiterOpener = payload -> {};
    private static volatile Consumer<HotChamberPressurePayload.Open> hotChamberOpener = payload -> {};
    private static volatile Consumer<DrillRangePayload.Open> drillRangeOpener = payload -> {};
    private static volatile Consumer<MassDriverPressurePayload.Open> massDriverPressureOpener = payload -> {};
    private static volatile Consumer<MassDriverDimensionPayload.Open> massDriverDimensionOpener = payload -> {};
    /** 坍缩末影之眼的第一人称回溯动画（客户端注入）。 */
    private static volatile Consumer<CollapsedEyeAnimationPayload> collapsedEyeAnimator = payload -> {};
    /** 质量炮发射 / 抵达的镜头冲击（客户端注入）。 */
    private static volatile Consumer<MassDriverShakePayload> massDriverShaker = payload -> {};

    /** 客户端入口在静态初始化时注入；服务端永不调用。 */
    public static void setScreenOpeners(Consumer<FlowLimitPayload.Open> flowLimiter,
                                        Consumer<HotChamberPressurePayload.Open> hotChamber,
                                        Consumer<DrillRangePayload.Open> drillRange,
                                        Consumer<MassDriverPressurePayload.Open> massDriverPressure,
                                        Consumer<MassDriverDimensionPayload.Open> massDriverDimension) {
        flowLimiterOpener = flowLimiter;
        hotChamberOpener = hotChamber;
        drillRangeOpener = drillRange;
        massDriverPressureOpener = massDriverPressure;
        massDriverDimensionOpener = massDriverDimension;
    }

    public static void openFlowLimiter(FlowLimitPayload.Open payload) {
        flowLimiterOpener.accept(payload);
    }

    public static void openHotChamberPressure(HotChamberPressurePayload.Open payload) {
        hotChamberOpener.accept(payload);
    }

    public static void openDrillRange(DrillRangePayload.Open payload) {
        drillRangeOpener.accept(payload);
    }

    public static void openMassDriverPressure(MassDriverPressurePayload.Open payload) {
        massDriverPressureOpener.accept(payload);
    }

    public static void openMassDriverDimension(MassDriverDimensionPayload.Open payload) {
        massDriverDimensionOpener.accept(payload);
    }

    /** 客户端注入：用原版「图腾物品旋转」特效播放坍缩末影之眼。 */
    public static void setCollapsedEyeAnimator(Consumer<CollapsedEyeAnimationPayload> animator) {
        collapsedEyeAnimator = animator;
    }

    public static void playCollapsedEyeAnimation(CollapsedEyeAnimationPayload payload) {
        collapsedEyeAnimator.accept(payload);
    }

    /** 客户端注入：镜头抖动 + FOV 冲击。 */
    public static void setMassDriverShaker(Consumer<MassDriverShakePayload> shaker) {
        massDriverShaker = shaker;
    }

    public static void shakeMassDriverScreen(MassDriverShakePayload payload) {
        massDriverShaker.accept(payload);
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN)
                .playToClient(FlowLimitPayload.Open.TYPE, FlowLimitPayload.Open.STREAM_CODEC,
                        FlowLimitPayload.Open::handle)
                .playToServer(FlowLimitPayload.Set.TYPE, FlowLimitPayload.Set.STREAM_CODEC,
                        FlowLimitPayload.Set::handle)
                .playToClient(HotChamberPressurePayload.Open.TYPE, HotChamberPressurePayload.Open.STREAM_CODEC,
                        HotChamberPressurePayload.Open::handle)
                .playToServer(HotChamberPressurePayload.Set.TYPE, HotChamberPressurePayload.Set.STREAM_CODEC,
                        HotChamberPressurePayload.Set::handle)
                .playToClient(DrillRangePayload.Open.TYPE, DrillRangePayload.Open.STREAM_CODEC,
                        DrillRangePayload.Open::handle)
                .playToServer(DrillRangePayload.Set.TYPE, DrillRangePayload.Set.STREAM_CODEC,
                        DrillRangePayload.Set::handle)
                .playToClient(MassDriverPressurePayload.Open.TYPE, MassDriverPressurePayload.Open.STREAM_CODEC,
                        MassDriverPressurePayload.Open::handle)
                .playToServer(MassDriverPressurePayload.Set.TYPE, MassDriverPressurePayload.Set.STREAM_CODEC,
                        MassDriverPressurePayload.Set::handle)
                .playToClient(MassDriverDimensionPayload.Open.TYPE, MassDriverDimensionPayload.Open.STREAM_CODEC,
                        MassDriverDimensionPayload.Open::handle)
                .playToServer(MassDriverDimensionPayload.Select.TYPE, MassDriverDimensionPayload.Select.STREAM_CODEC,
                        MassDriverDimensionPayload.Select::handle)
                .playToClient(CollapsedEyeAnimationPayload.TYPE, CollapsedEyeAnimationPayload.STREAM_CODEC,
                        CollapsedEyeAnimationPayload::handle)
                .playToClient(MassDriverShakePayload.TYPE, MassDriverShakePayload.STREAM_CODEC,
                        MassDriverShakePayload::handle)
                .playToServer(SteelToolModePayload.Set.TYPE, SteelToolModePayload.Set.STREAM_CODEC,
                        SteelToolModePayload.Set::handle);
        // 开发用快速配方书只在开发环境注册；生产环境不占用协议通道
        if (!FMLEnvironment.isProduction()) {
            registrar.playToServer(QuickRecipeSavePayload.TYPE, QuickRecipeSavePayload.STREAM_CODEC,
                    QuickRecipeSavePayload::handle);
        }
    }

    private StellarEvolutionNetwork() {}
}
