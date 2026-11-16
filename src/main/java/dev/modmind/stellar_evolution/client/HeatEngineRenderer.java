package dev.modmind.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.modmind.stellar_evolution.engine.EngineBlocks;
import dev.modmind.stellar_evolution.engine.HeatEngineBlock;
import dev.modmind.stellar_evolution.engine.HeatEngineBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 热力引擎渲染器：只画那根会转的<strong>铁桨叶</strong>（静态的石头 / 铜盘由普通方块模型负责）。
 * <p>
 * 借 {@link SubmitNodeCollector#submitMovingBlock} 直接画「隐藏方块」{@code heat_engine_rotor} 的烘焙模型：
 * 先平移到方块中心，按 {@link HeatEngineRenderState#facingY} 绕 Y 转正（与 blockstate 的 {@code y} 同约定、取负角），
 * 再绕 <b>X 轴</b>（＝方块长轴＝左右接管方向）转 {@link HeatEngineRenderState#angle}，即得「绕中心轴转动」。
 * <p>
 * 高度（y=8）与深度（z=8）都正好落在方块中心，所以旋转轴天然对准结构中心。
 */
public class HeatEngineRenderer implements BlockEntityRenderer<HeatEngineBlockEntity, HeatEngineRenderState> {
    public HeatEngineRenderer() {}

    @Override
    public HeatEngineRenderState createRenderState() {
        return new HeatEngineRenderState();
    }

    @Override
    public void extractRenderState(HeatEngineBlockEntity blockEntity, HeatEngineRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        BlockState blockState = blockEntity.getBlockState();
        state.facingY = yawOf(blockState.getValue(HeatEngineBlock.FACING));
        state.angle = blockEntity.angle(partialTicks);
        state.rotor = blockEntity.getLevel() instanceof ClientLevel level ? movingRotor(level, blockEntity.getBlockPos()) : null;
    }

    @Override
    public void submit(HeatEngineRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        MovingBlockRenderState rotor = state.rotor;
        if (rotor == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingY));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.angle));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        submitNodeCollector.submitMovingBlock(poseStack, rotor);
        poseStack.popPose();
    }

    private static MovingBlockRenderState movingRotor(ClientLevel level, BlockPos pos) {
        MovingBlockRenderState rotor = new MovingBlockRenderState();
        // 注意：移动块采样光照用的是 `blockPos` 那个位置的光，而热机是**实心方块、自身位置光照为 0**，
        // 直接填自身坐标会把转子照成全黑 —— 所以挑「自身 + 六邻居」里最亮的那个位置给它采样。
        // 模型本身由 `blockState` 决定（与位置无关），随机种子仍留在方块自身坐标。
        rotor.blockPos = brightestAround(level, pos);
        rotor.randomSeedPos = pos;
        rotor.blockState = EngineBlocks.HEAT_ENGINE_ROTOR.get().defaultBlockState();
        rotor.biome = level.getBiome(pos);
        rotor.cardinalLighting = level.cardinalLighting();
        rotor.lightEngine = level.getLightEngine();
        return rotor;
    }

    /** 取自身与六个邻居中最亮的位置（邻居即外部空气，光照与方块各面一致）。 */
    private static BlockPos brightestAround(ClientLevel level, BlockPos pos) {
        BlockPos best = pos;
        int bestLight = LevelRenderer.getLightCoords(level, pos);
        for (Direction direction : Direction.values()) {
            BlockPos candidate = pos.relative(direction);
            int light = LevelRenderer.getLightCoords(level, candidate);
            if (light > bestLight) {
                bestLight = light;
                best = candidate;
            }
        }
        return best;
    }

    /** 正面朝向 → 方块状态 {@code y} 旋转角（与 {@code blockstates/heat_engine.json} 一一对应）。 */
    private static float yawOf(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
