package dev.modmind.stellar_evolution.hep;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 高能物理线的「运行时成就」：无法用原版 {@code inventory_changed} 表达的两个瞬间
 * （首次 ADS 嬗变、湮灭反应堆首次点火）用 {@code minecraft:impossible} 触发，
 * 由对应方块实体在成功运行时授予附近玩家。
 */
public final class HepAdvancements {
    /** 首次 ADS 嬗变。 */
    public static final Identifier ADS = StellarEvolution.id("hep/ads");
    /** 湮灭反应堆首次点火。 */
    public static final Identifier ANNIHILATION = StellarEvolution.id("hep/annihilation");

    /** 给半径内的玩家授予成就；已获得的玩家跳过。 */
    public static void awardNearby(ServerLevel level, BlockPos pos, Identifier id, double radius) {
        AdvancementHolder holder = level.getServer().getAdvancements().get(id);
        if (holder == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(pos, radius)
                    && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
                player.getAdvancements().award(holder, "unlock");
            }
        }
    }

    private HepAdvancements() {}
}
