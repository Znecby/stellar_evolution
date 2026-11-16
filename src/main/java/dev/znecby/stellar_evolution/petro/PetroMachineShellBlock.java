package dev.znecby.stellar_evolution.petro;

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
 * 石化机器的「填充格」：一台机器在世界上真实占据 2×2×2 = 8 个方块格，
 * 主方块（{@link PetroMachineBlock}）只占其中一格，其余 7 格由本方块填充 —— 照生化线 {@code BioMachineShellBlock} 口径。
 *
 * <p>本方块：无物品、无可视模型（{@link RenderShape#INVISIBLE}）、不掉落、不挡光，
 * 但挂一个方块实体（{@link PetroMachineShellBlockEntity}）—— 只用来把机器本体的物品 / 流体能力转发过来，
 * 使「所有部分都视作本体」：瞄任意一格都能看到同样的物品 / 流体存量。
 */
public class PetroMachineShellBlock extends BaseEntityBlock implements LargeMachineBlock {
    public static final MapCodec<PetroMachineShellBlock> CODEC = simpleCodec(PetroMachineShellBlock::new);

    public PetroMachineShellBlock(Properties properties) {
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
        return PetroMachineBlocks.findMachine(level, pos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PetroMachineShellBlockEntity(pos, state);
    }

    /** 不可见：没有任何模型要渲染（填充格只是碰撞体）。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 天光可穿透（否则机器内部会被压暗）。 */
    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    /** 不挡光：填充格只是「碰撞体」，绝不能让机器内部 / 周围变黑。 */
    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    // 注意：**不要**把 getShape / getCollisionShape 置空，否则准星射线会穿过机器打到后面的方块（Jade 显示地面）。

    /** 破坏任一填充格（含爆炸等）→ 找到主方块并整台破坏（掉落由主方块负责）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos machine = PetroMachineBlocks.findMachine(level, pos);
            if (machine != null) {
                level.destroyBlock(machine, !player.isCreative());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 偶尔自检：若附近已经没有主方块（成了孤儿填充格），自行消失，避免留下隐形墙。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (PetroMachineBlocks.findMachine(level, pos) == null) {
            level.removeBlock(pos, false);
        }
    }
}
