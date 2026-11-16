package dev.modmind.stellar_evolution.equipment;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** 钢锄：范围耕地 / 连锁收割 / 时运 / 精准采集。 */
public class SteelHoeItem extends HoeItem implements ModeTool {
    public SteelHoeItem(ToolMaterial material, float attackDamage, float attackSpeed, Properties properties) {
        super(material, attackDamage, attackSpeed, properties);
    }

    @Override
    public SteelToolKind kind() {
        return SteelToolKind.HOE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        SteelModeSupport.appendTooltip(stack, tooltip);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!hasFeature(stack, SteelToolFeature.AREA)) {
            return super.useOn(context);
        }
        InteractionResult result = super.useOn(context);
        if (!result.consumesAction() || context.getLevel().isClientSide()) {
            return result;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return result;
        }
        Direction face = context.getClickedFace();
        int radius = areaRadius(stack);
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                BlockPos pos = offset(context.getClickedPos(), face.getAxis(), a, b);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
                super.useOn(new UseOnContext(player, context.getHand(), hit));
            }
        }
        return result;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        SteelCrafting.applyUnbreaking(stack, player);
    }

    private static BlockPos offset(BlockPos origin, Direction.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> origin.offset(0, a, b);
            case Y -> origin.offset(a, 0, b);
            case Z -> origin.offset(a, b, 0);
        };
    }
}
