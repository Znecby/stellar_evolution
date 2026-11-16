package dev.modmind.stellar_evolution.massdriver;

import dev.modmind.stellar_evolution.hatch.HatchBlock;
import dev.modmind.stellar_evolution.hatch.HatchBlockEntity;
import dev.modmind.stellar_evolution.hatch.HatchBlocks;
import dev.modmind.stellar_evolution.hatch.HatchOwner;
import dev.modmind.stellar_evolution.large.LargeMachine;
import dev.modmind.stellar_evolution.large.LargeMachineFluidHandler;
import dev.modmind.stellar_evolution.large.LargeMachineIo;
import dev.modmind.stellar_evolution.large.LargeMachineItemHandler;
import dev.modmind.stellar_evolution.multiblock.MultiblockCell;
import dev.modmind.stellar_evolution.multiblock.MultiblockControllerBlockEntity;
import dev.modmind.stellar_evolution.multiblock.MultiblockPattern;
import dev.modmind.stellar_evolution.network.MassDriverShakePayload;
import dev.modmind.stellar_evolution.thermal.ThermalConsumer;
import dev.modmind.stellar_evolution.thermal.ThermalLoad;
import dev.modmind.stellar_evolution.thermal.ThermalNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * 质量投射轨道炮控制器方块实体。
 *
 * <p><strong>结构</strong>：成型后结构里的机械物流仓被认领，整台机器作为一台<strong>大机器</strong>
 * 接入机械管物流网络；物流仓只作为端口。
 *
 * <p><strong>势能</strong>：本机是一台热压差<strong>消耗端</strong>（热阻 {@value #THERMAL_RESISTANCE}）。
 * 接入点是<strong>机械物流仓</strong>（控制器的任意一面<strong>不</strong>算）：铜块或底排铜管贴着仓室即可接入网络。
 * 所在网络的<strong>差压容量</strong>就是势能上限，网络<strong>热流率（HP/t）</strong>就是每 tick 充能速度；
 * 超载 / 没有生成端时不充能。维度选择时按目标维度扣固定势能，发射本身不再额外消耗势能。
 *
 * <p><strong>等压</strong>：与热室同款的输入界面（蹲下 + 剪刀右键）当前只存储数值，不参与结算。
 *
 * <p><strong>发射</strong>：玩家背包里携带<strong>带绑定信息的坍缩末影之眼</strong>、站在控制器
 * 水平 3 格、纵向以控制器为中心的范围里，持续 10 秒后先上升 {@value #LAUNCH_HEIGHT} 格，
 * 再传送到眼睛记录的目标维度的同 X/Z 地表。
 */
public class MassDriverControllerBlockEntity extends MultiblockControllerBlockEntity
        implements LargeMachine, ThermalConsumer, HatchOwner {
    /** 本机热阻：占用的差压容量 = 64 × 有效热流率（按基准封顶 64 HP/t ⇒ 最多 4096 DP）。 */
    public static final double THERMAL_RESISTANCE = 64.0D;
    /** 基准热流率（HP/t）：同时也是「有效热流率」上限，避免 64 热阻把整张网络拖进超载。 */
    public static final double BASE_FLOW_RATE = 64.0D;
    /** 等压值范围（与热室保持一致；当前只存储）。 */
    public static final int MAX_PRESSURE = 999;

    /** 站在中间 10 秒。 */
    private static final int COUNTDOWN_TICKS = 200;
    /** 升空高度（格）。 */
    private static final int LAUNCH_HEIGHT = 500;
    /** 升空速度：50 格/秒（每 tick 2.5 格），500 格约 10 秒。 */
    private static final double ASCENT_SPEED = 50.0D;
    /** 升空所需刻数。 */
    private static final int ASCENT_TICKS = (int) Math.ceil(LAUNCH_HEIGHT / (ASCENT_SPEED / 20.0D));
    /** 水平检测半径（直径 3 格）。 */
    private static final double ZONE_RADIUS = 1.5D;

    private final Set<BlockPos> hatchPositions = new HashSet<>();
    private final Map<BlockPos, LargeMachineIo> ioModes = new HashMap<>();
    private final LargeMachineItemHandler itemHandler = new LargeMachineItemHandler(this);
    private final LargeMachineFluidHandler fluidHandler = new LargeMachineFluidHandler(this);

    /** 已充入的势能（上限 = 网络差压容量 DP）。 */
    private double potential;
    /** 等压值（只存储）。 */
    private int pressure;
    /** 每名玩家在中间区域的持续刻数。 */
    private final Map<UUID, Integer> countdowns = new HashMap<>();
    /** 正在升空（飞上天）的玩家 → 升空状态；到顶后切维度。 */
    private final Map<UUID, Ascent> ascents = new HashMap<>();

    /** 一次升空：目标维度 + 固定的水平位置 + 起始高度 + 已飞刻数。 */
    private static final class Ascent {
        private final ResourceKey<Level> target;
        private final double x;
        private final double z;
        private final double startY;
        private int ticks;

        private Ascent(ResourceKey<Level> target, double x, double z, double startY) {
            this.target = target;
            this.x = x;
            this.z = z;
            this.startY = startY;
        }
    }

    public MassDriverControllerBlockEntity(BlockPos pos, BlockState state) {
        super(MassDriverBlocks.CONTROLLER_ENTITY.get(), pos, state);
    }

    @Override
    protected MultiblockPattern createPattern() {
        return MassDriverStructure.create();
    }

    // ------------------------------------------------------------------ 成型 / 失效

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()
                && getBlockState().getValue(MassDriverControllerBlock.FORMED)) {
            setFormedState(false);
        }
    }

    @Override
    protected void onFormed() {
        hatchPositions.clear();
        MultiblockPattern currentPattern = pattern();
        if (currentPattern != null) {
            for (Map.Entry<BlockPos, MultiblockCell> entry : currentPattern.cells().entrySet()) {
                if (entry.getValue() instanceof MultiblockCell.BlockCell blockCell
                        && blockCell.block() == HatchBlocks.LOGISTICS.get()) {
                    hatchPositions.add(currentPattern.toWorld(worldPosition, facing(), entry.getKey()));
                }
            }
        }
        for (BlockPos hatchPos : hatchPositions) {
            if (level != null && level.getBlockEntity(hatchPos) instanceof HatchBlockEntity hatch) {
                hatch.link(worldPosition);
                hatch.setCasing(hatchCasing());
                // 认领关系变化后，让贴着仓室的机械管按「只有覆盖层面能接」重算一次
                level.updateNeighborsAt(hatchPos, level.getBlockState(hatchPos).getBlock());
            }
        }
        countdowns.clear();
        ascents.clear();
        setFormedState(true);
    }

    @Override
    protected void onUnformed() {
        for (BlockPos hatchPos : hatchPositions) {
            if (level != null && level.getBlockEntity(hatchPos) instanceof HatchBlockEntity hatch) {
                hatch.unlink();
                hatch.setCasing(hatchCasing());
                level.updateNeighborsAt(hatchPos, level.getBlockState(hatchPos).getBlock());
            }
        }
        hatchPositions.clear();
        countdowns.clear();
        ascents.clear();
        setFormedState(false);
    }

    private void setFormedState(boolean formed) {
        if (!(level instanceof ServerLevel) || isRemoved()) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getBlock() != MassDriverBlocks.CONTROLLER.get() || state.getValue(MassDriverControllerBlock.FORMED) == formed) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(MassDriverControllerBlock.FORMED, formed), Block.UPDATE_ALL);
    }

    // ------------------------------------------------------------------ 每 tick

    @Override
    protected void tickFormed(ServerLevel level) {
        ThermalLoad load = networkLoad(level);
        if (load != null) {
            onThermalTick(load);
        }
        if (!ascents.isEmpty()) {
            tickAscents(level);
        }
        tickLaunchZone(level);
    }

    /**
     * 本机所在的热压差网络：逐个物流仓取账本，取<strong>容量最大</strong>的那张（通常只有一个仓室接了铜块）。
     * 没有物流仓 / 都没接入时返回 {@code null}（不充能）。
     */
    @Nullable
    private ThermalLoad networkLoad(ServerLevel level) {
        ThermalLoad best = null;
        double bestCapacity = 0.0D;
        for (BlockPos hatch : hatchPositions) {
            ThermalLoad load = ThermalNetwork.loadAt(level, hatch);
            if (load.capacity() > bestCapacity) {
                best = load;
                bestCapacity = load.capacity();
            }
        }
        return best;
    }

    /** Jade 用：优先报「真的接了铜块」的那个物流仓；都没接就退回第一个物流仓（没有则控制器）。 */
    public BlockPos thermalAnchor() {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos best = null;
            double bestCapacity = 0.0D;
            for (BlockPos hatch : hatchPositions) {
                double capacity = ThermalNetwork.loadAt(serverLevel, hatch).capacity();
                if (capacity > bestCapacity) {
                    best = hatch;
                    bestCapacity = capacity;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return hatchPositions.isEmpty() ? worldPosition : hatchPositions.iterator().next();
    }

    /** Jade 用：这名玩家当前的发射倒计时（秒）；没有倒计时返回 0。 */
    public int countdownSeconds(UUID player) {
        Integer ticks = countdowns.get(player);
        return ticks == null ? 0 : (COUNTDOWN_TICKS - ticks + 19) / 20;
    }

    // ------------------------------------------------------------------ 势能 / 等压

    /** 当前势能（四舍五入到整数用于展示时由调用方处理）。 */
    public double potential() {
        return potential;
    }

    /** 等压值（当前只存储、不参与结算）。 */
    public int pressure() {
        return pressure;
    }

    public void setPressure(int value) {
        int clamped = Math.max(0, Math.min(MAX_PRESSURE, value));
        if (pressure == clamped) {
            return;
        }
        pressure = clamped;
        setChanged();
    }

    /** 扣除势能；不够则返回 false（不扣）。 */
    public boolean spendPotential(int cost) {
        if (cost <= 0) {
            return true;
        }
        if (potential < cost) {
            return false;
        }
        potential -= cost;
        setChanged();
        return true;
    }

    // ------------------------------------------------------------------ ThermalConsumer

    @Override
    public double thermalResistance() {
        return THERMAL_RESISTANCE;
    }

    @Override
    public double baseFlowRate() {
        return BASE_FLOW_RATE;
    }

    /**
     * 有效热流率上限 = 基准热流率：占用 DP 因此封顶在 {@code 64 × 64 = 4096}，
     * 一台普通燃烧引擎（容量 5120 DP）就够用；超过 64 HP/t 的部分只加快充能，不再多占容量。
     */
    @Override
    public double maxUsefulFlowRate() {
        return BASE_FLOW_RATE;
    }

    /** 控制器基底是高负荷机械方块，成型后仓室也切成同款；未成型恢复不锈钢。 */
    @Override
    public HatchBlock.Casing hatchCasing() {
        return HatchBlock.Casing.HIGH_LOAD;
    }

    @Override
    public void onThermalTick(ThermalLoad load) {
        if (load.overloaded()) {
            return;
        }
        double capacity = load.capacity();
        double flowRate = load.flowRate();
        if (capacity <= 0.0D || flowRate <= 0.0D || potential >= capacity) {
            return;
        }
        double next = Math.min(capacity, potential + flowRate);
        if ((long) next != (long) potential) {
            setChanged();
        }
        potential = next;
    }

    // ------------------------------------------------------------------ 发射区

    /**
     * 发射区：<strong>整座结构水平正中心</strong>（X/Z）为圆心、直径 3 格；纵向以<strong>控制器的 Y</strong> 为中心。
     */
    private AABB launchZone() {
        double[] center = structureCenter();
        // 纵向 3 格、中心正好是控制器的 Y
        return new AABB(center[0] - ZONE_RADIUS, worldPosition.getY() - 1.5D, center[1] - ZONE_RADIUS,
                center[0] + ZONE_RADIUS, worldPosition.getY() + 1.5D, center[1] + ZONE_RADIUS);
    }

    /** 结构包围盒的水平中心（取图案两个对角的世界坐标平均，自动适配旋转）。 */
    private double[] structureCenter() {
        MultiblockPattern currentPattern = pattern();
        if (currentPattern == null) {
            return new double[]{worldPosition.getX() + 0.5D, worldPosition.getZ() + 0.5D};
        }
        BlockPos a = currentPattern.toWorld(worldPosition, facing(),
                new BlockPos(currentPattern.minX(), 0, currentPattern.minZ()));
        BlockPos b = currentPattern.toWorld(worldPosition, facing(),
                new BlockPos(currentPattern.maxX(), 0, currentPattern.maxZ()));
        return new double[]{(a.getX() + b.getX()) / 2.0D + 0.5D, (a.getZ() + b.getZ()) / 2.0D + 0.5D};
    }

    private void tickLaunchZone(ServerLevel level) {
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, launchZone());
        Set<UUID> present = new HashSet<>();
        double[] center = structureCenter();
        double baseY = worldPosition.getY() + 1.0D;
        double topY = structureTopY();
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            present.add(id);
            int eyes = MassDriverEyes.count(player);
            if (eyes > 1) {
                countdowns.remove(id);
                if (level.getGameTime() % 20L == 0L) {
                    player.sendOverlayMessage(
                            Component.translatable("stellar_evolution.mass_driver.too_many_eyes"));
                }
                continue;
            }
            ItemStack eye = eyes == 1 ? MassDriverEyes.firstBound(player) : null;
            CollapsedEyeData data = eye == null ? null : eye.get(MassDriverComponents.COLLAPSED_EYE.get());
            if (data == null) {
                countdowns.remove(id);
                continue;
            }
            int ticks = countdowns.merge(id, 1, Integer::sum);
            if (ticks >= COUNTDOWN_TICKS) {
                countdowns.remove(id);
                launch(level, player, data);
                continue;
            }
            int seconds = (COUNTDOWN_TICKS - ticks + 19) / 20;
            if (ticks % 20 == 1) {
                player.sendOverlayMessage(
                        Component.translatable("stellar_evolution.mass_driver.countdown", seconds));
            }
            // 加速时钟：越接近倒计时尾声走得越快（每个「脉冲」周期都比上一次短）
            double progress = Math.min(1.0D, ticks / (double) COUNTDOWN_TICKS);
            double clock = COUNTDOWN_TICKS * progress * (0.3D + 0.7D * progress);

            // 地面蓄力圈：反复从 3 格收缩到 0.6 格，每次循环比上一次更快
            double pulse = (clock % 16.0D) / 16.0D;
            if (ticks % 2 == 0) {
                double spin = (level.getGameTime() % 200L) * 0.25D;
                spawnArcRing(level, center[0], center[1], baseY, 3.0D - 2.4D * pulse, 24, spin);
            }

            // 顶部下落圈：一圈接一圈从顶部落到中心，下落周期同样越来越短
            double fall = (clock % 20.0D) / 20.0D;
            if (ticks % 2 == 1) {
                double spin = -(level.getGameTime() % 200L) * 0.25D;
                spawnArcRing(level, center[0], center[1],
                        topY + (baseY - topY) * fall, 1.5D, 20, spin);
            }

            // 八方向电弧：从中心向外辐射，随进度旋转
            if (ticks % 4 == 0) {
                spawnSparkLines(level, center[0], center[1], baseY + 0.6D, progress);
            }

            // 最后 6 秒：中心升起一根 END_ROD 光柱
            if (progress > 0.4D && ticks % 2 == 0) {
                spawnEndRodPillar(level, center[0], center[1], baseY);
            }

            // 最后 1 秒：单帧白闪，作为「临界」提示
            if (ticks == COUNTDOWN_TICKS - 20) {
                level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF),
                        center[0], baseY + 1.0D, center[1], 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            // 只视觉的闪电：有原版闪光 + 雷声，但不会引燃 / 伤人
            if (ticks % 30 == 0 && ticks < COUNTDOWN_TICKS - 20) {
                spawnChargeLightning(level, center, topY);
            }

            // 蓄力嗡鸣：播放间隔随倒计时缩短、音调逐渐升高
            int soundInterval = Math.max(2, 10 - (int) (8.0D * progress));
            if (ticks % soundInterval == 0) {
                level.playSound(null, center[0], baseY, center[1], SoundEvents.BEACON_AMBIENT,
                        SoundSource.BLOCKS, 0.7F, 0.8F + 0.8F * (float) progress);
            }
            // 额外的「充能」长音，每 3 秒叠一层
            if (ticks % 60 == 1) {
                level.playSound(null, center[0], baseY, center[1], SoundEvents.WARDEN_SONIC_CHARGE,
                        SoundSource.BLOCKS, 1.0F, 0.85F + 0.25F * (float) progress);
            }
        }
        countdowns.keySet().removeIf(id -> !present.contains(id));
    }

    private void launch(ServerLevel level, ServerPlayer player, CollapsedEyeData data) {
        ResourceKey<Level> target = data.target();
        if (target.equals(level.dimension())) {
            player.sendOverlayMessage(
                    Component.translatable("stellar_evolution.mass_driver.target_same_dimension"));
            return;
        }
        if (level.getServer().getLevel(target) == null) {
            player.sendOverlayMessage(
                    Component.translatable("stellar_evolution.mass_driver.target_missing"));
            return;
        }
        // 起飞瞬间：白光 → 大爆炸 → 冲击波 → 末影粒子，并给附近玩家屏幕震动
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF),
                player.getX(), player.getY() + 1.0D, player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 0.65F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.BLOCKS, 3.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 1.0D, player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
                160, 0.8D, 1.5D, 0.8D, 0.4D);
        spawnShockwave(level, player.getX(), player.getY(), player.getZ());
        spawnSonicRing(level, player.getX(), player.getY(), player.getZ());
        shakeNearby(level, player.getX(), player.getY(), player.getZ(), 64.0D, 22, 10.0F);
        player.sendOverlayMessage(Component.translatable("stellar_evolution.mass_driver.launch",
                MassDriverCosts.displayName(target.identifier())));
        ascents.put(player.getUUID(), new Ascent(target, player.getX(), player.getZ(), player.getY()));
    }

    /** 升空阶段：以 50 格/秒匀速上升，尾气不断生成音爆云；到顶后切维度。 */
    private void tickAscents(ServerLevel level) {
        Iterator<Map.Entry<UUID, Ascent>> iterator = ascents.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Ascent> entry = iterator.next();
            Ascent ascent = entry.getValue();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.level() != level) {
                iterator.remove();
                continue;
            }
            ascent.ticks++;
            double y = ascent.startY + Math.min(LAUNCH_HEIGHT, ascent.ticks * ASCENT_SPEED / 20.0D);
            player.teleportTo(ascent.x, y, ascent.z);
            player.resetFallDistance();
            if (ascent.ticks % 2 == 0) {
                spawnSonicRing(level, ascent.x, y, ascent.z);
                spawnAscentTrail(level, ascent.x, y, ascent.z);
            }
            // 阶段性「音爆」：大范围风环 + 音效
            if (ascent.ticks % 10 == 0) {
                level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, ascent.x, y + 0.2D, ascent.z,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.playSound(null, ascent.x, y, ascent.z, SoundEvents.FIREWORK_ROCKET_BLAST,
                        SoundSource.PLAYERS, 1.2F, 0.8F);
            }
            // 飞行声：低音量循环风噪
            if (ascent.ticks % 20 == 0) {
                level.playSound(null, ascent.x, y, ascent.z, SoundEvents.ELYTRA_FLYING,
                        SoundSource.PLAYERS, 0.8F, 0.9F);
            }
            if (ascent.ticks >= ASCENT_TICKS) {
                iterator.remove();
                finishAscent(level, player, ascent);
            }
        }
    }

    private void finishAscent(ServerLevel level, ServerPlayer player, Ascent ascent) {
        ServerLevel targetLevel = level.getServer().getLevel(ascent.target);
        if (targetLevel == null) {
            return;
        }
        // 到顶 / 消失前：原地爆一圈末影粒子
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
                160, 0.8D, 1.5D, 0.8D, 0.4D);
        Vec3 landing = safeLanding(targetLevel, ascent.x, ascent.z);
        player.teleportTo(targetLevel, landing.x, landing.y, landing.z, Set.of(),
                player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        // 从上方落到安全点：缓降覆盖整段下落，避免高度差造成摔死
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false, false));
        targetLevel.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF),
                landing.x, landing.y + 1.0D, landing.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        targetLevel.sendParticles(ParticleTypes.PORTAL, landing.x, landing.y + 1.0D, landing.z,
                120, 0.6D, 1.0D, 0.6D, 0.3D);
        spawnShockwave(targetLevel, landing.x, landing.y, landing.z);
        spawnEndRodPillar(targetLevel, landing.x, landing.z, landing.y);
        targetLevel.playSound(null, landing.x, landing.y, landing.z, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        targetLevel.playSound(null, landing.x, landing.y, landing.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.5F, 0.9F);
        shakeNearby(targetLevel, landing.x, landing.y, landing.z, 48.0D, 12, 5.0F);
        player.sendOverlayMessage(Component.translatable("stellar_evolution.mass_driver.arrived",
                MassDriverCosts.displayName(ascent.target.identifier())));
    }

    /** 结构顶部（最高一格的上一格）的世界 Y，用于「从顶部落下的电弧圈」。 */
    private double structureTopY() {
        MultiblockPattern currentPattern = pattern();
        if (currentPattern == null) {
            return worldPosition.getY() + 1.0D;
        }
        return currentPattern.toWorld(worldPosition, facing(),
                new BlockPos(0, currentPattern.maxY(), 0)).getY() + 1.0D;
    }

    /** 一圈电弧粒子：固定中心 X/Z 与高度，按给定半径和旋转相位铺一圈；每 4 个点夹一颗发光末地烛。 */
    private static void spawnArcRing(ServerLevel level, double cx, double cz, double y,
                                     double radius, int points, double spin) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points + spin;
            double px = cx + Math.cos(angle) * radius;
            double pz = cz + Math.sin(angle) * radius;
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, px, y, pz, 1,
                        0.02D, 0.03D, 0.02D, 0.01D);
            } else {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, y, pz, 1,
                        (cx - px) * 0.06D, 0.02D, (cz - pz) * 0.06D, 0.0D);
            }
        }
    }

    /** 八方向电弧：从中心向外辐射，随进度缓慢旋转。 */
    private static void spawnSparkLines(ServerLevel level, double cx, double cz, double y, double progress) {
        int lines = 8;
        for (int i = 0; i < lines; i++) {
            double angle = Math.PI * 2.0D * i / lines + progress * 2.5D;
            for (int step = 1; step <= 4; step++) {
                double r = 1.2D + step * 0.7D;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        cx + Math.cos(angle) * r, y, cz + Math.sin(angle) * r, 1,
                        0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    /** 中心光柱：一列发光末地烛。 */
    private static void spawnEndRodPillar(ServerLevel level, double cx, double cz, double baseY) {
        for (int i = 0; i < 6; i++) {
            level.sendParticles(ParticleTypes.END_ROD, cx, baseY + i, cz, 1,
                    0.02D, 0.05D, 0.02D, 0.01D);
        }
    }

    /** 蓄力阶段的只视觉闪电：有原版闪光与雷声，不引燃、不伤人。 */
    private static void spawnChargeLightning(ServerLevel level, double[] center, double topY) {
        double x = center[0] + (level.getRandom().nextDouble() - 0.5D) * 12.0D;
        double z = center[1] + (level.getRandom().nextDouble() - 0.5D) * 12.0D;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt == null) {
            return;
        }
        bolt.setPos(x, topY + 1.0D, z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /** 发射 / 抵达时的冲击波：一圈白云 + 大风环。 */
    private static void spawnShockwave(ServerLevel level, double x, double y, double z) {
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(ParticleTypes.CLOUD,
                    x + Math.cos(angle) * 1.2D, y + 0.2D, z + Math.sin(angle) * 1.2D, 1,
                    0.03D, 0.02D, 0.03D, 0.02D);
        }
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, x, y + 0.2D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.GUST, x, y + 0.3D, z, 6, 0.8D, 0.1D, 0.8D, 0.02D);
    }

    /** 升空尾气：末地烛光点 + 烟花火花 + 电弧。 */
    private static void spawnAscentTrail(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 4, 0.25D, 0.1D, 0.25D, 0.02D);
        level.sendParticles(ParticleTypes.FIREWORK, x, y + 0.2D, z, 3, 0.3D, 0.1D, 0.3D, 0.03D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.1D, z, 2, 0.2D, 0.05D, 0.2D, 0.02D);
    }

    /** 给半径内的所有玩家发屏幕震动（含发射者本人）。 */
    private static void shakeNearby(ServerLevel level, double x, double y, double z,
                                    double radius, int ticks, float strength) {
        for (ServerPlayer other : level.players()) {
            if (other.distanceToSqr(x, y, z) <= radius * radius) {
                PacketDistributor.sendToPlayer(other, new MassDriverShakePayload(ticks, strength));
            }
        }
    }

    /** 升空尾气：一圈向外炸开的音爆云（白色云团 + 风粒子）。 */
    private static void spawnSonicRing(ServerLevel level, double x, double y, double z) {
        int points = 12;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            level.sendParticles(ParticleTypes.CLOUD, x + dx * 0.7D, y + 0.1D, z + dz * 0.7D, 1,
                    0.02D, 0.02D, 0.02D, 0.02D);
        }
        level.sendParticles(ParticleTypes.GUST, x, y + 0.15D, z, 2, 0.5D, 0.05D, 0.5D, 0.0D);
    }

    /**
     * 目标维度的安全落点：先<strong>强制加载目标区块</strong>（未加载时 {@code getHeight} 会直接返回最小 Y，
     * 这正是之前「落到基岩下面」的原因），再取其地表上方若干格，让玩家从上往下落到地面。
     * 虚空列退回 (0,0)；仍无效则用生成器给出的出生高度。
     */
    private static Vec3 safeLanding(ServerLevel level, double x, double z) {
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        level.getChunk(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        if (surface <= minY + 1) {
            // 该列是虚空：改用原点附近的地表
            level.getChunk(0, 0);
            int originSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
            if (originSurface > minY + 1) {
                blockX = 0;
                blockZ = 0;
                x = 0.5D;
                z = 0.5D;
                surface = originSurface;
            }
        }
        if (surface <= minY + 1) {
            surface = level.getChunkSource().getGenerator().getSpawnHeight(level);
        }
        double landingY = Math.max(minY + 2.0D, Math.min(surface + 6.0D, maxY - 1.0D));
        return new Vec3(x, landingY, z);
    }

    // ------------------------------------------------------------------ LargeMachine

    @Override
    public boolean isLargeMachine() {
        return isFormed();
    }

    @Override
    public Level level() {
        return getLevel();
    }

    @Override
    public BlockPos origin() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> occupiedCells() {
        // 只有物流仓是热接入点 / 物流端口：铜块（或底排铜管）与机械管都贴物流仓接入。
        return new ArrayList<>(hatchPositions);
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

    @Nullable
    public ResourceHandler<ItemResource> itemHandler() {
        return isFormed() ? itemHandler : null;
    }

    @Nullable
    public ResourceHandler<FluidResource> fluidHandler() {
        return isFormed() ? fluidHandler : null;
    }

    // ------------------------------------------------------------------ 右键

    /** 普通右键不再打开信息界面：等压走剪刀、维度选择走末影之眼。 */
    @Override
    protected InteractionResult onUseFormed(Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ 存盘

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("potential", potential);
        output.putInt("pressure", pressure);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        potential = input.getDoubleOr("potential", 0.0D);
        pressure = input.getIntOr("pressure", 0);
    }
}
