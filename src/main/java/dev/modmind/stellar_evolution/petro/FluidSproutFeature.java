package dev.modmind.stellar_evolution.petro;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.mutable.MutableInt;

/**
 * 流体喷泉：完全照 GT 现代版 {@code gtceu:fluid_sprout}（1.20.1 分支）移植。
 *
 * <p>生成流程：
 * <ol>
 *   <li>以 origin 为球心、{@code size} 为直径灌一个流体球（不筛选目标方块，直接覆盖）；</li>
 *   <li>origin 必须在地表以下（origin.y &lt; WORLD_SURFACE_WG），否则放弃；</li>
 *   <li>以 {@code sprout_chance} 的概率从 origin 向上打一根 1×1 的流体柱，
 *       一直喷到「地表 + surface_offset」；地表以下的部分再加十字形四邻，
 *       于是地表会看到一根 6–9 格高的原油喷泉。</li>
 * </ol>
 *
 * <p>GT 的 {@code raw_oil_sprout} 参数：直径 9–13、喷泉概率 0.4、地表偏移 6–9、Y 10–40、稀有度 1/64。
 */
public class FluidSproutFeature extends Feature<FluidSproutConfiguration> {
    public FluidSproutFeature() {
        super(FluidSproutConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<FluidSproutConfiguration> context) {
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        FluidSproutConfiguration config = context.config();
        MutableInt placedAmount = new MutableInt(0);
        int size = config.size().sample(random);
        int radius = Mth.ceil(size / 2f);
        int x0 = origin.getX() - radius;
        int y0 = origin.getY() - radius;
        int z0 = origin.getZ() - radius;
        int width = size + 1;
        int length = size + 1;
        int height = size + 1;
        if (config.fluid().isSame(Fluids.EMPTY)) {
            return false;
        }
        int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        if (origin.getY() >= surfaceHeight) {
            return false;
        }
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        try (BulkSectionAccess bulkSectionAccess = new BulkSectionAccess(level)) {
            for (int x = 0; x < width; x++) {
                float dx = x * 2f / width - 1;
                if (dx * dx > 1) {
                    continue;
                }
                for (int y = 0; y < height; y++) {
                    float dy = y * 2f / height - 1;
                    if (dx * dx + dy * dy > 1) {
                        continue;
                    }
                    if (level.isOutsideBuildHeight(y0 + y)) {
                        continue;
                    }
                    for (int z = 0; z < length; z++) {
                        float dz = z * 2f / length - 1;
                        if (dx * dx + dy * dy + dz * dz > 1) {
                            continue;
                        }
                        setBlock(mutablePos, x0 + x, y0 + y, z0 + z, bulkSectionAccess, level, config, placedAmount);
                    }
                }
            }
            if (random.nextFloat() <= config.sproutChance()) {
                int currentX = origin.getX();
                int currentZ = origin.getZ();
                int springHeight = surfaceHeight + config.surfaceOffset().sample(random);
                for (int currentY = origin.getY(); currentY <= springHeight; ++currentY) {
                    setBlock(mutablePos, currentX, currentY, currentZ, bulkSectionAccess, level, config, placedAmount);
                    if (currentY <= surfaceHeight) {
                        setBlock(mutablePos, currentX + 1, currentY, currentZ, bulkSectionAccess, level, config, placedAmount);
                        setBlock(mutablePos, currentX - 1, currentY, currentZ, bulkSectionAccess, level, config, placedAmount);
                        setBlock(mutablePos, currentX, currentY, currentZ + 1, bulkSectionAccess, level, config, placedAmount);
                        setBlock(mutablePos, currentX, currentY, currentZ - 1, bulkSectionAccess, level, config, placedAmount);
                    }
                }
            }
        }
        return placedAmount.intValue() > 0;
    }

    private static void setBlock(BlockPos.MutableBlockPos mutablePos, int currentX, int currentY, int currentZ,
                                 BulkSectionAccess access, WorldGenLevel level,
                                 FluidSproutConfiguration config, MutableInt placedAmount) {
        mutablePos.set(currentX, currentY, currentZ);
        if (!level.ensureCanWrite(mutablePos)) {
            return;
        }
        LevelChunkSection section = access.getSection(mutablePos);
        if (section == null) {
            return;
        }
        section.setBlockState(SectionPos.sectionRelative(currentX), SectionPos.sectionRelative(currentY),
                SectionPos.sectionRelative(currentZ),
                config.fluid().defaultFluidState().createLegacyBlock(), false);
        level.getChunk(mutablePos).markPosForPostprocessing(mutablePos);
        placedAmount.add(1);
    }
}
