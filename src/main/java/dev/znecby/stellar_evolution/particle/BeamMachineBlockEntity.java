package dev.znecby.stellar_evolution.particle;

import dev.znecby.stellar_evolution.fission.FissionMachineBlockEntity;
import dev.znecby.stellar_evolution.fission.FissionMachineKind;
import dev.znecby.stellar_evolution.fission.FissionMachines;
import dev.znecby.stellar_evolution.pipe.BeamPipeBlock;
import dev.znecby.stellar_evolution.stream.StreamConsumer;
import dev.znecby.stellar_evolution.stream.StreamEndpoint;
import dev.znecby.stellar_evolution.stream.StreamNetwork;
import dev.znecby.stellar_evolution.stream.StreamType;
import dev.znecby.stellar_evolution.thermal.ThermalLoad;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * <strong>束流机器的通用方块实体</strong>：<strong>辐照仓</strong>与<strong>对撞机</strong>共用。
 *
 * <p>机器<strong>不预设自己吃哪一种流</strong>：旁边接了几种束流管，就把那几束网络都算作自己的来源，
 * 由配方表（{@link BeamRecipes}）决定<strong>当前吃哪一路 / 哪两路</strong>：
 * <ol>
 *   <li>每 tick 扫一遍相邻管格，得到「已接入的流类型集合」；</li>
 *   <li>按表序挑一条<strong>输入够 + 所有需求流都接着</strong>的配方，并<strong>粘住</strong>它；</li>
 *   <li>每一路需求<strong>各自累计进度</strong>，只对还没吃满的那几路报需求；</li>
 *   <li>所有路都吃满才扣料出产物；对撞机还会同时向外<strong>喷出输出流</strong>（{@link StreamEndpoint}）。</li>
 * </ol>
 * 同一种流如果接了两段互不相连的管网，只取<strong>供流最多的那一段</strong>（不叠加，避免双算）。
 */
public class BeamMachineBlockEntity extends FissionMachineBlockEntity implements StreamConsumer, StreamEndpoint {
    /** 机器状态（Jade / 调试用）。 */
    public enum State {
        IDLE,
        NO_STREAM,
        RUNNING,
        OUTPUT_BLOCKED
    }

    /** 本 tick 已接入的流类型（由相邻管段决定）。 */
    private final Set<StreamType> wired = EnumSet.noneOf(StreamType.class);
    /** 每一路需求各自的累计进度。 */
    private final Map<StreamType, Double> progress = new EnumMap<>(StreamType.class);

    private State state = State.IDLE;
    private BeamRecipes.Recipe lastRecipe;
    /** 当前正在吃的配方 id：有料就一直吃它，吃空后才换下一条（避免多配方互相打断进度）。 */
    private String currentRecipeId = "";
    /** 本 tick 这一路累计分到的量（Jade / 输出速率用）。 */
    private double activeShare;
    /** 累计喷出的粒子流总量（源侧统计）。 */
    private double emitted;
    /** 已接入的加速环段数量（上限 16）。 */
    private int ringLevel;

    public BeamMachineBlockEntity(BlockPos pos, BlockState state) {
        this(FissionMachines.kindOf(state.getBlock()) == FissionMachineKind.COLLIDER
                        ? FissionMachines.COLLIDER_ENTITY.get()
                        : FissionMachines.IRRADIATION_CHAMBER_ENTITY.get(),
                pos, state);
    }

    /** 子类（ADS）用：显式指定自己的方块实体类型。 */
    protected BeamMachineBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                     BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------ 结算

