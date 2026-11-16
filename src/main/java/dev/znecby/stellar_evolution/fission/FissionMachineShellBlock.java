package dev.znecby.stellar_evolution.fission;

import com.mojang.serialization.MapCodec;
import dev.znecby.stellar_evolution.large.LargeMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 裂变机器的「填充格」（照石化线 {@code PetroMachineShellBlock} 口径）：
 * 一台机器真实占据 2×2×2 = 8 格，主方块只占一格，其余 7 格由本方块占位。
 *
 * <p>无物品、无可视模型、不掉落、不挡光，但挂方块实体把本体能力（大机器物流）转发出去；
 * 破坏任一格都会整台拆掉。
 */
public class FissionMachineShellBlock extends BaseEntityBlock implements LargeMachineBlock {
    public static final MapCodec<FissionMachineShellBlock> CODEC = simpleCodec(FissionMachineShellBlock::new);

    public FissionMachineShellBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** 填充格属于哪台机器：反查主方块位置（大机器框架从任意一格反查原点时用）。 */
    @Nullable
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return FissionMachineStructure.findMachine(level, pos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FissionMachineShellBlockEntity(pos, state);
    }

    /** 不可见：填充格只是碰撞体。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 天光可穿透（否则机器内部会被压暗）。 */
    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    /** 不挡光。 */
    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    // 注意：不要把 getShape / getCollisionShape 置空，否则准星射线会穿过机器打到后面的方块。

    /** 破坏任一填充格 → 找到主方块并整台破坏（掉落由主方块负责）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos machine = FissionMachineStructure.findMachine(level, pos);
            if (machine != null) {
                level.destroyBlock(machine, !player.isCreative());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 偶尔自检：附近已经没有主方块（孤儿填充格）→ 自行消失。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (FissionMachineStructure.findMachine(level, pos) == null) {
            level.removeBlock(pos, false);
        }
    }
}
