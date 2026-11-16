package dev.modmind.stellar_evolution.thermal;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.bio.BioMachineBlocks;
import dev.modmind.stellar_evolution.bio.BioMaterials;
import dev.modmind.stellar_evolution.coal.CoalMaterials;
import dev.modmind.stellar_evolution.coal.CokeOvenBlocks;
import dev.modmind.stellar_evolution.fission.FissionBlocks;
import dev.modmind.stellar_evolution.fission.FissionMachines;
import dev.modmind.stellar_evolution.fission.FissionMaterials;
import dev.modmind.stellar_evolution.fission.FissionParts;
import dev.modmind.stellar_evolution.fission.FissionReactors;
import dev.modmind.stellar_evolution.fission.FissionSteam;
import dev.modmind.stellar_evolution.component.ComponentItems;
import dev.modmind.stellar_evolution.drill.DrillBlocks;
import dev.modmind.stellar_evolution.equipment.SteelCrafting;
import dev.modmind.stellar_evolution.equipment.SteelEquipment;
import dev.modmind.stellar_evolution.equipment.TungstenSteelEquipment;
import dev.modmind.stellar_evolution.equipment.TungstenSteelGearItem;
import dev.modmind.stellar_evolution.equipment.TungstenSteelTool;
import dev.modmind.stellar_evolution.engine.EngineBlocks;
import dev.modmind.stellar_evolution.engine.EngineMachines;
import dev.modmind.stellar_evolution.farmland.FarmlandBlocks;
import dev.modmind.stellar_evolution.frame.FrameBlocks;
import dev.modmind.stellar_evolution.hatch.HatchBlocks;
import dev.modmind.stellar_evolution.massdriver.MassDriverBlocks;
import dev.modmind.stellar_evolution.material.AlloyMaterials;
import dev.modmind.stellar_evolution.material.ElementDusts;
import dev.modmind.stellar_evolution.material.IntermediateItems;
import dev.modmind.stellar_evolution.material.LeadMaterials;
import dev.modmind.stellar_evolution.material.MaterialItems;
import dev.modmind.stellar_evolution.material.TungstenMaterials;
import dev.modmind.stellar_evolution.muon.MuonMaterials;
import dev.modmind.stellar_evolution.ore.BauxiteMaterials;
import dev.modmind.stellar_evolution.ore.MonaziteMaterials;
import dev.modmind.stellar_evolution.ore.OreMaterials;
import dev.modmind.stellar_evolution.ore.SaltMaterials;
import dev.modmind.stellar_evolution.ore.TraceElementDusts;
import dev.modmind.stellar_evolution.particle.ParticleBlocks;
import dev.modmind.stellar_evolution.petro.PetroMaterials;
import dev.modmind.stellar_evolution.petro.PetroMachineBlocks;
import dev.modmind.stellar_evolution.petro.PlasticTags;
import dev.modmind.stellar_evolution.pipe.PipeBlocks;
import dev.modmind.stellar_evolution.space.SpaceMaterials;
import dev.modmind.stellar_evolution.welding.WeldingMaterials;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * <strong>热室</strong>的配方表（{@link HotChamberRecipe}，单独配方类型：只吃物品、能源走热压差网络）。
 *
 * <p>分层（全部由<strong>原版材料</strong>起步，一层层往上一级做，最终造出<strong>生化线 6 台机器</strong>）：
 * <ol>
 *   <li><strong>锭 → 型材</strong>（铜板 / 铜杆 / 铜线、钢板 / 钢杆、铜齿轮 / 钢齿轮）走<strong>原版切石机 / 工作台</strong>
 *       （见 {@code data/stellar_evolution/recipe/*.json}）—— <strong>刻意不放进热室</strong>：
 *       同一个锭能出板 / 杆 / 线好几样，热室按「先命中先算」匹配，放进来必然互相遮蔽（切石机才有选单）；</li>
 *   <li><strong>钢锭</strong>：铁锭渗碳（热室）；</li>
 *   <li><strong>结构机械件</strong>：钢齿轮 + 钢杆 → 轴承 → 机械框架（{@link IntermediateItems}，热室）；</li>
 *   <li><strong>电气精密件</strong>：铜材 + 原版电子料（煤 / 玻璃 / 石英）+ <strong>铁板 / 金板 / 金线 / 钨线</strong>
 *       → 电阻 / 电容 / 传感器 / 活塞 / 马达 / 线圈 / 电路板 / 流体单元（2026-10-24 起少量接入新材）；</li>
 *   <li><strong>高级组件</strong>：控制模块 / 芯片 —— <strong>大量使用钨（板 / 线）、金板与金线</strong>
 *       （伺服机构 / 温控器已于 2026-11-08 改工作台配方）；</li>
 *   <li><strong>6 台生化机器</strong>（<strong>递进式</strong>：调浆机最便宜 → 生物反应器最贵）。</li>
 * </ol>
 * 口径：<strong>只吃物品</strong>、不吃流体；网络热流率<strong>只决定倍速</strong>（不改写产率），
 * 所以「堆热压差 = 线性加速」。
 * <p><strong>命中策略 = 「输入最具体优先」</strong>：先比<strong>输入种类数</strong>（越多越优先），
 * 再比<strong>输入总件数</strong>（种类并列时，吃料多那条优先），最后按表序。
 * 这样「钢板 + 钢杆」这类<strong>基础料的大配方不会压过专用配方</strong>：
 * 箱里同时有铜齿轮 / 铁板时会先出活塞组件，而不是把 4 钢板 + 4 钢杆做成机械框架；
 * 想造框架就只放钢板 / 钢杆，换料即换产物。
 * <p>由此得到一条不变式：<strong>严格子集的配方永远赢不过超集</strong>（甲的输入是乙的子集 ⇒ 甲的种类数与件数都 ≤ 乙的）。
 * 所以当两套原料同时在场时，小配方会被大配方遮蔽；只放小配方自己的原料时仍可命中。需要同时保留两条时，
 * 用<strong>等压分组</strong>拆开（见下）。唯一「永远做不出来」的是<strong>两条输入完全相同</strong>
 * （并列 ⇒ 表序在前的永远赢）—— 表建立时自检并告警（{@link #audit()}），
 * 这也是「板 / 杆 / 线」这类同一个锭出好几样必须放**切石机**的原因。
 *
 * <p><strong>同种类数互抢</strong>（两条都是 2 种料时，件数多的那条永远赢）用<strong>等压分组</strong>拆开：
 * 把会被大配方抢料的那条单独放到一个等压值（JEI 面板会写「等压 N」），玩家蹲下 + 剪刀右键把热室等压
 * 拨到 N 才做它。当前专用组：
 * <ul>
 *   <li><b>9</b> = 高负荷机械方块；<b>10</b> = 坚固支撑机械方块；</li>
 *   <li><b>11</b> = 锝钢锭；<b>12</b> = 锆合金管（锆-90 路线）；</li>
 *   <li><b>13</b> = 辐射屏蔽（稳定核素路线）；<b>14</b> = 辐射屏蔽（铪粉路线）；</li>
 *   <li><b>15</b> = 危险玻璃（HLW 玻璃）；<b>16</b> = 不熄合金；<b>17</b> = 后处理厂。</li>
 * </ul>
 *
 * <p>槽位：热室把<strong>整个箱子库存</strong>（双联 = 54 格）当输入 / 输出，不分区 —— 匹配「输入够 + 产物放得下」，
 * 完成时按配方扣掉输入、把产物并回箱内。
 * <p><strong>输入上限 = 9 种</strong>（JEI 面板固定 3×3）：超过 9 条的配方会在建表时直接抛异常，
 * 逼着把配方拆成两步中间产物，避免 JEI 排版溢出。
 */
public final class HotChamberRecipes {
    /** 批量炼钢铁料：铁锭或铁粉（数据标签 {@code stellar_evolution:steel_feed}）。 */
    private static final TagKey<Item> STEEL_FEED =
            TagKey.create(Registries.ITEM, StellarEvolution.id("steel_feed"));
    /** JEI 3×3 版面 → 每条配方最多 9 个输入槽（硬上限，建表时校验）。 */
    private static final int MAX_INPUT_SLOTS = 9;

    private static List<HotChamberRecipe> cache;

    /** 全部配方（懒加载：{@code ItemStack} 必须在注册表可用之后才构造）。 */
    public static List<HotChamberRecipe> all() {
        if (cache == null) {
            cache = List.of(
                    // ---------------- ① 钢锭：铁锭渗碳（钢系的源头；钢板 / 钢杆 / 齿轮见切石机 + 工作台配方）
                    recipe(items(Items.IRON_INGOT, 1, Items.COAL, 2), out(IntermediateItems.STEEL_INGOT.get(), 1), 200),
                    // 批量炼钢（2026-11-10）：铁锭或铁粉 8 + 焦炭 4 → 钢锭 8；等压 11 与基础配方分开
                    tagged(items(CoalMaterials.COKE.get(), 4),
                            tags(STEEL_FEED, 8, OreMaterials.IRON_DUST.get()),
                            out(IntermediateItems.STEEL_INGOT.get(), 8), 11, 500),
                    // 批量炼钢（石油焦）：铁锭或铁粉 8 + 石油焦 3 → 钢锭 8；等压 12
                    tagged(items(PetroMaterials.PETROLEUM_COKE.get(), 3),
                            tags(STEEL_FEED, 8, OreMaterials.IRON_DUST.get()),
                            out(IntermediateItems.STEEL_INGOT.get(), 8), 12, 450),

                    // ---------------- ①b 钨钢升级：钢装备 + 钨铜硬质合金锭（任意耐久）
                    // 消耗量 = 原钢系列配方里的钢锭数 × 2；输出全新满耐久钨钢装备
                    upgrade(SteelEquipment.STEEL_PICKAXE.get(), 6,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_PICKAXE.get(), 400),
                    upgrade(SteelEquipment.STEEL_AXE.get(), 6,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_AXE.get(), 400),
                    upgrade(SteelEquipment.STEEL_SHOVEL.get(), 2,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_SHOVEL.get(), 400),
                    upgrade(SteelEquipment.STEEL_HOE.get(), 4,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_HOE.get(), 400),
                    upgrade(SteelEquipment.STEEL_SWORD.get(), 4,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_SWORD.get(), 400),
                    upgrade(SteelEquipment.STEEL_HELMET.get(), 10,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_HELMET.get(), 600),
                    upgrade(SteelEquipment.STEEL_CHESTPLATE.get(), 16,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_CHESTPLATE.get(), 600),
                    upgrade(SteelEquipment.STEEL_LEGGINGS.get(), 14,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_LEGGINGS.get(), 600),
                    upgrade(SteelEquipment.STEEL_BOOTS.get(), 8,
                            TungstenSteelEquipment.TUNGSTEN_STEEL_BOOTS.get(), 600),

                    // ---------------- ② 结构机械件（轴承 / 机械框架 / 精密重型框架均已改工作台配方）

                    // ---------------- ③ 电气精密件（电阻 / 电容 / 传感器 / 伺服 / 温控器已改工作台；这里只留控制模块）
                    // 石化 3 去向：手性聚合物做精密伺服的低摩擦件（比工作台伺服省轴承 / 铜齿轮，产量翻倍）
                    recipe(items(PetroMaterials.CHIRAL_POLYMER.get(), 1,
                                    IntermediateItems.BEARING.get(), 1,
                                    MaterialItems.GOLD_WIRE.get(), 2),
                            out(IntermediateItems.SERVO.get(), 2), 400),
                    // 接枝塑料同样可做伺服（自润滑，排在后面：两种都在时优先手性聚合物）
                    recipe(items(PetroMaterials.GRAFTED_PLASTIC.get(), 1,
                                    IntermediateItems.BEARING.get(), 1,
                                    MaterialItems.GOLD_WIRE.get(), 2),
                            out(IntermediateItems.SERVO.get(), 2), 450),

                    // ---------------- ③b 重工业组件（芯片；马达 / 电路板 / 强化流体单元已改工作台）
                    // 芯片吃「一级塑料」标签（生物塑料颗粒 / 电木任选）；下界合金只留给芯片与精密重型框架。
                    tagged(items(ComponentItems.CIRCUIT_BOARD.get(), 1, MaterialItems.GOLD_WIRE.get(), 4,
                                    TungstenMaterials.TUNGSTEN_WIRE.get(), 2,
                                    MaterialItems.NETHERITE_PLATE.get(), 1,
                                    Items.QUARTZ, 4, Items.REDSTONE, 4),
                            tags(PlasticTags.T1, 1, BioMaterials.BIOPLASTIC_PELLETS.get()),
                            out(ComponentItems.CHIP.get(), 1), 300),

                    // ---------------- ③c 高温玻璃 / 裂变专属组件（只用于裂变机器）
                    recipe(items(Items.QUARTZ, 4, Items.GLASS, 1),
                            out(MaterialItems.QUARTZ_GLASS.get(), 1), 400),
                    recipe(items(MaterialItems.QUARTZ_GLASS.get(), 2),
                            out(FissionParts.TEMPERED_GLASS.get(), 2), 1, 300),
                    // 耐蚀内衬 / 离心机转子 / 装料头已改工作台配方（2026-11-13）
                    // 辐射屏蔽（2026-11-14 快速配方书）：T3 塑料标签 + 坚固钨 / 铅焊接板 + 钢化玻璃 + 不锈钢板
                    tagged(items(WeldingMaterials.RUGGED_TUNGSTEN_WELDING_PLATE.get(), 6,
                                    WeldingMaterials.RUGGED_LEAD_WELDING_PLATE.get(), 12,
                                    FissionParts.TEMPERED_GLASS.get(), 2,
                                    AlloyMaterials.STAINLESS_STEEL.plate.get(), 2),
                            tags(PlasticTags.T3, 6, PetroMaterials.NBR.get()),
                            out(FissionParts.RADIATION_SHIELDING.get(), 1), 500),
                    // 控制棒驱动：碳化硼棒 + 伺服 + 控制模块 + 高速钢-W 齿轮（耐磨传动）+ 铅板
                    recipe(items(FissionMaterials.BORON_CARBIDE_ROD.get(), 2, IntermediateItems.SERVO.get(), 2,
                                    IntermediateItems.CONTROL_MODULE.get(), 1,
                                    AlloyMaterials.HIGH_SPEED_STEEL.gear.get(), 2,
                                    LeadMaterials.LEAD_PLATE.get(), 1),
                            out(FissionParts.CONTROL_ROD_DRIVE.get(), 1), 800),
                    // 汽轮机叶片：硅橡胶（耐高温密封，走 T2 标签）+ 镍基高温合金板 + 稀土合金杆 + 钨铜硬质合金齿轮
                    // （2026-11-10 快速配方书版本）
                    tagged(items(AlloyMaterials.NICKEL_SUPERALLOY.plate.get(), 8,
                                    SpaceMaterials.RARE_EARTH_ALLOY_ROD.get(), 4,
                                    AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.gear.get(), 2),
                            tags(PlasticTags.T2, 4, PetroMaterials.SILICONE_RUBBER.get()),
                            out(FissionParts.TURBINE_BLADE.get(), 1), 900),
                    // 蒸汽阀组：耐高温密封 + 耐油密封 + 流体单元 + 不锈钢板（阀体）+ 控制模块 + 钢化玻璃
                    tagged(items(ComponentItems.FLUID_CELL.get(), 2,
                                    AlloyMaterials.STAINLESS_STEEL.plate.get(), 2,
                                    IntermediateItems.CONTROL_MODULE.get(), 1, FissionParts.TEMPERED_GLASS.get(), 2),
                            tags(PlasticTags.T2, 4, PetroMaterials.SILICONE_RUBBER.get(),
                                    PlasticTags.T3, 2, PetroMaterials.NBR.get()),
                            out(FissionParts.STEAM_VALVE_MANIFOLD.get(), 1), 850),

                    // ---------------- ④ 生化线 6 台机器（递进式：越往后越贵；2026-11-06 入口改为铜框架 + 钢板 + 基础件）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1, IntermediateItems.STEEL_PLATE.get(), 4,
                                    ComponentItems.BASIC_FLUID_CELL.get(), 2, ComponentItems.ELECTRIC_MOTOR.get(), 1),
                            out(BioMachineBlocks.SLURRY_MIXER_ITEM.get(), 1), 400),
                    // 生化第一台「能出燃料」的机器：铜框架 + 钨板 + 双线圈（2026-11-08 快速配方书版本）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1, TungstenMaterials.TUNGSTEN_PLATE.get(), 8,
                                    ComponentItems.HEATING_COIL.get(), 4, ComponentItems.FLUX_COIL.get(), 4),
                            out(BioMachineBlocks.FERMENTER_ITEM.get(), 1), 450),
                    // 灭菌罐：铜框架 + 铜板 + 加热线圈 + 石英玻璃（2026-11-08 快速配方书版本）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1, MaterialItems.COPPER_PLATE.get(), 12,
                                    ComponentItems.HEATING_COIL.get(), 4, MaterialItems.QUARTZ_GLASS.get(), 2),
                            out(BioMachineBlocks.STERILIZER_ITEM.get(), 1), 550),
                    // 分解机：铜框架 + 铜杆 + 高频线圈 + 伺服
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1, MaterialItems.COPPER_ROD.get(), 16,
                                    ComponentItems.HIGH_FREQUENCY_COIL.get(), 4, IntermediateItems.SERVO.get(), 2),
                            out(BioMachineBlocks.DECOMPOSER_ITEM.get(), 1), 600),
                    // 结晶器：铜框架 + 铜杆 + 轴承 + 加热线圈
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1, MaterialItems.COPPER_ROD.get(), 12,
                                    IntermediateItems.BEARING.get(), 4, ComponentItems.HEATING_COIL.get(), 4),
                            out(BioMachineBlocks.CRYSTALLIZER_ITEM.get(), 1), 650),
                    // 生物反应釜：铜框架 + 坚固钨焊接板 + 钨铜硬质合金杆 + 控制模块
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    WeldingMaterials.RUGGED_TUNGSTEN_WELDING_PLATE.get(), 4,
                                    AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.rod.get(), 12,
                                    IntermediateItems.CONTROL_MODULE.get(), 4),
                            out(BioMachineBlocks.REACTOR_ITEM.get(), 1), 800),

                    // ---------------- ④b 石化线 6 台机器（入口便宜、后续高级件；2026-10-25 组件化）
                    // 石化入口：铜框架 + 机械管 + 基础流体单元 + 温控（2026-11-08 快速配方书版本）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    PipeBlocks.MACHINE_PIPE_ITEM.get(), 12,
                                    ComponentItems.BASIC_FLUID_CELL.get(), 8,
                                    IntermediateItems.THERMOSTAT.get(), 6),
                            out(PetroMachineBlocks.DISTILLATION_TOWER_ITEM.get(), 1), 600),
                    // 裂解炉：铜框架 + 伺服 ×6 + 稀土合金锭 ×4 + 控制模块 ×2（2026-11-08 快速配方书版本）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    IntermediateItems.SERVO.get(), 6,
                                    SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get(), 4,
                                    IntermediateItems.CONTROL_MODULE.get(), 2),
                            out(PetroMachineBlocks.CRACKING_FURNACE_ITEM.get(), 1), 900),
                    // 重整炉：铜框架 + 高速钢板 ×8 + 稀土合金锭 ×6 + 控制模块 ×2 + 电路板 ×1（2026-11-08 快速配方书版本）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    AlloyMaterials.HIGH_SPEED_STEEL.plate.get(), 8,
                                    SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get(), 6,
                                    IntermediateItems.CONTROL_MODULE.get(), 2,
                                    ComponentItems.CIRCUIT_BOARD.get(), 1),
                            out(PetroMachineBlocks.REFORMER_ITEM.get(), 1), 850),
                    // 聚合釜：铜框架 + 基础流体单元 ×8 + 活塞组件 ×4 + 稀土合金板 ×6（2026-11-08 快速配方书版本）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    ComponentItems.BASIC_FLUID_CELL.get(), 8,
                                    ComponentItems.PISTON_ASSEMBLY.get(), 4,
                                    SpaceMaterials.RARE_EARTH_ALLOY_PLATE.get(), 6),
                            out(PetroMachineBlocks.POLYMERIZER_ITEM.get(), 1), 1000),

                    // ---------------- ④b' 煤化工线 2 台机器（贵；此前遗漏，补上生存获取）
                    // 煤气化炉：铜框架 + 不锈钢板 + 硅橡胶（T2 标签）+ 电路板 + 伺服（2026-11-10 快速配方书版本）
                    // 与等压 0 的「石墨棒（石油焦 ×4）」「灭菌罐」「氟化炉」互为子集 → 单独放等压 2，避免抢料
                    tagged(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    AlloyMaterials.STAINLESS_STEEL.plate.get(), 8,
                                    ComponentItems.CIRCUIT_BOARD.get(), 2,
                                    IntermediateItems.SERVO.get(), 4),
                            tags(PlasticTags.T2, 4, PetroMaterials.SILICONE_RUBBER.get()),
                            out(PetroMachineBlocks.GASIFIER_ITEM.get(), 1), 2, 1100),
                    // 合成塔：铜框架 + 坚固不锈钢焊接板 + 硅橡胶（T2 标签）+ 电路板 + 高频线圈（2026-11-10 快速配方书版本）
                    tagged(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    WeldingMaterials.RUGGED_STAINLESS_STEEL_WELDING_PLATE.get(), 4,
                                    ComponentItems.CIRCUIT_BOARD.get(), 6,
                                    ComponentItems.HIGH_FREQUENCY_COIL.get(), 8),
                            tags(PlasticTags.T2, 4, PetroMaterials.SILICONE_RUBBER.get()),
                            out(PetroMachineBlocks.SYNTHESIS_TOWER_ITEM.get(), 1), 2, 1200),

                    // ---------------- ④c 机械土壤（热室；铜框架 + 生物线 / 石化线）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 2, BioMaterials.BIOMASS.get(), 4,
                                    BioMaterials.ORGANIC_FERTILIZER.get(), 2, Items.DIRT, 1),
                            out(FarmlandBlocks.MECHANICAL_FARMLAND_ITEM.get(), 1), 400),
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 2, BioMaterials.BIOMASS.get(), 4,
                                    BioMaterials.ORGANIC_FERTILIZER.get(), 2, Items.SAND, 1),
                            out(FarmlandBlocks.MECHANICAL_SAND_ITEM.get(), 1), 400),
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 2, BioMaterials.BIOMASS.get(), 4,
                                    BioMaterials.ORGANIC_FERTILIZER.get(), 2, Items.SOUL_SAND, 1),
                            out(FarmlandBlocks.MECHANICAL_SOUL_SAND_ITEM.get(), 1), 400),
                    tagged(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 2,
                                    BioMaterials.BIOPLASTIC_PELLETS.get(), 2, Items.GRASS_BLOCK, 1),
                            tags(PlasticTags.T3, 4, PetroMaterials.SBR.get()),
                            out(FarmlandBlocks.MECHANICAL_GRASS_ITEM.get(), 1), 600),

                    // ---------------- ④d 燃烧引擎（热室；不吃自身，避免死循环）
                    // 铜框架 + 活塞组件 ×6 + 电容 ×8 + 轴承
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    ComponentItems.PISTON_ASSEMBLY.get(), 6,
                                    IntermediateItems.CAPACITOR.get(), 8,
                                    IntermediateItems.BEARING.get(), 1),
                            out(EngineBlocks.BURNING_ENGINE_ITEM.get(), 1), 800),
                    // 工业内燃机组：铜框架 + 坚固钨焊接板 + 坚固高速钢焊接板 + 伺服 + 活塞组件 + 流体单元 + 电路板 + 稀土合金板
                    // （2026-11-10 快速配方书版本）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    WeldingMaterials.RUGGED_TUNGSTEN_WELDING_PLATE.get(), 8,
                                    WeldingMaterials.RUGGED_HIGH_SPEED_STEEL_WELDING_PLATE.get(), 6,
                                    IntermediateItems.SERVO.get(), 10,
                                    ComponentItems.PISTON_ASSEMBLY.get(), 8,
                                    ComponentItems.FLUID_CELL.get(), 6,
                                    ComponentItems.CIRCUIT_BOARD.get(), 4,
                                    SpaceMaterials.RARE_EARTH_ALLOY_PLATE.get(), 6),
                            out(EngineMachines.INDUSTRIAL_ENGINE_ITEM.get(), 1), 1200),
                    // 联合燃气机组：铜框架 + 稀土合金锭 + 汽轮机叶片 + 轴承 + 流体单元 + 控制模块 + 高频线圈 + 高频磁通线圈
                    // （2026-11-10 快速配方书版本）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get(), 4,
                                    FissionParts.TURBINE_BLADE.get(), 2,
                                    IntermediateItems.BEARING.get(), 12,
                                    ComponentItems.FLUID_CELL.get(), 4,
                                    IntermediateItems.CONTROL_MODULE.get(), 2,
                                    ComponentItems.HIGH_FREQUENCY_COIL.get(), 6,
                                    ComponentItems.FLUX_COIL.get(), 6),
                            out(EngineMachines.GAS_TURBINE_ITEM.get(), 1), 1600),

                    // ---------------- ④e 开采钻：钻头走热室；整机走工作台配方（2026-11-07）
                    // 钻头：钨铜硬质合金板 ×4 + 钨铜硬质合金杆 ×6 + 钢齿轮 ×4
                    recipe(items(AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.plate.get(), 4,
                                    AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.rod.get(), 6,
                                    IntermediateItems.STEEL_GEAR.get(), 4),
                            out(DrillBlocks.DRILL_BIT.get(), 1), 350),

                    // ---------------- ⑤ 核工业中间件（裂变线专用）
                    // 锆合金粉：核级锆粉 + 锡 / 铁 / 铬（B2）
                    recipe(items(ElementDusts.ZIRCONIUM_DUST.get(), 4,
                                    MonaziteMaterials.TIN_DUST.get(), 1,
                                    OreMaterials.IRON_DUST.get(), 1,
                                    ElementDusts.CHROMIUM_DUST.get(), 1),
                            out(MonaziteMaterials.ZIRCALOY_POWDER.get(), 7), 400),
                    // 锆合金锭 / 管（B2；替换旧的「钢棒 + 石英 + 玻璃」占位配方）
                    recipe(items(MonaziteMaterials.ZIRCALOY_POWDER.get(), 1),
                            out(MonaziteMaterials.ZIRCALOY_INGOT.get(), 1), 300),
                    recipe(items(MonaziteMaterials.ZIRCALOY_INGOT.get(), 1),
                            out(FissionMaterials.ZIRCALOY_TUBE.get(), 1), 350),
                    // 碳化硼控制棒：硼粉 + 石油焦 + 钢棒烧结（B1 起必须吃真实硼粉）
                    recipe(items(IntermediateItems.STEEL_ROD.get(), 2, SaltMaterials.BORON_DUST.get(), 2,
                                    PetroMaterials.PETROLEUM_COKE.get(), 1,
                                    IntermediateItems.STEEL_PLATE.get(), 1),
                            out(FissionMaterials.BORON_CARBIDE_ROD.get(), 1), 300),
                    // 石墨棒：石油焦石墨化（裂变堆里吸收中子通量，会磨耐久）
                    recipe(items(PetroMaterials.PETROLEUM_COKE.get(), 4),
                            out(FissionMaterials.GRAPHITE_ROD.get(), 1), 400),
                    // 石墨棒（B0）：钻石渣二段浸出的石墨粉压制，给石墨粉一个明确 sink
                    recipe(items(ElementDusts.GRAPHITE_DUST.get(), 4),
                            out(FissionMaterials.GRAPHITE_ROD.get(), 1), 400),

                    // ---------------- ⑥ 裂变线机器（专属高级组件 + 精密重型框架；2026-10-25 组件化）
                    // 顺序说明（2026-11-01）：化学处理槽 / 离心机是**元素来源**（Cr/Ni/Co/Mo/Mn/Be/Zr/B/Na/Nb），
                    // 因此这两台**不吃合金**，否则会形成「要合金先要机器、要机器先要合金」的死循环。
                    // 2026-11-11：原氟化炉已并入化学处理槽（ore_leacher），不再单独有机器配方。
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1,
                                    FissionParts.CORROSION_LINER.get(), 1,
                                    WeldingMaterials.RUGGED_LEAD_WELDING_PLATE.get(), 16,
                                    ComponentItems.ELECTRIC_MOTOR.get(), 6),
                            out(FissionMachines.ORE_LEACHER_ITEM.get(), 1), 900),
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1,
                                    FissionParts.CENTRIFUGE_ROTOR.get(), 1,
                                    SpaceMaterials.RARE_EARTH_ALLOY_ROD.get(), 12,
                                    IntermediateItems.SERVO.get(), 8),
                            out(FissionMachines.CENTRIFUGE_ITEM.get(), 1), 1100),
                    // ↓ 从罐装机开始可以吃合金（不再是元素来源，所以不会成环）
                    // 罐装机（T1，1 种合金）：不锈钢 板 + 杆（管路 / 内衬）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1,
                                    FissionParts.FUEL_LOADING_HEAD.get(), 1,
                                    AlloyMaterials.STAINLESS_STEEL.plate.get(), 8,
                                    IntermediateItems.CONTROL_MODULE.get(), 8),
                            out(FissionMachines.CANNING_MACHINE_ITEM.get(), 1), 900),
                    // 后处理厂（2026-11-15 快速配方书；同日平衡：内衬 4→2、伺服 8→4）
                    // 等压 17：输入包含离心机的全部种类，会遮蔽离心机 → 单列一组
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1,
                                    FissionParts.RADIATION_SHIELDING.get(), 1,
                                    FissionParts.CENTRIFUGE_ROTOR.get(), 1,
                                    FissionParts.CORROSION_LINER.get(), 2,
                                    IntermediateItems.SERVO.get(), 4,
                                    SpaceMaterials.RARE_EARTH_ALLOY_ROD.get(), 12,
                                    WeldingMaterials.RUGGED_STAINLESS_STEEL_WELDING_PLATE.get(), 6),
                            out(FissionMachines.REPROCESSOR_ITEM.get(), 1), 17, 1400),
                    // 裂变堆（2026-11-15 快速配方书；同日平衡：屏蔽 4→3、控制棒驱动 4→3）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1,
                                    FissionParts.RADIATION_SHIELDING.get(), 3,
                                    FissionParts.CONTROL_ROD_DRIVE.get(), 3,
                                    FissionParts.REFLECTOR_MODULE.get(), 4,
                                    IntermediateItems.CONTROL_MODULE.get(), 4,
                                    AlloyMaterials.NICKEL_SUPERALLOY.plate.get(), 12,
                                    SpaceMaterials.RARE_EARTH_ALLOY_PLATE.get(), 12),
                            out(FissionReactors.FISSION_REACTOR_ITEM.get(), 1), 1800),
                    // 蒸汽涡轮机组（T3，3 种合金）：钨铜板（阀座）+ 钛钼铬杆（转子轴）+ 镍基高温板（缸体）
                    recipe(items(FrameBlocks.COPPER_FRAME_ITEM.get(), 1,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1,
                                    FissionParts.TURBINE_BLADE.get(), 2,
                                    FissionParts.STEAM_VALVE_MANIFOLD.get(), 1,
                                    WeldingMaterials.RUGGED_STAINLESS_STEEL_WELDING_PLATE.get(), 4,
                                    AlloyMaterials.NICKEL_SUPERALLOY.gear.get(), 4,
                                    ComponentItems.FLUX_COIL.get(), 8,
                                    ComponentItems.HIGH_FREQUENCY_COIL.get(), 6),
                            out(FissionSteam.STEAM_TURBINE_ITEM.get(), 1), 1600),

                    // ---------------- ⑥b 高能物理线第一期：同位素产品 + 回旋加速器
                    // （一期只到下面的 CYCLOTRON_ITEM；再往下的粒子源 / 辐照仓 / 对撞机 / RTG 属第二期）
                    // 稀土磁体：只保留钕粉路线；混合稀土只做催化剂 / 钐-149
                    // （2026-11-03 删除 rare_earth_mix 磁体配方，避免同产物双路线）
                    // 铂族催化剂：铂族金属 + 单个稀土助剂（钕 / 钐 / 钆 / 铈），载体用不锈钢板（耐蚀）
                    recipe(items(FissionMaterials.PLATINUM_GROUP.get(), 2,
                                    MonaziteMaterials.NEODYMIUM_DUST.get(), 1,
                                    MonaziteMaterials.SAMARIUM_DUST.get(), 1,
                                    MonaziteMaterials.GADOLINIUM_DUST.get(), 1,
                                    MonaziteMaterials.CERIUM_DUST.get(), 1,
                                    AlloyMaterials.STAINLESS_STEEL.plate.get(), 1),
                            out(FissionMaterials.PLATINUM_CATALYST.get(), 1), 450),
                    // 等压 15：与石英玻璃同为 2 种料（共用玻璃），件数更少会被抢 → 单列一组
                    recipe(items(FissionMaterials.NUCLEAR_WASTE.get(), 1, Items.GLASS, 2),
                            out(FissionMaterials.HLW_GLASS.get(), 1), 15, 400),
                    recipe(items(FissionMaterials.HLW_GLASS.get(), 4),
                            out(FissionBlocks.HLW_GLASS_BLOCK_ITEM.get(), 1), 400),
                    // 回旋加速器（T3，3 种合金）：不锈钢板（真空室）+ 镍基高温杆（磁极支撑）+ 锆基非晶线（高频线圈）
                    recipe(items(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 2,
                                    FissionMaterials.RARE_EARTH_MAGNET.get(), 6,
                                    FissionParts.RADIATION_SHIELDING.get(), 2,
                                    ComponentItems.CHIP.get(), 2,
                                    TungstenMaterials.TUNGSTEN_PLATE.get(), 4,
                                    MaterialItems.COPPER_WIRE.get(), 8,
                                    AlloyMaterials.STAINLESS_STEEL.plate.get(), 4,
                                    AlloyMaterials.NICKEL_SUPERALLOY.rod.get(), 4,
                                    AlloyMaterials.TITANIUM_ALLOY.wire.get(), 4),
                            out(FissionMachines.CYCLOTRON_ITEM.get(), 1), 1100),
                    // 粒子源（T4，4 种合金）：镍基高温杆（电极）+ 不锈钢线（母线）+ 钛钼铬板（靶室）+ 锆基非晶线（绝缘线圈）
                    recipe(items(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 2,
                                    FissionMaterials.RARE_EARTH_MAGNET.get(), 6,
                                    FissionParts.RADIATION_SHIELDING.get(), 2,
                                    ComponentItems.CHIP.get(), 2,
                                    AlloyMaterials.NICKEL_SUPERALLOY.rod.get(), 8,
                                    FissionMaterials.TECHNETIUM_STEEL.get(), 2,
                                    AlloyMaterials.STAINLESS_STEEL.wire.get(), 6,
                                    AlloyMaterials.TITANIUM_ALLOY.plate.get(), 6,
                                    AlloyMaterials.TITANIUM_ALLOY.wire.get(), 6),
                            out(FissionMachines.PARTICLE_SOURCE_ITEM.get(), 1), 1, 1100),
                    // 束流增压模块（D2）：放进粒子源输入箱即可 +50% 束流（能耗同步 +50%）
                    recipe(items(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1,
                                    FissionMaterials.RARE_EARTH_MAGNET.get(), 2,
                                    ComponentItems.CHIP.get(), 1,
                                    TungstenMaterials.TUNGSTEN_PLATE.get(), 2,
                                    MaterialItems.GOLD_WIRE.get(), 4),
                            out(dev.modmind.stellar_evolution.particle.ParticleItems.BOOSTER_MODULE.get(), 1),
                            4, 800),
                    // 辐照仓（pressure 3，T4，4 种合金）：钛钼铬板（靶室壁）+ 镍基高温线（加热丝）+ 不锈钢杆（管路）+ 钨铜板（靶座）
                    recipe(items(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 3,
                                    FissionMaterials.RARE_EARTH_MAGNET.get(), 6,
                                    FissionParts.RADIATION_SHIELDING.get(), 4,
                                    ComponentItems.CHIP.get(), 4,
                                    AlloyMaterials.TITANIUM_ALLOY.plate.get(), 10,
                                    AlloyMaterials.NICKEL_SUPERALLOY.wire.get(), 4,
                                    AlloyMaterials.STAINLESS_STEEL.rod.get(), 6,
                                    AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.plate.get(), 6,
                                    FissionMaterials.TECHNETIUM_STEEL.get(), 2),
                            out(FissionMachines.IRRADIATION_CHAMBER_ITEM.get(), 1), 3, 1600),
                    // 对撞机（pressure 5，T5，5 种合金）：镍基高温杆（支撑）+ 钛钼铬板（真空管）+ 不锈钢线（母线）+ 钨铜板 + 高速钢-W 齿轮（准直传动）
                    recipe(items(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 3,
                                    FissionMaterials.RARE_EARTH_MAGNET.get(), 10,
                                    ComponentItems.CHIP.get(), 4,
                                    AlloyMaterials.NICKEL_SUPERALLOY.rod.get(), 12,
                                    AlloyMaterials.TITANIUM_ALLOY.plate.get(), 4,
                                    FissionMaterials.HARDENED_ALLOY.get(), 6,
                                    AlloyMaterials.STAINLESS_STEEL.wire.get(), 10,
                                    AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.plate.get(), 10,
                                    AlloyMaterials.HIGH_SPEED_STEEL.gear.get(), 8),
                            out(FissionMachines.COLLIDER_ITEM.get(), 1), 5, 2000),
                    // ADS（pressure 6，T6，5 种合金）：镍基高温杆 + 钛钼铬杆 + 不锈钢线 + 钨铜板 + 锆基非晶板
                    recipe(items(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 6,
                                    FissionMaterials.RARE_EARTH_MAGNET.get(), 10,
                                    ComponentItems.CHIP.get(), 6,
                                    FissionMaterials.ZR_90.get(), 6,
                                    AlloyMaterials.NICKEL_SUPERALLOY.rod.get(), 12,
                                    AlloyMaterials.TITANIUM_ALLOY.rod.get(), 8,
                                    AlloyMaterials.STAINLESS_STEEL.wire.get(), 10,
                                    AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.plate.get(), 10,
                                    AlloyMaterials.TITANIUM_ALLOY.plate.get(), 8),
                            out(FissionMachines.ADS_ITEM.get(), 1), 6, 2300),
                    // 加速环段（pressure 5，T5，5 种合金）：不锈钢杆 + 镍基高温线 + 钨铜板 + 高速钢-W 齿轮 + 锆基非晶线
                    recipe(items(FissionMaterials.LIVERMORIUM_300.get(), 1,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1,
                                    FissionMaterials.RARE_EARTH_MAGNET.get(), 3,
                                    AlloyMaterials.STAINLESS_STEEL.rod.get(), 4,
                                    AlloyMaterials.NICKEL_SUPERALLOY.wire.get(), 4,
                                    AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.plate.get(), 4,
                                    AlloyMaterials.HIGH_SPEED_STEEL.gear.get(), 4,
                                    AlloyMaterials.TITANIUM_ALLOY.wire.get(), 4,
                                    MaterialItems.COPPER_WIRE.get(), 8),
                            out(FissionMachines.ACCELERATOR_RING_ITEM.get(), 1), 5, 800),
                    // 湮灭反应堆（pressure 7，T7，全部 7 种合金）：齿轮 / 杆 / 线 / 板混用，机体由合金本体堆出来
                    recipe(items(FissionMaterials.RARE_EARTH_MAGNET.get(), 12,
                                    ComponentItems.CHIP.get(), 8,
                                    AlloyMaterials.STAINLESS_STEEL.gear.get(), 10,
                                    AlloyMaterials.HIGH_SPEED_STEEL.gear.get(), 10,
                                    AlloyMaterials.HIGH_SPEED_STEEL.rod.get(), 10,
                                    AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.plate.get(), 12,
                                    AlloyMaterials.NICKEL_SUPERALLOY.wire.get(), 12,
                                    AlloyMaterials.TITANIUM_ALLOY.rod.get(), 12,
                                    AlloyMaterials.TITANIUM_ALLOY.plate.get(), 12),
                            out(FissionMachines.ANNIHILATION_REACTOR_ITEM.get(), 1), 7, 3000),
                    // 同位素电源（RTG）：锶-90 → 长期低功率热压差
                    recipe(items(FissionMaterials.STRONTIUM_90.get(), 1,
                                    LeadMaterials.LEAD_PLATE.get(), 2,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1),
                            out(ParticleBlocks.ISOTOPE_GENERATOR_ITEM.get(), 1), 400),
                    // 钚-238 → 同位素电源（更经典的同位素电源燃料，与锶-90 二选一）
                    recipe(items(FissionMaterials.PLUTONIUM_238.get(), 1,
                                    LeadMaterials.LEAD_PLATE.get(), 2,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 1),
                            out(ParticleBlocks.ISOTOPE_GENERATOR_ITEM.get(), 1), 400),
                    // 锔-244 → 高功率同位素电源
                    recipe(items(FissionMaterials.CURIUM_244.get(), 1,
                                    LeadMaterials.LEAD_PLATE.get(), 4,
                                    IntermediateItems.PRECISION_HEAVY_FRAME.get(), 2,
                                    FissionMaterials.TECHNETIUM_STEEL.get(), 2,
                                    FissionMaterials.HARDENED_ALLOY.get(), 2),
                            out(ParticleBlocks.HIGH_POWER_ISOTOPE_GENERATOR_ITEM.get(), 1), 600),
                    // 锝-99 的硬用途：与钢板热压成锝钢锭（耐蚀高强合金，粒子源整机 / 高功率同位素电源的必需结构件）
                    // 等压 11：2 种料，和机械框架 / 高负荷机壳共用钢板 → 单列一组
                    recipe(items(FissionMaterials.TECHNETIUM_99.get(), 1,
                                    IntermediateItems.STEEL_PLATE.get(), 2),
                            out(FissionMaterials.TECHNETIUM_STEEL.get(), 2), 11, 500),
                    // 嬗变产物去向：锆-90 → 锆合金管；钕-143 → 稀土磁体；钼-95 → 铂族催化剂替代路线
                    // 等压 12：与「锆合金锭 → 管」共用产物，2 种料会被机械框架等抢料 → 单列一组
                    recipe(items(FissionMaterials.ZR_90.get(), 2, IntermediateItems.STEEL_PLATE.get(), 1),
                            out(FissionMaterials.ZIRCALOY_TUBE.get(), 1), 12, 400),
                    // 锆-90 → 辐射屏蔽件（嬗变产物的常规去向，与钨板路线二选一）
                    // 等压 13：2 种料但件数少，会被机械框架 / 坚固支撑机壳抢料 → 单列一组
                    recipe(items(FissionMaterials.ZR_90.get(), 2, LeadMaterials.LEAD_PLATE.get(), 2),
                            out(FissionParts.RADIATION_SHIELDING.get(), 1), 13, 400),
                    // 超重元素 → 超重反射棒（终局反应堆增益件；每根按 2 根反射棒计，耐久 4,000）
                    recipe(items(FissionMaterials.SUPERHEAVY_ELEMENT.get(), 2,
                                    FissionMaterials.REFLECTOR_ROD.get(), 1,
                                    FissionMaterials.HARDENED_ALLOY.get(), 1),
                            out(FissionMaterials.SUPERHEAVY_REFLECTOR_ROD.get(), 1), 800),
                    // 奇异物质 → 奇异反射棒（超重反射棒的升级；每根按 4 根反射棒计，耐久 8,000）
                    recipe(items(FissionMaterials.EXOTIC_MATTER.get(), 1,
                                    FissionMaterials.SUPERHEAVY_REFLECTOR_ROD.get(), 1),
                            out(FissionMaterials.EXOTIC_REFLECTOR_ROD.get(), 1), 6, 1_200),
                    recipe(items(FissionMaterials.ND_143.get(), 4, OreMaterials.IRON_DUST.get(), 4,
                                    SaltMaterials.BORON_DUST.get(), 1, MaterialItems.COPPER_WIRE.get(), 4),
                            out(FissionMaterials.RARE_EARTH_MAGNET.get(), 1), 450),
                    recipe(items(FissionMaterials.MO_95.get(), 4,
                                    AlloyMaterials.STAINLESS_STEEL.plate.get(), 1),
                            out(FissionMaterials.PLATINUM_CATALYST.get(), 1), 450),

                    // ---------------- ⑦ 选冶线（G 线）：宝石粉 / 精砂 → 宝石；煤 / 红石精砂 → 原版物品
                    recipe(items(OreMaterials.DIAMOND_DUST.get(), 1), out(Items.DIAMOND, 1), 200),
                    recipe(items(OreMaterials.EMERALD_DUST.get(), 1), out(Items.EMERALD, 1), 200),
                    recipe(items(OreMaterials.LAPIS_DUST.get(), 1), out(Items.LAPIS_LAZULI, 1), 200),
                    recipe(items(OreMaterials.QUARTZ_DUST.get(), 1), out(Items.QUARTZ, 1), 200),
                    recipe(items(OreMaterials.DIAMOND_CONCENTRATE_SAND.get(), 1), out(Items.DIAMOND, 1), 200),
                    recipe(items(OreMaterials.EMERALD_CONCENTRATE_SAND.get(), 1), out(Items.EMERALD, 1), 200),
                    recipe(items(OreMaterials.LAPIS_CONCENTRATE_SAND.get(), 1), out(Items.LAPIS_LAZULI, 1), 200),
                    recipe(items(OreMaterials.QUARTZ_CONCENTRATE_SAND.get(), 1), out(Items.QUARTZ, 1), 200),
                    recipe(items(OreMaterials.COAL_CONCENTRATE_SAND.get(), 1), out(Items.COAL, 1), 200),
                    recipe(items(OreMaterials.REDSTONE_CONCENTRATE_SAND.get(), 1), out(Items.REDSTONE, 1), 200),

                    // ---------------- ⑦b 独居石 / 铝土矿去向（B2 / B3）
                    // 钕粉 → 稀土磁体（与裂变稀土 / 钕-143 路线并行）
                    recipe(items(MonaziteMaterials.NEODYMIUM_DUST.get(), 4, OreMaterials.IRON_DUST.get(), 4,
                                    SaltMaterials.BORON_DUST.get(), 1, MaterialItems.COPPER_WIRE.get(), 4),
                            out(FissionMaterials.RARE_EARTH_MAGNET.get(), 1), 450),
                    // 铪粉 → 辐射屏蔽件（钆改去可燃毒物棒，2026-11-03）
                    // 等压 14：2 种料但件数少，与稳定核素路线分开，避免同组互抢
                    recipe(items(MonaziteMaterials.HAFNIUM_POWDER.get(), 2,
                                    LeadMaterials.LEAD_PLATE.get(), 2),
                            out(FissionParts.RADIATION_SHIELDING.get(), 1), 14, 400),
                    // 铈粉 → 玻璃（抛光 / 玻璃副产）
                    recipe(items(MonaziteMaterials.CERIUM_DUST.get(), 1, Items.QUARTZ, 4),
                            out(Items.GLASS, 4), 200),
                    // 磷酸盐 → 骨粉（生化营养）
                    recipe(items(MonaziteMaterials.PHOSPHATE.get(), 1),
                            out(Items.BONE_MEAL, 3), 200),
                    // 铝土矿粉不在热室处理；留 tag 只为让 JEI 不出现空分类

                    // ---------------- ⑧ 合金材料（2026-11-03 简化）：配料粉 → 合金锭，出锭数 = 投料总份数
                    // 高速钢：钨 8 + 铬 4 + 钢 12 → 24 锭（2026-11-08 起去钒）
                    alloy(AlloyMaterials.HIGH_SPEED_STEEL,
                            items(TungstenMaterials.TUNGSTEN_DUST.get(), 8,
                                    ElementDusts.CHROMIUM_DUST.get(), 4,
                                    IntermediateItems.STEEL_DUST.get(), 12)),
                    // 不锈钢：铁 8 + 铬 3 + 镍 1 → 12 锭（去掉锰）
                    alloy(AlloyMaterials.STAINLESS_STEEL,
                            items(OreMaterials.IRON_DUST.get(), 8,
                                    ElementDusts.CHROMIUM_DUST.get(), 3,
                                    ElementDusts.NICKEL_DUST.get(), 1)),
                    // 钨铜硬质合金：铜 3 + 钨 14 → 17 锭（去掉钴）
                    alloy(AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY,
                            items(MaterialItems.COPPER_DUST.get(), 3,
                                    TungstenMaterials.TUNGSTEN_DUST.get(), 14)),
                    // 镍基高温合金：镍 7 + 铬 3 + 钼 2 → 12 锭（去掉铌 / 钛）
                    alloy(AlloyMaterials.NICKEL_SUPERALLOY,
                            items(ElementDusts.NICKEL_DUST.get(), 7,
                                    ElementDusts.CHROMIUM_DUST.get(), 3,
                                    ElementDusts.MOLYBDENUM_DUST.get(), 2)),
                    // 钛合金：钛 8 + 铝 4 + 钒 2 → 14 锭（替代钛钼铬）
                    alloy(AlloyMaterials.TITANIUM_ALLOY,
                            items(ElementDusts.TITANIUM_DUST.get(), 8,
                                    ElementDusts.ALUMINUM_DUST.get(), 4,
                                    TraceElementDusts.VANADIUM_DUST.get(), 2)),
                    // 铝镁合金：铝 8 + 镁 2 → 10 锭（航天轻量化）
                    alloy(AlloyMaterials.ALUMINUM_MAGNESIUM_ALLOY,
                            items(ElementDusts.ALUMINUM_DUST.get(), 8,
                                    TraceElementDusts.MAGNESIUM_DUST.get(), 2)),

                    // ---------------- ⑨ 航天材料（2026-11-03）
                    // 稀土合金锭：2026-11-08 起迁到生化反应釜（独居石 + 石脑油 + 汞，见 BioRecipes）
                    // NbTi 超导线：铌 3 + 钛 1 → 4 线
                    recipe(items(ElementDusts.NIOBIUM_DUST.get(), 3,
                                    ElementDusts.TITANIUM_DUST.get(), 1),
                            out(SpaceMaterials.NB_TI_WIRE.get(), 4), 500),
                    // 高负荷机械方块：钢板 + 钨板（等压 9，避开机械框架的同种类数抢料）
                    recipe(items(IntermediateItems.STEEL_PLATE.get(), 4,
                                    TungstenMaterials.TUNGSTEN_PLATE.get(), 1),
                            out(HatchBlocks.HIGH_LOAD_CASING_ITEM.get(), 1), 9, 500),
                    // 坚固支撑机械方块：钢板 + 铅板（等压 10，同理）
                    recipe(items(IntermediateItems.STEEL_PLATE.get(), 4,
                                    LeadMaterials.LEAD_PLATE.get(), 2),
                            out(HatchBlocks.RUGGED_SUPPORT_CASING_ITEM.get(), 1), 10, 500),
                    // 超导线圈方块：高负荷机壳 + NbTi 线 + 稀土合金板
                    recipe(items(HatchBlocks.HIGH_LOAD_CASING_ITEM.get(), 1,
                                    SpaceMaterials.NB_TI_WIRE.get(), 8,
                                    SpaceMaterials.RARE_EARTH_ALLOY_PLATE.get(), 2),
                            out(HatchBlocks.SUPERCONDUCTING_COIL_ITEM.get(), 1), 800),
                    // 机械物流仓改用工作台配方（2026-11-16）：8 钢板 + 1 机械管
                    // 焦化炉控制器（3×3×4 砖块多方块）：砖块 / 铁板 / 铜板，炉体砖块玩家自备
                    recipe(items(Blocks.BRICKS.asItem(), 8,
                                    MaterialItems.IRON_PLATE.get(), 4,
                                    MaterialItems.COPPER_PLATE.get(), 2),
                            out(CokeOvenBlocks.CONTROLLER_ITEM.get(), 1), 240),
                    // 2026-11-11 批次 1：电石 / 活性炭 / 针状焦石墨化
                    // 电石：焦炭 + 方解石（电石炉，等压 4 与活化错开）
                    recipe(items(CoalMaterials.COKE.get(), 2, Items.CALCITE, 2),
                            out(CoalMaterials.CALCIUM_CARBIDE.get(), 2), 4, 800),
                    recipe(items(CoalMaterials.COKE.get(), 1),
                            out(CoalMaterials.ACTIVATED_CARBON.get(), 1), 400),
                    // 粉料活化：煤炭粉 / 木炭粉（破碎机 → 粉 → 活性炭，1 出 1）
                    recipe(items(CoalMaterials.COAL_DUST.get(), 1),
                            out(CoalMaterials.ACTIVATED_CARBON.get(), 1), 400),
                    recipe(items(CoalMaterials.CHARCOAL_DUST.get(), 1),
                            out(CoalMaterials.ACTIVATED_CARBON.get(), 1), 400),
                    // 石墨电极：针状焦石墨化；再车成裂变堆石墨棒
                    recipe(items(CoalMaterials.NEEDLE_COKE.get(), 2),
                            out(CoalMaterials.GRAPHITE_ELECTRODE.get(), 1), 1200),
                    recipe(items(CoalMaterials.GRAPHITE_ELECTRODE.get(), 1),
                            out(FissionMaterials.GRAPHITE_ROD.get(), 2), 600),
                    // 质量投射轨道炮控制器：9 格输入
                    recipe(items(IntermediateItems.PRECISION_HEAVY_FRAME.get(), 4,
                                    SpaceMaterials.RARE_EARTH_ALLOY_INGOT.get(), 2,
                                    SpaceMaterials.RARE_EARTH_ALLOY_PLATE.get(), 2,
                                    SpaceMaterials.RARE_EARTH_ALLOY_ROD.get(), 2,
                                    SpaceMaterials.NB_TI_WIRE.get(), 8,
                                    IntermediateItems.CONTROL_MODULE.get(), 4,
                                    ComponentItems.CHIP.get(), 4,
                                    ComponentItems.FLUX_COIL.get(), 4,
                                    HatchBlocks.SUPERCONDUCTING_COIL_ITEM.get(), 2),
                            out(MassDriverBlocks.CONTROLLER_ITEM.get(), 1), 3000),
                    // §7.12 残骸线：不熄合金 / 离火棒外壳 / 星核聚变芯核
                    // 等压 16：与「锆合金锭 → 锆合金管」共用锆合金锭 → 单列一组
                    recipe(items(TungstenMaterials.TUNGSTEN_INGOT.get(), 2,
                                    MonaziteMaterials.ZIRCALOY_INGOT.get(), 1,
                                    FissionMaterials.TECHNETIUM_STEEL.get(), 1),
                            out(FissionMaterials.UNQUENCHABLE_ALLOY.get(), 2), 16, 400),
                    recipe(items(FissionMaterials.STAR_CORE_PELLET.get(), 2,
                                    FissionMaterials.UNQUENCHABLE_ALLOY.get(), 1,
                                    FissionMaterials.ZIRCALOY_TUBE.get(), 1),
                            out(FissionMaterials.EMBER_FUEL_ROD_SHELL.get(), 1), 300),
                    recipe(items(FissionMaterials.STAR_CORE_PELLET.get(), 2,
                                    MuonMaterials.DT_FUSION_CORE.get(), 1),
                            out(MuonMaterials.STAR_FUSION_CORE.get(), 1), 600));
            audit();
        }
        return cache;
    }

    /**
     * <strong>配方自检</strong>（只在表首次建立时跑一遍，稳态零开销）：
     * <ul>
     *   <li><strong>同一等压组内输入完全相同</strong> → 种类数与件数都并列，表序在前的永远赢，后面的永远做不出来；</li>
     *   <li><strong>同一等压组内严格子集</strong> → 子集配方会被超集配方抢料；应把其中一条挪到别的等压值。</li>
     * </ul>
     * 不同等压组互不干扰，不算冲突。发现即打警告，提示加配方的人改输入 / 加催化剂 / 改等压。
     */
    private static void audit() {
        for (int i = 0; i < cache.size(); i++) {
            for (int j = i + 1; j < cache.size(); j++) {
                HotChamberRecipe a = cache.get(i);
                HotChamberRecipe b = cache.get(j);
                if (a.pressure() != b.pressure()) {
                    continue;   // 不同等压组互不冲突
                }
                boolean aInB = inputsSubsetOf(a, b);
                boolean bInA = inputsSubsetOf(b, a);
                if (aInB && bInA) {
                    StellarEvolution.LOGGER.warn(
                            "Hot chamber recipes share identical inputs at pressure {}: {} and {} — the later one can never be crafted",
                            a.pressure(), outputId(a), outputId(b));
                } else if (aInB || bInA) {
                    HotChamberRecipe smaller = aInB ? a : b;
                    HotChamberRecipe larger = aInB ? b : a;
                    StellarEvolution.LOGGER.warn(
                            "Hot chamber recipe {} is a strict input subset of {} at pressure {} — the smaller one can be shadowed; assign a different pressure",
                            outputId(smaller), outputId(larger), a.pressure());
                }
            }
        }
    }

    /** {@code a} 的输入是不是全被 {@code b} 的输入覆盖（同物品、数量够）。{@code 含标签输入的配方不参与子集检查}。 */
    private static boolean inputsSubsetOf(HotChamberRecipe a, HotChamberRecipe b) {
        if (!a.tagInputs().isEmpty() || !b.tagInputs().isEmpty()) {
            return false;   // 标签与具体物品的覆盖关系依赖运行时标签，自检阶段不判定（避免误报）
        }
        for (ItemStack need : a.itemInputs()) {
            int available = 0;
            for (ItemStack other : b.itemInputs()) {
                if (sameInput(other, need)) {
                    available += other.getCount();
                }
            }
            if (available < need.getCount()) {
                return false;
            }
        }
        return true;
    }

    /** 配方的产出 id（自检日志用）。 */
    private static String outputId(HotChamberRecipe recipe) {
        return recipe.itemOutputs().isEmpty()
                ? "?"
                : BuiltInRegistries.ITEM.getKey(recipe.itemOutputs().getFirst().getItem()).toString();
    }

    /**
     * 找一条配方（驱动结算用）：在<strong>所有满足</strong>「输入够 + 产物放得下」的配方里取
     * <strong>输入种类最多</strong>的那条（种类并列取件数最多，再并列按表序）；找不到返回空。
     */
    public static Optional<HotChamberRecipe> find(Container container, int pressure) {
        return best(container, true, pressure);
    }

    /**
     * 找一条配方（<strong>只看输入</strong>、不看产物空间）——Jade 给「当前热流率」封顶用；
     * 与熔炉的 {@code cookerMaxFlowRate} 同一口径：产物满了也不该让显示的热流率无上限。
     */
    public static Optional<HotChamberRecipe> byInputs(Container container, int pressure) {
        return best(container, false, pressure);
    }

    /**
     * 命中策略实现：<strong>先按「输入种类数」、再按「输入总件数」从多到少</strong>扫一遍，
     * 取第一条满足的（完全并列时保留表序在前的那条）。
     *
     * @param needSpace 是否还要检查产物放得下（Jade 封顶用 false）
     * @param pressure  只考虑<strong>等压值相同</strong>的配方组
     */
    private static Optional<HotChamberRecipe> best(Container container, boolean needSpace, int pressure) {
        HotChamberRecipe best = null;
        int bestTypes = -1;
        int bestItems = -1;
        for (HotChamberRecipe recipe : all()) {
            if (recipe.pressure() != pressure) {
                continue;
            }
            int types = recipe.inputSlotCount();
            int items = itemCount(recipe);
            if (!moreSpecific(types, items, bestTypes, bestItems)) {
                continue;   // 不比当前最优更具体（完全并列也不换，保留表序在前的）
            }
            if (!hasInputs(container, recipe)) {
                continue;
            }
            if (needSpace && !hasOutputSpace(container, recipe)) {
                continue;
            }
            best = recipe;
            bestTypes = types;
            bestItems = items;
        }
        return Optional.ofNullable(best);
    }

    /**
     * 具体度比较：<strong>输入种类多的更具体</strong>；种类相同再比<strong>总件数</strong>（吃料多的优先）。
     * 完全并列返回 {@code false}，让表序在前的配方胜出。
     */
    private static boolean moreSpecific(int types, int items, int bestTypes, int bestItems) {
        if (types != bestTypes) {
            return types > bestTypes;
        }
        return items > bestItems;
    }

    /** 配方的输入总件数（具体度比较的第二关键字）。 */
    private static int itemCount(HotChamberRecipe recipe) {
        int total = 0;
        for (ItemStack need : recipe.itemInputs()) {
            total += need.getCount();
        }
        for (HotChamberRecipe.TagInput tag : recipe.tagInputs()) {
            total += tag.count();
        }
        return total;
    }

    /** 按配方扣掉输入（调用前须确认 {@link #hasInputs} 通过）。 */
    public static void consume(Container container, HotChamberRecipe recipe) {
        for (ItemStack need : recipe.itemInputs()) {
            int left = need.getCount();
            for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!sameInput(stack, need)) {
                    continue;
                }
                int take = Math.min(left, stack.getCount());
                container.removeItem(i, take);
                left -= take;
            }
        }
        for (HotChamberRecipe.TagInput tag : recipe.tagInputs()) {
            int left = tag.count();
            for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty() || !stack.is(tag.tag())) {
                    continue;
                }
                int take = Math.min(left, stack.getCount());
                container.removeItem(i, take);
                left -= take;
            }
        }
        container.setChanged();
    }

    /**
     * 把配方产出并回箱内（调用前须确认 {@link #hasOutputSpace} 通过）。
     * <p>钨铜升级产物不是工作台合成，不会触发 {@code onCraftedBy}，所以在这里补上
     * 「耐久 III」与护甲的「火焰保护 IV」。
     */
    public static void produce(ServerLevel level, Container container, HotChamberRecipe recipe) {
        for (ItemStack output : recipe.itemOutputs()) {
            ItemStack stack = output.copy();
            applyBuiltInEnchantments(level, stack);
            CopperHeat.addToContainer(container, stack);
        }
        container.setChanged();
    }

    private static void applyBuiltInEnchantments(ServerLevel level, ItemStack stack) {
        if (stack.getItem() instanceof TungstenSteelGearItem) {
            SteelCrafting.applyEnchantment(stack, level.registryAccess(), Enchantments.UNBREAKING, 3);
            SteelCrafting.applyEnchantment(stack, level.registryAccess(), Enchantments.FIRE_PROTECTION, 4);
        } else if (stack.getItem() instanceof TungstenSteelTool) {
            SteelCrafting.applyEnchantment(stack, level.registryAccess(), Enchantments.UNBREAKING, 3);
        }
    }

    // ------------------------------------------------------------------ 表构造 / 匹配工具

    private static HotChamberRecipe recipe(List<ItemStack> inputs, List<ItemStack> outputs, int duration) {
        return recipe(inputs, outputs, 0, duration);
    }

    /** 钨钢升级：1 件钢装备 + N 块钨铜硬质合金锭 → 1 件钨钢装备。 */
    private static HotChamberRecipe upgrade(Item steel, int alloyCount, Item result, int duration) {
        return recipe(items(steel, 1, AlloyMaterials.TUNGSTEN_COPPER_HARD_ALLOY.ingot.get(), alloyCount),
                out(result, 1), duration);
    }

    /**
     * <strong>合金配方</strong>：出锭数 = 投料粉的总份数（质量守恒），周期按「每份 60t」递增。
     * 只用于 {@link dev.modmind.stellar_evolution.material.AlloyMaterials} 这 7 条。
     */
    private static HotChamberRecipe alloy(AlloyMaterials.Alloy alloy, List<ItemStack> inputs) {
        int total = 0;
        for (ItemStack stack : inputs) {
            total += stack.getCount();
        }
        return recipe(inputs, out(alloy.ingot.get(), total), total * 60);
    }

    private static HotChamberRecipe recipe(List<ItemStack> inputs, List<ItemStack> outputs,
                                           int pressure, int duration) {
        HotChamberRecipe recipe = new HotChamberRecipe(inputs, outputs, duration, pressure);
        checkInputSlots(recipe);
        return recipe;
    }

    /** 带标签输入的配方（标签槽位也必须计入 3×3 上限）。 */
    private static HotChamberRecipe tagged(List<ItemStack> inputs, List<HotChamberRecipe.TagInput> tags,
                                           List<ItemStack> outputs, int duration) {
        return tagged(inputs, tags, outputs, 0, duration);
    }

    /** 带标签输入 + 等压值的配方。 */
    private static HotChamberRecipe tagged(List<ItemStack> inputs, List<HotChamberRecipe.TagInput> tags,
                                           List<ItemStack> outputs, int pressure, int duration) {
        HotChamberRecipe recipe = new HotChamberRecipe(inputs, tags, outputs, duration, pressure);
        checkInputSlots(recipe);
        return recipe;
    }

    /** 把 {@code 标签, 数量, 图标物品, 标签, 数量, 图标物品…} 展成标签输入表。 */
    private static List<HotChamberRecipe.TagInput> tags(Object... tagCountDisplay) {
        List<HotChamberRecipe.TagInput> list = new ArrayList<>(tagCountDisplay.length / 3);
        for (int i = 0; i + 2 < tagCountDisplay.length; i += 3) {
            list.add(new HotChamberRecipe.TagInput((TagKey<Item>) tagCountDisplay[i],
                    (Integer) tagCountDisplay[i + 1], new ItemStack((Item) tagCountDisplay[i + 2])));
        }
        return list;
    }

    /** 硬上限：JEI 面板 3×3，输入超过 9 格就没地方摆 —— 建表时直接失败，避免上线后才发现溢出。 */
    private static void checkInputSlots(HotChamberRecipe recipe) {
        int slots = recipe.inputSlotCount();
        if (slots > MAX_INPUT_SLOTS) {
            throw new IllegalArgumentException("Hot chamber recipe " + outputId(recipe) + " has " + slots
                    + " input slots but the JEI panel only fits " + MAX_INPUT_SLOTS + " (3x3)");
        }
    }

    private static List<ItemStack> out(Item item, int count) {
        return List.of(new ItemStack(item, count));
    }

    /** 把 {@code 物品, 数量, 物品, 数量…} 展成输入 / 产出表。 */
    private static List<ItemStack> items(Object... itemAndCount) {
        List<ItemStack> stacks = new ArrayList<>(itemAndCount.length / 2);
        for (int i = 0; i + 1 < itemAndCount.length; i += 2) {
            stacks.add(new ItemStack((Item) itemAndCount[i], (Integer) itemAndCount[i + 1]));
        }
        return stacks;
    }

    /**
     * 输入匹配：可损坏物品（工具 / 护甲）只看物品类型，忽略耐久与附魔等组件，
     * 这样热室能用<strong>任意耐久</strong>的钢装备升级成钨钢；其它材料仍精确匹配组件。
     */
    private static boolean sameInput(ItemStack stack, ItemStack need) {
        if (stack.isEmpty()) {
            return false;
        }
        if (need.isDamageableItem()) {
            return stack.is(need.getItem());
        }
        return ItemStack.isSameItemSameComponents(stack, need);
    }

    /** 箱内这几种物品各够不够（数量累加，忽略槽位分布；标签输入按「任意属于该标签的物品」累加）。 */
    private static boolean hasInputs(Container container, HotChamberRecipe recipe) {
        for (ItemStack need : recipe.itemInputs()) {
            int total = 0;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (sameInput(stack, need)) {
                    total += stack.getCount();
                }
            }
            if (total < need.getCount()) {
                return false;
            }
        }
        for (HotChamberRecipe.TagInput tag : recipe.tagInputs()) {
            int total = 0;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && stack.is(tag.tag())) {
                    total += stack.getCount();
                }
            }
            if (total < tag.count()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 产物放不放得下：先在<strong>副本</strong>里扣掉输入（腾出的格子要能被产物用上），
     * 再按「并入同类堆 → 填空槽」模拟一遍，任一产物溢出即 false。
     */
    private static boolean hasOutputSpace(Container container, HotChamberRecipe recipe) {
        int size = container.getContainerSize();
        List<ItemStack> simulated = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            simulated.add(container.getItem(i).copy());
        }
        for (ItemStack need : recipe.itemInputs()) {
            int left = need.getCount();
            for (int i = 0; i < size && left > 0; i++) {
                ItemStack stack = simulated.get(i);
                if (!sameInput(stack, need)) {
                    continue;
                }
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        for (HotChamberRecipe.TagInput tag : recipe.tagInputs()) {
            int left = tag.count();
            for (int i = 0; i < size && left > 0; i++) {
                ItemStack stack = simulated.get(i);
                if (stack.isEmpty() || !stack.is(tag.tag())) {
                    continue;
                }
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        for (ItemStack output : recipe.itemOutputs()) {
            int left = output.getCount();
            int max = output.getMaxStackSize();
            for (int i = 0; i < size && left > 0; i++) {
                ItemStack stack = simulated.get(i);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, output)) {
                    continue;
                }
                int move = Math.min(max - stack.getCount(), left);
                stack.grow(move);
                left -= move;
            }
            for (int i = 0; i < size && left > 0; i++) {
                if (!simulated.get(i).isEmpty()) {
                    continue;
                }
                int move = Math.min(max, left);
                simulated.set(i, output.copyWithCount(move));
                left -= move;
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    private HotChamberRecipes() {}
}
