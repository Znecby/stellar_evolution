package dev.znecby.stellar_evolution;

import com.mojang.logging.LogUtils;
import dev.znecby.stellar_evolution.bedrock.BedrockComponents;
import dev.znecby.stellar_evolution.bedrock.BedrockFluids;
import dev.znecby.stellar_evolution.bedrock.BedrockMaterials;
import dev.znecby.stellar_evolution.air.AirBlocks;
import dev.znecby.stellar_evolution.air.AirFluids;
import dev.znecby.stellar_evolution.bio.BioFluids;
import dev.znecby.stellar_evolution.bio.BioMachineBlocks;
import dev.znecby.stellar_evolution.bio.BioMaterials;
import dev.znecby.stellar_evolution.coal.CoalFluids;
import dev.znecby.stellar_evolution.coal.CoalMaterials;
import dev.znecby.stellar_evolution.coal.CokeOvenBlocks;
import dev.znecby.stellar_evolution.component.ComponentItems;
import dev.znecby.stellar_evolution.dev.DevConfig;
import dev.znecby.stellar_evolution.dev.QuickRecipeItems;
import dev.znecby.stellar_evolution.dev.QuickRecipeMenus;
import dev.znecby.stellar_evolution.drill.DrillBlocks;
import dev.znecby.stellar_evolution.engine.EngineBlocks;
import dev.znecby.stellar_evolution.engine.EngineMachines;
import dev.znecby.stellar_evolution.equipment.SteelEquipment;
import dev.znecby.stellar_evolution.equipment.SteelToolComponents;
import dev.znecby.stellar_evolution.equipment.TungstenSteelEquipment;
import dev.znecby.stellar_evolution.farmland.FarmlandBlocks;
import dev.znecby.stellar_evolution.fission.FissionBlocks;
import dev.znecby.stellar_evolution.fission.FissionConfig;
import dev.znecby.stellar_evolution.fission.FissionComponents;
import dev.znecby.stellar_evolution.fission.FissionFluids;
import dev.znecby.stellar_evolution.fission.FissionMachines;
import dev.znecby.stellar_evolution.fission.FissionMachineStructure;
import dev.znecby.stellar_evolution.fission.FissionMaterials;
import dev.znecby.stellar_evolution.fission.FissionParts;
import dev.znecby.stellar_evolution.fission.FissionPools;
import dev.znecby.stellar_evolution.fission.FissionReactors;
import dev.znecby.stellar_evolution.fission.FissionSteam;
import dev.znecby.stellar_evolution.fission.ReflectorRodItem;
import dev.znecby.stellar_evolution.frame.FrameBlocks;
import dev.znecby.stellar_evolution.hatch.HatchBlocks;
import dev.znecby.stellar_evolution.machine.MachineBlocks;
import dev.znecby.stellar_evolution.massdriver.MassDriverBlocks;
import dev.znecby.stellar_evolution.massdriver.MassDriverComponents;
import dev.znecby.stellar_evolution.material.AlloyMaterials;
import dev.znecby.stellar_evolution.material.ElementDusts;
import dev.znecby.stellar_evolution.material.IntermediateItems;
import dev.znecby.stellar_evolution.material.LeadBlocks;
import dev.znecby.stellar_evolution.material.LeadMaterials;
import dev.znecby.stellar_evolution.material.MaterialItems;
import dev.znecby.stellar_evolution.material.TungstenBlocks;
import dev.znecby.stellar_evolution.material.TungstenMaterials;
import dev.znecby.stellar_evolution.moon.MoonBlocks;
import dev.znecby.stellar_evolution.moon.MoonOreBlocks;
import dev.znecby.stellar_evolution.moon.MoonEnvironment;
import dev.znecby.stellar_evolution.muon.MuonMaterials;
import dev.znecby.stellar_evolution.multiblock.MultiblockToolItems;
import dev.znecby.stellar_evolution.network.StellarEvolutionNetwork;
import dev.znecby.stellar_evolution.ore.BauxiteBlocks;
import dev.znecby.stellar_evolution.ore.BauxiteMaterials;
import dev.znecby.stellar_evolution.ore.MonaziteBlocks;
import dev.znecby.stellar_evolution.ore.MonaziteMaterials;
import dev.znecby.stellar_evolution.ore.OreFluids;
import dev.znecby.stellar_evolution.ore.OreMaterials;
import dev.znecby.stellar_evolution.ore.SaltMaterials;
import dev.znecby.stellar_evolution.ore.SulfurBlocks;
import dev.znecby.stellar_evolution.ore.TitaniumMaterials;
import dev.znecby.stellar_evolution.ore.TraceElementDusts;
import dev.znecby.stellar_evolution.space.SpaceMaterials;
import dev.znecby.stellar_evolution.particle.ParticleBlocks;
import dev.znecby.stellar_evolution.particle.ParticleComponents;
import dev.znecby.stellar_evolution.particle.ParticleItems;
import dev.znecby.stellar_evolution.petro.PetroBlocks;
import dev.znecby.stellar_evolution.petro.PetroFluids;
import dev.znecby.stellar_evolution.petro.PetroMachineBlocks;
import dev.znecby.stellar_evolution.petro.PetroMaterials;
import dev.znecby.stellar_evolution.pipe.PipeBlocks;
import dev.znecby.stellar_evolution.welding.WeldingBlocks;
import dev.znecby.stellar_evolution.welding.WeldingFluids;
import dev.znecby.stellar_evolution.welding.WeldingMaterials;
import dev.znecby.stellar_evolution.pulse.PulseBlocks;
import dev.znecby.stellar_evolution.tank.TankBlocks;
import dev.znecby.stellar_evolution.thermal.ThermalBlocks;
import dev.znecby.stellar_evolution.wood.WoodFluids;
import dev.znecby.stellar_evolution.wood.WoodMaterials;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import org.slf4j.Logger;

