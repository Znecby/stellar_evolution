package dev.modmind.stellar_evolution.pipe;

import com.mojang.serialization.MapCodec;
import dev.modmind.stellar_evolution.network.FlowLimitPayload;
import dev.modmind.stellar_evolution.thermal.ThermalNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * <strong>限流管</strong>（{@code flow_limiter}）：热压差网络的<strong>闸门</strong> ——
 * 6×6 粗的<strong>直管</strong>（雕文铜块外皮 + 空腔铜内壁，照机械管的断面口径），顶上有一枚
 * <strong>石英「&gt;」</strong>指着网络流向。
 *
 * <p><strong>只连两端</strong>：一根直管，两个接口是<strong>相对的两个面</strong>（前后 / 左右 / 上下）——
 * 放置时按<strong>点击面的轴向</strong>定管子的走向；流向（{@link #FACING}）优先取玩家视线方向
 * （视线与轴向平行时），否则取点击面方向。
 *
 * <p><strong>两种右键</strong>：
 * <ul>
 *   <li><strong>剪刀</strong>右键 → <strong>翻转流向</strong>（进出两端对调，管子造型不变）；</li>
 *   <li><strong>空手</strong>右键 → 打开<strong>「下游热流率」输入界面</strong>（原版风文本框，见
 *       {@code client.FlowLimiterScreen}）：默认填的就是<strong>输入端网络当前的热流率</strong>，
 *       改完写回设定值 —— 上游容量原样透传、热流率按这个数卡住（详见 {@link FlowLimiterBlockEntity}）。</li>
 * </ul>
 *
 * <p><strong>网络语义</strong>：本方块是网络成员且<strong>照常导通</strong> —— 热压差 BFS 会穿过它，
 * 两侧始终是<strong>同一张账本</strong>；它只在「从 {@link #FACING} 面走出去」的那一步给下游压一道
 * <strong>位置热流率上限</strong>（见 {@code thermal.ThermalGate} / {@code ThermalNetwork}）。
 */
public class FlowLimiterBlock extends BaseEntityBlock {
    /** 流向：从 {@link #FACING} 的反面进、朝 {@link #FACING} 面出。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** 三种轴向的管身（6×6，贯通整格）。 */
    private static final VoxelShape SHAPE_X = Block.box(0.0, 5.0, 5.0, 16.0, 11.0, 11.0);
    private static final VoxelShape SHAPE_Y = Block.box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);
    private static final VoxelShape SHAPE_Z = Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 16.0);

    public FlowLimiterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FlowLimiterBlock> codec() {
        return simpleCodec(FlowLimiterBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 走向 = 点击面的轴向（照原版原木 / 管道的手感）；流向：视线与轴向平行时跟视线，否则朝点击面的外法线
        Direction.Axis axis = context.getClickedFace().getAxis();
        Direction looking = context.getNearestLookingDirection();
        Direction flow = looking.getAxis() == axis ? looking : context.getClickedFace();
        return defaultBlockState().setValue(FACING, flow);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.getRotation(state.getValue(FACING)).rotate(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state.getValue(FACING));
    }

    private static VoxelShape shapeOf(Direction facing) {
        return switch (facing.getAxis()) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlowLimiterBlockEntity(pos, state);
    }

    /**
     * 剪刀右键：翻转流向（进出两端对调）。
     * <p><strong>非剪刀一律返回 {@link InteractionResult#TRY_WITH_EMPTY_HAND}</strong> ——
     * 26.x 的服务端只有当 {@code useItemOn} 给出这个哨兵值、且是主手时，才会回落到
     * {@link #useWithoutItem}（空手那种交互）；返回 {@code PASS} 的话空手右键根本不会到那里。
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(Items.SHEARS)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getOpposite()), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /** 空手右键：打开「下游热流率」输入界面（默认值 = 输入端网络当前的热流率）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FlowLimiterBlockEntity limiter
                && player instanceof ServerPlayer serverPlayer) {
            // 输入端的「网络热流率」——现读一次（服务端、同一 tick 内走缓存），给玩家当默认值
            double upstreamFlow = ThermalNetwork.loadAt(level, pos.relative(limiter.inputFace())).flowRate();
            PacketDistributor.sendToPlayer(serverPlayer,
                    new FlowLimitPayload.Open(pos, limiter.limit(), upstreamFlow));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
