package dev.modmind.stellar_evolution.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import org.jetbrains.annotations.Nullable;

/**
 * 「高负荷机械方块」的<strong>连接材质（CTM）模型</strong>：16 × 16 基础贴图（四个外侧角）
 * + 32 × 32 CTM 图集（其余象限），按 8 方向连接情况拼出每个面的 4 个象限。
 *
 * <p>逻辑照 GT {@code TextureConnections} / {@code CTMMeshBuilder} 移植；因为要在
 * {@link DynamicBlockStateModel#collectParts} 里按世界位置取邻居，所以用 NeoForge 的
 * 动态方块状态模型，而不是普通 JSON 模型。
 *
 * <p>方块状态里这样引用：
 * <pre>{@code
 * "model": {
 *   "type": "stellar_evolution:ctm_casing",
 *   "base": "stellar_evolution:block/hatch/hatch_base",
 *   "ctm":  "stellar_evolution:block/hatch/hatch_base_ctm"
 * }
 * }</pre>
 */
public final class CtmCasingModel implements DynamicBlockStateModel {
    /** 面内象限的子图（像素尺度，16 = 整张贴图）。 */
    private record Sub(float width, float height, float xOffset, float yOffset) {
        Sub unit() {
            return new Sub(width / 16.0F, height / 16.0F, xOffset / 16.0F, yOffset / 16.0F);
        }

        Sub flipX() {
            return new Sub(width, height, 16.0F - xOffset - width, yOffset);
        }

        Sub flipY() {
            return new Sub(width, height, xOffset, 16.0F - yOffset - height);
        }
    }

    /** 象限裁剪用的 8 × 8 子图；索引 [x][y]（x = 左 / 右，y = 下 / 上）。 */
    private static final Sub[][] CROP = {
            { new Sub(8, 8, 0, 0), new Sub(8, 8, 8, 0) },
            { new Sub(8, 8, 0, 8), new Sub(8, 8, 8, 8) }
    };

    private final Material.Baked base;
    private final Material.Baked ctm;
    /** 可选的第二层（自发光覆盖层，例如线圈的 bloom）。 */
    private final @Nullable Material.Baked bloom;
    private final @Nullable Material.Baked bloomCtm;
    private final List<BakedQuad>[] allQuads;

    @SuppressWarnings("unchecked")
    public CtmCasingModel(Material.Baked base, Material.Baked ctm,
                          @Nullable Material.Baked bloom, @Nullable Material.Baked bloomCtm) {
        this.base = base;
        this.ctm = ctm;
        this.bloom = bloom;
        this.bloomCtm = bloomCtm;
        this.allQuads = new List[6 * 256];
        for (Direction side : Direction.values()) {
            for (int mask = 0; mask < 256; mask++) {
                this.allQuads[side.ordinal() * 256 + mask] = buildFace(side, mask);
            }
        }
    }

    /** 按连接掩码拼一个面：4 个象限，每个象限从基础贴图或 CTM 图集取图；有 bloom 时再叠一层自发光。 */
    private List<BakedQuad> buildFace(Direction side, int mask) {
        int[][] ids = CtmConnections.submapIds(mask);
        List<BakedQuad> quads = new ArrayList<>(8);
        appendLayer(quads, side, ids, this.base, this.ctm, 0.0F, false);
        if (this.bloom != null) {
            // 向外偏 0.005 格，避免与基底共面闪烁；自发光、不吃阴影
            appendLayer(quads, side, ids, this.bloom, this.bloomCtm, -0.005F, true);
        }
        return List.copyOf(quads);
    }

    private static void appendLayer(List<BakedQuad> quads, Direction side, int[][] ids,
                                    Material.Baked baseMaterial, Material.Baked ctmMaterial,
                                    float depth, boolean emissive) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                int id = ids[x][y];
                int row = id >> 4;
                int column = id & 0xF;
                boolean fromBase = row >= 4 && column >= 4;
                Material.Baked material = fromBase ? baseMaterial : ctmMaterial;
                Sub source = fromBase
                        ? new Sub(8, 8, (column - 4) * 8.0F, (row - 4) * 8.0F)
                        : new Sub(4, 4, column * 4.0F, row * 4.0F);

                MutableQuad quad = new MutableQuad();
                quad.setCubeFaceFromSpriteCoords(side, 0, 0, 1, 1, depth);
                quad.setUv(0, 0, 0);
                quad.setUv(1, 0, 1);
                quad.setUv(2, 1, 1);
                quad.setUv(3, 1, 0);

                subsect(quad, CROP[x][y]);
                remapUvs(quad, source);

