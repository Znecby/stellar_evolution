package dev.znecby.stellar_evolution.engine;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 火箭引擎尾焰：8 格长的锥形喷流（参考 HBM 涡扇的「焰心 + 烟环」层次）。
 *
 * <ul>
 *   <li><strong>焰心</strong>：喷口处蓝白色（灵魂火），沿轴向后过渡为橙色火焰；</li>
 *   <li><strong>烟环</strong>：每个采样点沿锥面撒大烟 / 云，越远越粗、越散；</li>
 *   <li><strong>吹飞</strong>：沿喷口方向强推（带一点上抛与径向外抛）；</li>
 *   <li><strong>点燃</strong>：着火 10 秒；</li>
 *   <li><strong>瞬间伤害</strong>：自定义伤害类型 {@code rocket_exhaust}（带 {@code bypasses_armor} 标签），
 *       数值 1000 —— 全套下界合金也一瞬即死。</li>
 * </ul>
 */
public final class RocketPlume {
    /** 自定义伤害类型 id。 */
    public static final String DAMAGE_ID = "rocket_exhaust";
    /** 尾焰长度（格）。 */
    public static final double LENGTH = 8.0D;
    /** 尾焰锥最大半径（格）。 */
    public static final double RADIUS = 2.0D;
    /** 伤害值：远高于任何护甲能挡的量。 */
    public static final float DAMAGE = 1000.0F;
    /** 点燃时长（tick）= 10 秒。 */
    public static final int FIRE_TICKS = 200;
    /** 推力强度。 */
    public static final double KNOCKBACK = 2.2D;
    /** 沿轴的粒子采样点数（8 格 / 12 ≈ 每 0.67 格一个）。 */
    private static final int SAMPLES = 12;

    /** 服务端每 tick 结算一次（喷口方向的 8 格锥形）。 */
    public static void tick(ServerLevel level, BlockPos pos, Direction dir) {
        Vec3 axis = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        // 机器占 2×2×2（主方块在 min 角）：喷口在整机外表面，即方块中心再外移 1 格。
        Vec3 base = Vec3.atCenterOf(pos).add(0.5D, 0.5D, 0.5D).add(axis.scale(1.0D));

        spawnPlume(level, base, axis);
        applyDamage(level, base, axis);
    }

    // ------------------------------------------------------------------ 粒子

    /**
     * 锥形焰流：每个采样点画「焰心」+「锥面烟环」，越远半径越大。
     * 轴是水平的（火箭只朝四个水平方向喷），所以用世界上方向 + 侧向构造截面圆。
     */
    private static void spawnPlume(ServerLevel level, Vec3 base, Vec3 axis) {
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = axis.cross(up).normalize();

        for (int i = 1; i <= SAMPLES; i++) {
            double t = i / (double) SAMPLES;
            double distance = LENGTH * t;
            Vec3 center = base.add(axis.scale(distance));

            // 焰心：近喷口蓝白，中段橙黄，末端变暗烟
            if (i <= 2) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        center.x, center.y, center.z, 3, 0.07D, 0.07D, 0.07D, 0.0D);
            } else if (i <= 9) {
                level.sendParticles(ParticleTypes.FLAME,
                        center.x, center.y, center.z, 3, 0.10D, 0.10D, 0.10D, 0.01D);
            } else {
                level.sendParticles(ParticleTypes.SMALL_FLAME,
                        center.x, center.y, center.z, 2, 0.12D, 0.12D, 0.12D, 0.01D);
            }

            // 锥面烟环：半径随距离张开，末端向外翻卷
            double radius = 0.25D + (RADIUS - 0.25D) * t * t;
            for (int ring = 0; ring < 2; ring++) {
                double angle = (level.getRandom().nextDouble() * 2.0D - 1.0D) * Math.PI;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double px = center.x + (side.x * cos + up.x * sin) * radius;
                double py = center.y + (side.y * cos + up.y * sin) * radius;
                double pz = center.z + (side.z * cos + up.z * sin) * radius;
                level.sendParticles(i >= 8 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                        px, py, pz, 1, 0.06D, 0.06D, 0.06D, 0.008D);
            }

            // 末端：大团烟被吹散
            if (i == SAMPLES) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        center.x, center.y, center.z, 4, 0.35D, 0.35D, 0.35D, 0.02D);
            }
        }

        // 喷口火花（短距离、贴着脸喷）
        for (int i = 0; i < 4; i++) {
            level.sendParticles(ParticleTypes.FLAME,
                    base.x, base.y, base.z, 1, 0.12D, 0.12D, 0.12D, 0.03D);
        }
    }

    // ------------------------------------------------------------------ 伤害

    /** 锥形范围结算：吹飞所有实体，点燃并秒杀活体。 */
    private static void applyDamage(ServerLevel level, Vec3 base, Vec3 axis) {
        Vec3 tip = base.add(axis.scale(LENGTH));

        var lookup = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var holder = lookup.get(ResourceKey.create(Registries.DAMAGE_TYPE, StellarEvolution.id(DAMAGE_ID)));
        DamageSource source = holder.map(h -> new DamageSource(h, null, null)).orElse(null);

        AABB box = new AABB(base, tip).inflate(RADIUS + 0.5D);
        for (Entity entity : level.getEntities((Entity) null, box)) {
            Vec3 rel = entity.position().subtract(base);
            double along = rel.dot(axis);
            if (along < 0.0D || along > LENGTH) {
                continue;
            }
            Vec3 lateralVec = rel.subtract(axis.scale(along));
            double lateral = lateralVec.length();
            if (lateral > RADIUS) {
                continue;
            }

            // 点燃 + 破甲秒杀（先清无敌帧，保证每 tick 都能结算）
            if (entity instanceof LivingEntity living) {
                living.setRemainingFireTicks(Math.max(living.getRemainingFireTicks(), FIRE_TICKS));
                if (source != null) {
                    living.invulnerableTime = 0;
                    living.hurt(source, DAMAGE);
                }
            }

            // 吹飞：主轴强推 + 少量径向外抛 + 上抛
            double outward = lateral > 1.0E-4D ? 0.35D / lateral : 0.0D;
            entity.push(axis.x * KNOCKBACK + lateralVec.x * outward,
                    0.75D,
                    axis.z * KNOCKBACK + lateralVec.z * outward);
            entity.hurtMarked = true;
        }
    }

    private RocketPlume() {}
}
