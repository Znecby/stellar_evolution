package dev.modmind.stellar_evolution.large;

import com.mojang.brigadier.context.CommandContext;
import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.bio.BioRecipe;
import dev.modmind.stellar_evolution.bio.BioRecipeType;
import dev.modmind.stellar_evolution.bio.BioRecipes;
import dev.modmind.stellar_evolution.thermal.ThermalLoad;
import dev.modmind.stellar_evolution.thermal.ThermalNetwork;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 大机器框架的验收命令：{@code /se large}。
 *
 * <p>以「准星瞄到的方块」为起点：瞄到大型机器（或其填充格）就用它；瞄到物流容器就反查它所属的机器。
 * 汇报 —— 机器名、热压差是否接入（经端口铜块）、物流网络里的容器清单（位置 + 输入 / 输出方向）。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class LargeMachineCommand {
    private static final double REACH = 8.0D;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("se")
                        .then(Commands.literal("large")
                                .executes(LargeMachineCommand::report)
                                .then(Commands.literal("recipes")
                                        .executes(LargeMachineCommand::listRecipes))));
    }

    /** {@code /se large recipes}：逐类列出正式生化配方链（不依赖世界）。 */
    private static int listRecipes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        for (BioRecipeType type : BioRecipeType.values()) {
            List<BioRecipe> recipes = BioRecipes.forType(type);
            if (recipes.isEmpty()) {
                continue;
            }
            Component name = Component.translatable("block.stellar_evolution." + type.machine().name().toLowerCase(Locale.ROOT));
            source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.large.recipes",
                    name, recipes.size()), false);
            for (BioRecipe recipe : recipes) {
                String conditions = recipe.describeConditions();
                String line = recipe.describeInputs() + "  →  " + recipe.describeOutputs()
                        + (conditions.isEmpty() ? "" : "   [" + conditions + "]");
                source.sendSuccess(() -> Component.literal(" - " + line), false);
            }
        }
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos target = targetOf(source);

        LargeMachine machine = LargeMachines.machineAt(level, target);
        if (machine == null) {
            BlockPos origin = LargeMachines.originOf(level, target);
            machine = origin == null ? null : LargeMachines.machineAt(level, origin);
        }
        if (machine == null) {
            machine = LargeMachines.containerMachine(level, target);
        }
        if (machine == null) {
            source.sendFailure(Component.translatable("commands.stellar_evolution.large.none"));
            return 0;
        }

        LargeMachine resolved = machine;
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.large.header",
                formatPos(resolved.origin())), false);
        BlockEntity blockEntity = level.getBlockEntity(resolved.origin());
        String name = blockEntity == null ? "?" : blockEntity.getBlockState().getBlock().getName().getString();
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.large.machine", name), false);

        if (LargeMachines.touchesCopperHeat(level, resolved)) {
            ThermalLoad load = ThermalNetwork.loadAt(level, resolved.origin());
            source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.large.thermal_on",
                    decimal(load.occupancy()), decimal(load.flowRate())), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.large.thermal_off"), false);
        }

        List<LargeMachines.Connected> containers = LargeMachines.connectedContainers(level, resolved);
        if (containers.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.large.containers_none"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.large.containers",
                containers.size()), false);
        for (LargeMachines.Connected connected : containers) {
            BlockState state = level.getBlockState(connected.pos());
            String containerName = state.getBlock().getName().getString();
            LargeMachineIo io = resolved.ioMode(connected.pos());
            String ioKey = io == LargeMachineIo.INPUT
                    ? "stellar_evolution.large.io.input"
                    : "stellar_evolution.large.io.output";
            source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.large.container",
                    containerName, formatPos(connected.pos()), Component.translatable(ioKey)), false);
        }
        return 1;
    }

    /** 准星瞄到的方块；没瞄到就用玩家所在格。 */
    private static BlockPos targetOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return BlockPos.containing(source.getPosition());
        }
        HitResult hit = player.pick(REACH, 1.0F, false);
        return hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : player.blockPosition();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** 固定用 ROOT 区域格式化；≥ 10⁹ 用科学计数法（网络容量 / 占用可能到十几位）。 */
    private static String decimal(double value) {
        if (Math.abs(value) >= 1.0e9D) {
            return String.format(java.util.Locale.ROOT, "%.2E", value).replace("E+", "E");
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private LargeMachineCommand() {}
}
