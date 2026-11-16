package dev.modmind.stellar_evolution.fission;

import dev.modmind.stellar_evolution.large.LargeMachine;
import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.large.LargeMachineIo;
import dev.modmind.stellar_evolution.large.LargeMachineItemHandler;
import dev.modmind.stellar_evolution.steam.SteamEndpoint;
import dev.modmind.stellar_evolution.steam.SteamNetwork;
import dev.modmind.stellar_evolution.steam.SteamState;
import dev.modmind.stellar_evolution.thermal.ThermalGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 蒸汽涡轮机组方块实体 —— <strong>蒸汽网络的消耗端</strong> + 热压差<strong>生成端</strong>。
 *
 * <p>蒸汽 <strong>只走蒸汽网络</strong>（压力管 → {@link SteamNetwork}）：不再从储罐吃蒸汽、也不产凝结水。
 * 反应堆每 tick 给出的蒸汽数据（量 / 温度 / 压力）决定本机转速，转速再换算成热压差出力。
 *
 * <ul>
 *   <li><strong>转速</strong>：按蒸汽等级 + 压力分档：饱和 <b>1000</b>、过热 <b>3000</b>；
 *       超临界按压力 221/300/400/500 bar 分 <b>8000 / 12000 / 16000 / 20000 RPM</b>。
 *       到档位上限后耗汽不再增加，多余蒸汽由同网络多台汽轮机分摊。</li>
 *   <li><strong>升速</strong>：起步线性 <b>+1/t</b>（超临界 <b>+10/t</b>），并按本机拿到的蒸汽比例
 *       最高加速到 <b>+100/t</b>；<strong>目标转速由分到的蒸汽量决定</strong>（供汽少 → 目标低 → 自动降速），
 *       只有蒸汽够跑满该档上限时才升到上限。</li>
 *   <li><strong>出力</strong>：转速 <b>&gt; 100 RPM</b> 才开始输出；每点转速 <b>64 DP / 2 HP·t⁻¹</b>，
 *       蒸汽压力再额外提高 DP（600 bar 封顶，最高 +120%）。</li>
 *   <li><strong>蒸汽消耗</strong>：按转速需求（`38.4 × (1 + RPM)` mB/t）在网络里分配；满速 20,000 RPM 恰好吃掉
 *       一根 450 MW 燃料棒的产汽（≈768,000 mB/t），蒸汽直接消耗、不产水。</li>
 * </ul>
 */