/**
 * 模组主类（恒星演变 / Stellar Evolution）：<strong>工业科技向</strong>。
 * <p>
 * 只做组装（注册 / 网络），具体逻辑在各自包里。
 * <p>
 * <strong>方向变更记录（2026-10-07）</strong>：本模组原为「暗黑式刷宝（ARPG loot）」方向，
 * P0–P7 那套刷宝内容（词缀 / 掉落判定 / 难度层级 / 精英怪 / 星界熔炉 / 饰品 / 属性面板）
 * 已整体移出到独立的旧项目；本模组保留当前内容并转为工业科技向，
 * 现存内容为工业科技向的热压差网络、铜管 / 铜框架、热力引擎 / 燃烧引擎与储罐等基础设施，
 * 以及生化 / 石化 / 煤化工 / 核裂变 / 选冶五条产线（早期三台 FE 实验机器已于 2026-10-25 删除）。
 */
@Mod(StellarEvolution.MOD_ID)
public final class StellarEvolution {
    public static final String MOD_ID = "stellar_evolution";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StellarEvolution(IEventBus modEventBus, ModContainer modContainer) {
        // 模组总线：注册阶段一次性事件
        modEventBus.addListener(StellarEvolutionNetwork::onRegisterPayloadHandlers);
        // 钢系列装备（2026-11-01）：工具 / 武器 / 护甲 + 模式数据组件
        SteelToolComponents.register(modEventBus);
        SteelEquipment.register(modEventBus);
        TungstenSteelEquipment.register(modEventBus);
        StellarEvolutionTabs.register(modEventBus);
        PipeBlocks.register(modEventBus);
        FrameBlocks.register(modEventBus);
        // 质量投射轨道炮：结构来自客户端保存的 114514，控制器基底为高负荷机械方块
        MassDriverBlocks.register(modEventBus);
        MassDriverComponents.register(modEventBus);
        modEventBus.addListener(MassDriverBlocks::registerCapabilities);
        MultiblockToolItems.register(modEventBus);
        EngineBlocks.register(modEventBus);
        // 燃烧类发电机组：工业内燃机组 / 联合燃气机组 / 航空航天火箭引擎
        EngineMachines.register(modEventBus);
        // 仓室（只保留机械物流仓）：本身无 UI / 无库存，成型后作为大机器物流端口
        HatchBlocks.register(modEventBus);
        // 材料加工机械（破碎机 / 压模机 / 线材轧机 / 钢炉）
        MachineBlocks.register(modEventBus);
        // 焊接线：焊接机 + 焊接板材料 + 氧气
        WeldingBlocks.register(modEventBus);
        WeldingMaterials.register(modEventBus);
        WeldingFluids.register(modEventBus);
        // 开采钻：纯外观单方块（钻杆 / 绞盘自转），本轮无开采机制
        DrillBlocks.register(modEventBus);
        TankBlocks.register(modEventBus);
        PulseBlocks.register(modEventBus);
        ThermalBlocks.register(modEventBus);
        FarmlandBlocks.register(modEventBus);
        BioMachineBlocks.register(modEventBus);
        BioMaterials.register(modEventBus);
        BioFluids.register(modEventBus);
        // 选冶线（G 线）：粗矿 / 粉 / 精砂 / 富精矿浆 / 硫酸
        OreMaterials.register(modEventBus);
        OreFluids.register(modEventBus);
        // 盐湖 / 矿物盐支线（B1）：硼砂 / 硼酸 / 硼粉 / 钠粉 / 氯化钠 / 氢氧化钠
        SaltMaterials.register(modEventBus);
        // B4：无下游元素只做粉
        TraceElementDusts.register(modEventBus);
        SpaceMaterials.register(modEventBus);
        // 新矿石线（B2 独居石 / B3 铝土矿）
        MonaziteMaterials.register(modEventBus);
        MonaziteBlocks.register(modEventBus);
        TitaniumMaterials.register(modEventBus);
        BauxiteMaterials.register(modEventBus);
        BauxiteBlocks.register(modEventBus);
        SulfurBlocks.register(modEventBus);
        MaterialItems.register(modEventBus);
        TungstenMaterials.register(modEventBus);
        TungstenBlocks.register(modEventBus);
        LeadMaterials.register(modEventBus);
        LeadBlocks.register(modEventBus);
        // 合金材料（2026-11-01）：7 种合金全 8 形态 + 8 种配料元素粉 + 钢粉
        ElementDusts.register(modEventBus);
        AlloyMaterials.register(modEventBus);
        ComponentItems.register(modEventBus);
        // 开发用：快速配方添加书（物品 + 3×3 记录菜单；生产环境不注册）
        if (!FMLEnvironment.isProduction()) {
            QuickRecipeItems.register(modEventBus);
            QuickRecipeMenus.register(modEventBus);
        }
        IntermediateItems.register(modEventBus);
        PetroBlocks.register(modEventBus);
        StellarEvolutionWorldgen.register(modEventBus);
        PetroMachineBlocks.register(modEventBus);
        PetroFluids.register(modEventBus);
        PetroMaterials.register(modEventBus);
        // 空气分馏线：不锈钢格栅（虚拟空气源）+ 空气 / 氮气
        AirBlocks.register(modEventBus);
        AirFluids.register(modEventBus);
        // 煤化工线（C 线）：煤头支线物质（复用石化机器框架）
        CoalMaterials.register(modEventBus);
        CoalFluids.register(modEventBus);
        // 木化工线（干馏 / 松节油 / 纤维素）：2026-11-11 批次 1
        WoodMaterials.register(modEventBus);
        WoodFluids.register(modEventBus);
        // 焦化炉（3×3×4 砖块多方块）：只注册控制器，砖块玩家自备
        CokeOvenBlocks.register(modEventBus);
        modEventBus.addListener(CokeOvenBlocks::registerCapabilities);
        // 核裂变线（第三期）F0：物质地基
        FissionBlocks.register(modEventBus);
        FissionMaterials.register(modEventBus);
        FissionParts.register(modEventBus);
        FissionComponents.register(modEventBus);
        FissionFluids.register(modEventBus);
        // 基岩无限资源线（2026-11-01）：同位素溶剂 + 五段萃取材料
        BedrockComponents.register(modEventBus);
        BedrockMaterials.register(modEventBus);
        BedrockFluids.register(modEventBus);
        // 裂变线 2×2×2 结构公共层（填充格 / 接管口 / 可动部件载体）——须先于主方块注册
        FissionMachineStructure.register(modEventBus);
        FissionMachines.register(modEventBus);
        FissionReactors.register(modEventBus);
        FissionPools.register(modEventBus);
        FissionSteam.register(modEventBus);
        // 月球维度：月壤方块（其余地形 / 矿石复用原版与本模组已有方块）
        MoonBlocks.register(modEventBus);
        MoonOreBlocks.register(modEventBus);
        // 月球环境：低重力 / 坠落伤害（原版属性修饰符；首版不做氧气）
        MoonEnvironment.register();
        // 聚变线（μCF）：聚变芯核（约束缪子流）
        MuonMaterials.register(modEventBus);
        // 粒子线：压力罐 + 粒子类型组件 + 同位素电源（RTG）
        ParticleItems.register(modEventBus);
        ParticleComponents.register(modEventBus);
        ParticleBlocks.register(modEventBus);
        // 裂变线 F2 余项：熔毁严重程度的配置
        FissionConfig.register(modContainer);
        // 开发工具配置：开发者模式默认只在开发环境开启
        DevConfig.register(modContainer);
        // 反射棒：铁砧 + 1 下界合金锭 → 直接回满
        NeoForge.EVENT_BUS.addListener(StellarEvolution::onAnvilUpdate);

        // 手册是可选的：只有客户端装了 GuideME 才加载注册类，保持零硬前置。
        // GuideME 的 GuideBuilder 会加载客户端类（BlockAndTintGetter），专用服务器上必须跳过。
        if (ModList.get().isLoaded("guideme") && FMLEnvironment.getDist() == Dist.CLIENT) {
            dev.znecby.stellar_evolution.guide.StellarGuide.register();
        }

        LOGGER.info("Stellar Evolution core initialised");
    }

    private static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (!(left.getItem() instanceof ReflectorRodItem) || !event.getRight().is(Items.NETHERITE_INGOT)
                || left.getDamageValue() == 0) {
            return;
        }
        ItemStack output = left.copy();
        output.setDamageValue(0);
        event.setOutput(output);
        event.setXpCost(1);
        event.setMaterialCost(1);
    }

    /** 生成本模组命名空间下的资源 ID。 */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
