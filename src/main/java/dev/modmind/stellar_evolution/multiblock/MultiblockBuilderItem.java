package dev.modmind.stellar_evolution.multiblock;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 一键搭建工具（附魔木棍外观）：<strong>潜行 + 右键任意多方块控制器</strong>，
 * 按控制器当前朝向把整座图案强制铺到世界里。
 *
 * <p>只替换图案里的实体格，空气格不动；未加载的区块跳过不建。
 * 摆放完给控制器排一次延迟校验，2 tick 后自动成型。
 */
public class MultiblockBuilderItem extends Item {
    public MultiblockBuilderItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.stellar_evolution.multiblock_builder.tooltip"));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos controllerPos = context.getClickedPos();
        if (!(level.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        MultiblockPattern pattern = controller.pattern();
        if (pattern == null) {
            return InteractionResult.PASS;
        }

        Direction facing = controller.facing();
        int placed = 0;
        int skipped = 0;
        for (Map.Entry<BlockPos, MultiblockCell> entry : pattern.cells().entrySet()) {
            Block block = buildBlock(entry.getValue());
            if (block == null) {
                continue;
            }
            BlockPos world = pattern.toWorld(controllerPos, facing, entry.getKey());
            if (!level.isLoaded(world)) {
                skipped++;
                continue;
            }
            place(level, world, block.defaultBlockState());
            placed++;
        }

        controller.requestValidation(true, 2);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.stellar_evolution.multiblock_builder.result", placed, skipped));
        }
        return InteractionResult.SUCCESS;
    }

    /** 只有实体方块需要摆放；控制器格跳过。 */
    @Nullable
    private static Block buildBlock(MultiblockCell cell) {
        if (cell instanceof MultiblockCell.BlockCell blockCell) {
            return blockCell.block();
        }
        if (cell instanceof MultiblockCell.AirCell) {
            return Blocks.AIR;
        }
        return null;
    }

    private static void place(Level level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).equals(state)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }
}
