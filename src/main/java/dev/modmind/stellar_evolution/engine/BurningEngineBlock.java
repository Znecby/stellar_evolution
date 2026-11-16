package dev.modmind.stellar_evolution.engine;

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
 * 燃烧引擎（burning engine）：<strong>纯外观方块</strong>——不判热自身、不参与配方；
 * 动画与热力引擎同源：<strong>下方有正在工作的熔炉 / 烟熏炉 / 高炉时才动</strong>，否则停回静止位。
 * <p>
 * 整机被一圈 <strong>2px 粗的铜框架</strong>罩住（实心铜底板 + 四角柱 + 顶梁），左右两侧中心各一块
 * 6×6 铜板 —— 那两处就是与铜管对接的位置（见 {@code CopperPipeBlock#connectsTo}）。
 * 框内是现实中的 <strong>V6 内燃机</strong>：油底壳 → 曲轴箱 → 左右两列缸体（各偏竖直 30°，形成 V 形），
 * 每列 3 根深色缸套分隔条＝6 个缸，两侧排气歧管外沿各一条岩浆色「烧红」条；一端是<strong>可动的风扇</strong>。
 * 风扇与六缸活塞都由 {@code client.BurningEngineRenderer} 借「隐藏载体方块 + {@code submitMovingBlock}」
 * 画在方块外，共用同一曲轴转角：风扇绕长轴自转，<strong>活塞沿各自斜缸轴往复</strong>，
 * 且同一列里中间那根与外侧两根反相、左右两列再反相，于是运行时交错运动。
 * <p>
 * 长轴＝左右两端面（＝曲轴方向），{@link #FACING} 决定整体绕 Y 转多少，放置时正面朝玩家。
 */
public class BurningEngineBlock extends BaseEntityBlock {
    /** 水平朝向：正面（结构的 z=0 那侧）朝哪。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BurningEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BurningEngineBlock> codec() {
        return simpleCodec(BurningEngineBlock::new);
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
        return new BurningEngineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 两端都 tick：客户端推进曲轴角度即可（服务端保持对称，无实际逻辑）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, EngineBlocks.BURNING_ENGINE_ENTITY.get(), BurningEngineBlockEntity::tick);
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
