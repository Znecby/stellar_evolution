package dev.znecby.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.znecby.stellar_evolution.engine.EngineAnimated;
import dev.znecby.stellar_evolution.engine.EngineMachines;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 三台燃烧类发电机组的「可动部件」渲染器：
 * <ul>
 *   <li>内燃机组 —— 前端散热风扇（绕 Z 轴）；</li>
 *   <li>联合燃气机组 —— 进气口里的压气机叶轮（绕 X 轴）；</li>
 *   <li>火箭引擎 —— 涡轮泵顶面叶轮（绕 Y 轴）。</li>
 * </ul>
 *
 * <p>画法照 {@link FissionRenderer} / {@link HeatEngineRenderer}：借隐藏载体方块的烘焙模型，
 * 用 {@link SubmitNodeCollector#submitMovingBlock} 画；不同点是整机模型是 32³ 且按朝向预旋转过 4 份，
 * 所以先把坐标系绕<strong>机器中心</strong>（原点方块内的 (1,1,1) 处）反向转回北向，再按部件枢轴自转。
 */
public class EngineMachineRenderer<T extends BlockEntity & EngineAnimated>
        implements BlockEntityRenderer<T, EngineMachineRenderState> {
    /** 模型单位 → 方块单位（1px = 1/16）。 */
    private static final float PX = 1.0F / 16.0F;

    private final List<EngineRotor> rotors;

    public EngineMachineRenderer(List<EngineRotor> rotors) {
        this.rotors = rotors;
    }

    /** 内燃机组：前端风扇。 */
    public static List<EngineRotor> industrialRotors() {
        return List.of(new EngineRotor(EngineMachines.INDUSTRIAL_FAN.get(), Direction.Axis.Z,
                26.0F, 16.0F, 13.8F, 1.2F));
    }

    /** 联合燃气机组：压气机叶轮。 */
    public static List<EngineRotor> gasTurbineRotors() {
        // 枢轴 x=29.1：必须落在压气机机匣东面（x=28）之外，否则叶轮被机匣整块挡住看不见
        return List.of(new EngineRotor(EngineMachines.GAS_TURBINE_COMPRESSOR.get(), Direction.Axis.X,
                30.0F, 29.1F, 12.95F, 16.0F));
    }

    /** 火箭引擎：涡轮泵叶轮。 */
    public static List<EngineRotor> rocketRotors() {
        return List.of(new EngineRotor(EngineMachines.ROCKET_TURBOPUMP.get(), Direction.Axis.Y,
                34.0F, 23.6F, 24.8F, 21.6F));
    }

    @Override
    public EngineMachineRenderState createRenderState() {
        return new EngineMachineRenderState();
    }

    @Override
    public void extractRenderState(T blockEntity, EngineMachineRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        BlockState blockState = blockEntity.getBlockState();
        state.rotors = rotors;
        state.age = blockEntity.age(partialTicks);
        state.yaw = blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? yawOf(blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)) : 0.0F;
        state.moving.clear();
        ClientLevel level = blockEntity.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
        BlockPos pos = blockEntity.getBlockPos();
        for (EngineRotor rotor : rotors) {
            state.moving.add(level != null ? moving(level, pos, rotor.carrier().defaultBlockState()) : null);
        }
        // 机器是整格、自身位置光照可能为 0：采样「自身 + 六邻居」最亮处
        if (level != null) {
            int light = state.lightCoords;
            for (Direction direction : Direction.values()) {
                light = net.minecraft.util.LightCoordsUtil.max(light,
                        LevelRenderer.getLightCoords(level, pos.relative(direction)));
            }
            state.lightCoords = light;
        }
    }

    @Override
    public void submit(EngineMachineRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        if (state.rotors.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        // 整机模型在 32³ 空间里绕 (16,16,16) 预旋转过；原点方块内的机器中心是 (1,1,1) 方块
        poseStack.translate(1.0F, 1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        poseStack.translate(-1.0F, -1.0F, -1.0F);

        for (int i = 0; i < state.rotors.size(); i++) {
            MovingBlockRenderState moving = i < state.moving.size() ? state.moving.get(i) : null;
            if (moving == null) {
                continue;
            }
            EngineRotor rotor = state.rotors.get(i);
            float px = rotor.pivotX() * PX;
            float py = rotor.pivotY() * PX;
            float pz = rotor.pivotZ() * PX;
            poseStack.pushPose();
            poseStack.translate(px, py, pz);
            poseStack.mulPose(switch (rotor.axis()) {
                case X -> Axis.XP.rotationDegrees(rotor.angle(state.age));
                case Z -> Axis.ZP.rotationDegrees(rotor.angle(state.age));
                default -> Axis.YP.rotationDegrees(rotor.angle(state.age));
            });
            poseStack.translate(-px, -py, -pz);
            submitNodeCollector.submitMovingBlock(poseStack, moving);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static MovingBlockRenderState moving(ClientLevel level, BlockPos pos, BlockState carrierState) {
        MovingBlockRenderState moving = new MovingBlockRenderState();
        moving.blockPos = brightestAround(level, pos);
        moving.randomSeedPos = pos;
        moving.blockState = carrierState;
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

    /** 朝向 → 方块状态 y 旋转角（与 blockstates 的 variants 一一对应）。 */
    private static float yawOf(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
