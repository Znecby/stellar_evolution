package dev.modmind.stellar_evolution.pipe;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 铜管家族注册表：四种管子各一个方块 + 对应物品（都无方块实体、无能力接口）。
 * <ul>
 *   <li>{@code copper_pipe} <strong>铜管</strong>：4×4 细管，<b>热压差网络的传递件</b>（导电）；</li>
 *   <li>{@code machine_pipe} <strong>机械管</strong>：6×6、壁厚 2px、中心 2×2 空心，
 *       <b>大机器物流网络</b>专用（不导电、只与同类及大机器 / 物流容器对接）；</li>
 *   <li>{@code pressure_pipe} <strong>压力管</strong>：外形 / 连接手感照机械管，但<b>实心</b>
 *       （中心 2×2 换成深板岩瓦、外皮下界合金块），<b>蒸汽网络的载体</b>（只与同类 + 蒸汽端点互连，
 *       见 {@code steam.SteamNetwork}）。</li>
 *   <li>{@code beam_pipe} <strong>束流管</strong>：同样实心，外皮钨块、中心 2×2 磨制安山岩，
 *       <b>粒子流网络的载体</b>（单管单流；只与同类型束流管 + 同类型粒子流端点互连，
 *       见 {@code stream.StreamNetwork}）。</li>
 * </ul>
 * 贴图直接复用原版 / 本模组方块（铜块 / 切制铜块 / 磨制黑石 / 下界合金块 / 深板岩瓦 / 钨块 / 磨制安山岩），
 * 机械管 / 压力管 / 束流管做程序化模型（{@code tools/gen_large_copper_pipe.ps1}）。
 * 物品出现在本模组自己的创造页（见 {@code StellarEvolutionTabs}），不塞进原版页。
 */
public final class PipeBlocks {
    public static final String COPPER_PIPE_NAME = "copper_pipe";
    public static final String MACHINE_PIPE_NAME = "machine_pipe";
    public static final String PRESSURE_PIPE_NAME = "pressure_pipe";
    public static final String BEAM_PIPE_NAME = "beam_pipe";
    public static final String FLOW_LIMITER_NAME = "flow_limiter";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StellarEvolution.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StellarEvolution.MOD_ID);

    public static final DeferredBlock<CopperPipeBlock> COPPER_PIPE =
            BLOCKS.register(COPPER_PIPE_NAME, () -> new CopperPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    // 细管不该让邻居消面、也不该挡光（否则贴着实心方块会出现「洞」）
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(COPPER_PIPE_NAME)))));

    public static final DeferredItem<BlockItem> COPPER_PIPE_ITEM =
            ITEMS.register(COPPER_PIPE_NAME, key -> new BlockItem(COPPER_PIPE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /** 机械管：大机器物流网络专用（6×6 外径 + 2px 壁厚 + 2×2 空心）。 */
    public static final DeferredBlock<MachinePipeBlock> MACHINE_PIPE =
            BLOCKS.register(MACHINE_PIPE_NAME, () -> new MachinePipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(MACHINE_PIPE_NAME)))));

    public static final DeferredItem<BlockItem> MACHINE_PIPE_ITEM =
            ITEMS.register(MACHINE_PIPE_NAME, key -> new BlockItem(MACHINE_PIPE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /** 压力管：蒸汽网络载体，只与同类 / 蒸汽端点互连。 */
    public static final DeferredBlock<PressurePipeBlock> PRESSURE_PIPE =
            BLOCKS.register(PRESSURE_PIPE_NAME, () -> new PressurePipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(PRESSURE_PIPE_NAME)))));

    public static final DeferredItem<BlockItem> PRESSURE_PIPE_ITEM =
            ITEMS.register(PRESSURE_PIPE_NAME, key -> new BlockItem(PRESSURE_PIPE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /** 束流管：粒子流网络专用（外皮钨块 + 中心磨制安山岩，6×6 实心）。 */
    public static final DeferredBlock<BeamPipeBlock> BEAM_PIPE =
            BLOCKS.register(BEAM_PIPE_NAME, () -> new BeamPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(BEAM_PIPE_NAME)))));

    public static final DeferredItem<BlockItem> BEAM_PIPE_ITEM =
            ITEMS.register(BEAM_PIPE_NAME, key -> new BlockItem(BEAM_PIPE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /** 限流管：热压差网络的<strong>闸门</strong>（6×6 直管 + 石英「&gt;」流向标记 + 数值输入界面）。 */
    public static final DeferredBlock<FlowLimiterBlock> FLOW_LIMITER =
            BLOCKS.register(FLOW_LIMITER_NAME, () -> new FlowLimiterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .forceSolidOn()
                    .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(FLOW_LIMITER_NAME)))));

    public static final DeferredItem<BlockItem> FLOW_LIMITER_ITEM =
            ITEMS.register(FLOW_LIMITER_NAME, key -> new BlockItem(FLOW_LIMITER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, key))
                            .useBlockDescriptionPrefix()));

    /** 限流管的方块实体：存设定值 + 每 tick 读一次上游账本。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlowLimiterBlockEntity>> FLOW_LIMITER_ENTITY =
            BLOCK_ENTITIES.register(FLOW_LIMITER_NAME,
                    () -> new BlockEntityType<>(FlowLimiterBlockEntity::new, FLOW_LIMITER.get()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private PipeBlocks() {}
}
