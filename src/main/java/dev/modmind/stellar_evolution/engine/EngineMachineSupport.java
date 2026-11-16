package dev.modmind.stellar_evolution.engine;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.bio.BioMachineBlocks;
import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.pipe.CopperPipeBlock;
import dev.modmind.stellar_evolution.pipe.MachinePipeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * 三台燃烧类发电机组共用的「2×2×2 结构 / 取料 / 环境」工具：
 * <ul>
 *   <li>8 格占位（主方块在 min 角，其余 7 格由对应的填充格占位）—— 照生化 / 石化机器的成功口径；</li>
 *   <li><b>取料统一走机械管网</b>：燃料 / 助燃氧 / 水都从机械管连接的「输入」储罐（或铜格栅虚拟水源）抽取，
 *       与生化 / 石化机器同一口径（{@link LargeMachineFluidHandler}）；火箭只取燃料（氧化剂已预混）；</li>
 *   <li>12 个「机械管接点」（铜垫）状态刷新，与大机器框架一致；</li>
 *   <li>判断维度有没有大气（月球 = 真空：内燃机 / 燃气轮机不能烧，火箭可以，燃料自带氧化剂）。</li>
 * </ul>
 */
public final class EngineMachineSupport {
    /** 没有大气的维度（path 段）；火箭引擎不受限。 */
    private static final Set<String> VACUUM_DIMENSIONS = Set.of("moon", "orbit", "space");

    /** 一台机器占据的 8 格偏移（主方块在 (0,0,0)）。 */
    public static final int[][] CELL_OFFSETS = {
            {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
            {1, 1, 0}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}
    };

    /** 整台机器占的 8 格世界坐标。 */
    public static List<BlockPos> cells(BlockPos origin) {
        List<BlockPos> cells = new ArrayList<>(CELL_OFFSETS.length);
        for (int[] offset : CELL_OFFSETS) {
            cells.add(origin.offset(offset[0], offset[1], offset[2]));
        }
        return cells;
    }

    /** 该维度有没有可用的空气。 */
    public static boolean hasAtmosphere(Level level) {
        Identifier id = level.dimension().identifier();
        return !(id.getNamespace().equals(StellarEvolution.MOD_ID) && VACUUM_DIMENSIONS.contains(id.getPath()));
    }

    /** 8 格里除主方块外的 7 格是否都能被占用（放机器前的检查）。 */
    public static boolean canOccupy(BlockGetter level, BlockPos origin) {
        for (int[] offset : CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            if (!level.getBlockState(origin.offset(offset[0], offset[1], offset[2])).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** 把另外 7 格填成对应的不可见填充格；返回本次补了几格。 */
    public static int fillShells(Level level, BlockPos origin, Block shell) {
        int filled = 0;
        for (int[] offset : CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
            if (level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, shell.defaultBlockState(), 3);
                filled++;
            }
        }
        return filled;
    }

    /** 清掉一台机器占的另外 7 格填充格（任一种引擎填充格都清）。 */
    public static void clearShells(Level level, BlockPos origin) {
        for (int[] offset : CELL_OFFSETS) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue;
            }
            BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
            if (level.getBlockState(pos).getBlock() instanceof EngineMachineShellBlock) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** 从某个填充格出发，找出它所属机器的主方块位置（破坏填充格时用）。 */
    @Nullable
    public static BlockPos findMachine(BlockGetter level, BlockPos shellPos) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos candidate = shellPos.offset(-dx, -dy, -dz);
                    if (level.getBlockState(candidate).getBlock() instanceof EngineMachineBlock) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 刷新 12 个「机械管接点」状态（与生化 / 石化机器同一轮询口径，见 {@link BioMachineBlocks#refreshPorts}）：
     * 接点上贴着「愿意连这台机器」的机械管 → 铜垫 capped，否则 open。
     */
    public static boolean refreshPorts(Level level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof EngineMachineBlock)) {
            return false;
        }
        BlockState updated = state;
        for (BioMachineBlocks.Port port : BioMachineBlocks.PORTS) {
            BlockPos portPos = origin.offset(port.dx(), port.dy(), port.dz());
            BlockState pipeState = level.getBlockState(portPos);
            boolean connected = (pipeState.getBlock() instanceof MachinePipeBlock
                    && pipeState.getValue(MachinePipeBlock.CONNECTIONS.get(port.towardMachine())))
                    || (pipeState.getBlock() instanceof CopperPipeBlock
                    && CopperPipeBlock.connectsToMachine(level, portPos,
                    portPos.relative(port.towardMachine()), port.towardMachine()));
            if (updated.getValue(port.property()) != connected) {
                updated = updated.setValue(port.property(), connected);
            }
        }
        if (updated == state) {
            return false;
        }
        level.setBlock(origin, updated, Block.UPDATE_ALL);
        return true;
    }

    /** 在机械管网的「输入」储罐里找第一种符合条件的流体；没有则 null。 */
    @Nullable
    public static Fluid findNetworkFluid(LargeMachineFluidHandler fluids, Predicate<Fluid> match) {
        FluidResource resource = fluids.firstInInputs(fluidResource -> match.test(fluidResource.getFluid()));
        return resource == null ? null : resource.getFluid();
    }

    /** 从机械管网「输入」储罐抽 amount mB 指定流体；返回实际抽到的量。 */
    public static int drainNetwork(LargeMachineFluidHandler fluids, Fluid fluid, int amount) {
        if (amount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int got = fluids.extractFromInputs(FluidResource.of(fluid), amount, transaction);
            if (got > 0) {
                transaction.commit();
            }
            return got;
        }
    }

    /**
     * 三台机组的运行状态（Jade 用，服务端每 tick 结算一次）。
     * <p>顺序即优先级：真空 > 无燃料 > 缺液氧 > 运转 / 停机。
     */
    public enum FuelState {
        /** 有燃料在烧（是否有液氧另由 {@code oxygenated} 单独报，缺氧只降出力）。 */
        RUNNING,
        /** 停机（转速 / 室压已归零）。 */
        IDLE,
        /** 管网「输入」储罐里没有符合这台机器的燃料。 */
        NO_FUEL,
        /** 有燃料但缺液氧：内燃 / 燃气仍能烧，出力受限；火箭不会进入此状态。 */
        NO_OXYGEN,
        /** 真空维度（月球 / 轨道 / 太空）：内燃机与燃气轮机无法燃烧，火箭不受限。 */
        VACUUM
    }

    private EngineMachineSupport() {}
}