public class SteamTurbineBlockEntity extends BlockEntity
        implements ThermalGenerator, LargeMachine, FissionAnimated, FissionMachineHost, SteamEndpoint {
    /** 本机机种（配方 / JEI 分组用）。 */
    public static final FissionMachineKind KIND = FissionMachineKind.STEAM_TURBINE;

    /** 起步升速：普通蒸汽每 tick +1 RPM。 */
    public static final double BASE_ACCEL = 1.0D;
    /** 起步升速：超临界蒸汽每 tick +10 RPM。 */
    public static final double SUPERCRITICAL_ACCEL = 10.0D;
    /** 供汽充足时的最快升速（每 tick）。 */
    public static final double MAX_ACCEL = 100.0D;
    /** 达到「最快升速」所需的蒸汽（mB/t）：一个「额外 8 水源」格栅 / 一根基础 60 MW 棒的产汽量。 */
    public static final double ACCEL_FULL_STEAM = 102_400.0D;
    /** 开始输出热压差的最低转速。 */
    public static final double MIN_OUTPUT_RPM = 100.0D;
    /** 每点转速提供的差压容量（DP）。 */
    public static final double DP_PER_RPM = 64.0D;
    /** 每点转速提供的热流率（HP/t）。 */
    public static final double HP_PER_RPM = 2.0D;
    /** 空载时的蒸汽需求（mB/t，≈ 1 RPM 的需求量）。 */
    public static final double DEMAND_BASE = 38.4D;
    /** 每点转速额外需要的蒸汽（mB/t）：450 MW 棒满功率 768,000 mB/t ÷ 20,000 RPM ≈ 38.4。 */
    public static final double DEMAND_PER_RPM = 38.4D;
    /** 转速同步给客户端的间隔（tick）。 */
    private static final int SYNC_INTERVAL = 20;

    private final LargeMachineItemHandler itemHandler = new LargeMachineItemHandler(this);
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);
    private final Map<BlockPos, LargeMachineIo> ioModes = new HashMap<>();

    /** 当前转速（RPM）。 */
    private double rpm;
    /** 上次同步给客户端的转速（用于减少方块更新）。 */
    private double lastSyncedRpm = -1.0D;
    private int syncTicks;
    /** 本 tick 实际拿到的蒸汽（mB/t）。 */
    private double lastSteamUse;
    /** 当前蒸汽数据（无汽时 {@link SteamState#NONE}）。 */
    private SteamState lastSteam = SteamState.NONE;
    private SteamStatus steamStatus = SteamStatus.NO_STEAM;

    private float age;
    private float lastAge;
    /** 客户端平滑转速 0..1（起转 / 停机时缓入缓出）。 */
    private float spin;
    private boolean working;
    private boolean structureChecked;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(FissionSteam.TURBINE_ENTITY.get(), pos, state);
    }

    public FissionMachineKind kind() {
        return KIND;
    }

    @Override
    public ResourceHandler<ItemResource> itemCapability() {
        return itemHandler;
    }

    @Override
    public ResourceHandler<FluidResource> fluidCapability() {
        return fluidHandler;
    }

    @Override
    public float age(float partialTick) {
        return Mth.lerp(partialTick, lastAge, age);
    }

    // ---- 只读快照（Jade 用）

    public double rpm() {
        return rpm;
    }

    public double steamUse() {
        return lastSteamUse;
    }

    public SteamState steamState() {
        return lastSteam;
    }

    public SteamStatus steamStatus() {
        return steamStatus;
    }

    public double dpOutput() {
        return capacity();
    }

    public double hpOutput() {
        return flowRate();
    }

    // ------------------------------------------------------------------ ThermalGenerator

    @Override
    public double capacity() {
        return rpm > MIN_OUTPUT_RPM ? rpm * DP_PER_RPM * lastSteam.dpMultiplier() : 0.0D;
    }

    @Override
    public double flowRate() {
        return rpm > MIN_OUTPUT_RPM ? rpm * HP_PER_RPM : 0.0D;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SteamTurbineBlockEntity turbine) {
        if (level.isClientSide()) {
            turbine.lastAge = turbine.age;
            turbine.spin = Mth.clamp(turbine.spin + (turbine.rpm > 1.0D ? 0.06F : -0.12F), 0.0F, 1.0F);
            // 转速越高，转子转得越快（age 是各动件共用的「运行时长」）
            float speed = 0.15F + (float) (turbine.rpm / 300.0D);
            turbine.age += turbine.spin * speed;
            return;
        }
        if (!turbine.structureChecked) {
            turbine.structureChecked = true;
            FissionMachineStructure.refreshLight(level, pos);
            FissionMachineStructure.fillShells(level, pos);
            FissionMachineStructure.refreshLight(level, pos);
        }
        FissionMachineStructure.refreshPorts(level, pos);
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 40L) == 0L) {
            int filled = FissionMachineStructure.fillShells(level, pos);
            if (filled > 0) {
                FissionMachineStructure.refreshLight(level, pos);
            }
        }
        turbine.serverTick(level, state);
    }

    /** 每 tick：从蒸汽网络取汽 → 调转速 → 出力（蒸汽直接消耗，不产水）。 */
    private void serverTick(Level level, BlockState state) {
        SteamNetwork.State network = SteamNetwork.stateAt(level, worldPosition);
        boolean hasPipe = !network.pipes().isEmpty();
        SteamState steam = network.steam();
        // 网络已按「就近优先、先供满速」分好份额，这里只认自己那份额
        double share = network.allocationFor(worldPosition);

        if (share > 0.0D) {
            lastSteam = steam;
            // 供多少汽就跑多少转：份额换算出的转速就是目标转速（供汽不足时目标低于当前 → 自动降速）
            double target = Math.min(maxRpm(steam), Math.max(0.0D, share / DEMAND_PER_RPM - 1.0D));
            if (target > rpm) {
                double base = steam.supercritical() ? SUPERCRITICAL_ACCEL : BASE_ACCEL;
                double ratio = Math.min(1.0D, share / ACCEL_FULL_STEAM);
                rpm = Math.min(target, rpm + base + (MAX_ACCEL - base) * ratio);
            } else if (target < rpm) {
                // 欠汽降速：缺口越大降得越快（下限 1 RPM/t），避免明明汽不够还挂着高速
                double decel = Math.min(MAX_ACCEL, Math.max(BASE_ACCEL, (rpm - target) * 0.05D));
                rpm = Math.max(target, rpm - decel);
            }
        } else {
            lastSteam = SteamState.NONE;
            rpm = Math.max(0.0D, rpm - Math.max(1.0D, rpm * 0.01D));
        }
        lastSteamUse = share;
        steamStatus = !hasPipe || steam.isEmpty() ? SteamStatus.NO_STEAM
                : share <= 0.0D ? SteamStatus.STARVED
                : rpm > MIN_OUTPUT_RPM ? SteamStatus.RUNNING
                : SteamStatus.SPINNING_UP;

        boolean nowWorking = rpm > 1.0D;
        if (nowWorking != working) {
            working = nowWorking;
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
        if (++syncTicks >= SYNC_INTERVAL) {
            syncTicks = 0;
            if (Math.abs(rpm - lastSyncedRpm) >= 1.0D) {
                lastSyncedRpm = rpm;
                setChanged();
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            }
        }
    }

    /** 转速上限：按蒸汽等级 + 压力分档（普通 1000/3000，超临界 8000..20000，过超临界 25000）。 */
    private static double maxRpm(SteamState steam) {
        if (steam.isEmpty()) {
            return 0.0D;
        }
        if (steam.grade() == SteamLoop.SteamGrade.ULTRA_SUPERCRITICAL) {
            return 25_000.0D;
        }
        if (steam.grade() == SteamLoop.SteamGrade.SUPERCRITICAL) {
            double pressure = steam.pressureBar();
            if (pressure >= 500.0D) {
                return 20_000.0D;
            }
            if (pressure >= 400.0D) {
                return 16_000.0D;
            }
            if (pressure >= 300.0D) {
                return 12_000.0D;
            }
            return 8_000.0D;
        }
        return steam.grade() == SteamLoop.SteamGrade.SATURATED ? 1_000.0D : 3_000.0D;
    }

    // ------------------------------------------------------------------ SteamEndpoint（消耗端）

    @Override
    public boolean steamGenerator() {
        return false;
    }

    @Override
    public double steamDemand() {
        return DEMAND_BASE + Math.max(0.0D, rpm) * DEMAND_PER_RPM;
    }

    /**
     * 分汽用需求：跑满<strong>当前蒸汽档位上限</strong>所需的蒸汽。
     *
     * <p>网络按这个数「就近优先、先供满速」地分：前面的机组先拿够满速的量，
     * 剩下的才轮到更远的机组；每台再拿自己分到的份额换算目标转速。
     */
    @Override
    public double steamMaxDemand(SteamState steam) {
        return DEMAND_BASE + maxRpm(steam) * DEMAND_PER_RPM;
    }

    @Override
    public List<BlockPos> steamCells() {
        return occupiedCells();
    }

    @Override
    public BlockPos steamOrigin() {
        return getBlockPos();
    }

    // ------------------------------------------------------------------ LargeMachine

    @Override
    public boolean isLargeMachine() {
        return true;
    }

    @Override
    public Level level() {
        return getLevel();
    }

    @Override
    public BlockPos origin() {
        return getBlockPos();
    }

    @Override
    public List<BlockPos> occupiedCells() {
        BlockPos origin = getBlockPos();
        List<BlockPos> cells = new ArrayList<>(FissionMachineStructure.CELL_OFFSETS.length);
        for (int[] offset : FissionMachineStructure.CELL_OFFSETS) {
            cells.add(origin.offset(offset[0], offset[1], offset[2]));
        }
        return cells;
    }

    @Override
    public LargeMachineIo ioMode(BlockPos container) {
        return ioModes.getOrDefault(container, LargeMachineIo.INPUT);
    }

    @Override
    public void setIoMode(BlockPos container, LargeMachineIo mode) {
        ioModes.put(container.immutable(), mode);
        setChanged();
    }

    @Override
    public int remainingRecipeTicks() {
        return -1;
    }

    @Override
    public double recipeProgress() {
        return 0.0D;
    }

    // ------------------------------------------------------------------ 存盘 / 同步

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        int count = 0;
        for (LargeMachineIo mode : ioModes.values()) {
            if (mode == LargeMachineIo.OUTPUT) {
                count++;
            }
        }
        int[] outs = new int[count * 3];
        int index = 0;
        for (Map.Entry<BlockPos, LargeMachineIo> entry : ioModes.entrySet()) {
            if (entry.getValue() != LargeMachineIo.OUTPUT) {
                continue;
            }
            BlockPos pos = entry.getKey();
            outs[index++] = pos.getX();
            outs[index++] = pos.getY();
            outs[index++] = pos.getZ();
        }
        output.putIntArray("large_io_output", outs);
        output.putBoolean("working", working);
        output.putDouble("rpm", rpm);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ioModes.clear();
        int[] outs = input.getIntArray("large_io_output").orElse(new int[0]);
        for (int i = 0; i + 2 < outs.length; i += 3) {
            ioModes.put(new BlockPos(outs[i], outs[i + 1], outs[i + 2]), LargeMachineIo.OUTPUT);
        }
        working = input.getBooleanOr("working", false);
        rpm = input.getDoubleOr("rpm", 0.0D);
        lastSyncedRpm = rpm;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
