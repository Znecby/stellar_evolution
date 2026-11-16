package dev.modmind.stellar_evolution;

import dev.modmind.stellar_evolution.bedrock.BedrockFluids;
import dev.modmind.stellar_evolution.bedrock.BedrockMaterials;
import dev.modmind.stellar_evolution.bio.BioFluids;
import dev.modmind.stellar_evolution.bio.BioMachineBlocks;
import dev.modmind.stellar_evolution.bio.BioMaterials;
import dev.modmind.stellar_evolution.coal.CoalFluids;
import dev.modmind.stellar_evolution.coal.CoalMaterials;
import dev.modmind.stellar_evolution.component.ComponentItems;
import dev.modmind.stellar_evolution.drill.DrillBlocks;
import dev.modmind.stellar_evolution.engine.EngineMachines;
import dev.modmind.stellar_evolution.engine.EngineBlocks;
import dev.modmind.stellar_evolution.equipment.SteelEquipment;
import dev.modmind.stellar_evolution.equipment.TungstenSteelEquipment;
import dev.modmind.stellar_evolution.farmland.FarmlandBlocks;
import dev.modmind.stellar_evolution.fission.FissionBlocks;
import dev.modmind.stellar_evolution.fission.FissionFluids;
import dev.modmind.stellar_evolution.fission.FissionMachines;
import dev.modmind.stellar_evolution.fission.FissionMaterials;
import dev.modmind.stellar_evolution.fission.FissionParts;
import dev.modmind.stellar_evolution.fission.FissionPools;
import dev.modmind.stellar_evolution.fission.FissionReactors;
import dev.modmind.stellar_evolution.fission.FissionSteam;
import dev.modmind.stellar_evolution.air.AirBlocks;
import dev.modmind.stellar_evolution.air.AirFluids;
import dev.modmind.stellar_evolution.coal.CokeOvenBlocks;
import dev.modmind.stellar_evolution.wood.WoodFluids;
import dev.modmind.stellar_evolution.wood.WoodMaterials;
import dev.modmind.stellar_evolution.frame.FrameBlocks;
import dev.modmind.stellar_evolution.hatch.HatchBlocks;
import dev.modmind.stellar_evolution.machine.MachineBlocks;
import dev.modmind.stellar_evolution.massdriver.MassDriverBlocks;
import dev.modmind.stellar_evolution.material.AlloyMaterials;
import dev.modmind.stellar_evolution.material.ElementDusts;
import dev.modmind.stellar_evolution.material.IntermediateItems;
import dev.modmind.stellar_evolution.material.LeadBlocks;
import dev.modmind.stellar_evolution.material.LeadMaterials;
import dev.modmind.stellar_evolution.material.MaterialItems;
import dev.modmind.stellar_evolution.material.TungstenBlocks;
import dev.modmind.stellar_evolution.material.TungstenMaterials;
import dev.modmind.stellar_evolution.moon.MoonBlocks;
import dev.modmind.stellar_evolution.muon.MuonMaterials;
import dev.modmind.stellar_evolution.ore.BauxiteBlocks;
import dev.modmind.stellar_evolution.ore.BauxiteMaterials;
import dev.modmind.stellar_evolution.ore.MonaziteBlocks;
import dev.modmind.stellar_evolution.ore.MonaziteMaterials;
import dev.modmind.stellar_evolution.ore.OreFluids;
import dev.modmind.stellar_evolution.ore.OreMaterials;
import dev.modmind.stellar_evolution.ore.SaltMaterials;
import dev.modmind.stellar_evolution.ore.TitaniumMaterials;
import dev.modmind.stellar_evolution.ore.TraceElementDusts;
import dev.modmind.stellar_evolution.welding.WeldingBlocks;
import dev.modmind.stellar_evolution.welding.WeldingFluids;
import dev.modmind.stellar_evolution.welding.WeldingMaterials;
import dev.modmind.stellar_evolution.particle.ParticleBlocks;
import dev.modmind.stellar_evolution.particle.ParticleComponents;
import dev.modmind.stellar_evolution.particle.ParticleItems;
import dev.modmind.stellar_evolution.particle.ParticleType;
import dev.modmind.stellar_evolution.petro.PetroFluids;
import dev.modmind.stellar_evolution.petro.PetroMachineBlocks;
import dev.modmind.stellar_evolution.petro.PetroMaterials;
import dev.modmind.stellar_evolution.pipe.PipeBlocks;
import dev.modmind.stellar_evolution.pulse.PulseBlocks;
import dev.modmind.stellar_evolution.tank.TankBlocks;
import dev.modmind.stellar_evolution.thermal.ThermalBlocks;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组自己的创造模式物品栏（不再往原版「功能方块」页里塞东西）。
 * <p>
 * 按用途分成 11 个页签：大型机器 / 机械方块 / 基础材料 / 合金材料 / 进阶组件 / 高级组件 / 杂项物品 / 流体 / 其他 /
 * 装备 / 物品基底（灰度素材）。
 * 每个条目只出现在其中一个页签里；页签标题走 {@code itemGroup.stellar_evolution.<id>}。
 */
