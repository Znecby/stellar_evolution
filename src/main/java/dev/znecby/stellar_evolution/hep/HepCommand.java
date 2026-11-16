package dev.znecby.stellar_evolution.hep;

import com.mojang.brigadier.context.CommandContext;
import dev.znecby.stellar_evolution.StellarEvolution;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 高能物理线的验收命令：{@code /se hep}。
 *
 * <ul>
 *   <li>{@code /se hep recipes} —— 列出回旋加速器配方与差速需求；</li>
 *   <li>{@code /se hep machine} —— 看准星所指回旋加速器的差速 / 状态。</li>
 * </ul>
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class HepCommand {
    private static final double REACH = 8.0D;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("se")
                        .then(Commands.literal("hep")
                                .then(Commands.literal("recipes").executes(HepCommand::listRecipes))
                                .then(Commands.literal("machine").executes(HepCommand::machine))));
    }

    private static int listRecipes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.hep.recipes.header",
                CyclotronRecipes.all().size()), false);
        for (CyclotronRecipe recipe : CyclotronRecipes.all()) {
            String outputs = describeOutputs(recipe);
            source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.hep.recipes.line",
                    Component.translatable(recipe.input().getDescriptionId()), recipe.inputCount(),
                    (long) recipe.demand(), outputs), false);
        }
        return 1;
    }

    private static String describeOutputs(CyclotronRecipe recipe) {
        StringBuilder builder = new StringBuilder();
        for (CyclotronRecipe.ItemOutput item : recipe.itemOutputs()) {
            if (!builder.isEmpty()) {
                builder.append(" + ");
            }
            builder.append(Component.translatable(item.item().getDescriptionId()).getString())
                    .append(" x").append(item.count());
        }
        for (CyclotronRecipe.FluidOutput fluid : recipe.fluidOutputs()) {
            if (!builder.isEmpty()) {
                builder.append(" + ");
            }
            builder.append(fluid.fluid().getFluidType().getDescription().getString())
                    .append(" ").append(fluid.amount()).append(" mB");
        }
        return builder.toString();
    }

    private static int machine(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos target = targetOf(source);
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (!(blockEntity instanceof CyclotronBlockEntity cyclotron)) {
            source.sendFailure(Component.translatable("commands.stellar_evolution.hep.machine.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.hep.machine.header",
                target.getX() + ", " + target.getY() + ", " + target.getZ()), false);
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.hep.machine.charge",
                decimal(cyclotron.cyclotronCharge()), decimal(cyclotron.cyclotronCap())), false);
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.hep.machine.recipe",
                (long) cyclotron.cyclotronDemand(), cyclotron.cyclotronParallel(), cyclotron.cyclotronOps()), false);
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.hep.machine.state",
                cyclotron.cyclotronStateName()), false);
        return 1;
    }

    private static BlockPos targetOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return BlockPos.containing(source.getPosition());
        }
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return player.blockPosition();
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private HepCommand() {}
}
