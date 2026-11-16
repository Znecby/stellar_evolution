package dev.modmind.stellar_evolution.engine;

import com.mojang.serialization.MapCodec;
import dev.modmind.stellar_evolution.large.LargeMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 燃烧类发电机组的「填充格」：一台机器真实占据 2×2×2 = 8 个方块格，
 * 主方块只占其中一格，其余 7 格由本方块填充 —— 照生化 / 石化机器（{@code BioMachineShellBlock}）的成功口径。
 *
 * <p>原版方块碰撞形状一般不能超过 1×1×1，单靠主方块的超格 {@code VoxelShape} 会漏格（玩家能钻进机器）；
 * 用 7 个 1×1×1 的填充格补齐后，碰撞才是完整 8 格。
 *
 * <p>本方块：无物品、无可视模型（{@link RenderShape#INVISIBLE}）、不掉落、不挡光。
 * 它实现 {@link LargeMachineBlock}，让热网 / 蒸汽网络从<b>任意一格</b>都能反查到机器本体
 * （否则压力管 / 铜管接在填充格那一面时找不到机器 —— 就是「机器不工作」的根因）。
 */
public class EngineMachineShellBlock extends Block implements LargeMachineBlock {
    public static final MapCodec<EngineMachineShellBlock> CODEC = simpleCodec(EngineMachineShellBlock::new);

    public EngineMachineShellBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** 填充格属于哪台机器：反查主方块位置。 */
    @Nullable
    @Override
    public BlockPos largeMachineOrigin(BlockGetter level, BlockPos pos) {
        return EngineMachineSupport.findMachine(level, pos);
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

    // 注意：**不要**把 getShape / getCollisionShape 置空，否则准星射线会穿过机器打到后面的方块。

    /** 破坏任一填充格（含爆炸等）→ 找到主方块并整台破坏（掉落由主方块负责）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos machine = EngineMachineSupport.findMachine(level, pos);
            if (machine != null) {
                level.destroyBlock(machine, !player.isCreative());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 偶尔自检：若附近已经没有主方块（成了孤儿填充格），自行消失，避免留下隐形墙。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (EngineMachineSupport.findMachine(level, pos) == null) {
            level.removeBlock(pos, false);
        }
    }
}
