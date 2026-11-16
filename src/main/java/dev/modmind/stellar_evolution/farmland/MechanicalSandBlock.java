package dev.modmind.stellar_evolution.farmland;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * <strong>机械沙地</strong>：铜框架 + 沙子的一台设备（不掉落、不受重力），
 * 底下连着铜块即接入热压差网络。
 * <p>
 * 能种什么：进 {@code minecraft:supports_sugar_cane} / {@code minecraft:supports_cactus} 标签 ——
 * 甘蔗 / 仙人掌可以种上来（甘蔗仍需相邻有水）；海带靠 {@code GrowingPlantBlock} 的「附着在坚固方块上」判定，
 * 机械沙地是完整实心方块，水下的海带天然能附着在上面，无需额外标签。
 */
public class MechanicalSandBlock extends MechanicalSoilBlock {
    public static final MapCodec<MechanicalSandBlock> CODEC = simpleCodec(MechanicalSandBlock::new);

    public MechanicalSandBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<MechanicalSandBlock> codec() {
        return CODEC;
    }
}
