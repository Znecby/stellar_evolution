package dev.modmind.stellar_evolution.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DispenserMenu;
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
 * 材料加工机械的方块（破碎机 / 压模机 / 线材轧机共用，机种由注册表按方块实例区分）——
 * 照 {@code engine.BurningEngineBlock} 口径。
 *
 * <p>加工与数值在 {@link MachineBlockEntity}：机内 9 格料仓作为输入、<strong>正上方容器作为输出</strong>，
 * 每个周期把料仓里每一格各处理一次；本类只负责朝向、方块实体与右键开界面。
 *
 * <p>整机是一格，外面罩一圈 <strong>2px 粗的铜框架</strong>（实心铜底板 + 四角柱 + 顶部四根梁），
 * 框内是各自的现实机构：破碎机＝偏心飞轮 + 动颚破碎腔，压模机＝四立柱 + 滑块 + 压力表，线材轧机＝上下轧辊牌坊 + 传动轮。
 * <p>{@link #FACING} 决定整体绕 Y 转多少（模型的 <b>z=0 那侧是正面</b>），放置时正面朝玩家。
 * <p>右键打开机内 9 格（3×3）料仓；压模机使用带<strong>模具槽</strong>的专用界面，其余三台复用投掷器界面。
 */
public class MachineBlock extends BaseEntityBlock {
    /** 水平朝向：正面（结构的 z=0 那侧）朝哪。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return simpleCodec(MachineBlock::new);
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
        return new MachineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 两端都 tick：客户端推进运行时长即可（服务端无逻辑）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, MachineBlocks.MACHINE_ENTITY.get(), MachineBlockEntity::tick);
    }

    /** 右键：压模机打开带模具槽的专用界面，其余复用投掷器界面。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof MachineBlockEntity machine
                && player instanceof ServerPlayer serverPlayer) {
            if (machine.kind() == MachineKind.DIE_PRESS) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, opener) -> new DiePressMenu(containerId, inventory, machine),
                        getName()));
            } else {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, opener) -> new DispenserMenu(containerId, inventory, machine),
                        getName()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** 拆除时把机内料仓的物品吐出来，避免随方块一起消失。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.dropContents(level, pos);
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
}
