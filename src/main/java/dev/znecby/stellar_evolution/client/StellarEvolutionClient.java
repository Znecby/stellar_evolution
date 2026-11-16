package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.air.AirFluids;
import dev.znecby.stellar_evolution.bio.BioFluids;
import dev.znecby.stellar_evolution.bio.BioMachineBlocks;
import dev.znecby.stellar_evolution.dev.QuickRecipeMenus;
import dev.znecby.stellar_evolution.drill.DrillBlocks;
import dev.znecby.stellar_evolution.engine.EngineBlocks;
import dev.znecby.stellar_evolution.engine.EngineMachines;
import dev.znecby.stellar_evolution.farmland.FarmlandBlocks;
import dev.znecby.stellar_evolution.bedrock.BedrockFluids;
import dev.znecby.stellar_evolution.fission.FissionFluids;
import dev.znecby.stellar_evolution.fission.FissionMachines;
import dev.znecby.stellar_evolution.fission.FissionReactors;
import dev.znecby.stellar_evolution.fission.FissionSteam;
import dev.znecby.stellar_evolution.machine.MachineBlocks;
import dev.znecby.stellar_evolution.network.DrillRangePayload;
import dev.znecby.stellar_evolution.network.FlowLimitPayload;
import dev.znecby.stellar_evolution.network.HotChamberPressurePayload;
import dev.znecby.stellar_evolution.network.MassDriverDimensionPayload;
import dev.znecby.stellar_evolution.network.MassDriverPressurePayload;
import dev.znecby.stellar_evolution.network.StellarEvolutionNetwork;
import dev.znecby.stellar_evolution.ore.OreFluids;
import dev.znecby.stellar_evolution.pipe.PipeBlocks;
import dev.znecby.stellar_evolution.coal.CoalFluids;
import dev.znecby.stellar_evolution.wood.WoodFluids;
import dev.znecby.stellar_evolution.petro.PetroFluids;
import dev.znecby.stellar_evolution.petro.PetroMachineBlocks;
import dev.znecby.stellar_evolution.tank.TankBlocks;
import dev.znecby.stellar_evolution.welding.WeldingBlocks;
import dev.znecby.stellar_evolution.welding.WeldingFluids;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端入口：把「只存在于客户端」的注册集中在一处，避免公共侧主类引用客户端类。
 * <p>
 * 目前负责：流体外观、方块实体渲染器、菜单 Screen、CTM 机壳模型、方块 tint 与工具模式界面。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID, value = Dist.CLIENT)
public final class StellarEvolutionClient {
    /**
     * 把「打开输入界面」的客户端实现注入公共网络层。
     * <p>
     * payload 类只调用 {@link StellarEvolutionNetwork} 的空实现；真正的 {@code Minecraft / Screen}
     * 引用只存在于这个客户端类里，避免 dedicated server 加载 payload 时触发 {@code Screen} 类缺失。
     */
    static {
        StellarEvolutionNetwork.setScreenOpeners(
                payload -> Minecraft.getInstance().setScreen(new FlowLimiterScreen(
                        payload.pos(), payload.limit(), payload.upstreamFlow())),
                payload -> Minecraft.getInstance().setScreen(new HotChamberPressureScreen(
                        payload.pos(), payload.pressure())),
                payload -> Minecraft.getInstance().setScreen(new DrillRangeScreen(
                        payload.pos(), payload.mode(), payload.value(), payload.min(), payload.max())),
                payload -> Minecraft.getInstance().setScreen(new MassDriverPressureScreen(
                        payload.pos(), payload.pressure())),
                payload -> Minecraft.getInstance().setScreen(new MassDriverDimensionScreen(
                        payload.pos(), payload.potential(), payload.entries())));
        // 坍缩末影之眼回溯：用原版「图腾物品旋转」特效，但显示的是这枚眼睛本身
        StellarEvolutionNetwork.setCollapsedEyeAnimator(payload ->
                Minecraft.getInstance().gameRenderer.displayItemActivation(payload.stack()));
        // 质量炮发射 / 抵达：镜头抖动 + FOV 冲击
        StellarEvolutionNetwork.setMassDriverShaker(payload ->
                MassDriverCameraEffects.shake(payload.ticks(), payload.intensity()));
    }

