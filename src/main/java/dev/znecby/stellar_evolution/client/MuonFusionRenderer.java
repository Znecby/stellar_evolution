package dev.znecby.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.znecby.stellar_evolution.muon.MuonFusionBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 缪子聚变仪渲染器：可动插栓照 {@link FissionRenderer}，另外在球壳内部画一颗「太阳」。 */
public class MuonFusionRenderer extends FissionRenderer<MuonFusionBlockEntity> {
    @Override
    public void extractRenderState(MuonFusionBlockEntity blockEntity, FissionMachineRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.muonCore = true;
    }

    @Override
    public void submit(FissionMachineRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);
        poseStack.pushPose();
        // 太阳在球壳中心：跟机体一起绕整机中心旋转
        poseStack.translate(1.0F, 1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.translate(-1.0F, -1.0F, -1.0F);
        MuonSunRenderer.submit(state, poseStack, submitNodeCollector);
        poseStack.popPose();
    }
}
