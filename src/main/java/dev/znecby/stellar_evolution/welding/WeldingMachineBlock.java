package dev.znecby.stellar_evolution.welding;

import com.mojang.serialization.MapCodec;
import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
 * 焊接机：单方块焊接台（参考 HBM 焊接台 + 现实工作台）。
 *
 * <p>正面（{@link #FACING}、模型 z=0 那侧）是操作面；右键打开 1×3 料仓界面。
 * 真正的工作逻辑在 {@link WeldingMachineBlockEntity}：下方储罐供流体、上方容器收产物。
 */
public class WeldingMachineBlock extends BaseEntityBlock {
    /** 水平朝向：正面（模型 z=0 那侧）朝哪。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public WeldingMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends WeldingMachineBlock> codec() {
        return simpleCodec(WeldingMachineBlock::new);
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
        return new WeldingMachineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 服务端 ticker（客户端不需要任何逻辑）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, WeldingBlocks.WELDING_MACHINE_ENTITY.get(),
                WeldingMachineBlockEntity::tick);
    }

    /** 右键：打开 1×3 料仓界面。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof WeldingMachineBlockEntity welding
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, opener) ->
                            new WeldingMenu(containerId, inventory, welding, welding.data()),
                    getName()));
        }
        return InteractionResult.SUCCESS;
    }

    /** 拆除时把机内料仓的物品吐出来。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WeldingMachineBlockEntity welding) {
            welding.dropContents(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.getRotation(state.getValue(FACING)).rotate(state.getValue(FACING)));
    }

    /** 方块注册属性（与材料加工机械同口径：非整方块造型、不挡光）。 */
    public static Properties properties(String id) {
        return Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .forceSolidOn()
                .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(id)));
    }
}
