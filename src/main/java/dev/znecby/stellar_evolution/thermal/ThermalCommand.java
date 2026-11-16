package dev.znecby.stellar_evolution.thermal;

import com.mojang.brigadier.context.CommandContext;
import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.farmland.MechanicalGrassBlock;
import dev.znecby.stellar_evolution.farmland.MechanicalSoilBlockEntity;
import dev.znecby.stellar_evolution.farmland.MechanicalTreeHarvest;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 热压差网络的查看命令：{@code /se thermal}。
 *
 * <p>以「准星瞄到的方块」为起点（没瞄到方块就用玩家脚下），扫描它所在的那张热压差网络并汇报：
 * 生成端几个 / 容量多少 DP / 热流率多少 HP/t、消耗端几个 / 占用多少 DP / 负载率、传递件几个、是否超载。
 *
 * <p>这一版是<strong>网络验收工具</strong>：可以扫描已接入的生成端 / 消耗端 / 传递件与超载状态。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class ThermalCommand {
    private static final double REACH = 8.0D;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("se")
                        .then(Commands.literal("thermal")
                                .executes(ThermalCommand::report))
                        .then(Commands.literal("tree")
                                .executes(ThermalCommand::treeDebug)));
    }

    /** {@code /se tree}：对着机械草地（或其上方树苗）执行，逐项打印「虚拟长树」的诊断。 */
    private static int treeDebug(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = targetOf(source);
        BlockPos grassPos;
        if (level.getBlockState(pos).getBlock() instanceof MechanicalGrassBlock) {
            grassPos = pos;
        } else if (level.getBlockState(pos.below()).getBlock() instanceof MechanicalGrassBlock) {
            grassPos = pos.below();
        } else {
            source.sendFailure(Component.literal("[机械草地] 准星没瞄到机械草地（或其正上方一格）"));
            return 0;
        }

        BlockPos saplingPos = grassPos.above();
        BlockState sapling = level.getBlockState(saplingPos);
        boolean hasContainer = level.getBlockEntity(grassPos.below()) instanceof Container;
        source.sendSuccess(() -> Component.literal("[机械草地] 草地=" + formatPos(grassPos)
                + "；上方=" + sapling.getBlock() + "；是树苗=" + MechanicalTreeHarvest.isTree(sapling)
                + "；底部有容器=" + hasContainer), false);

        if (!MechanicalTreeHarvest.isTree(sapling)) {
            return 0;
        }
        MechanicalTreeHarvest.Tree tree = MechanicalTreeHarvest.treeFor(sapling);
        source.sendSuccess(() -> Component.literal("[机械草地] 规模表（原木数 / 树叶数 / 原木 / 掉苹果）= " + tree.logs()
                + " / " + tree.leaves() + " / " + tree.log() + " / " + tree.apple()), false);
        if (tree.blocks() > 0) {
            List<ItemStack> drops = MechanicalTreeHarvest.rollHarvest(sapling, tree, level.getRandom());
            int count = drops.stream().mapToInt(ItemStack::getCount).sum();
            boolean fit = hasContainer && level.getBlockEntity(grassPos.below()) instanceof Container container
                    && MechanicalSoilBlockEntity.canFitAll(container, drops);
            source.sendSuccess(() -> Component.literal("[机械草地] 掉落：合并后 " + drops.size()
                    + " 堆 / 共 " + count + " 件；整批塞得下=" + fit), false);
        }
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos origin = targetOf(source);

        ThermalNetwork.Scan scan = ThermalNetwork.scan(level, origin);
        ThermalLoad load = scan.load();

        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.thermal.header", formatPos(origin)), false);

        if (scan.generatorCount() == 0 && scan.consumerCount() == 0 && scan.transmitterCount() == 0) {
            source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.thermal.none"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.thermal.generators",
                scan.generatorCount(), decimal(load.capacity()), decimal(load.flowRate())), false);
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.thermal.consumers",
                scan.consumerCount(), decimal(load.occupancy()), percent(load.loadRatio())), false);
        source.sendSuccess(() -> Component.translatable("commands.stellar_evolution.thermal.transmitters",
                scan.transmitterCount()), false);
        source.sendSuccess(() -> Component.translatable(load.overloaded()
                ? "commands.stellar_evolution.thermal.status.overloaded"
                : "commands.stellar_evolution.thermal.status.ok"), false);
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

    /** 固定用 ROOT 区域格式化，避免不同语言环境出逗号小数点（{@code 12,5}）；≥ 10⁹ 用科学计数法。 */
    private static String decimal(double value) {
        if (Math.abs(value) >= 1.0e9D) {
            return String.format(Locale.ROOT, "%.2E", value).replace("E+", "E");
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(double ratio) {
        if (!Double.isFinite(ratio)) {
            return "∞";
        }
        return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0D);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private ThermalCommand() {}
}
