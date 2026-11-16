package dev.znecby.stellar_evolution.stream;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * <strong>粒子流类型</strong>（纯数据，不依赖世界 / 方块逻辑）。
 *
 * <p>每种聚变芯核只对应一种粒子流；束流管也按类型互连 —— <strong>不同流的管子不会连在一起</strong>。
 * 序数同时写进束流管的方块状态（{@code BeamPipeBlock.STREAM}），<strong>已有值不要插队</strong>，新类型一律追加。
 */
public enum StreamType implements StringRepresentable {
    /** 缪子流：氘氚芯核的产物（也是粒子源的 MUON 罐）。 */
    MUON("muon", "stellar_evolution.stream.muon", "stellar_evolution.stream.muon.unit"),
    /** 质子流：粒子源最基础的罐。 */
    PROTON("proton", "stellar_evolution.stream.proton", "stellar_evolution.stream.proton.unit"),
    /** 氘核流。 */
    DEUTERON("deuteron", "stellar_evolution.stream.deuteron", "stellar_evolution.stream.deuteron.unit"),
    /** α 粒子流。 */
    ALPHA("alpha", "stellar_evolution.stream.alpha", "stellar_evolution.stream.alpha.unit"),
    /** 中子流：辐照 / 增殖 / 嬗变的主用流。 */
    NEUTRON("neutron", "stellar_evolution.stream.neutron", "stellar_evolution.stream.neutron.unit"),
    /** 铅离子流：重离子碰撞。 */
    LEAD_ION("lead_ion", "stellar_evolution.stream.lead_ion", "stellar_evolution.stream.lead_ion.unit"),
    /** 铀离子流：最重的离子束。 */
    URANIUM_ION("uranium_ion", "stellar_evolution.stream.uranium_ion", "stellar_evolution.stream.uranium_ion.unit"),
    /** 极化质子流。 */
    POLARIZED_PROTON("polarized_proton",
            "stellar_evolution.stream.polarized_proton", "stellar_evolution.stream.polarized_proton.unit"),
    /** 放射性离子流。 */
    RADIOACTIVE_ION("radioactive_ion",
            "stellar_evolution.stream.radioactive_ion", "stellar_evolution.stream.radioactive_ion.unit");

    private final String id;
    private final String nameKey;
    private final String unitKey;

    /** 存档 / JEI ingredient 用的字符串编解码。 */
    public static final Codec<StreamType> CODEC = Codec.STRING.xmap(StreamType::byId, StreamType::id);

    StreamType(String id, String nameKey, String unitKey) {
        this.id = id;
        this.nameKey = nameKey;
        this.unitKey = unitKey;
    }

    /** 存档 / 资源用的短 id（如 {@code muon}）。 */
    public String id() {
        return id;
    }

    /** 方块状态序列化名（{@code EnumProperty} 要求）。 */
    @Override
    public String getSerializedName() {
        return id;
    }

    /** 显示名翻译键（如「缪子流」）。 */
    public String nameKey() {
        return nameKey;
    }

    /** 单位翻译键（如「μ」）。 */
    public String unitKey() {
        return unitKey;
    }

    /** 按序数安全取值（方块状态 / Jade 传输用）。 */
    public static StreamType byOrdinal(int ordinal) {
        StreamType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MUON;
    }

    /** 按 id 取值；未知 id 兜底 {@link #MUON}。 */
    public static StreamType byId(String id) {
        for (StreamType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return MUON;
    }
}
