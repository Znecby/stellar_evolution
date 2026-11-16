package dev.modmind.stellar_evolution.farmland;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * <strong>机械灵魂沙地</strong>：铜框架 + 灵魂沙的一台设备（不掉落、不受重力），
 * 底下连着铜块即接入热压差网络。
 * <p>
 * 能种什么：进 {@code minecraft:supports_nether_wart} 标签 —— 下界疣可以种上来，并由方块实体自动催熟 + 收割复种。
 */
public class MechanicalSoulSandBlock extends MechanicalSoilBlock {
    public static final MapCodec<MechanicalSoulSandBlock> CODEC = simpleCodec(MechanicalSoulSandBlock::new);

    public MechanicalSoulSandBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<MechanicalSoulSandBlock> codec() {
        return CODEC;
    }
}
