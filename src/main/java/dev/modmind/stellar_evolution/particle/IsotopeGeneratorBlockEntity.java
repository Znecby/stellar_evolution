package dev.modmind.stellar_evolution.particle;

import dev.modmind.stellar_evolution.thermal.ThermalGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <strong>同位素电源（RTG）</strong>：裂变废料的长期低功率去向。
 *
 * <p>由热室用<strong>锶-90 / 钚-238</strong>制成；不耗燃料、不 tick，热压差网络取数据时现给——
 * 标准型恒 <strong>{@value #CAPACITY} DP</strong> / <strong>{@value #FLOW_RATE} HP·t⁻¹</strong>，
 * 高功率型（锔-244）恒 <strong>{@value #HIGH_CAPACITY} DP</strong> / <strong>{@value #HIGH_FLOW_RATE} HP·t⁻¹</strong>；
 * 定位是「远离主基地的小型/后备供能」，仍弱于燃烧引擎（2 048 DP / 1 024 HP）。
 */
public class IsotopeGeneratorBlockEntity extends BlockEntity implements ThermalGenerator {
    /** 标准同位素电源（锶-90 / 钚-238）：差压容量（DP）。 */
    public static final double CAPACITY = 512.0D;
    /** 标准同位素电源：固定热流率（HP/t）。 */
    public static final double FLOW_RATE = 64.0D;
    /** 高功率同位素电源（锔-244）：差压容量（DP）。 */
    public static final double HIGH_CAPACITY = 2_048.0D;
    /** 高功率同位素电源：固定热流率（HP/t）。 */
    public static final double HIGH_FLOW_RATE = 256.0D;

    public IsotopeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ParticleBlocks.ISOTOPE_GENERATOR_ENTITY.get(), pos, state);
    }

    /** 是不是高功率型号（同一方块实体类型挂两块方块）。 */
    public boolean highPower() {
        return getBlockState().getBlock() == ParticleBlocks.HIGH_POWER_ISOTOPE_GENERATOR.get();
    }

    @Override
    public double capacity() {
        return highPower() ? HIGH_CAPACITY : CAPACITY;
    }

    @Override
    public double flowRate() {
        return highPower() ? HIGH_FLOW_RATE : FLOW_RATE;
    }
}