    @Override
    public void onThermalTick(ThermalLoad load) {
        if (getLevel() == null || getLevel().isClientSide()) {
            return;
        }
        // 先扫管：吃哪几路由「接了哪些管」决定，必须在取快照之前算好
        scanWiredTypes();
        // 加速环段只在每 20t 重扫一次（放置 / 拆除后最多 1 秒生效），避免每 tick 查 48 个邻接方块
        if (getLevel().getGameTime() % 20L == 0L) {
            ringLevel = scanRingLevel();
        }
        BeamRecipes.Recipe recipe = currentRecipe();
        lastRecipe = recipe;
        activeShare = 0.0D;
        if (recipe == null) {
            progress.clear();
            currentRecipeId = "";
            state = State.IDLE;
            working = false;
            syncState();
            return;
        }
        // 换配方 → 所有路的进度从零开始
        if (!recipe.id().equals(currentRecipeId)) {
            currentRecipeId = recipe.id();
            progress.clear();
        }
        if (load.overloaded() || load.flowRate() <= 0.0D) {
            state = State.NO_STREAM;
            working = false;
            syncState();
            return;
        }
        // 逐路取流：网络给多少吃多少（不再按 demand 限速）；已吃满的那一路不再报需求
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            if (progressFor(demand.type()) >= demand.required()) {
                continue;
            }
            double share = shareOf(demand.type());
            if (share > 0.0D) {
                streamAccept(demand.type(), share);
                activeShare += share;
            }
        }
        working = activeShare > 0.0D;
        // 硬门槛：**每一路**都必须吃满才允许结算（缺一路就一直等）
        boolean ready = true;
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            if (progressFor(demand.type()) < demand.required()) {
                ready = false;
                break;
            }
        }
        if (ready) {
            if (craft(recipe)) {
                progress.clear();
            } else {
                // 产物放不下：保持满进度，等输出腾出空间
                for (BeamRecipes.StreamDemand demand : recipe.demands()) {
                    progress.put(demand.type(), demand.required());
                }
                state = State.OUTPUT_BLOCKED;
                working = false;
                syncState();
                return;
            }
        }
        state = working ? State.RUNNING : State.NO_STREAM;
        if (!working && allSatisfied(recipe)) {
            state = State.OUTPUT_BLOCKED;
        }
        emitted += streamRate();
        syncState();
    }

    /** 扫一遍机器 8 格相邻的束流管，收集「已接入的流类型」（只算**真的接到这一面**的管，空管不算）。 */
    private void scanWiredTypes() {
        wired.clear();
        Level level = getLevel();
        if (level == null) {
            return;
        }
        for (BlockPos cell : occupiedCells()) {
            for (Direction direction : Direction.values()) {
                BlockState neighbor = level.getBlockState(cell.relative(direction));
                if (!(neighbor.getBlock() instanceof BeamPipeBlock)) {
                    continue;
                }
                // 管子的连接状态要朝着机器这一面，否则只是「贴着」而不是「接着」
                if (!neighbor.getValue(BeamPipeBlock.CONNECTIONS.get(direction.getOpposite()))) {
                    continue;
                }
                int stream = neighbor.getValue(BeamPipeBlock.STREAM);
                if (stream != BeamPipeBlock.EMPTY) {
                    wired.add(StreamType.byOrdinal(stream));
                }
            }
        }
    }

    /**
     * 当前配方：<strong>先粘住上一条</strong>（只要它还有料、且那几路还接着），
     * 否则按表序取第一条「输入够 + 所有需求流都接着」的。
     */
    private BeamRecipes.Recipe currentRecipe() {
        List<BeamRecipes.Recipe> recipes = BeamRecipes.forMachine(kind());
        if (!currentRecipeId.isEmpty()) {
            for (BeamRecipes.Recipe recipe : recipes) {
                if (recipe.id().equals(currentRecipeId) && isReady(recipe)) {
                    return recipe;
                }
            }
        }
        for (BeamRecipes.Recipe recipe : recipes) {
            if (isReady(recipe)) {
                return recipe;
            }
        }
        return null;
    }

    /** 这一条能不能开工：所有需求流都接着，且输入够。 */
    private boolean isReady(BeamRecipes.Recipe recipe) {
        if (ringLevel < recipe.minRingLevel()) {
            return false;
        }
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            if (!wired.contains(demand.type())) {
                return false;
            }
        }
        if (recipe.input() != null) {
            ItemResource wanted = ItemResource.of(new ItemStack(recipe.input()));
            if (items().countInInputs(wanted) < recipe.inputCount()) {
                return false;
            }
        }
        for (BeamRecipes.FluidInput input : recipe.fluidInputs()) {
            FluidResource resource = FluidResource.of(new FluidStack(input.fluid(), input.amount()));
            if (fluids().countInInputs(resource) < input.amount()) {
                return false;
            }
        }
        return true;
    }

    /** 数一遍贴着机器的加速环段（去重，上限 16）。 */
    private int scanRingLevel() {
        Level level = getLevel();
        if (level == null) {
            return 0;
        }
        Set<BlockPos> rings = new HashSet<>();
        for (BlockPos cell : occupiedCells()) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = cell.relative(direction);
                if (level.getBlockState(neighbor).getBlock() instanceof AcceleratorRingBlock) {
                    rings.add(neighbor);
                    if (rings.size() >= 16) {
                        return 16;
                    }
                }
            }
        }
        return rings.size();
    }

    private double progressFor(StreamType type) {
        return progress.getOrDefault(type, 0.0D);
    }

    private boolean allSatisfied(BeamRecipes.Recipe recipe) {
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            if (progressFor(demand.type()) < demand.required()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 这一路本 tick 能分到多少：对该类型的<strong>每一段</strong>相邻管网各取一次快照，
     * 取<strong>最大值</strong>（同类型多段不叠加，避免同一份需求被算两次）。
     */
    private double shareOf(StreamType type) {
        Level level = getLevel();
        if (level == null) {
            return 0.0D;
        }
        int wanted = type.ordinal();
        double best = 0.0D;
        for (BlockPos cell : occupiedCells()) {
            for (Direction direction : Direction.values()) {
                BlockPos pipePos = cell.relative(direction);
                BlockState pipe = level.getBlockState(pipePos);
                if (!(pipe.getBlock() instanceof BeamPipeBlock)
                        || pipe.getValue(BeamPipeBlock.STREAM) != wanted
                        || !pipe.getValue(BeamPipeBlock.CONNECTIONS.get(direction.getOpposite()))) {
                    continue;
                }
                double share = StreamNetwork.stateAt(level, pipePos).shareOf(getBlockPos());
                if (share > best) {
                    best = share;
                }
            }
        }
        return best;
    }

    private boolean craft(BeamRecipes.Recipe recipe) {
        try (Transaction transaction = Transaction.openRoot()) {
            if (recipe.input() != null) {
                ItemResource input = ItemResource.of(new ItemStack(recipe.input()));
                if (items().extractFromInputs(input, recipe.inputCount(), transaction) < recipe.inputCount()) {
                    return false;
                }
            }
            for (BeamRecipes.FluidInput input : recipe.fluidInputs()) {
                FluidResource resource = FluidResource.of(new FluidStack(input.fluid(), input.amount()));
                if (fluids().extractFromInputs(resource, input.amount(), transaction) < input.amount()) {
                    return false;
                }
            }
            for (BeamRecipes.Output output : recipe.outputs()) {
                int amount = output.count();
                if (items().insertIntoOutputs(ItemResource.of(new ItemStack(output.item())), amount, transaction)
                        < amount) {
                    return false;
                }
            }
            for (BeamRecipes.FluidOutput output : recipe.fluidOutputs()) {
                FluidResource resource = FluidResource.of(new FluidStack(output.fluid(), output.amount()));
                if (fluids().insertIntoOutputs(resource, output.amount(), transaction) < output.amount()) {
                    return false;
                }
            }
            transaction.commit();
            onCrafted(recipe);
            return true;
        }
    }

    /** 配方成功结算后的钩子（ADS 用它授予「首次嬗变」成就）。 */
    protected void onCrafted(BeamRecipes.Recipe recipe) {
    }

    /** 基类的普通配方推进不适用；全部逻辑在 {@link #onThermalTick(ThermalLoad)}。 */
    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state, ThermalLoad load) {
        // no-op
    }

    @Override
    protected boolean animating() {
        return state == State.RUNNING;
    }

    private void syncState() {
        setChanged();
        Level level = getLevel();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ StreamConsumer

    /** 当前在吃的那一路（多路时取第一路）；空闲时退回「唯一接入的那种流」，都没接则兜底中子流。 */
    @Override
    public StreamType streamType() {
        if (lastRecipe != null && !lastRecipe.demands().isEmpty()) {
            return lastRecipe.demands().getFirst().type();
        }
        if (wired.size() == 1) {
            return wired.iterator().next();
        }
        return StreamType.NEUTRON;
    }

    /** 只对<strong>当前配方里还没吃满的那几路</strong>报需求；其余网络看到 0，不参与分配。 */
    @Override
    public double streamDemand(StreamType type) {
        BeamRecipes.Recipe recipe = lastRecipe != null ? lastRecipe : currentRecipe();
        if (recipe == null) {
            return 0.0D;
        }
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            if (demand.type() == type) {
                return Math.max(0.0D, demand.required() - progressFor(type));
            }
        }
        return 0.0D;
    }

    @Override
    public void streamAccept(StreamType type, double amount) {
        BeamRecipes.Recipe recipe = lastRecipe;
        if (recipe == null) {
            return;
        }
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            if (demand.type() == type) {
                progress.put(type, Math.min(demand.required(), progressFor(type) + amount));
                return;
            }
        }
    }

    /**
     * 只要该类型出现在本机配方里（任一路需求，或作为输出流），管段就能接上来 ——
     * 这样「还没开工 / 还没接满」的机器也不会把管子挡在外面。
     */
    @Override
    public boolean acceptsStream(StreamType type) {
        for (BeamRecipes.Recipe recipe : BeamRecipes.forMachine(kind())) {
            for (BeamRecipes.StreamDemand demand : recipe.demands()) {
                if (demand.type() == type) {
                    return true;
                }
            }
            if (recipe.outputStream() == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BlockPos> streamCells() {
        return occupiedCells();
    }

    @Override
    public BlockPos streamOrigin() {
        return getBlockPos();
    }

    // ------------------------------------------------------------------ StreamEndpoint（对撞机喷 μ 子）

    /** 输出流类型：优先当前配方；没开工时取本机种「任一有输出的配方」的输出（对撞机 = 缪子流）。 */
    @Override
    public StreamType outputStreamType() {
        if (lastRecipe != null && lastRecipe.outputStream() != null) {
            return lastRecipe.outputStream();
        }
        for (BeamRecipes.Recipe recipe : BeamRecipes.forMachine(kind())) {
            if (recipe.outputStream() != null) {
                return recipe.outputStream();
            }
        }
        return StreamType.MUON;
    }

    /** 只有「配方表里存在输出流」的机器才算会喷流（对撞机）；辐照仓返回 false。 */
    @Override
    public boolean emitsStream() {
        for (BeamRecipes.Recipe recipe : BeamRecipes.forMachine(kind())) {
            if (recipe.outputStream() != null) {
                return true;
            }
        }
        return false;
    }

    /** 运行时向外喷的速率（单位/t）；不在运行就是 0。 */
    @Override
    public double streamRate() {
        if (lastRecipe == null || lastRecipe.outputStream() == null || !working) {
            return 0.0D;
        }
        return lastRecipe.outputRate();
    }

    @Override
    public double streamEmitted() {
        return emitted;
    }

    // ------------------------------------------------------------------ 展示 / 存盘

    /** 最慢的那一路（Jade 用）。 */
    public StreamType bottleneckType() {
        BeamRecipes.Recipe recipe = lastRecipe;
        if (recipe == null || recipe.demands().isEmpty()) {
            return StreamType.NEUTRON;
        }
        BeamRecipes.StreamDemand worst = recipe.demands().getFirst();
        double worstRatio = 1.0D;
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            double ratio = progressFor(demand.type()) / Math.max(1.0E-9D, demand.required());
            if (ratio < worstRatio) {
                worstRatio = ratio;
                worst = demand;
            }
        }
        return worst.type();
    }

    public double beamProgress() {
        BeamRecipes.Recipe recipe = lastRecipe;
        if (recipe == null) {
            return 0.0D;
        }
        return progressFor(bottleneckType());
    }

    public double beamRequired() {
        BeamRecipes.Recipe recipe = lastRecipe;
        if (recipe == null) {
            return 0.0D;
        }
        StreamType bottleneck = bottleneckType();
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            if (demand.type() == bottleneck) {
                return demand.required();
            }
        }
        return 0.0D;
    }

    public double beamShare() {
        return activeShare;
    }

    public State beamState() {
        return state;
    }

    public BeamRecipes.Recipe beamRecipe() {
        return lastRecipe;
    }

    /** 已接入的流类型（Jade「粒子：中子流 / 质子流」用）。 */
    public List<StreamType> wiredTypes() {
        return new ArrayList<>(wired);
    }

    /** 贴在机器旁的加速环段数量（上限 16；Jade 显示当前等级）。 */
    public int ringLevel() {
        return ringLevel;
    }

    /** 本机正在喷出的流类型；不喷时为 null。 */
    public StreamType beamOutputStream() {
        return lastRecipe == null ? null : lastRecipe.outputStream();
    }

    /** 每一路需求的进度摘要（`uranium_ion=4200/6000;lead_ion=0/3000`），供 Jade 逐路显示。 */
    public String beamDemandSummary() {
        BeamRecipes.Recipe recipe = lastRecipe;
        if (recipe == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (BeamRecipes.StreamDemand demand : recipe.demands()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(demand.type().id())
                    .append('=')
                    .append((long) progressFor(demand.type()))
                    .append('/')
                    .append((long) demand.required());
        }
        return builder.toString();
    }

    public double beamOutputRate() {
        return streamRate();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // 键名沿用辐照仓时代的写法，旧存档照常读得出来。
        output.putString("irradiation_recipe", currentRecipeId);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<StreamType, Double> entry : progress.entrySet()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry.getKey().id()).append('=').append(entry.getValue());
        }
        output.putString("irradiation_progress", builder.toString());
        output.putDouble("beam_emitted", emitted);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        currentRecipeId = input.getStringOr("irradiation_recipe", "");
        progress.clear();
        String raw = input.getStringOr("irradiation_progress", "");
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                progress.put(StreamType.byId(part.substring(0, eq)), Double.parseDouble(part.substring(eq + 1)));
            } catch (NumberFormatException ignored) {
                // 坏数据直接跳过，进度当 0
            }
        }
        emitted = input.getDoubleOr("beam_emitted", 0.0D);
    }
}
