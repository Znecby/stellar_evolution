package dev.modmind.stellar_evolution.thermal;

import com.mojang.serialization.Codec;
import dev.modmind.stellar_evolution.StellarEvolution;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 热室的<strong>等压值</strong>存档（按热室代表格记录）—— 类似格雷的虚拟电路：
 * 热室只结算 {@link HotChamberRecipe#pressure()} 与当前值相同的配方组。
 *
 * <p>热室是「铜箱子 + 铜块」的原版结构，没有自己的方块实体，所以值存在关卡级
 * {@link SavedData} 里，键 = 代表格的 {@link BlockPos#asLong()}。
 */
public final class HotChamberPressureData extends SavedData {
    /** 允许的等压范围（0 = 默认配方组）。 */
    public static final int MAX_PRESSURE = 999;

    private static final Codec<HotChamberPressureData> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .xmap(HotChamberPressureData::new, HotChamberPressureData::serialize);

    public static final SavedDataType<HotChamberPressureData> TYPE =
            new SavedDataType<>(StellarEvolution.id("hot_chamber_pressure"),
                    HotChamberPressureData::new, CODEC);

    private final Map<Long, Integer> values = new HashMap<>();

    public HotChamberPressureData() {}

    private HotChamberPressureData(Map<String, Integer> map) {
        map.forEach((key, value) -> {
            try {
                values.put(Long.parseLong(key), value);
            } catch (NumberFormatException ignored) {
                // 坏键直接跳过，不阻塞整份存档
            }
        });
    }

    private Map<String, Integer> serialize() {
        Map<String, Integer> out = new HashMap<>();
        values.forEach((pos, value) -> out.put(Long.toString(pos), value));
        return out;
    }

    public static HotChamberPressureData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 某热室的等压值（默认 0）。 */
    public int get(BlockPos controller) {
        return values.getOrDefault(controller.asLong(), 0);
    }

    /** 写入等压值（夹到 0..{@value #MAX_PRESSURE}）；变化时才标脏。 */
    public void set(BlockPos controller, int pressure) {
        int clamped = Math.max(0, Math.min(MAX_PRESSURE, pressure));
        if (values.getOrDefault(controller.asLong(), 0) == clamped) {
            return;
        }
        values.put(controller.asLong(), clamped);
        setDirty();
    }
}
