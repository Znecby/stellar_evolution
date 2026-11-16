package dev.znecby.stellar_evolution.hatch;

import com.mojang.serialization.MapCodec;
import dev.znecby.stellar_evolution.large.LargeMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
 * 机械物流仓：本身没有界面、没有内置库存。
 *
 * <p>只有被已成型多方块结构认领后，才会作为该结构接入机械管网络的端口；
 * 独立放置时就是一个普通方块，不提供任何能力。
 *
 * <p><strong>基底材质随结构变化</strong>（灵感来自 GTCEu 的仓室）：未成型时是
 * <strong>不锈钢块</strong>；被控制器认领后改成该控制器外壳的材质（焦化炉 → 红砖块），
 * 玩家远远看一眼就能判断结构是否成型。
 */
public class HatchBlock extends BaseEntityBlock implements LargeMachineBlock {
    /** 六面朝向：正面（画覆盖层的那面）。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** 基底材质：默认不锈钢；成型后由控制器切换成机器外壳材质。 */
    public enum Casing implements StringRepresentable {
        /** 默认：不锈钢块（未成型）。 */
        STAINLESS("stainless"),
        /** 红砖块（焦化炉）。 */
        BRICKS("bricks"),
        /** 高负荷机械方块（质量投射轨道炮，与控制器基底贴图一致）。 */
        HIGH_LOAD("high_load");

        private final String name;

        Casing(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Casing> CASING = EnumProperty.create("casing", Casing.class);

    public HatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CASING, Casing.STAINLESS));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(HatchBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CASING);
    }

    /**
     * 放置朝向：一般情况正面（覆盖层）朝玩家水平方向——放在地上时也能面向房间；
     * 只有视线几乎垂直（俯仰角超过 75°）时才用上 / 下，保留六面能力。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing;
        if (context.getPlayer() != null && Math.abs(context.getPlayer().getXRot()) > 75.0F) {
            facing = context.getPlayer().getXRot() > 0.0F ? Direction.UP : Direction.DOWN;
        } else {
            facing = context.getHorizontalDirection().getOpposite();
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HatchBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 只有服务端需要 tick：载入后强制让相邻机械管重算一次连接（修复旧存档里残留的错面连接）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, HatchBlocks.HATCH_ENTITY.get(), HatchBlockEntity::tick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && oldState.getBlock() != state.getBlock()) {
            // 必须走 updateNeighbourShapes 才会触发机械管的 updateShape
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** 被多方块控制器认领时，把 Jade / 大机器框架反查到控制器原点。 */
    @Nullable
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return level instanceof Level serverLevel
                && serverLevel.getBlockEntity(pos) instanceof HatchBlockEntity hatch
                ? hatch.controllerPos() : null;
    }
}