    /** 开发用快速配方书：把自定义 3×3 菜单挂到原版工作台底图界面。 */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        if (!net.neoforged.fml.loading.FMLEnvironment.isProduction()) {
            event.register(QuickRecipeMenus.QUICK_RECIPE.get(), QuickRecipeScreen::new);
            event.register(QuickRecipeMenus.QUICK_RECIPE_HOT_CHAMBER.get(), QuickRecipeScreen::new);
        }
        // 焊接机 1×3 界面
        event.register(WeldingBlocks.WELDING_MENU.get(), WeldingScreen::new);
        // 压模机：3×3 料仓 + 模具槽
        event.register(MachineBlocks.DIE_PRESS_MENU.get(), DiePressScreen::new);
    }

    /** 自定义流体的外观（26.x 的流体外观走「流体模型」而非客户端扩展）：贴图取自 {@code tools/gen_bio_materials.ps1}。 */
    @SubscribeEvent
    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        for (BioFluids.Entry entry : BioFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            // 贴图本身已带最终颜色 → 不额外染色（5 参构造，避免与已废弃的 4 参重载撞 null）
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
        // 石化线流体（贴图取自 tools/gen_petro_materials.ps1）
        for (PetroFluids.Entry entry : PetroFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
        // 空气分馏线流体（贴图取自 tools/gen_air_materials.ps1）
        for (AirFluids.Entry entry : AirFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
        // 煤化工线流体（贴图取自 tools/gen_coal_materials.ps1）
        for (CoalFluids.Entry entry : CoalFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
        // 木化工线流体（贴图取自 tools/gen_wood_materials.ps1）
        for (WoodFluids.Entry entry : WoodFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
        // 核裂变线流体（贴图取自 tools/gen_fission_materials.ps1）
        for (FissionFluids.Entry entry : FissionFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
        // 选冶线流体（贴图取自 tools/gen_ore_materials.ps1）
        for (OreFluids.Entry entry : OreFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
        // 基岩线流体（贴图取自 tools/gen_bedrock_materials.ps1）
        for (BedrockFluids.Entry entry : BedrockFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
        // 焊接线流体（贴图取自 tools/gen_welding_assets.ps1）
        for (WeldingFluids.Entry entry : WeldingFluids.ALL) {
            Identifier still = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_still");
            Identifier flowing = Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "block/" + entry.name() + "_flow");
            event.register(new FluidModel.Unbaked(new Material(still), new Material(flowing), null, null, null),
                    entry.source(), entry.flowing());
        }
    }

    /** 热力引擎的铁桨叶 / 燃烧引擎的铁曲轴 + 储罐的流体渲染器 + 加工台顶面的静态物品。 */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EngineBlocks.HEAT_ENGINE_ENTITY.get(), context -> new HeatEngineRenderer());
        event.registerBlockEntityRenderer(EngineBlocks.BURNING_ENGINE_ENTITY.get(), context -> new BurningEngineRenderer());
        // 三台燃烧类发电机组：风扇 / 压气机叶轮 / 涡轮泵叶轮（各自一个隐藏载体方块）
        event.registerBlockEntityRenderer(EngineMachines.INDUSTRIAL_ENTITY.get(),
                context -> new EngineMachineRenderer<>(EngineMachineRenderer.industrialRotors()));
        event.registerBlockEntityRenderer(EngineMachines.GAS_TURBINE_ENTITY.get(),
                context -> new EngineMachineRenderer<>(EngineMachineRenderer.gasTurbineRotors()));
        event.registerBlockEntityRenderer(EngineMachines.ROCKET_ENTITY.get(),
                context -> new EngineMachineRenderer<>(EngineMachineRenderer.rocketRotors()));
        event.registerBlockEntityRenderer(MachineBlocks.MACHINE_ENTITY.get(), context -> new MachineRenderer());
        event.registerBlockEntityRenderer(DrillBlocks.DRILL_ENTITY.get(), context -> new DrillRenderer());
        event.registerBlockEntityRenderer(BioMachineBlocks.BIO_MACHINE_ENTITY.get(), context -> new BioMachineRenderer());
        event.registerBlockEntityRenderer(PetroMachineBlocks.PETRO_MACHINE_ENTITY.get(), context -> new PetroMachineRenderer());
        event.registerBlockEntityRenderer(FissionMachines.MACHINE_ENTITY.get(), context -> new FissionMachineRenderer());
        event.registerBlockEntityRenderer(FissionMachines.CYCLOTRON_ENTITY.get(), context -> new FissionMachineRenderer());
        // 粒子源 / 辐照仓 / 对撞机各有独立方块实体类型，必须逐个挂渲染器，否则只有静态模型、不显示可动部件
        event.registerBlockEntityRenderer(FissionMachines.PARTICLE_SOURCE_ENTITY.get(), context -> new FissionMachineRenderer());
        event.registerBlockEntityRenderer(FissionMachines.IRRADIATION_CHAMBER_ENTITY.get(), context -> new FissionMachineRenderer());
        event.registerBlockEntityRenderer(FissionMachines.COLLIDER_ENTITY.get(), context -> new FissionMachineRenderer());
        event.registerBlockEntityRenderer(FissionMachines.MUON_ENTITY.get(), context -> new MuonFusionRenderer());
        event.registerBlockEntityRenderer(FissionReactors.REACTOR_ENTITY.get(), context -> new WaterReactorRenderer());
        event.registerBlockEntityRenderer(FissionReactors.LEGACY_REACTOR_ENTITY.get(), context -> new WaterReactorRenderer());
        event.registerBlockEntityRenderer(FissionSteam.TURBINE_ENTITY.get(), context -> new SteamTurbineRenderer());
        event.registerBlockEntityRenderer(TankBlocks.TANK_ENTITY.get(), context -> new TankRenderer());
    }

    /** 「高负荷机械方块」的连接材质（CTM）模型加载器。 */
    @SubscribeEvent
    public static void onRegisterBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(StellarEvolution.id("ctm_casing"), CtmCasingModel.Unbaked.CODEC);
    }

    /** 铜管的超载告警着色（26.x 的 {@code BlockTintSource}，不是老的 {@code BlockColor}）。 */
    @SubscribeEvent
    public static void onRegisterBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(ThermalPipeTintSource.INSTANCE), PipeBlocks.COPPER_PIPE.get());
        // 机械草地的草皮：直接复用原版草方块的染色源 —— 物品栏取默认草色、世界里取所在群系的平均草色（和原版一样随群系变色）。
        event.register(List.of(BlockTintSources.grassBlock()), FarmlandBlocks.MECHANICAL_GRASS.get());
    }

    private StellarEvolutionClient() {}
}
