package dev.znecby.stellar_evolution.drill;

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
 * 开采钻（mining drill）：<strong>单方块</strong>——照 {@code engine.BurningEngineBlock} 与
 * {@code machine.MachineBlock} 的口径：一格大小、一圈 2px 铜框架，主机是「底座 + 两根导轨 + 顶部齿轮箱 +
 * 中央钻杆」，钻杆与正面绞盘由 {@code client.DrillRenderer} 借「隐藏载体方块 + {@code submitMovingBlock}」画出。
 *
 * <p><strong>机制</strong>（2026-10-26）：热压差网络的消耗端，两种作业模式（范围破坏 / 精准采集），
 * 详细数值与扫描规则见 {@link DrillBlockEntity}。
 * <ul>
 *   <li><strong>剪刀右键</strong>（不蹲下）＝切换模式，并从机器下方重新开始；</li>
 *   <li><strong>蹲下 + 剪刀右键</strong>＝打开范围输入界面（文本 UI，按当前模式解释 N 的单位）。</li>
 * </ul>
 *
 * <p>{@link #FACING} 决定整体绕 Y 转多少（模型的 <b>z=0 那侧是正面</b>），放置时正面朝玩家。
 */
public class DrillBlock extends BaseEntityBlock {
    /** 水平朝向：正面（结构的 z=0 那侧）朝哪。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public DrillBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DrillBlock> codec() {
        return simpleCodec(DrillBlock::new);
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
        return new DrillBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 两端都 tick：服务端跑热压差消耗端逻辑，客户端推进动画时长。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, DrillBlocks.DRILL_ENTITY.get(), DrillBlockEntity::tick);
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
