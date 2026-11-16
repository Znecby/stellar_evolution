package dev.modmind.stellar_evolution.farmland;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * <strong>机械耕地</strong>：外观是「被 2px 铜框架框住的耕地」（见 {@code models/block/mechanical_farmland*.json}），
 * 耕作<strong>玩法与原版耕地一致</strong> —— 直接继承 {@link FarmlandBlock}，于是湿润度（{@code moisture}）、
 * 附近水源 / 下雨补水、作物生长提速（配合 {@code minecraft:grows_crops} 标签）全都白拿。
 *
 * <p><strong>「可以种」靠的是标签</strong>：26.x 的 {@code CropBlock.mayPlaceOn} 判的是
 * {@code BlockTags.SUPPORTS_CROPS}（{@code minecraft:supports_crops}），不再写死 {@code Blocks.FARMLAND}。
 * 所以只要把本方块加进 {@code minecraft:supports_crops}，原版作物就能直接种上来 —— 不用改作物、也不用 mixin。
 *
 * <p><strong>与「原版耕地」的区别：这是一台设备，不会被环境变回泥土。</strong>
 * 原版 {@link FarmlandBlock} 有三条路径会把它变成 {@code minecraft:dirt}，这里全部掐掉：
 * <ol>
 *   <li>{@code getStateForPlacement}：上方是实心块时原版直接返回泥土 —— 这里照常放下本方块；</li>
 *   <li>{@code randomTick}：原版「既没水、上方也没作物」时会转泥土 —— 这里只保留含水逻辑，永不转；</li>
 *   <li>{@code fallOn}：原版被踩踏有概率转泥土 —— 这里不被踩坏。</li>
 * </ol>
 * 相应地 {@link #canSurvive} 恒为 true（原版判的是「上方不能是实心块」，随之而来的计划刻转泥土一并失效）。
 *
 * <p><strong>它还是热压差网络的一台消耗端</strong>：贴着铜块即接入（16 DP / 8 HP），
 * 每 200 刻（按倍速）让上方作物成熟一次；底下有容器时自动收割 + 重新种 + 产物进容器 —— 逻辑在
 * {@link MechanicalSoilBlockEntity#serverTick}。所以它带方块实体（原版 farmland 没有）。
 */
public class MechanicalFarmlandBlock extends FarmlandBlock implements EntityBlock, MechanicalSoil {
    /** 找水范围：与原版 {@code isNearWater} 一致（水平 ±4、上方 +1）。 */
    private static final int WATER_RANGE = 4;

    /** 服务端 ticker（催熟 / 自动收获），见 {@link MechanicalSoilBlockEntity#serverTick}。 */
    private static final BlockEntityTicker<MechanicalSoilBlockEntity> TICKER =
            MechanicalSoilBlockEntity::serverTick;

    public MechanicalFarmlandBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<FarmlandBlock> codec() {
        return simpleCodec(MechanicalFarmlandBlock::new);
    }

    /** 方块实体只放「催熟进度」，没有额外数据。 */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanicalSoilBlockEntity(pos, state);
    }

    /** 服务端每刻推进催熟；客户端不 tick。 */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != FarmlandBlocks.MECHANICAL_SOIL_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) TICKER;
    }

    /** 照常放下本方块，不因为「上方是实心块」换成泥土。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    /** 不需要任何支撑（原版判的是「上方不能是实心块」，那是它转泥土的触发条件之一）。 */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    /**
     * 让<strong>「右键收获」作物（{@link FarmlandBlocks#RIGHT_CLICK_HARVEST}，如甜浆果丛）</strong>也能种在机械耕地上。
     * <p>
     * 这类作物（{@code SweetBerryBushBlock}）的放置判的是 {@code minecraft:supports_vegetation}，机械耕地不在其中；
     * NeoForge 的 {@code VegetationBlock.canSurvive} 会先问下方方块的 {@link #canSustainPlant}，只要这里返回
     * {@link TriState#TRUE} 就能种上来 —— 比把本方块塞进 {@code supports_vegetation}（会顺带让树苗 / 花也能种）更精准。
     */
    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soilPosition, Direction facing, BlockState plant) {
        if (facing == Direction.UP && plant.is(FarmlandBlocks.RIGHT_CLICK_HARVEST)) {
            return TriState.TRUE;
        }
        return TriState.DEFAULT;
    }

    /**
     * 只保留<strong>含水</strong>逻辑：附近有水源或正在下雨 → 湿润度拉满；否则缓慢回落。
     * <strong>不保留</strong>原版「干着、上方又没作物就变泥土」那一条。
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int moisture = state.getValue(MOISTURE);
        if (isNearWater(level, pos) || level.isRainingAt(pos.above())) {
            if (moisture < MAX_MOISTURE) {
                level.setBlock(pos, state.setValue(MOISTURE, MAX_MOISTURE), 2);
            }
        } else if (moisture > 0) {
            level.setBlock(pos, state.setValue(MOISTURE, moisture - 1), 2);
        }
    }

    /** 机械耕地不会被踩坏（原版 {@code fallOn} 有概率转泥土）。 */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        // 有意留空
    }

    /** 附近有没有水（原版是 private，这里照抄同样的范围）。 */
    private static boolean isNearWater(LevelReader level, BlockPos pos) {
        for (BlockPos waterPos : BlockPos.betweenClosed(
                pos.offset(-WATER_RANGE, 0, -WATER_RANGE),
                pos.offset(WATER_RANGE, 1, WATER_RANGE))) {
            if (level.getFluidState(waterPos).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}
