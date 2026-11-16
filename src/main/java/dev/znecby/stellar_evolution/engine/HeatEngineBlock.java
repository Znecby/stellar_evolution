package dev.znecby.stellar_evolution.engine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 热力引擎（heat engine）：外观取自存档结构 {@code heat_engine.nbt}（见开发文档 §6.3）。
 * <p>
 * 造型：整座 {@code 7 × 4 × 4} 结构按比例缩进 1 格 —— 平滑石底板 / 背板 / 端柱是<strong>固定机身</strong>，
 * 中间 {@code 2 × 2 × 7} 的芯按结构「铜/铁相间」拆成两件：<strong>铜色圆盘（固定机身）</strong> +
 * <strong>铁色桨叶（可动转子）</strong>。转子由 {@code client.HeatEngineRenderer} 单独渲染并绕长轴转动。
 * <ul>
 *   <li><strong>朝向</strong>：{@link #FACING}（水平四向）决定「正面」朝哪；长轴＝左右两端面，即接管方向，
 *       是朝向旋转 90° 的那根水平轴。放置时正面朝玩家。</li>
 *   <li><strong>接管</strong>：左右两端面可与铜管相接（铜管侧见 {@code CopperPipeBlock#connectsTo}）。</li>
 *   <li><strong>工作</strong>：走燃烧引擎的<strong>固体模式</strong> —— 正下方是<strong>物品容器</strong>且里面有熔炉燃料时，
 *       消耗燃料、转子顺时针转并对外供能；没容器 / 没燃料则停机，转子缓慢回落到静止位 45°
 *       （「常态下与铜块错开 45 度」）。</li>
 * </ul>
 */
public class HeatEngineBlock extends BaseEntityBlock {
    /** 水平朝向：正面（结构的 z=0 那侧）朝哪。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public HeatEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(HeatEngineBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 放置时正面朝玩家（玩家朝向的反向）。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeatEngineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 两端都要 tick：服务端只判热，客户端还要推进转子角度（纯本地动画，不联机同步）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, EngineBlocks.HEAT_ENGINE_ENTITY.get(), HeatEngineBlockEntity::tick);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.getRotation(state.getValue(FACING)).rotate(state.getValue(FACING)));
    }
}
