package dev.modmind.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.modmind.stellar_evolution.bio.BioMachineBlockEntity;
import dev.modmind.stellar_evolution.bio.BioMachineBlocks;
import dev.modmind.stellar_evolution.bio.BioMachineKind;
import dev.modmind.stellar_evolution.bio.BioMachinePart;
import dev.modmind.stellar_evolution.multiblock.MultiblockRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * 生化机器渲染器：把每台机器的「可动部件」（各自一个隐藏载体方块）画出来，绕各自枢轴与轴旋转。
 * <p>
 * 每台 2 个部件：调浆机（下层桨叶 + 上层锚式框）、发酵罐（顶置桨 + 飞轮）、离心机（转鼓 + 同轴皮带轮）、
 * 灭菌罐（舱门手轮 + 压力表指针摆动）、结晶机（晶体簇 + 底部星型卸料阀）、生物反应器（风扇 + 导流涡轮）。
 * <p>
 * 手法与 {@link BurningEngineRenderer} 一致：{@code submitMovingBlock} 画隐藏载体方块的烘焙模型，
 * 并按那条坑取「自身 + 六邻居」最亮处采样光照（超格方块自身光照为 0）。
 * 部件模型同样画在 0..32 空间（所以整件是 2×2×2）；枢轴 / 轴 / 转速由 {@link BioMachinePart} 给出。
 */
public class BioMachineRenderer implements BlockEntityRenderer<BioMachineBlockEntity, BioMachineRenderState> {
    /** 模型单位 → 方块单位（1px = 1/16）。 */
    private static final float PX = 1.0F / 16.0F;

    public BioMachineRenderer() {}

    @Override
    public BioMachineRenderState createRenderState() {
        return new BioMachineRenderState();
    }

    @Override
    public void extractRenderState(BioMachineBlockEntity blockEntity, BioMachineRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        BioMachineKind kind = BioMachineBlocks.kindOf(blockEntity.getBlockState().getBlock());
        state.kind = kind;
        state.age = blockEntity.age(partialTicks);
        state.fluidFill = blockEntity.fluidFill(partialTicks);
        var blockState = blockEntity.getBlockState();
        state.yaw = blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? MultiblockRotation.yaw(blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)) : 0.0F;
        state.rotors.clear();
        ClientLevel level = blockEntity.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
        for (BioMachinePart part : kind.parts()) {
            Block carrier = BioMachineBlocks.carrier(part.carrier());
            state.rotors.add(level != null && carrier != null ? moving(level, blockEntity.getBlockPos(), carrier) : null);
        }
        // 罐里的水用「自身 + 六邻居」最亮处采样光照（机器是整格、自身光照可能为 0）
        if (level != null) {
            BlockPos pos = blockEntity.getBlockPos();
            int light = state.lightCoords;
            for (Direction direction : Direction.values()) {
                light = LightCoordsUtil.max(light, LevelRenderer.getLightCoords(level, pos.relative(direction)));
            }
            state.lightCoords = light;
        }
    }

    @Override
    public void submit(BioMachineRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
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
            BioMachinePart part = parts.get(i);
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
        // 生物反应釜：机器网络里有流体时，在笼式罐体里画「升起的水」（fill 由客户端向目标平滑逼近）
        if (state.kind == BioMachineKind.REACTOR && state.fluidFill > 0.0F) {
            submitFluid(poseStack, submitNodeCollector, state.lightCoords, state.fluidFill);
        }
        poseStack.popPose();
    }

    /** 罐内腔（模型单位）：x/z 9..23、y 7..16（灌满 ≈ 到视窗玻璃上沿）。 */
    private static final float FLUID_MIN = 9.0F * PX;
    private static final float FLUID_MAX = 23.0F * PX;
    private static final float FLUID_BOTTOM = 7.0F * PX;
    private static final float FLUID_TOP = 16.0F * PX;

    /**
     * 在罐里画水：<strong>无论机器网络里是什么流体，都用水</strong>的贴图与染色。
     *
     * @param fill 当前水位比例 0..1（客户端在上升 / 下降过程中逐 tick 逼近目标，所以看得出「升起」）
     */
    private static void submitFluid(PoseStack poseStack, SubmitNodeCollector collector, int light, float fill) {
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(Fluids.WATER.defaultFluidState());
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        int tint = model.fluidTintSource() != null
                ? model.fluidTintSource().colorAsStack(new FluidStack(Fluids.WATER, 1000))
                : -1;
        int color = tint | 0xFF000000;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        // 液面高度按 fill 从罐底往上长；侧面贴图只取到液面为止（vTop）
        float top = FLUID_BOTTOM + (FLUID_TOP - FLUID_BOTTOM) * fill;
        float vTop = v1 - (v1 - v0) * fill;
        collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockSheet(), (pose, buffer) -> {
            // 上表面（贴图铺满）
            vertex(buffer, pose, FLUID_MIN, top, FLUID_MIN, color, u0, v0, light);
            vertex(buffer, pose, FLUID_MIN, top, FLUID_MAX, color, u0, v1, light);
            vertex(buffer, pose, FLUID_MAX, top, FLUID_MAX, color, u1, v1, light);
            vertex(buffer, pose, FLUID_MAX, top, FLUID_MIN, color, u1, v0, light);
            // 北（z = MIN）
            vertex(buffer, pose, FLUID_MIN, top, FLUID_MIN, color, u0, vTop, light);
            vertex(buffer, pose, FLUID_MAX, top, FLUID_MIN, color, u1, vTop, light);
            vertex(buffer, pose, FLUID_MAX, FLUID_BOTTOM, FLUID_MIN, color, u1, v1, light);
            vertex(buffer, pose, FLUID_MIN, FLUID_BOTTOM, FLUID_MIN, color, u0, v1, light);
            // 南（z = MAX）
            vertex(buffer, pose, FLUID_MAX, top, FLUID_MAX, color, u0, vTop, light);
            vertex(buffer, pose, FLUID_MIN, top, FLUID_MAX, color, u1, vTop, light);
            vertex(buffer, pose, FLUID_MIN, FLUID_BOTTOM, FLUID_MAX, color, u1, v1, light);
            vertex(buffer, pose, FLUID_MAX, FLUID_BOTTOM, FLUID_MAX, color, u0, v1, light);
            // 西（x = MIN）
            vertex(buffer, pose, FLUID_MIN, top, FLUID_MAX, color, u0, vTop, light);
            vertex(buffer, pose, FLUID_MIN, top, FLUID_MIN, color, u1, vTop, light);
            vertex(buffer, pose, FLUID_MIN, FLUID_BOTTOM, FLUID_MIN, color, u1, v1, light);
            vertex(buffer, pose, FLUID_MIN, FLUID_BOTTOM, FLUID_MAX, color, u0, v1, light);
            // 东（x = MAX）
            vertex(buffer, pose, FLUID_MAX, top, FLUID_MIN, color, u0, vTop, light);
            vertex(buffer, pose, FLUID_MAX, top, FLUID_MAX, color, u1, vTop, light);
            vertex(buffer, pose, FLUID_MAX, FLUID_BOTTOM, FLUID_MAX, color, u1, v1, light);
            vertex(buffer, pose, FLUID_MAX, FLUID_BOTTOM, FLUID_MIN, color, u0, v1, light);
        });
    }

    /** 一个顶点：用带 Pose 的重载先把方块局部坐标变换到世界空间（照抄原版流体渲染 / 储罐渲染器）。 */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                               int color, float u, float v, int light) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
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
