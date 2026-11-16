package dev.znecby.stellar_evolution.equipment;

import dev.znecby.stellar_evolution.StellarEvolution;
import dev.znecby.stellar_evolution.material.OreBlocks;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * 钢制工具的功能实现（2026-11-01）。
 *
 * <ul>
 *   <li><strong>范围</strong>：以「点中的方块面」为基准，破坏 / 耕地同一平面上的 (2r+1)²；</li>
 *   <li><strong>连锁</strong>：镐 → 矿石；斧 → 原木；锹 → 锹可挖方块；锄 → 作物；
 *       26 邻接（含斜对角），额外方块掉落集中到起始方块；</li>
 *   <li><strong>时运 / 精准</strong>：用临时工具结算原版战利品，不写真实附魔；</li>
 *   <li><strong>横扫</strong>：钢剑近战命中后，对周围 3 格内生物追加 1.75 伤害（横扫之刃 III 等效）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class SteelToolEvents {
    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> SWEEPING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 连锁期间把额外方块的掉落物集中到起始方块。 */
    private static final ThreadLocal<BlockPos> CHAIN_DROP_ORIGIN = new ThreadLocal<>();

    /** 横扫的基础伤害（1 + 0.75，对应原版横扫之刃 III）。 */
    private static final float SWEEP_DAMAGE = 1.75F;

    // ------------------------------------------------------------------ 范围 / 连锁

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BreakBlockEvent event) {
        if (event.isCanceled() || BREAKING.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof ModeTool modeTool) || modeTool.kind() == SteelToolKind.SWORD) {
            return;
        }
        int features = modeTool.features(tool);
        SteelToolKind kind = modeTool.kind();
        Predicate<BlockState> chainPredicate = chainPredicate(kind);
        boolean chain = chainPredicate != null && (features & chainFeature(kind).mask()) != 0;
        boolean area = (features & SteelToolFeature.AREA.mask()) != 0
                && kind != SteelToolKind.HOE;   // 锄头的范围是右键耕地，不走破坏事件

        if (!chain && !area) {
            return;
        }
        BlockPos origin = event.getPos();
        BlockState originState = event.getState();
        if (chain && !chainPredicate.test(originState)) {
            return;
        }

        BREAKING.set(Boolean.TRUE);
        if (chain) {
            CHAIN_DROP_ORIGIN.set(origin);
        }
        try {
            if (chain) {
                breakChain(level, player, origin, modeTool.veinLimit(tool), chainPredicate);
            } else {
                breakArea(level, player, origin, modeTool.areaRadius(tool), clickedFace(player, origin));
            }
        } finally {
            CHAIN_DROP_ORIGIN.remove();
            BREAKING.set(Boolean.FALSE);
        }
    }

    /** 各工具对应的连锁功能；剑没有。 */
    private static SteelToolFeature chainFeature(SteelToolKind kind) {
        return switch (kind) {
            case PICKAXE -> SteelToolFeature.VEIN;
            case AXE -> SteelToolFeature.FELL;
            case SHOVEL -> SteelToolFeature.BULK;
            case HOE -> SteelToolFeature.HARVEST;
            case SWORD -> SteelToolFeature.SWEEP;
        };
    }

    /** 各工具连锁匹配的方块；返回 null 表示不连锁。 */
    private static Predicate<BlockState> chainPredicate(SteelToolKind kind) {
        return switch (kind) {
            case PICKAXE -> OreBlocks::isOre;
            case AXE -> state -> state.is(BlockTags.LOGS);
            case SHOVEL -> state -> state.is(BlockTags.MINEABLE_WITH_SHOVEL);
            case HOE -> SteelToolEvents::isCrop;
            case SWORD -> null;
        };
    }

    /** 只连锁成熟作物，避免把幼苗一起收掉。 */
    private static boolean isCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        }
        if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        return false;
    }

    /** 范围破坏：以点击面为平面，半径 r。 */
    private static void breakArea(ServerLevel level, ServerPlayer player, BlockPos origin, int radius, Direction face) {
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                destroy(level, player, offset(origin, face.getAxis(), a, b));
            }
        }
    }

    /** 连锁：从挖掘点向外 26 邻接（含斜对角），只走匹配方块，最多 {@code limit} 块（含原点）。 */
    private static void breakChain(ServerLevel level, ServerPlayer player, BlockPos origin, int limit,
                                   Predicate<BlockState> predicate) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        int broken = 0;
        while (!queue.isEmpty() && broken < limit - 1) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!seen.add(next) || !level.isLoaded(next)
                                || !predicate.test(level.getBlockState(next))) {
                            continue;
                        }
                        if (destroy(level, player, next)) {
                            broken++;
                        }
                        queue.add(next);
                        if (broken >= limit - 1) {
                            break;
                        }
                    }
                    if (broken >= limit - 1) {
                        break;
                    }
                }
                if (broken >= limit - 1) {
                    break;
                }
            }
        }
    }

    /** 破坏一个额外方块；跳过空气 / 流体 / 方块实体 / 不可破坏方块。 */
    private static boolean destroy(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.liquid() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return player.gameMode.destroyBlock(pos);
    }

    /**
     * 点击面：优先用玩家射线命中的方块面；射线打偏时按「眼位相对方块中心的主轴」估算。
     * 这是范围平面朝向的唯一依据，不再看玩家整体朝向。
     */
    private static Direction clickedFace(ServerPlayer player, BlockPos pos) {
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
            return blockHit.getDirection();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        if (Math.abs(dy) >= Math.abs(dz)) {
            return dy >= 0 ? Direction.UP : Direction.DOWN;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static BlockPos offset(BlockPos origin, Direction.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> origin.offset(0, a, b);
            case Y -> origin.offset(a, 0, b);
            case Z -> origin.offset(a, b, 0);
        };
    }

    // ------------------------------------------------------------------ 时运 / 精准

    /**
     * 用<strong>临时工具</strong>结算原版战利品：给副本加 Fortune / Silk Touch，再调用
     * {@link Block#getDrops}。真实工具不加附魔，砂轮刷经验等问题不会出现。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.isCanceled() || !(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack tool = event.getTool().isEmpty() ? player.getMainHandItem() : event.getTool();
        if (!(tool.getItem() instanceof ModeTool modeTool)) {
            return;
        }
        int features = modeTool.features(tool);
        boolean fortune = (features & SteelToolFeature.FORTUNE.mask()) != 0;
        boolean silk = (features & SteelToolFeature.SILK.mask()) != 0;
        BlockPos dropOrigin = CHAIN_DROP_ORIGIN.get();
        if (!fortune && !silk && dropOrigin == null) {
            return;
        }

        ServerLevel level = event.getLevel();
        if (fortune || silk) {
            ItemStack temp = tool.copy();
            var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(temp.getTagEnchantments());
            if (silk) {
                mutable.removeIf(holder -> holder.is(Enchantments.FORTUNE));
                mutable.set(lookup.getOrThrow(Enchantments.SILK_TOUCH), 1);
            } else {
                mutable.removeIf(holder -> holder.is(Enchantments.SILK_TOUCH));
                var fortuneHolder = lookup.getOrThrow(Enchantments.FORTUNE);
                mutable.set(fortuneHolder, Math.max(mutable.getLevel(fortuneHolder), modeTool.fortuneLevel(tool)));
            }
            temp.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());

            List<ItemStack> drops = Block.getDrops(event.getState(), level, event.getPos(),
                    event.getBlockEntity(), player, temp);
            event.getDrops().clear();
            double x = event.getPos().getX() + 0.5D;
            double y = event.getPos().getY() + 0.5D;
            double z = event.getPos().getZ() + 0.5D;
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    event.getDrops().add(new ItemEntity(level, x, y, z, drop));
                }
            }
            int xp = event.getState().getExpDrop(level, event.getPos(), event.getBlockEntity(), player, temp);
            event.setDroppedExperience(EnchantmentHelper.processBlockExperience(level, temp, xp));
        }

        // 连锁额外方块的掉落物统一集中到起始方块，避免满山遍野捡东西
        if (dropOrigin != null) {
            double x = dropOrigin.getX() + 0.5D;
            double y = dropOrigin.getY() + 0.5D;
            double z = dropOrigin.getZ() + 0.5D;
            for (ItemEntity item : event.getDrops()) {
                item.setPos(x, y, z);
                item.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    // ------------------------------------------------------------------ 横扫

    /**
     * 钢剑抢夺：实体战利品查询 Looting 等级时，按模式功能返回 3 级。
     * 只认真实近战击杀（直接造成伤害的是玩家本人），不污染弓 / 三叉戟击杀。
     */
    @SubscribeEvent
    public static void onEntityLoot(EnchantedEntityLootEvent event) {
        if (event.getDamageSource() == null
                || !(event.getDamageSource().getEntity() instanceof ServerPlayer player)
                || event.getDamageSource().getDirectEntity() != player) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof ModeTool modeTool)
                || modeTool.kind() != SteelToolKind.SWORD
                || !modeTool.hasFeature(tool, SteelToolFeature.LOOTING)) {
            return;
        }
        if (event.getEnchantment().is(Enchantments.LOOTING)) {
            event.setEnchantmentLevel(3);
        }
    }

    /** 钢剑横扫：近战命中后，对周围 3 格内其它生物追加 1.75 伤害。 */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (SWEEPING.get()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof ModeTool modeTool)
                || modeTool.kind() != SteelToolKind.SWORD
                || !modeTool.hasFeature(tool, SteelToolFeature.SWEEP)) {
            return;
        }
        if (player.isSprinting() || player.getAttackStrengthScale(0.5F) < 0.9F) {
            return;
        }
        LivingEntity primary = event.getEntity();
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(3.0D),
                entity -> entity != player && entity != primary && entity.isAlive() && !entity.isSpectator());
        if (nearby.isEmpty()) {
            return;
        }
        SWEEPING.set(Boolean.TRUE);
        try {
            for (LivingEntity entity : nearby) {
                entity.hurt(player.damageSources().playerAttack(player), SWEEP_DAMAGE);
                entity.knockback(0.4D, player.getX() - entity.getX(), player.getZ() - entity.getZ());
            }
        } finally {
            SWEEPING.set(Boolean.FALSE);
        }
    }

    private SteelToolEvents() {}
}
