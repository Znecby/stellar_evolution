package dev.znecby.stellar_evolution.multiblock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 固定多方块<strong>控制器方块</strong>的公共基类。
 *
 * <p>具体机器继承本类并实现：
 * <ul>
 *   <li>{@link #newBlockEntity(BlockPos, BlockState)} —— 返回自己的控制器方块实体；</li>
 *   <li>{@link #blockEntityType()} —— 返回注册好的方块实体类型；</li>
 *   <li>{@link #codec()} —— 照项目其它方块写 {@code simpleCodec(...)}。</li>
 * </ul>
 *
 * <p>本类只负责：朝向属性、放置朝向、旋转 / 镜像、ticker、右键转给方块实体、拆除时通知控制器。
 * 结构扫描、成型 / 拆解、固定端口收集都在 {@link MultiblockControllerBlockEntity}。
 */
public abstract class MultiblockControllerBlock extends BaseEntityBlock {
    /** 水平朝向：图案的“正面”朝哪。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected MultiblockControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** 具体机器注册的方块实体类型。 */
    protected abstract BlockEntityType<? extends MultiblockControllerBlockEntity> blockEntityType();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        BlockEntityType<MultiblockControllerBlockEntity> expected =
                (BlockEntityType<MultiblockControllerBlockEntity>) (BlockEntityType<?>) blockEntityType();
        return createTickerHelper(type, expected, MultiblockControllerBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            return controller.onUse(player, hitResult);
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.onControllerRemoved();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
