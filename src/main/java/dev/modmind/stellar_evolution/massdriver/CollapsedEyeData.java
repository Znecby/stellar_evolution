package dev.modmind.stellar_evolution.massdriver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 「坍缩末影之眼」里保存的绑定信息（数据组件）。
 *
 * <ul>
 *   <li>{@code ownerName / ownerId}：绑定它的玩家（名字只用于展示，UUID 用于识别）；</li>
 *   <li>{@code point}：玩家<strong>蹲下右键控制器</strong>时所站的位置（右键回溯的落点）；</li>
 *   <li>{@code origin}：绑定时的维度（回溯目标维度）；</li>
 *   <li>{@code target}：在维度选择界面里选中的<strong>发射目标维度</strong>。</li>
 * </ul>
 */
public record CollapsedEyeData(String ownerName, String ownerId, BlockPos point,
                               ResourceKey<Level> origin, ResourceKey<Level> target) {
    public static final Codec<CollapsedEyeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("owner_name").forGetter(CollapsedEyeData::ownerName),
            Codec.STRING.fieldOf("owner_id").forGetter(CollapsedEyeData::ownerId),
            BlockPos.CODEC.fieldOf("point").forGetter(CollapsedEyeData::point),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("origin").forGetter(CollapsedEyeData::origin),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("target").forGetter(CollapsedEyeData::target)
    ).apply(instance, CollapsedEyeData::new));

    public static final StreamCodec<ByteBuf, CollapsedEyeData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CollapsedEyeData::ownerName,
            ByteBufCodecs.STRING_UTF8, CollapsedEyeData::ownerId,
            BlockPos.STREAM_CODEC, CollapsedEyeData::point,
            Identifier.STREAM_CODEC, data -> data.origin().identifier(),
            Identifier.STREAM_CODEC, data -> data.target().identifier(),
            (ownerName, ownerId, point, origin, target) -> new CollapsedEyeData(ownerName, ownerId, point,
                    ResourceKey.create(Registries.DIMENSION, origin),
                    ResourceKey.create(Registries.DIMENSION, target)));
}