                quad.setSprite(material);
                for (int vertex = 0; vertex < 4; vertex++) {
                    quad.setUvFromSprite(vertex, quad.u(vertex), quad.v(vertex));
                }
                if (emissive) {
                    quad.setShade(false);
                    quad.setLightEmission(15);
                    quad.setAmbientOcclusion(false);
                } else {
                    quad.setShade(true);
                }
                quad.recomputeNormals(true);
                quads.add(quad.toBakedQuad());
            }
        }
    }

    /** 把整面四边形裁到某一个 8 × 8 象限（照 GT {@code CTMMeshBuilder#subsect}）。 */
    private static void subsect(MutableQuad quad, Sub submap) {
        Direction normal = quad.direction();
        float[][] xy = new float[4][2];
        float[][] newXy = new float[4][2];
        for (int i = 0; i < 4; i++) {
            switch (normal.getAxis()) {
                case X -> {
                    xy[i][0] = quad.z(i);
                    xy[i][1] = quad.y(i);
                }
                case Y -> {
                    xy[i][0] = quad.x(i);
                    xy[i][1] = quad.z(i);
                }
                case Z -> {
                    xy[i][0] = quad.x(i);
                    xy[i][1] = quad.y(i);
                }
            }
        }

        if (normal != Direction.UP) {
            submap = submap.flipY();
        }
        if (normal == Direction.EAST || normal == Direction.NORTH) {
            submap = submap.flipX();
        }
        submap = submap.unit();

        if (normal.getAxis() == Direction.Axis.Y || normal == Direction.SOUTH || normal == Direction.WEST) {
            newXy[0][0] = Math.max(xy[0][0], submap.xOffset());
            newXy[1][0] = Math.max(xy[1][0], submap.xOffset());
            newXy[2][0] = Math.min(xy[2][0], submap.xOffset() + submap.width());
            newXy[3][0] = Math.min(xy[3][0], submap.xOffset() + submap.width());
        } else {
            newXy[0][0] = Math.min(xy[0][0], submap.xOffset() + submap.width());
            newXy[1][0] = Math.min(xy[1][0], submap.xOffset() + submap.width());
            newXy[2][0] = Math.max(xy[2][0], submap.xOffset());
            newXy[3][0] = Math.max(xy[3][0], submap.xOffset());
        }
        if (normal != Direction.UP) {
            newXy[0][1] = Math.min(xy[0][1], submap.yOffset() + submap.height());
            newXy[1][1] = Math.max(xy[1][1], submap.yOffset());
            newXy[2][1] = Math.max(xy[2][1], submap.yOffset());
            newXy[3][1] = Math.min(xy[3][1], submap.yOffset() + submap.height());
        } else {
            newXy[0][1] = Math.max(xy[0][1], submap.yOffset());
            newXy[1][1] = Math.min(xy[1][1], submap.yOffset() + submap.height());
            newXy[2][1] = Math.min(xy[2][1], submap.yOffset() + submap.height());
            newXy[3][1] = Math.max(xy[3][1], submap.yOffset());
        }

        float u0 = normalize(newXy[0][0], xy[0][0], xy[3][0]);
        float v0 = normalize(newXy[0][1], xy[0][1], xy[1][1]);
        float u1 = normalize(newXy[1][0], xy[1][0], xy[2][0]);
        float v1 = normalize(newXy[1][1], xy[1][1], xy[0][1]);
        float u2 = normalize(newXy[2][0], xy[2][0], xy[1][0]);
        float v2 = normalize(newXy[2][1], xy[2][1], xy[3][1]);
        float u3 = normalize(newXy[3][0], xy[3][0], xy[0][0]);
        float v3 = normalize(newXy[3][1], xy[3][1], xy[2][1]);

        quad.setUv(0, Mth.lerp(u0, quad.u(0), quad.u(3)), Mth.lerp(v0, quad.v(0), quad.v(1)));
        quad.setUv(1, Mth.lerp(u1, quad.u(1), quad.u(2)), Mth.lerp(v1, quad.v(1), quad.v(0)));
        quad.setUv(2, Mth.lerp(u2, quad.u(2), quad.u(1)), Mth.lerp(v2, quad.v(2), quad.v(3)));
        quad.setUv(3, Mth.lerp(u3, quad.u(3), quad.u(0)), Mth.lerp(v3, quad.v(3), quad.v(2)));

        for (int i = 0; i < 4; i++) {
            switch (normal.getAxis()) {
                case X -> quad.setPosition(i, quad.x(i), newXy[i][1], newXy[i][0]);
                case Y -> quad.setPosition(i, newXy[i][0], quad.y(i), newXy[i][1]);
                case Z -> quad.setPosition(i, newXy[i][0], newXy[i][1], quad.z(i));
            }
        }
    }

    /** 把象限内的 0..1 UV 映射到连接子图（照 GT {@code CTMMeshBuilder#remapUVs}）。 */
    private static void remapUvs(MutableQuad quad, Sub submap) {
        Sub unit = submap.unit();
        float[] u = new float[4];
        float[] v = new float[4];
        float maxU = Float.MIN_VALUE;
        float maxV = Float.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            u[i] = quad.u(i);
            v[i] = quad.v(i);
            maxU = Math.max(maxU, u[i]);
            maxV = Math.max(maxV, v[i]);
        }
        float minU = maxU - 0.5F > Mth.EPSILON ? 0.5F : 0.0F;
        float minV = maxV - 0.5F > Mth.EPSILON ? 0.5F : 0.0F;
        float maxUScaled = maxU - 0.5F > Mth.EPSILON ? 1.0F : 0.5F;
        float maxVScaled = maxV - 0.5F > Mth.EPSILON ? 1.0F : 0.5F;
        for (int i = 0; i < 4; i++) {
            u[i] = normalize(u[i], minU, maxUScaled);
            v[i] = normalize(v[i], minV, maxVScaled);
        }

        float quadMinU = Float.MAX_VALUE;
        float quadMinV = Float.MAX_VALUE;
        float quadMaxU = Float.MIN_VALUE;
        float quadMaxV = Float.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            quadMinU = Math.min(quadMinU, u[i]);
            quadMinV = Math.min(quadMinV, v[i]);
            quadMaxU = Math.max(quadMaxU, u[i]);
            quadMaxV = Math.max(quadMaxV, v[i]);
        }
        float width = quadMaxU - quadMinU;
        float height = quadMaxV - quadMinV;

        float baseU = unit.xOffset() + quadMinU * unit.width();
        float baseV = unit.yOffset() + quadMinV * unit.height();
        float endU = baseU + width * unit.width();
        float endV = baseV + height * unit.height();

        for (int i = 0; i < 4; i++) {
            quad.setUv(i, u[i] <= quadMinU ? baseU : endU, v[i] <= quadMinV ? baseV : endV);
        }
    }

    private static float normalize(float value, float min, float max) {
        if (min == max) {
            return 0.5F;
        }
        return Mth.clamp(Mth.inverseLerp(value, min, max), 0.0F, 1.0F);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        parts.add(new Part(this.quadsFor(level, pos, state), this.base));
    }

    /** 兼容旧接口：没有世界上下文时按「全不连接」出图。 */
    @Override
    @SuppressWarnings("deprecation")
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        List<BakedQuad>[] faceQuads = newFaceLists();
        for (Direction side : Direction.values()) {
            faceQuads[side.ordinal()] = this.allQuads[side.ordinal() * 256];
        }
        parts.add(new Part(faceQuads, this.base));
    }

    @Override
    public Material.Baked particleMaterial() {
        return this.base;
    }

    @Override
    public int materialFlags() {
        return 0;
    }

    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                              RandomSource random) {
        long key = 0L;
        for (Direction side : Direction.values()) {
            key |= ((long) CtmConnections.mask(level, pos, state, side)) << (side.ordinal() * 8);
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private List<BakedQuad>[] quadsFor(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        List<BakedQuad>[] faceQuads = new List[6];
        for (Direction side : Direction.values()) {
            int mask = CtmConnections.mask(level, pos, state, side);
            faceQuads[side.ordinal()] = this.allQuads[side.ordinal() * 256 + mask];
        }
        return faceQuads;
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] newFaceLists() {
        return new List[6];
    }

    private record Part(List<BakedQuad>[] quads, Material.Baked particle) implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? List.of() : this.quads[direction.ordinal()];
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public Material.Baked particleMaterial() {
            return this.particle;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }

    /** 未烘焙模型：贴图位置；可选的 {@code bloom} / {@code bloom_ctm} 生成第二层自发光。 */
    public record Unbaked(Identifier base, Identifier ctm, java.util.Optional<Identifier> bloom,
                          java.util.Optional<Identifier> bloomCtm) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
                Identifier.CODEC.fieldOf("ctm").forGetter(Unbaked::ctm),
                Identifier.CODEC.optionalFieldOf("bloom").forGetter(Unbaked::bloom),
                Identifier.CODEC.optionalFieldOf("bloom_ctm").forGetter(Unbaked::bloomCtm)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return CODEC;
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            ModelDebugName name = () -> "stellar_evolution:ctm_casing";
            Material.Baked baseMaterial = baker.materials().get(new Material(this.base), name);
            Material.Baked ctmMaterial = baker.materials().get(new Material(this.ctm), name);
            Material.Baked bloomMaterial = this.bloom.isPresent()
                    ? baker.materials().get(new Material(this.bloom.get()), name) : null;
            Material.Baked bloomCtmMaterial = this.bloomCtm.isPresent()
                    ? baker.materials().get(new Material(this.bloomCtm.get()), name) : null;
            return new CtmCasingModel(baseMaterial, ctmMaterial, bloomMaterial, bloomCtmMaterial);
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            // 贴图直接走 MaterialBaker（方块图集会扫描 textures/block 下的全部贴图），
            // 不能把贴图 id 当成模型依赖 markDependency，否则会报 Missing block model。
        }
    }
}
