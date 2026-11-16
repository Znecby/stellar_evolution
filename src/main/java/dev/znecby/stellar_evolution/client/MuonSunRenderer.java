package dev.znecby.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * 缪子聚变仪球壳内部那颗「太阳」：直接用岩浆方块贴图（含动画）做的自转方块，全亮绘制。
 *
 * <p>球壳是实心的，只有格与格之间留了细缝，所以主要在缝里透出岩浆的暖光；
 * 动画是「两个不同频率正弦叠加的呼吸胀缩」+「X / Y 两轴各自带扰动的自转」。
 */
final class MuonSunRenderer {
    /** 岩浆源方块在方块图集里的贴图（带 flipbook 动画）。 */
    private static final SpriteId LAVA = new SpriteId(TextureAtlas.LOCATION_BLOCKS,
            Identifier.withDefaultNamespace("block/lava_still"));
    /** 半个边长（格）：0.34 → 0.68 格方块；呼吸最大 1.24 倍约 0.84，仍在球壳内径 1.35 里。 */
    private static final float HALF = 0.34F;

    private MuonSunRenderer() {}

    static void submit(FissionMachineRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (!state.muonCore) {
            return;
        }
        TextureAtlasSprite lava = Minecraft.getInstance().getAtlasManager().get(LAVA);
        float age = state.age;
        poseStack.pushPose();
        // 2×2×2 机器的中心在 (1, 1, 1) 格
        poseStack.translate(1.0F, 1.0F, 1.0F);
        // 两轴同时转，转速用正弦扰动：时快时慢，看起来没有固定规律
        poseStack.mulPose(Axis.YP.rotationDegrees(1.2F * age + 18.0F * Mth.sin(0.008F * age + 2.0F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(0.7F * age + 14.0F * Mth.sin(0.011F * age)));
        // 两个频率不成整数比的正弦叠加 → 不规则"呼吸"胀缩（约 0.76~1.24 倍）
        float breath = 1.0F + 0.16F * Mth.sin(0.045F * age) + 0.08F * Mth.sin(0.013F * age + 1.7F);
        poseStack.scale(breath, breath, breath);
        RenderType type = RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);
        collector.submitCustomGeometry(poseStack, type, (pose, vertex) -> cube(pose, vertex, lava));
        poseStack.popPose();
    }

    /** 六个面都贴岩浆贴图；每个面正反各画一次，免得背面剔除后只剩一半。 */
    private static void cube(PoseStack.Pose pose, VertexConsumer vertex, TextureAtlasSprite sprite) {
        float h = HALF;
        face(pose, vertex, sprite, h, -h, -h, h, -h, h, h, h, h, h, h, -h, 1F, 0F, 0F);
        face(pose, vertex, sprite, -h, -h, h, -h, -h, -h, -h, h, -h, -h, h, h, -1F, 0F, 0F);
        face(pose, vertex, sprite, -h, h, -h, h, h, -h, h, h, h, -h, h, h, 0F, 1F, 0F);
        face(pose, vertex, sprite, -h, -h, h, h, -h, h, h, -h, -h, -h, -h, -h, 0F, -1F, 0F);
        face(pose, vertex, sprite, -h, -h, -h, h, -h, -h, h, h, -h, -h, h, -h, 0F, 0F, -1F);
        face(pose, vertex, sprite, -h, -h, h, -h, h, h, h, h, h, h, -h, h, 0F, 0F, 1F);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vertex, TextureAtlasSprite sprite,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        quad(pose, vertex, x0, y0, z0, u0, v1, x1, y1, z1, u1, v1, x2, y2, z2, u1, v0, x3, y3, z3, u0, v0, nx, ny, nz);
        quad(pose, vertex, x3, y3, z3, u0, v0, x2, y2, z2, u1, v0, x1, y1, z1, u1, v1, x0, y0, z0, u0, v1, -nx, -ny, -nz);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertex,
                             float x0, float y0, float z0, float ua, float va,
                             float x1, float y1, float z1, float ub, float vb,
                             float x2, float y2, float z2, float uc, float vc,
                             float x3, float y3, float z3, float ud, float vd,
                             float nx, float ny, float nz) {
        vertex(pose, vertex, x0, y0, z0, ua, va, nx, ny, nz);
        vertex(pose, vertex, x1, y1, z1, ub, vb, nx, ny, nz);
        vertex(pose, vertex, x2, y2, z2, uc, vc, nx, ny, nz);
        vertex(pose, vertex, x3, y3, z3, ud, vd, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertex, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        vertex.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(pose, nx, ny, nz);
    }
}
