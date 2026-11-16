package dev.modmind.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.modmind.stellar_evolution.fission.FissionAnimated;
import dev.modmind.stellar_evolution.fission.FissionMachinePart;
import dev.modmind.stellar_evolution.fission.FissionMachineStructure;
import dev.modmind.stellar_evolution.multiblock.MultiblockRotation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * 裂变机器渲染器基类：把每台机器的「可动部件」（各自一个隐藏载体方块）画出来，绕各自枢轴与轴旋转 ——
 * 照石化线 {@code PetroMachineRenderer} 口径。
 *
 * <p>三个方块实体（燃料机器 / 水冷反应堆 / 蒸汽轮机）共用本实现，只是泛型不同。
 */
public abstract class FissionRenderer<T extends BlockEntity & FissionAnimated>
        implements BlockEntityRenderer<T, FissionMachineRenderState> {
    /** 模型单位 → 方块单位（1px = 1/16）。 */
    private static final float PX = 1.0F / 16.0F;

    @Override
    public FissionMachineRenderState createRenderState() {
        return new FissionMachineRenderState();
    }

    @Override
    public void extractRenderState(T blockEntity, FissionMachineRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.parts = FissionMachineStructure.partsFor(blockEntity.getBlockState().getBlock());
        state.age = blockEntity.age(partialTicks);
        var blockState = blockEntity.getBlockState();
        state.yaw = blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? MultiblockRotation.yaw(blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)) : 0.0F;
        state.rotors.clear();
        ClientLevel level = blockEntity.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
        for (FissionMachinePart part : state.parts) {
            Block carrier = FissionMachineStructure.carrier(part.carrier());
            state.rotors.add(level != null && carrier != null ? moving(level, blockEntity.getBlockPos(), carrier) : null);
        }
        // 机器是整格、自身光照可能为 0：采样「自身 + 六邻居」最亮处
        if (level != null) {
            BlockPos pos = blockEntity.getBlockPos();
            int light = state.lightCoords;
            for (Direction direction : Direction.values()) {
                light = net.minecraft.util.LightCoordsUtil.max(light, LevelRenderer.getLightCoords(level, pos.relative(direction)));
            }
            state.lightCoords = light;
        }
    }

    @Override
    public void submit(FissionMachineRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        poseStack.pushPose();
        // 机体模型按 32³ 空间绕整机中心 (16,16,16) 预旋转；可动部件绕同一枢轴
        poseStack.translate(1.0F, 1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.translate(-1.0F, -1.0F, -1.0F);
        for (int i = 0; i < state.parts.size(); i++) {
            MovingBlockRenderState rotor = i < state.rotors.size() ? state.rotors.get(i) : null;
            if (rotor == null) {
                continue;
            }
            FissionMachinePart part = state.parts.get(i);
            poseStack.pushPose();
            if (part.translate()) {
                // 平移部件（缪子聚变仪的插栓）：沿自身轴向反复深入 / 抽出
                float shift = part.offset(state.age) * PX;
                switch (part.axis()) {
                    case X -> poseStack.translate(shift, 0F, 0F);
                    case Z -> poseStack.translate(0F, 0F, shift);
                    default -> poseStack.translate(0F, shift, 0F);
                }
            } else {
                float px = part.pivotX() * PX;
                float py = part.pivotY() * PX;
                float pz = part.pivotZ() * PX;
                // 绕枢轴旋转：移到枢轴 → 转 → 移回（submitMovingBlock 以方块角点为原点摆模型）
                poseStack.translate(px, py, pz);
                poseStack.mulPose(rotation(part.axis(), part.angle(state.age)));
                poseStack.translate(-px, -py, -pz);
            }
            submitNodeCollector.submitMovingBlock(poseStack, rotor);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** 旋转轴 + 角度 → 四元数。 */
    private static Quaternionf rotation(Direction.Axis axis, float degrees) {
        return switch (axis) {
            case X -> Axis.XP.rotationDegrees(degrees);
            case Z -> Axis.ZP.rotationDegrees(degrees);
            default -> Axis.YP.rotationDegrees(degrees);
        };
    }

    private static MovingBlockRenderState moving(ClientLevel level, BlockPos pos, Block block) {
        MovingBlockRenderState moving = new MovingBlockRenderState();
        moving.blockPos = brightestAround(level, pos);
        moving.randomSeedPos = pos;
        moving.blockState = block.defaultBlockState();
        moving.biome = level.getBiome(pos);
        moving.cardinalLighting = level.cardinalLighting();
        moving.lightEngine = level.getLightEngine();
        return moving;
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
}
