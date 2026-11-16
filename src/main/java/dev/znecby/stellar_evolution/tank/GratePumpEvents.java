package dev.znecby.stellar_evolution.tank;

import dev.znecby.stellar_evolution.StellarEvolution;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 铜格栅的泵模式驱动：原版方块没有方块实体，所以用服务端 tick 事件跑
 * （只有「被网络登记过」的格栅才会被遍历，见 {@link GrateSource#register}）。
 */
@EventBusSubscriber(modid = StellarEvolution.MOD_ID)
public final class GratePumpEvents {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            GrateSource.pumpTick(level);
        }
    }

    private GratePumpEvents() {}
}
