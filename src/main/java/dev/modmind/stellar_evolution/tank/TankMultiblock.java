package dev.modmind.stellar_evolution.tank;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * 储罐的<strong>多方块装配</strong>（照机械动力 {@code ConnectivityHandler} 的口径，砍到只服务储罐）。
 * <p>
 * 规则：一整片 6 面相连的储罐，会被切成若干<strong>方形底</strong>的长方体大罐：
 * 水平边长 <strong>1 / 3</strong> 格（**不做 2×2**）、竖直高 1 ~ {@value #MAX_HEIGHT} 格，必须<strong>实心填满</strong>。
 * 零件先全部复位成单格罐，再用「最小角起、贪心取最大长方体」的方式切分（正好覆盖 1×1、上下叠、3×3）。
 * <p>
 * 每个大罐的<strong>控制器</strong> = 其最小角那一格，由它独占存储（部件把流体全部汇入）；
 * 容量 = 格数 × 本档位单格容量（{@link TankTier}）。
 * <p><strong>同档位才拼</strong>：铜 / 钢 / 硬质三档互不合并（相邻两档视为各自独立的小罐）。
 * <p>
 * 外观（顶 / 底盖、内部面剔除、开窗）由每格相对「所属长方体」的位置推出，见 {@link #windowAt} + {@link TankBlock#shape}。
 */
final class TankMultiblock {
    /** 允许的水平边长（**方形底**；只做 1×1 与 3×3，不做 2×2）。 */
    private static final int[] WIDTHS = {1, 3};
    /** 竖直高度上限。 */
    static final int MAX_HEIGHT = 32;

    /** 贪心切分出的一个长方体：最小角 + 水平边长 + 高度。 */
    record Prism(BlockPos origin, int width, int height) {
        int size() {
            return width * width * height;
        }
    }

    /** 重算前的形态快照：只有真变了才推同步包（避免区块载入时白推一堆包）。 */
    private record Snapshot(@Nullable BlockPos controller, int width, int height, boolean window) {
        Snapshot(TankBlockEntity tank) {
            this(tank.controllerPos(), tank.width(), tank.height(), tank.window());
        }
    }

    /** 取最小角：先比 x、再比 z、最后比 y（长方体只会朝 +x/+y/+z 长）。 */
    private static final Comparator<BlockPos> MIN_CORNER = Comparator
            .comparingInt((BlockPos pos) -> pos.getX())
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getY);

    private TankMultiblock() {}

    /**
     * 重算「start 所在的那一整片储罐」的多方块形态。幂等：每 tick 只在有请求时跑一次。
     */
    static void resolve(ServerLevel level, BlockPos start) {
        List<TankBlockEntity> component = collect(level, start);
        if (component.isEmpty()) {
            return;
        }
        // 1) 全部先复位成单格罐（流体原样留在各自格子里，待会儿汇入控制器）
        Map<BlockPos, Snapshot> before = new HashMap<>();
        for (TankBlockEntity tank : component) {
            before.put(tank.getBlockPos(), new Snapshot(tank));
            tank.clearConnectivityRequest();
            tank.resetMulti();
        }
        // 2) 贪心切分
        Map<BlockPos, Prism> membership = new HashMap<>();
        List<Prism> prisms = partition(component, membership);
        // 3) 控制器先扩容，好接住各部件里的流体
        for (Prism prism : prisms) {
            TankBlockEntity controller = beAt(level, prism.origin());
            if (controller != null) {
                controller.configure(null, prism.width(), prism.height(), windowAt(0, 0, prism.width()),
                        prism.size() * controller.capacityPerBlock());
            }
        }
        // 4) 流体汇入控制器（放不下的残留直接丢弃：与「拆罐丢流体」的既有口径一致）
        for (Prism prism : prisms) {
            TankBlockEntity controller = beAt(level, prism.origin());
            if (controller == null) {
                continue;
            }
            forEachCell(prism, (pos, dx, dz) -> {
                if (pos.equals(prism.origin())) {
                    return;
                }
                TankBlockEntity part = beAt(level, pos);
                if (part != null) {
                    moveAll(part, controller);
                }
            });
            // 收完再夹一次：保证控制器「存量 ≤ 容量」（外部按 存量/容量 画进度条的代码依赖它）
            controller.clampContents();
        }
        // 5) 写回尺寸 / 开窗 + 刷新外观
        for (TankBlockEntity tank : component) {
            BlockPos pos = tank.getBlockPos();
            Prism prism = membership.get(pos);
            if (prism == null) {
                tank.refreshState();
                continue;
            }
            if (!pos.equals(prism.origin())) {
                int dx = pos.getX() - prism.origin().getX();
                int dz = pos.getZ() - prism.origin().getZ();
                tank.configure(prism.origin(), prism.width(), prism.height(), windowAt(dx, dz, prism.width()), 0);
            }
            tank.refreshState();
        }
        // 6) 形态真变了的格才把数据推给客户端（渲染器要按大罐尺寸画液面；部件也要让客户端知道自己不是控制器）
        for (TankBlockEntity tank : component) {
            Snapshot snapshot = before.get(tank.getBlockPos());
            if (!new Snapshot(tank).equals(snapshot)) {
                tank.pushUpdate();
            }
        }
    }

    /**
     * 破坏某格时，<strong>同一个大罐</strong>里还剩下的格子（不含被拆的那格）。
     *
     * <p><strong>只按控制器记录的长方体范围遍历</strong>（origin + width × height），
     * 绝不沿「相邻格子」泛洪 —— 并排但各自独立的小罐虽然 6 面相邻，却不是同一个大罐，
     * 一旦把流体拆过去就会变成「拆一个罐，隔壁罐凭空灌满」的刷流体漏洞。
     */
    static List<TankBlockEntity> cellsOf(ServerLevel level, TankBlockEntity controller, BlockPos excluded) {
        List<TankBlockEntity> result = new ArrayList<>();
        for (BlockPos pos : prismCells(controller.getBlockPos(),
                Math.max(1, controller.width()), Math.max(1, controller.height()))) {
            if (pos.equals(excluded)) {
                continue;
            }
            if (level.getBlockState(pos).getBlock() instanceof TankBlock block
                    && block.tier() == controller.tier()
                    && level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
                result.add(tank);
            }
        }
        return result;
    }

    /**
     * 一个大罐占的全部格子：原点起、水平 {@code width × width}、竖直 {@code height}。
     * <p>破坏储罐时「剩下的格子」必须由它算出来（而不是沿相邻格泛洪）—— 并排的两个独立小罐
     * 虽然 6 面相邻，但不是同一个大罐，把流体拆过去就是刷流体漏洞。
     */
    static List<BlockPos> prismCells(BlockPos origin, int width, int height) {
        List<BlockPos> cells = new ArrayList<>();
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < width; dz++) {
                    cells.add(origin.offset(dx, dy, dz));
                }
            }
        }
        return cells;
    }

    /** 某格是否开窗：1×1 全开；3×3 只有四条边的中间格开窗（四角与正中是铜板）。 */
    static boolean windowAt(int dx, int dz, int width) {
        if (width == 1) {
            return true;
        }
        return (dx == 1) != (dz == 1);
    }

    // ------------------------------------------------------------------ 组件收集 / 切分

    /** 从 start 出发做 6 面广度优先，收齐这一片<strong>同档位</strong>的储罐（越界 / 未加载的邻居自然不在其中）。 */
    private static List<TankBlockEntity> collect(ServerLevel level, BlockPos start) {
        if (!(level.getBlockEntity(start) instanceof TankBlockEntity origin)) {
            return List.of();
        }
        TankTier tier = origin.tier();
        List<TankBlockEntity> component = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(start);
        frontier.add(start);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            if (!(level.getBlockEntity(pos) instanceof TankBlockEntity tank) || tank.isRemoved()) {
                continue;
            }
            component.add(tank);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (visited.add(next) && level.getBlockState(next).getBlock() instanceof TankBlock block
                        && block.tier() == tier) {
                    frontier.add(next);
                }
            }
        }
        return component;
    }

    /** 贪心切分：每次取剩余格子里最小角那一格为起点，尝试最大的「方形底长方体」。 */
    private static List<Prism> partition(List<TankBlockEntity> component, Map<BlockPos, Prism> membership) {
        Set<BlockPos> remaining = new LinkedHashSet<>();
        for (TankBlockEntity tank : component) {
            remaining.add(tank.getBlockPos().immutable());
        }
        List<Prism> prisms = new ArrayList<>();
        while (!remaining.isEmpty()) {
            BlockPos origin = null;
            for (BlockPos pos : remaining) {
                if (origin == null || MIN_CORNER.compare(pos, origin) < 0) {
                    origin = pos;
                }
            }
            int bestWidth = 1;
            int bestHeight = 1;
            int bestSize = 0;
            for (int width : WIDTHS) {
                for (int height = 1; height <= MAX_HEIGHT; height++) {
                    int size = width * width * height;
                    if (size <= bestSize || !fits(remaining, origin, width, height)) {
                        continue;
                    }
                    bestWidth = width;
                    bestHeight = height;
                    bestSize = size;
                }
            }
            Prism prism = new Prism(origin, bestWidth, bestHeight);
            forEachCell(prism, (pos, dx, dz) -> {
                membership.put(pos, prism);
                remaining.remove(pos);
            });
            prisms.add(prism);
        }
        return prisms;
    }

    /** 长方体是否被剩余格子实心填满。 */
    private static boolean fits(Set<BlockPos> remaining, BlockPos origin, int width, int height) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < width; dz++) {
                    if (!remaining.contains(origin.offset(dx, dy, dz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 小工具

    private interface CellAction {
        void accept(BlockPos pos, int dx, int dz);
    }

    /** 遍历长方体所有格（水平按 dx / dz、竖直按 dy）。 */
    private static void forEachCell(Prism prism, CellAction action) {
        for (int dy = 0; dy < prism.height(); dy++) {
            for (int dx = 0; dx < prism.width(); dx++) {
                for (int dz = 0; dz < prism.width(); dz++) {
                    action.accept(prism.origin().offset(dx, dy, dz), dx, dz);
                }
            }
        }
    }

    @Nullable
    private static TankBlockEntity beAt(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TankBlockEntity tank ? tank : null;
    }

    /** 把 from 里的流体整份搬进 to（放不下的残留丢弃）。 */
    private static void moveAll(TankBlockEntity from, TankBlockEntity to) {
        FluidStacksResourceHandler source = from.rawFluids();
        FluidResource resource = source.getResource(0);
        int amount = source.getAmountAsInt(0);
        if (resource.isEmpty() || amount <= 0) {
            return;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = to.rawFluids().insert(0, resource, amount, transaction);
            if (moved > 0) {
                source.extract(0, resource, moved, transaction);
            }
            transaction.commit();
        }
        if (source.getAmountAsInt(0) > 0) {
            from.clearContents();
        }
    }
}
