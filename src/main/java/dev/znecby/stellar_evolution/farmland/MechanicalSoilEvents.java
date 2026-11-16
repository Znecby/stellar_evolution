package dev.znecby.stellar_evolution.farmland;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 机械土壤的右键肥料入口。
 *
 * <p>方块自己的 {@code useItemOn} 只覆盖「准星正对土壤」的情况；实际上玩家经常对着
 * <strong>土壤上面的作物</strong>右键（小麦 / 下界疣 / 仙人掌 / 甘蔗 / 树苗等），
 * 这时交互会被作物截走，土壤收不到肥料。这里在右键事件里补一层：
 * 手持肥料右键任意方块时，如果该方块本身或它下面一格是机械土壤，就把肥料折成点数。
 *
 * <p>只在服务端处理；点数满 / 不是肥料时完全不拦截，交还原版行为。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class MechanicalSoilEvents {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        ItemStack held = player.getItemInHand(event.getHand());
        if (!MechanicalSoilBlockEntity.isFertilizer(held)) {
            return;
        }

        Level level = event.getLevel();
        BlockPos clicked = event.getPos();
        BlockPos soilPos = findSoil(level, clicked);
        if (soilPos == null || !(level.getBlockEntity(soilPos) instanceof MechanicalSoilBlockEntity soil)) {
            return;
        }
        if (!soil.addFertilizer(held)) {
            return;   // 点数已满 / 不接收：不拦截，交还原版
        }

        event.setCanceled(true);
    }

    /**
     * 从准星指到的方块往下找机械土壤：允许直接点土壤，也允许点土壤上的作物
     * （小麦 / 下界疣 / 仙人掌 / 甘蔗 / 树苗 / 甜浆果丛等）。最多找 4 格，
     * 够覆盖甘蔗 / 仙人掌的整柱；点到石头、箱子之类非植物方块时不会误伤。
     */
    private static BlockPos findSoil(Level level, BlockPos clicked) {
        BlockPos cursor = clicked;
        for (int i = 0; i < 4; i++) {
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof MechanicalSoil) {
                return cursor;
            }
            if (!isPlant(state)) {
                return null;
            }
            cursor = cursor.below();
        }
        return null;
    }

    private static boolean isPlant(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BushBlock || block instanceof SugarCaneBlock || block instanceof CactusBlock
                || block instanceof GrowingPlantBlock || block instanceof StemBlock;
    }

    private MechanicalSoilEvents() {}
}
