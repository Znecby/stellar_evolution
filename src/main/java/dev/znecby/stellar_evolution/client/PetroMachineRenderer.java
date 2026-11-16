package dev.znecby.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.znecby.stellar_evolution.petro.PetroMachineBlockEntity;
import dev.znecby.stellar_evolution.petro.PetroMachineBlocks;
import dev.znecby.stellar_evolution.petro.PetroMachineKind;
import dev.znecby.stellar_evolution.petro.PetroMachinePart;
import dev.znecby.stellar_evolution.multiblock.MultiblockRotation;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * 石化机器渲染器：把每台机器的「可动部件」（各自一个隐藏载体方块）画出来，绕各自枢轴与轴旋转 ——
 * 照生化线 {@code BioMachineRenderer} 口径。
 *
 * <p>每台 2 个部件：分馏塔（顶置冷凝风扇 + 底部卸料阀）、裂解炉（烟囱抽风 + 炉门手轮）、
 * 重整器（搅拌桨 + 底部阀门）、聚合釜（搅拌桨 + 顶部联轴节）、挤出机（卧式螺杆 + 同轴皮带轮）、
 * 硫化机（舱门手轮 + 压力表指针摆动）。
 *
 * <p>手法与 {@code BurningEngineRenderer} 一致：{@code submitMovingBlock} 画隐藏载体方块的烘焙模型，
 * 并按那条坑取「自身 + 六邻居」最亮处采样光照（超格方块自身光照为 0）。
 */
public class PetroMachineRenderer implements BlockEntityRenderer<PetroMachineBlockEntity, PetroMachineRenderState> {
    /** 模型单位 → 方块单位（1px = 1/16）。 */
    private static final float PX = 1.0F / 16.0F;

    public PetroMachineRenderer() {}

    @Override
    public PetroMachineRenderState createRenderState() {
        return new PetroMachineRenderState();
    }

    @Override
    public void extractRenderState(PetroMachineBlockEntity blockEntity, PetroMachineRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        PetroMachineKind kind = PetroMachineBlocks.kindOf(blockEntity.getBlockState().getBlock());
        state.kind = kind;
        state.age = blockEntity.age(partialTicks);
        var blockState = blockEntity.getBlockState();
        state.yaw = blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? MultiblockRotation.yaw(blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)) : 0.0F;
        state.rotors.clear();
        ClientLevel level = blockEntity.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
        for (PetroMachinePart part : kind.parts()) {
            Block carrier = PetroMachineBlocks.carrier(part.carrier());
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
    public void submit(PetroMachineRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.kind == null) {
            return;
        }
        poseStack.pushPose();
        // 机体模型按 32³ 空间绕整机中心 (16,16,16) 预旋转；可动部件绕同一枢轴
        poseStack.translate(1.0F, 1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.translate(-1.0F, -1.0F, -1.0F);
        var parts = state.kind.parts();
        for (int i = 0; i < parts.size(); i++) {
            MovingBlockRenderState rotor = i < state.rotors.size() ? state.rotors.get(i) : null;
            if (rotor == null) {
                continue;
            }
            PetroMachinePart part = parts.get(i);
            float px = part.pivotX() * PX;
            float py = part.pivotY() * PX;
            float pz = part.pivotZ() * PX;
            poseStack.pushPose();
            // 绕枢轴旋转：移到枢轴 → 转 → 移回（submitMovingBlock 以方块角点为原点摆模型）
            poseStack.translate(px, py, pz);
            poseStack.mulPose(rotation(part.axis(), part.angle(state.age)));
            poseStack.translate(-px, -py, -pz);
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
