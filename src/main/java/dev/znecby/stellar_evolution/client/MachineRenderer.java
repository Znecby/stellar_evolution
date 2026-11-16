package dev.znecby.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.znecby.stellar_evolution.machine.MachineBlock;
import dev.znecby.stellar_evolution.machine.MachineBlockEntity;
import dev.znecby.stellar_evolution.machine.MachineBlocks;
import dev.znecby.stellar_evolution.machine.MachineKind;
import dev.znecby.stellar_evolution.machine.MachinePart;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * 材料加工机械渲染器：把每台机器的「可动部件」（各自一个隐藏载体方块）画出来 ——
 * 照 {@code PetroMachineRenderer} 口径，并按 {@code BurningEngineRenderer} 的写法补上整机朝向。
 *
 * <p>三台机器的动效：
 * <ul>
 *   <li><strong>破碎机</strong>：侧面大飞轮绕 X 自转（偏心轴），前端动颚绕顶部铰座（X 轴）往复摆动；</li>
 *   <li><strong>压模机</strong>：左侧飞轮绕 X 自转，中间滑块沿 Y 往复下压，正面压力表指针绕 Z 摆动；</li>
 *   <li><strong>线材轧机</strong>：牌坊窗口里上下两辊同速反向自转（辊轴沿 X、同一份载体模型 + Y 偏移 2.8px），
 *       右侧轴承座上与辊轴同轴的传动轮绕 X 自转。</li>
 * </ul>
 * 手法与 {@code BurningEngineRenderer} 一致：{@code submitMovingBlock} 画隐藏载体方块的烘焙模型，
 * 并按那条坑取「自身 + 六邻居」最亮处采样光照（非整方块造型自身光照为 0）。
 */
public class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity, MachineRenderState> {
    /** 模型单位 → 方块单位（1px = 1/16）。 */
    private static final float PX = 1.0F / 16.0F;
    /** 方块中心（模型单位的 8）对应 0.5。 */
    private static final double CENTER = 0.5D;

    public MachineRenderer() {}

    @Override
    public MachineRenderState createRenderState() {
        return new MachineRenderState();
    }

    @Override
    public void extractRenderState(MachineBlockEntity blockEntity, MachineRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        MachineKind kind = MachineBlocks.kindOf(blockEntity.getBlockState().getBlock());
        state.kind = kind;
        state.facingY = yawOf(blockEntity.getBlockState().getValue(MachineBlock.FACING));
        state.age = blockEntity.age(partialTicks);
        state.rotors.clear();
        ClientLevel level = blockEntity.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
        for (MachinePart part : kind.parts()) {
            Block carrier = MachineBlocks.carrier(part.carrier());
            state.rotors.add(level != null && carrier != null ? moving(level, blockEntity.getBlockPos(), carrier) : null);
        }
        // 机器整格、自身光照可能为 0：采样「自身 + 六邻居」最亮处
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
    public void submit(MachineRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        MachineKind kind = state.kind;
        if (kind == null) {
            return;
        }
        var parts = kind.parts();
        for (int i = 0; i < parts.size(); i++) {
            MovingBlockRenderState rotor = i < state.rotors.size() ? state.rotors.get(i) : null;
            if (rotor == null) {
                continue;
            }
            MachinePart part = parts.get(i);
            poseStack.pushPose();
            // 朝向：绕方块中心转 -facingY（与 blockstate 的 y 同约定、取负角 —— 见 HeatEngineRenderer）
            poseStack.translate(CENTER, CENTER, CENTER);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingY));
            poseStack.translate(-CENTER, -CENTER, -CENTER);
            // 安装偏移（同一份载体模型画在别处时用，例如轧机上辊）
            poseStack.translate(part.offsetX() * PX, part.offsetY() * PX, part.offsetZ() * PX);
            float amount = part.amount(state.age);
            if (part.motion() == MachinePart.Motion.SLIDE) {
                // 往复平移：沿轴走行程（模型单位）
                float slide = amount * PX;
                switch (part.axis()) {
                    case X -> poseStack.translate(slide, 0.0D, 0.0D);
                    case Y -> poseStack.translate(0.0D, slide, 0.0D);
                    default -> poseStack.translate(0.0D, 0.0D, slide);
                }
            } else {
                // 自转 / 摆动：绕枢轴旋转（submitMovingBlock 以方块角点为原点摆模型）
                float px = part.pivotX() * PX;
                float py = part.pivotY() * PX;
                float pz = part.pivotZ() * PX;
                poseStack.translate(px, py, pz);
                poseStack.mulPose(rotation(part.axis(), amount));
                poseStack.translate(-px, -py, -pz);
            }
            submitNodeCollector.submitMovingBlock(poseStack, rotor);
            poseStack.popPose();
        }
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

    /** 正面朝向 → 方块状态 {@code y} 旋转角（与 {@code blockstates/<机器>.json} 一一对应）。 */
    private static float yawOf(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
