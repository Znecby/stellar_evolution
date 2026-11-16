package dev.znecby.stellar_evolution.dev;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import dev.znecby.stellar_evolution.thermal.HotChamber;

/**
 * 快速配方添加书：右键打开 3×3 记录界面；蹲下右键完整热室则打开热室模式。
 * 仅开发用。
 */
public class QuickRecipeBookItem extends Item {
    public QuickRecipeBookItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!DevConfig.developerMode()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, QuickRecipeMode.CRAFTING_TABLE);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!DevConfig.developerMode() || player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!HotChamber.isComplete(level, context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, QuickRecipeMode.HOT_CHAMBER);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    private static void open(ServerPlayer player, QuickRecipeMode mode) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new QuickRecipeMenu(containerId, inventory, mode),
                Component.translatable(mode.titleKey())));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.stellar_evolution.quick_recipe_book.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