public final class StellarEvolutionTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StellarEvolution.MOD_ID);

    /** 1. 大型机器：全部 2×2×2 整机，<strong>按元素获取顺序</strong>排列（生化 → 石化 → 裂变元素源 → 罐装 / 后处理 → 堆 / 轮机 → 加速器链 → 湮灭）。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LARGE_MACHINES =
            tab("large_machines", "itemGroup.stellar_evolution.large_machines",
                    () -> new ItemStack(BioMachineBlocks.REACTOR_ITEM.get()), output -> {
                        output.accept(BioMachineBlocks.SLURRY_MIXER_ITEM.get());
                        output.accept(BioMachineBlocks.FERMENTER_ITEM.get());
                        output.accept(BioMachineBlocks.DECOMPOSER_ITEM.get());
                        output.accept(BioMachineBlocks.STERILIZER_ITEM.get());
                        output.accept(BioMachineBlocks.CRYSTALLIZER_ITEM.get());
                        output.accept(BioMachineBlocks.REACTOR_ITEM.get());
                        for (var machine : PetroMachineBlocks.MACHINE_ITEMS) {
                            output.accept(machine.get());
                        }
                        // 焦化炉：3×3×4 砖块多方块，只给控制器（砖块自备）
                        output.accept(CokeOvenBlocks.CONTROLLER_ITEM.get());
                        // T0：元素来源（不吃合金）
                        output.accept(FissionMachines.ORE_LEACHER_ITEM.get());
                        output.accept(FissionMachines.CENTRIFUGE_ITEM.get());
                        // T1–T3：合金从少到多
                        output.accept(FissionMachines.CANNING_MACHINE_ITEM.get());
                        output.accept(FissionMachines.REPROCESSOR_ITEM.get());
                        output.accept(FissionReactors.FISSION_REACTOR_ITEM.get());
                        output.accept(FissionSteam.STEAM_TURBINE_ITEM.get());
                        // 加速器链：越后越吃合金
                        output.accept(FissionMachines.CYCLOTRON_ITEM.get());
                        output.accept(FissionMachines.PARTICLE_SOURCE_ITEM.get());
                        output.accept(FissionMachines.IRRADIATION_CHAMBER_ITEM.get());
                        output.accept(FissionMachines.COLLIDER_ITEM.get());
                        output.accept(FissionMachines.ACCELERATOR_RING_ITEM.get());
                        output.accept(FissionMachines.ADS_ITEM.get());
                        output.accept(FissionMachines.MUON_FUSION_ITEM.get());
                        output.accept(FissionMachines.ANNIHILATION_REACTOR_ITEM.get());
                        // 燃烧类发电机组（2×2×2 大机器）：内燃机组 / 联合燃气 / 火箭引擎
                        output.accept(EngineMachines.INDUSTRIAL_ENGINE_ITEM.get());
                        output.accept(EngineMachines.GAS_TURBINE_ITEM.get());
                        output.accept(EngineMachines.ROCKET_ENGINE_ITEM.get());
                    });

    /** 2. 机械方块：所有带功能机制的单方块。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MACHINES =
            tab("machines", "itemGroup.stellar_evolution.machines",
                    () -> new ItemStack(EngineBlocks.BURNING_ENGINE_ITEM.get()), output -> {
                        output.accept(ThermalBlocks.CREATIVE_PRESSURE_ITEM.get());
                        output.accept(ParticleBlocks.ISOTOPE_GENERATOR_ITEM.get());
                        output.accept(ParticleBlocks.HIGH_POWER_ISOTOPE_GENERATOR_ITEM.get());
                        output.accept(PipeBlocks.COPPER_PIPE_ITEM.get());
                        output.accept(PipeBlocks.MACHINE_PIPE_ITEM.get());
                        output.accept(PipeBlocks.PRESSURE_PIPE_ITEM.get());
                        output.accept(PipeBlocks.BEAM_PIPE_ITEM.get());
                        output.accept(PipeBlocks.FLOW_LIMITER_ITEM.get());
                        output.accept(EngineBlocks.HEAT_ENGINE_ITEM.get());
                        output.accept(EngineBlocks.BURNING_ENGINE_ITEM.get());
                        output.accept(MachineBlocks.CRUSHER_ITEM.get());
                        output.accept(MachineBlocks.DIE_PRESS_ITEM.get());
                        output.accept(MachineBlocks.ROLLING_MILL_ITEM.get());
                        output.accept(MachineBlocks.STEEL_FURNACE_ITEM.get());
                        output.accept(WeldingBlocks.WELDING_MACHINE_ITEM.get());
                        output.accept(DrillBlocks.MINING_DRILL_ITEM.get());
                        output.accept(TankBlocks.COPPER_TANK_ITEM.get());
                        output.accept(TankBlocks.STEEL_TANK_ITEM.get());
                        output.accept(TankBlocks.HARDENED_TANK_ITEM.get());
                        // 仓室基底：高负荷机械方块（连接材质）
                        output.accept(HatchBlocks.HIGH_LOAD_CASING_ITEM.get());
                        // 仓室基底：坚固支撑机械方块（钨色连接材质）
                        output.accept(HatchBlocks.RUGGED_SUPPORT_CASING_ITEM.get());
                        // 超导线圈方块：下界合金色 + 连接材质 + bloom
                        output.accept(HatchBlocks.SUPERCONDUCTING_COIL_ITEM.get());
                        // 机械物流仓：唯一保留的仓室，成型后接入大机器物流
                        output.accept(HatchBlocks.LOGISTICS_ITEM.get());
                        output.accept(FarmlandBlocks.MECHANICAL_FARMLAND_ITEM.get());
                        output.accept(FarmlandBlocks.MECHANICAL_SAND_ITEM.get());
                        output.accept(FarmlandBlocks.MECHANICAL_SOUL_SAND_ITEM.get());
                        output.accept(FarmlandBlocks.MECHANICAL_GRASS_ITEM.get());
                        output.accept(FissionReactors.REACTION_CHAMBER_ITEM.get());
                        output.accept(FissionPools.SPENT_FUEL_POOL_ITEM.get());
                    });

    /** 3.1 基础材料：<strong>没有 3D 模型</strong>的全部物品（材料、粉 / 板 / 杆 / 线、锭）。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE_MATERIALS =
            tab("base_materials", "itemGroup.stellar_evolution.base_materials",
                    () -> new ItemStack(MaterialItems.COPPER_PLATE.get()), output -> {
                        output.accept(MaterialItems.COPPER_DUST.get());
                        output.accept(MaterialItems.COPPER_PLATE.get());
                        output.accept(MaterialItems.COPPER_ROD.get());
                        output.accept(MaterialItems.COPPER_WIRE.get());
                        output.accept(MaterialItems.COPPER_GEAR.get());
                        output.accept(MaterialItems.IRON_PLATE.get());
                        output.accept(MaterialItems.GOLD_PLATE.get());
                        output.accept(MaterialItems.GOLD_WIRE.get());
                        output.accept(MaterialItems.NETHERITE_PLATE.get());
                        output.accept(MaterialItems.NETHERITE_ROD.get());
                        for (var material : TungstenMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : LeadMaterials.ALL) {
                            output.accept(material.get());
                        }
                        output.accept(IntermediateItems.STEEL_DUST.get());
                        output.accept(IntermediateItems.STEEL_INGOT.get());
                        output.accept(IntermediateItems.STEEL_PLATE.get());
                        output.accept(IntermediateItems.STEEL_ROD.get());
                        output.accept(IntermediateItems.STEEL_GEAR.get());
                        output.accept(IntermediateItems.PLATE_MOLD.get());
                        output.accept(IntermediateItems.ROD_MOLD.get());
                        output.accept(IntermediateItems.GEAR_MOLD.get());
                        for (var dust : ElementDusts.ALL) {
                            output.accept(dust.get());
                        }
                        for (var material : BioMaterials.ALL) {
                            if (material == BioMaterials.CULTURE_STRAIN || material == BioMaterials.VITAMINS) {
                                continue;   // 有 3D 模型 → 进阶组件
                            }
                            output.accept(material.get());
                        }
                        for (var material : PetroMaterials.ALL) {
                            if (material == PetroMaterials.CIRCUIT_SUBSTRATE) {
                                continue;   // 有 3D 模型 → 进阶组件
                            }
                            output.accept(material.get());
                        }
                        for (var material : CoalMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : WoodMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : OreMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : SaltMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : TraceElementDusts.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : MonaziteMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : TitaniumMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : BauxiteMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : BedrockMaterials.ALL) {
                            output.accept(material.get());
                        }
                        for (var material : WeldingMaterials.ALL) {
                            output.accept(material.get());
                        }
                    });

    /** 3.2 合金材料：6 种合金的全部 7 种形态（锭 / 粒 / 板 / 杆 / 线 / 齿轮 / 块）+ 稀土合金 / NbTi。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ALLOY_MATERIALS =
            tab("alloy_materials", "itemGroup.stellar_evolution.alloy_materials",
                    () -> new ItemStack(AlloyMaterials.STAINLESS_STEEL.ingot.get()), output -> {
                        for (AlloyMaterials.Alloy alloy : AlloyMaterials.ALL) {
                            for (var item : alloy.items()) {
                                output.accept(item.get());
                            }
                        }
                        // 航天线专用合金：稀土合金 3 形态 + NbTi 超导线
                        output.accept(dev.modmind.stellar_evolution.space.SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get());
                        output.accept(dev.modmind.stellar_evolution.space.SpaceMaterials.RARE_EARTH_ALLOY_PLATE.get());
                        output.accept(dev.modmind.stellar_evolution.space.SpaceMaterials.RARE_EARTH_ALLOY_ROD.get());
                        output.accept(dev.modmind.stellar_evolution.space.SpaceMaterials.NB_TI_WIRE.get());
                    });

    /** 3.3 进阶组件：<strong>有 3D 模型</strong>，且配方里<strong>没有</strong>其他 3D 模型物品。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INTERMEDIATE_COMPONENTS =
            tab("intermediate_components", "itemGroup.stellar_evolution.intermediate_components",
                    () -> new ItemStack(IntermediateItems.BEARING.get()), output -> {
                        output.accept(MaterialItems.QUARTZ_GLASS.get());
                        // 结构 / 电气件
                        output.accept(IntermediateItems.BEARING.get());
                        output.accept(IntermediateItems.MACHINE_FRAME.get());
                        output.accept(IntermediateItems.PRECISION_HEAVY_FRAME.get());
                        output.accept(IntermediateItems.RESISTOR.get());
                        output.accept(IntermediateItems.CAPACITOR.get());
                        output.accept(IntermediateItems.SENSOR.get());
                        // 重工业件
                        output.accept(ComponentItems.PISTON_ASSEMBLY.get());
                        output.accept(ComponentItems.ELECTRIC_MOTOR.get());
                        output.accept(ComponentItems.FLUX_COIL.get());
                        output.accept(ComponentItems.HEATING_COIL.get());
                        output.accept(ComponentItems.HIGH_FREQUENCY_COIL.get());
                        output.accept(ComponentItems.BASIC_FLUID_CELL.get());
                        output.accept(ComponentItems.FLUID_CELL.get());
                        output.accept(DrillBlocks.DRILL_BIT.get());
                        // 生化 / 石化件
                        output.accept(BioMaterials.CULTURE_STRAIN.get());
                        output.accept(BioMaterials.VITAMINS.get());
                        output.accept(PetroMaterials.CIRCUIT_SUBSTRATE.get());
                        // 稀土磁体 / 铂族催化剂（2026-11-01 从「杂项物品」移入进阶组件）
                        output.accept(FissionMaterials.RARE_EARTH_MAGNET.get());
                        output.accept(FissionMaterials.PLATINUM_CATALYST.get());
                        // 裂变部件：配方里还没有 3D 件
                        output.accept(FissionParts.REFLECTOR_MODULE.get());
                    });

    /** 3.4 高级组件：<strong>有 3D 模型</strong>，且配方里<strong>含</strong>其他 3D 模型物品。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ADVANCED_COMPONENTS =
            tab("advanced_components", "itemGroup.stellar_evolution.advanced_components",
                    () -> new ItemStack(ComponentItems.CHIP.get()), output -> {
                        output.accept(IntermediateItems.SERVO.get());
                        output.accept(IntermediateItems.THERMOSTAT.get());
                        output.accept(IntermediateItems.CONTROL_MODULE.get());
                        output.accept(ComponentItems.CIRCUIT_BOARD.get());
                        output.accept(ComponentItems.CHIP.get());
                        output.accept(FissionParts.TEMPERED_GLASS.get());
                        output.accept(FissionParts.CORROSION_LINER.get());
                        output.accept(FissionParts.RADIATION_SHIELDING.get());
                        output.accept(FissionParts.CENTRIFUGE_ROTOR.get());
                        output.accept(FissionParts.FUEL_LOADING_HEAD.get());
                        output.accept(FissionParts.CONTROL_ROD_DRIVE.get());
                        output.accept(FissionParts.TURBINE_BLADE.get());
                        output.accept(FissionParts.STEAM_VALVE_MANIFOLD.get());
                    });

    /** 4. 杂项物品：裂变线的全部物品（核材料、芯块、燃料棒、乏燃料、废料等）。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MISC_ITEMS =
            tab("misc_items", "itemGroup.stellar_evolution.misc_items",
                    () -> new ItemStack(FissionMaterials.SPENT_FUEL.get()), output -> {
                        output.accept(ComponentItems.COLLAPSED_ENDER_EYE.get());
                        for (var item : FissionMaterials.ALL) {
                            if (item == FissionMaterials.RARE_EARTH_MAGNET
                                    || item == FissionMaterials.PLATINUM_CATALYST) {
                                continue;   // 已移入「进阶组件」页
                            }
                            output.accept(item.get());
                        }
                        for (var item : MuonMaterials.ALL) {
                            output.accept(item.get());
                        }
                        for (var item : ParticleItems.ALL) {
                            output.accept(item.get());
                        }
                        // 9 种带 particle_type 的压力罐（创造页直接可取，免去每次跑罐装机）
                        for (ParticleType type : ParticleType.values()) {
                            ItemStack tank = new ItemStack(ParticleItems.PRESSURE_TANK.get());
                            tank.set(ParticleComponents.PARTICLE_TYPE.get(), type);
                            output.accept(tank);
                        }
                    });

    /** 5. 流体：所有自定义流体的桶。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLUIDS =
            tab("fluids", "itemGroup.stellar_evolution.fluids",
                    () -> new ItemStack(BioFluids.ALL.get(0).bucket().get()), output -> {
                        for (BioFluids.Entry fluid : BioFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                        for (PetroFluids.Entry fluid : PetroFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                        for (AirFluids.Entry fluid : AirFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                        for (CoalFluids.Entry fluid : CoalFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                        for (WoodFluids.Entry fluid : WoodFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                        for (FissionFluids.Entry fluid : FissionFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                        for (OreFluids.Entry fluid : OreFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                        for (BedrockFluids.Entry fluid : BedrockFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                        for (WeldingFluids.Entry fluid : WeldingFluids.ALL) {
                            output.accept(fluid.bucket().get());
                        }
                    });

    /** 6. 其他：结构件、纯外观方块与矿石方块。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OTHER =
            tab("other", "itemGroup.stellar_evolution.other",
                    () -> new ItemStack(FrameBlocks.COPPER_FRAME_ITEM.get()), output -> {
                        output.accept(FrameBlocks.COPPER_FRAME_ITEM.get());
                        output.accept(AirBlocks.STEEL_GRATE_ITEM.get());
                        // 质量投射轨道炮控制器
                        output.accept(MassDriverBlocks.CONTROLLER_ITEM.get());
                        // 通用一键搭建工具
                        output.accept(dev.modmind.stellar_evolution.multiblock.MultiblockToolItems.BUILDER.get());
                        // 开发用：快速配方添加书（仅开发环境且开发者模式显示）
                        if (!net.neoforged.fml.loading.FMLEnvironment.isProduction()
                                && dev.modmind.stellar_evolution.dev.DevConfig.developerMode()) {
                            output.accept(dev.modmind.stellar_evolution.dev.QuickRecipeItems.QUICK_RECIPE_BOOK.get());
                        }
                        output.accept(PulseBlocks.PULSE_CORE_ITEM.get());
                        output.accept(FissionBlocks.URANIUM_ORE_ITEM.get());
                        output.accept(FissionBlocks.DEEPSLATE_URANIUM_ORE_ITEM.get());
                        output.accept(FissionBlocks.HLW_GLASS_BLOCK_ITEM.get());
                        output.accept(TungstenBlocks.TUNGSTEN_ORE_ITEM.get());
                        output.accept(TungstenBlocks.DEEPSLATE_TUNGSTEN_ORE_ITEM.get());
                        output.accept(TungstenBlocks.TUNGSTEN_BLOCK_ITEM.get());
                        output.accept(LeadBlocks.LEAD_ORE_ITEM.get());
                        output.accept(LeadBlocks.DEEPSLATE_LEAD_ORE_ITEM.get());
                        output.accept(LeadBlocks.LEAD_BLOCK_ITEM.get());
                        output.accept(MonaziteBlocks.MONAZITE_ORE_ITEM.get());
                        output.accept(MonaziteBlocks.DEEPSLATE_MONAZITE_ORE_ITEM.get());
                        output.accept(BauxiteBlocks.BAUXITE_ORE_ITEM.get());
                        output.accept(BauxiteBlocks.DEEPSLATE_BAUXITE_ORE_ITEM.get());
                        output.accept(dev.modmind.stellar_evolution.ore.SulfurBlocks.SULFUR_ORE_ITEM.get());
                        // 月球地表方块
                        output.accept(MoonBlocks.MOON_REGOLITH_ITEM.get());
                        output.accept(dev.modmind.stellar_evolution.moon.MoonOreBlocks.MOON_ILMENITE_ORE_ITEM.get());
                        output.accept(dev.modmind.stellar_evolution.moon.MoonOreBlocks.DEEPSLATE_MOON_ILMENITE_ORE_ITEM.get());
                        output.accept(dev.modmind.stellar_evolution.moon.MoonOreBlocks.MOON_ICE_ROCK_ORE_ITEM.get());
                        output.accept(dev.modmind.stellar_evolution.moon.MoonOreBlocks.DEEPSLATE_MOON_ICE_ROCK_ORE_ITEM.get());
                        output.accept(dev.modmind.stellar_evolution.moon.MoonOreBlocks.MOON_GLOW_TITANIUM_ORE_ITEM.get());
                    });

    /** 7.5 装备：钢系列工具 / 武器 / 护甲（2026-11-01）。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EQUIPMENT =
            tab("equipment", "itemGroup.stellar_evolution.equipment",
                    () -> new ItemStack(TungstenSteelEquipment.TUNGSTEN_STEEL_PICKAXE.get()), output -> {
                        for (var item : SteelEquipment.ALL) {
                            output.accept(item.get());
                        }
                        for (var item : TungstenSteelEquipment.ALL) {
                            output.accept(item.get());
                        }
                    });

    private static DeferredHolder<CreativeModeTab, CreativeModeTab> tab(
            String name, String titleKey, Supplier<ItemStack> icon, Consumer<CreativeModeTab.Output> entries) {
        return TABS.register(name, () -> CreativeModeTab.builder()
                .title(Component.translatable(titleKey))
                .icon(icon)
                .displayItems((parameters, output) -> entries.accept(output))
                .build());
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    private StellarEvolutionTabs() {}
}
