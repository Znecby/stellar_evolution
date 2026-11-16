package dev.znecby.stellar_evolution.thermal;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * <strong>创造差压</strong>：热压差网络的调试用电源，容量恒为 long 上限的 DP（见
 * {@link CreativePressureBlockEntity#CAPACITY}），热流率可现场调。
 * <p>
 * 调法（步进 64，蹲下 ×16 = 1024）：
 * <ul>
 *   <li><strong>空手右键</strong>：+64 HP/t（蹲下 +1024）；</li>
 *   <li><strong>空手左键</strong>：-64 HP/t（蹲下 -1024）—— 由 {@link CreativePressureEvents} 接住并拦下破坏，
 *       手里拿着东西时左键仍是正常挖掘 / 破坏。</li>
 * </ul>
 * 材质用原版铜块。
 */
public class CreativePressureBlock extends BaseEntityBlock {
    public CreativePressureBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(CreativePressureBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativePressureBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 空手右键：+64（蹲下 +1024）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        return adjust(level, pos, player, 1L);
    }

    /**
     * 改这台电源的热流率：{@code sign} 为 +1（右键）/ -1（左键）。
     * 只在<strong>服务端</strong>落账，并把新值用动作栏文字回显给玩家。
     */
    static InteractionResult adjust(Level level, BlockPos pos, Player player, long sign) {
        if (!(level.getBlockEntity(pos) instanceof CreativePressureBlockEntity source)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            long step = (player.isShiftKeyDown()
                    ? CreativePressureBlockEntity.STEP_LARGE
                    : CreativePressureBlockEntity.STEP_SMALL) * sign;
            long value = source.addFlowRate(step);
            player.sendOverlayMessage(Component.translatable("stellar_evolution.creative_pressure.set", value));
        }
        return InteractionResult.SUCCESS;
    }
}
