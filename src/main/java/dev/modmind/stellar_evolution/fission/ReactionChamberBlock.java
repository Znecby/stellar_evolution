package dev.modmind.stellar_evolution.fission;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * 反应仓方块：木桶模型 / 木桶同款开合状态与音效，右键打开<strong>大箱子界面</strong>（6 行 × 9 列）。
 *
 * <p>本身是普通容器；裂变堆只从它这里读取燃料棒 / 石墨棒 / 反射棒。
 */
public class ReactionChamberBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public ReactionChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ReactionChamberBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactionChamberBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(OPEN, false);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof ReactionChamberBlockEntity chamber
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, opener) -> new ChestMenu(
                            MenuType.GENERIC_9x6, containerId, inventory, chamber, 6),
                    Component.translatable("container.stellar_evolution.reaction_chamber")));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ReactionChamberBlockEntity chamber) {
            chamber.dropContents(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 与木桶一致：开 / 合时切换 {@link #OPEN}，客户端据此换 open 模型。 */
    public static void setOpen(Level level, BlockState state, BlockPos pos, boolean open) {
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
    }

    /** 与木桶一致的开 / 合音效。 */
    public static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        RandomSource random = level.getRandom();
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                sound, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.1F + 0.9F);
    }
}
