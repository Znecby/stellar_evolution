package dev.modmind.stellar_evolution.petro;

import com.mojang.brigadier.context.CommandContext;
import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.List;
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
 * 石化线的验收命令：{@code /se petro}。
 *
 * <ul>
 *   <li>{@code /se petro recipes} —— 逐机种列出配方（直接读 {@link PetroLine} 行表，不依赖世界）；</li>
 *   <li>{@code /se petro machine} —— 看准星所指石化机器的机种 / 状态 / 进度。</li>
 * </ul>
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class PetroCommand {
    private static final double REACH = 8.0D;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("se")
                        .then(Commands.literal("petro")
                                .then(Commands.literal("recipes").executes(PetroCommand::listRecipes))
                                .then(Commands.literal("machine").executes(PetroCommand::machine))));
    }

    /** {@code /se petro recipes}：逐机种列出配方。 */
    private static int listRecipes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        for (PetroMachineKind kind : PetroMachineKind.values()) {
            List<String> lines = PetroRecipes.describe(kind);
            Component name = Component.translatable("block.stellar_evolution." + kind.id());
            source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.petro.recipes", name, lines.size()), false);
            for (String line : lines) {
                source.sendSuccess(() -> Component.literal(" - " + line), false);
            }
        }
        return 1;
    }

    /** {@code /se petro machine}：看准星所指石化机器的状态。 */
    private static int machine(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos target = targetOf(source);
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (!(blockEntity instanceof PetroMachineBlockEntity machine)
                || !PetroMachineBlocks.isMachine(blockEntity.getBlockState().getBlock())) {
            source.sendFailure(Component.translatable("commands.stellar_evolution.petro.machine.none"));
            return 0;
        }
        PetroMachineKind kind = machine.kind();
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.petro.machine.header",
                target.getX() + ", " + target.getY() + ", " + target.getZ()), false);
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.petro.machine.kind",
                Component.translatable("block.stellar_evolution." + kind.id())), false);
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.petro.machine.status",
                Component.translatable("commands.stellar_evolution.petro.status." + machine.status())), false);
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.petro.machine.progress",
                decimal(machine.progress())), false);
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

    /** ≥ 10⁹ 用科学计数法（网络容量 / 占用可能到十几位）。 */
    private static String decimal(double value) {
        if (Math.abs(value) >= 1.0e9D) {
            return String.format(Locale.ROOT, "%.2E", value).replace("E+", "E");
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private PetroCommand() {}
}
