package dev.modmind.stellar_evolution.particle;

import com.mojang.serialization.Codec;
import dev.modmind.stellar_evolution.stream.StreamType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * <strong>粒子类型</strong>：压力罐里装的是什么，决定粒子源输出哪种流、要多少 DP / HP、每轮磨损多少耐久。
 *
 * <p>数值照《高能物理线资料》§4.3.3 原样；{@link #stream()} 与 {@link StreamType} 一一对应。
 * 序数写进数据组件，<strong>已有值不要插队</strong>。
 */
public enum ParticleType implements StringRepresentable {
    PROTON("proton", StreamType.PROTON, 5.0D, 512.0D, 128.0D, 1),
    DEUTERON("deuteron", StreamType.DEUTERON, 4.0D, 1_024.0D, 256.0D, 1),
    ALPHA("alpha", StreamType.ALPHA, 3.0D, 2_048.0D, 512.0D, 1),
    NEUTRON("neutron", StreamType.NEUTRON, 2.0D, 4_096.0D, 1_024.0D, 1),
    LEAD_ION("lead_ion", StreamType.LEAD_ION, 2.0D, 4_096.0D, 1_024.0D, 1),
    URANIUM_ION("uranium_ion", StreamType.URANIUM_ION, 1.0D, 8_192.0D, 2_048.0D, 1),
    POLARIZED_PROTON("polarized_proton", StreamType.POLARIZED_PROTON, 2.0D, 4_096.0D, 2_048.0D, 1),
    RADIOACTIVE_ION("radioactive_ion", StreamType.RADIOACTIVE_ION, 1.0D, 8_192.0D, 2_048.0D, 1),
    MUON("muon", StreamType.MUON, 1.0D, 16_384.0D, 4_096.0D, 2);

    /** 存档 / 资源用的短 id。 */
    public static final Codec<ParticleType> CODEC =
            Codec.STRING.xmap(ParticleType::byId, ParticleType::id);
    /** 网络同步用。 */
    public static final StreamCodec<ByteBuf, ParticleType> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(ParticleType::byId, ParticleType::id);

    private final String id;
    private final StreamType stream;
    private final double rate;
    private final double requiredDp;
    private final double requiredHp;
    private final int wearPerCycle;

    ParticleType(String id, StreamType stream, double rate, double requiredDp, double requiredHp, int wearPerCycle) {
        this.id = id;
        this.stream = stream;
        this.rate = rate;
        this.requiredDp = requiredDp;
        this.requiredHp = requiredHp;
        this.wearPerCycle = wearPerCycle;
    }

    /** 短 id（如 {@code neutron}）。 */
    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** 对应的粒子流类型。 */
    public StreamType stream() {
        return stream;
    }

    /** 输出速率（单位/t）。 */
    public double rate() {
        return rate;
    }

    /** 开工所需的网络 DP。 */
    public double requiredDp() {
        return requiredDp;
    }

    /** 开工所需的网络 HP/t。 */
    public double requiredHp() {
        return requiredHp;
    }

    /** 每 20t 一轮磨损的耐久。 */
    public int wearPerCycle() {
        return wearPerCycle;
    }

    /** 显示名翻译键。 */
    public String nameKey() {
        return "stellar_evolution.particle." + id;
    }

    /** 按 id 取值；未知 id 兜底 {@link #PROTON}（与 {@link StreamType#byOrdinal} 同口径）。 */
    public static ParticleType byId(String id) {
        for (ParticleType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return PROTON;
    }
}
