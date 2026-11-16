package dev.modmind.stellar_evolution.bio;

import com.mojang.serialization.MapCodec;
import dev.modmind.stellar_evolution.large.LargeMachineBlock;
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
 * 生化机器的「填充格」：一台机器在世界上**真实占据 2×2×2 = 8 个方块格**，
 * 主方块（{@link BioMachineBlock}）只占其中一格，其余 7 格由本方块填充。
 * <p>
 * 为什么要这样做：原版方块碰撞形状**一般不能超过 1×1×1**（只有 +Y 能略超，如栅栏），
 * 单靠主方块的超格 {@code VoxelShape} 会漏格 —— 玩家 / AI 能钻进机器里。
 * 用 7 个 1×1×1 的填充格补齐后，碰撞才是**完整 8 格**，机器内部也无法再放方块。
 * <p>
 * 本方块：无物品、无可视模型（{@link RenderShape#INVISIBLE}）、不掉落、不挡光，
 * 但**挂了一个方块实体**（{@link BioMachineShellBlockEntity}）—— 只用来把机器本体的**物品 / 流体能力转发过来**，
 * 使「所有部分都视作本体」：瞄任意一格都能看到同样的物品 / 流体存量。
 */
public class BioMachineShellBlock extends BaseEntityBlock implements LargeMachineBlock {
    public static final MapCodec<BioMachineShellBlock> CODEC = simpleCodec(BioMachineShellBlock::new);

    public BioMachineShellBlock(Properties properties) {
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
        return BioMachineBlocks.findMachine(level, pos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BioMachineShellBlockEntity(pos, state);
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

    /** 不挡光：填充格只是「碰撞体」，绝不能让机器内部/周围变黑（默认对整格形状会挡 15 级光）。 */
    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    // 注意：**不要**把 getShape / getCollisionShape 置空。
    // 选择形状（getShape）参与「玩家准星射线 / Jade / 轮廓渲染」，置空会让射线穿过机器打到后面的方块
    // （表现为 Jade 显示地面方块）。这里保持默认的完整 1×1×1，既挡住玩家，也让准星正确命中机器。

    /** 破坏任一填充格（含爆炸等）→ 找到主方块并整台破坏（掉落由主方块负责）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos machine = BioMachineBlocks.findMachine(level, pos);
            if (machine != null) {
                level.destroyBlock(machine, !player.isCreative());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 偶尔自检：若附近已经没有主方块（成了孤儿填充格），自行消失，避免留下隐形墙。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (BioMachineBlocks.findMachine(level, pos) == null) {
            level.removeBlock(pos, false);
        }
    }
}
