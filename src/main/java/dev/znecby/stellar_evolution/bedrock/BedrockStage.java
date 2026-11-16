package dev.znecby.stellar_evolution.bedrock;

import com.mojang.serialization.Codec;
import java.util.Locale;

/**
 * 基岩浸出渣的<strong>段位</strong>（S1–S4）。
 *
 * <p>与 {@code LeachResidueType} 同一口径：一个残渣物品 + 4 个变体，
 * 不新增 4 个残渣 id；下一段配方按段位匹配。
 */
public enum BedrockStage {
    /** 卤化挥发段之后的残渣。 */
    S1,
    /** 自热熔盐段之后的残渣。 */
    S2,
    /** α 螯合段之后的残渣。 */
    S3,
    /** 硫化沉淀段之后的残渣。 */
    S4;

    public static final Codec<BedrockStage> CODEC = Codec.STRING.xmap(
            name -> valueOf(name.toUpperCase(Locale.ROOT)),
            stage -> stage.name().toLowerCase(Locale.ROOT));
}
