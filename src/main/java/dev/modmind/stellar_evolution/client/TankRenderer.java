package dev.modmind.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.modmind.stellar_evolution.tank.TankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * 储罐渲染器：把罐里的流体画在内部空腔里（玻璃后面），液面高度 = 存量 / 大罐容量。
 * <p>
 * 只由<strong>控制器</strong>画一次，画的是<strong>整个大罐</strong>的空腔（按 {@code width × height} 铺开），
 * 所以 3×3 / 上下叠的罐看起来是一整罐流体，不会被隔墙切成几块。
 * <p>
 * 做法：拿该流体的 {@link FluidModel}（静置贴图 + 染色），用
 * {@link SubmitNodeCollector#submitCustomGeometry} 自己搭一个长方体 —— 只有上表面与四个侧面，
 * 底面被铜底板挡住所以不画。
 * <p>
 * <b>注意（踩过的坑）</b>：{@code submitCustomGeometry} 的回调给的是 {@link PoseStack.Pose}（一个矩阵），
 * <b>不是</b> PoseStack —— {@code buffer.addVertex(x,y,z,...)} 不会自动应用变换，必须用
 * {@link VertexConsumer#addVertex(PoseStack.Pose, float, float, float) 带 Pose 的重载}
 * （法线用 {@link VertexConsumer#setNormal(PoseStack.Pose, float, float, float)}）。
 * 早先直接写方块局部 0..1 坐标，几何就被丢到了错误位置、罐里什么都看不到。
 * <p>
 * <b>为什么用 cutout 层</b>：玻璃墙走模型的 translucent 层；{@code submitCustomGeometry} 内部按
 * {@code renderType.hasBlending()} 分组，cutout 无混合 → 归 solid 组，一定早于 translucent 绘制，
 * 于是「先流体、后玻璃」的顺序天然成立，玻璃正叠在流体上。
 * <p>
 * 顶点顺序照抄原版 {@code FluidRenderer}（保证朝向 / 背面剔除正确）；法线原版也是常量占位，故同样给常量。
 */
public class TankRenderer implements BlockEntityRenderer<TankBlockEntity, TankRenderState> {
    /** 空腔范围（单格方块空间 0..1）：x/z 4..12 px，y 从 4 px 起、最高到 12 px。 */
    private static final float MIN = 4.0F / 16.0F;
    private static final float MAX = 12.0F / 16.0F;
    private static final float BOTTOM = 4.0F / 16.0F;
    /** 单格空腔净高：12/16 - 4/16 = 8/16。 */
    private static final float HEIGHT = 8.0F / 16.0F;

    public TankRenderer() {}

    @Override
    public TankRenderState createRenderState() {
        return new TankRenderState();
    }

    @Override
    public void extractRenderState(TankBlockEntity blockEntity, TankRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.active = blockEntity.isController();
        state.width = blockEntity.width();
        state.height = blockEntity.height();
        state.capacityPerBlock = blockEntity.capacityPerBlock();
        state.fluid = state.active ? blockEntity.fluid() : FluidStack.EMPTY;
        // 罐是实心方块：它「自己位置」的光照实测是 0x0（实心挡光），拿它画流体就会全黑。
        // 改取「自身 + 六个邻居」的最大值 —— 邻居是罐外的空气，正是方块模型各面使用的那份光照，
        // 这样流体亮度就和罐体一致（原版 FluidRenderer 也是 max(自身, 上方) 这个路子）。
        if (blockEntity.getLevel() instanceof Level level) {
            BlockPos pos = blockEntity.getBlockPos();
            int light = state.lightCoords;
            for (Direction direction : Direction.values()) {
                light = LightCoordsUtil.max(light, LevelRenderer.getLightCoords(level, pos.relative(direction)));
            }
            state.lightCoords = light;
        }
    }

    @Override
    public void submit(TankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        FluidStack fluid = state.fluid;
        if (!state.active || fluid.isEmpty()) {
            return;
        }
        // 整罐容量 = 格数 × 本档位单格容量；液面高度 = 存量百分比 × 空腔净高
        long capacity = (long) state.width * state.width * state.height * state.capacityPerBlock;
        float fraction = capacity <= 0 ? 0.0F : Math.min(1.0F, (float) fluid.getAmount() / capacity);
        if (fraction <= 0.0F) {
            return;
        }

        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(fluid.getFluid().defaultFluidState());
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        // 无染色源时用白色；强制不透明（cutout 的 alpha 测试会把 alpha=0 的像素整个丢掉）
        int tint = model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fluid) : -1;
        final int color = tint | 0xFF000000;

        // 整个大罐的空腔：水平从 4px 到 (边长-1)+12px，竖直从 4px 起、净高 (高-0.5) 格
        final float minX = MIN;
        final float maxX = (state.width - 1) + MAX;
        final float minZ = MIN;
        final float maxZ = (state.width - 1) + MAX;
        final float cavity = (state.height - 1) + HEIGHT;
        final float surface = BOTTOM + cavity * fraction;
        // 空腔按「每格 8px」切成单元：合并后的罐要一格一格铺贴图，不能把一张贴图拉满整个大罐
        final int cellsX = state.width * 2 - 1;
        final int cellsZ = state.width * 2 - 1;
        final int cellsY = state.height * 2 - 1;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int light = state.lightCoords;

        submitNodeCollector.submitCustomGeometry(poseStack, Sheets.cutoutBlockSheet(), (pose, buffer) -> {
            // 上表面：按空腔单元逐格铺满
            for (int i = 0; i < cellsX; i++) {
                float xa = minX + i * HEIGHT;
                float xb = xa + HEIGHT;
                for (int k = 0; k < cellsZ; k++) {
                    float za = minZ + k * HEIGHT;
                    float zb = za + HEIGHT;
                    vertex(buffer, pose, xa, surface, za, color, u0, v0, light);
                    vertex(buffer, pose, xa, surface, zb, color, u0, v1, light);
                    vertex(buffer, pose, xb, surface, zb, color, u1, v1, light);
                    vertex(buffer, pose, xb, surface, za, color, u1, v0, light);
                }
            }
            // 四个侧面：横竖都按空腔单元切；最上面那层只画到液面，并按该层剩余比例裁贴图
            for (int j = 0; j < cellsY; j++) {
                float yb = BOTTOM + j * HEIGHT;
                if (surface <= yb) {
                    break;
                }
                float yt = Math.min(yb + HEIGHT, surface);
                boolean partial = yt < yb + HEIGHT - 1.0E-4F;
                float vt = partial ? v1 - (v1 - v0) * ((yt - yb) / HEIGHT) : v0;
                for (int i = 0; i < cellsX; i++) {
                    float xa = minX + i * HEIGHT;
                    float xb = xa + HEIGHT;
                    // 北（z = minZ）
                    vertex(buffer, pose, xa, yt, minZ, color, u0, vt, light);
                    vertex(buffer, pose, xb, yt, minZ, color, u1, vt, light);
                    vertex(buffer, pose, xb, yb, minZ, color, u1, v1, light);
                    vertex(buffer, pose, xa, yb, minZ, color, u0, v1, light);
                    // 南（z = maxZ）
                    vertex(buffer, pose, xb, yt, maxZ, color, u0, vt, light);
                    vertex(buffer, pose, xa, yt, maxZ, color, u1, vt, light);
                    vertex(buffer, pose, xa, yb, maxZ, color, u1, v1, light);
                    vertex(buffer, pose, xb, yb, maxZ, color, u0, v1, light);
                }
                for (int k = 0; k < cellsZ; k++) {
                    float za = minZ + k * HEIGHT;
                    float zb = za + HEIGHT;
                    // 西（x = minX）
                    vertex(buffer, pose, minX, yt, zb, color, u0, vt, light);
                    vertex(buffer, pose, minX, yt, za, color, u1, vt, light);
                    vertex(buffer, pose, minX, yb, za, color, u1, v1, light);
                    vertex(buffer, pose, minX, yb, zb, color, u0, v1, light);
                    // 东（x = maxX）
                    vertex(buffer, pose, maxX, yt, za, color, u0, vt, light);
                    vertex(buffer, pose, maxX, yt, zb, color, u1, vt, light);
                    vertex(buffer, pose, maxX, yb, zb, color, u1, v1, light);
                    vertex(buffer, pose, maxX, yb, za, color, u0, v1, light);
                }
            }
        });
    }

    /**
     * 大罐的流体几何会超出控制器那一格，所以不吃「方块是否在视锥内」的裁剪，交给距离判断即可。
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /**
     * 一个顶点：用「带 Pose 的重载」先把方块局部坐标变换到世界空间，再写颜色 / UV / 光照 / 法线。
     * 法线给常量占位（与原版流体渲染一致，光照靠顶点色）。
     */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                               int color, float u, float v, int light) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
