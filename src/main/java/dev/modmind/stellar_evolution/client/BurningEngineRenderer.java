package dev.modmind.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.modmind.stellar_evolution.engine.BurningEngineBlock;
import dev.modmind.stellar_evolution.engine.BurningEngineBlockEntity;
import dev.modmind.stellar_evolution.engine.EngineBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 燃烧引擎渲染器：把「隐藏载体方块」的烘焙模型画在方块外，做两件动效 ——
 * <ul>
 *   <li><strong>前端风扇</strong>：{@code burning_engine_rotor} 绕长轴（X 轴）自转；</li>
 *   <li><strong>六缸活塞</strong>：{@code burning_engine_piston} 同一模型画 6 次（左右两列各 3 缸），
 *       每次先挪到该缸的 X、偏进所在缸体（左右各 1px），绕曲轴转到该列的倾角（±{@link #BANK_ANGLE}°），
 *       沿缸轴平移一个行程 —— 于是活塞沿斜缸往复。</li>
 * </ul>
 * 两件动效共用同一曲轴转角 {@link BurningEngineRenderState#angle}：风扇按角度旋转、活塞按
 * 「角度 + 各自相位」的正弦取行程。相位使同一列里中间那根与外侧两根反相、左右两列整体再反相，
 * 于是运行时交错运动；静止位（{@link BurningEngineBlockEntity#REST_ANGLE}）则停在「中间缩、外边伸」的姿态。
 * <p>
 * 该曲轴角只在「引擎在跑」时推进（见 {@link BurningEngineBlockEntity}：下方物品容器 / 储罐供给燃料，双模），
 * 否则平滑回到静止位，所以正常状态完全不动。
 * <p>
 * 与 {@link HeatEngineRenderer} 同一手法（{@code submitMovingBlock}），并沿用那条坑：
 * 移动块采样的是 {@code blockPos} 处的光，方块本体实心、自身光照为 0，故取「自身 + 六邻居」最亮处采样。
 */
public class BurningEngineRenderer implements BlockEntityRenderer<BurningEngineBlockEntity, BurningEngineRenderState> {
    /** 三对气缸的 X 位置（模型单位，1 = 1px）：左右两列各在这 3 处，与模型里的缸套条对齐。 */
    private static final float[] CYLINDER_X = {4.0F, 7.0F, 10.0F};
    /** V 形夹角：两列缸体各偏离竖直 30°，与模型里的静态 {@code rotation} 一致。 */
    private static final float BANK_ANGLE = 30.0F;
    /**
     * 活塞行程的基准量（模型单位）：行程 = 基准 + 振幅×sin(...)。
     * 基准不为 0，是为了让「回缩」那一端也停在缸口附近 —— 否则活塞沉得太深，会把缸套内部
     * 那圈深色顶面露出来。取 0.8 时回缩端约在缸口下 0.4px，顶端仍是铁色活塞。
     */
    private static final float STROKE_BASE = 0.8F;
    /** 活塞行程振幅（模型单位）：曲轴角正弦 × 此值。 */
    private static final float STROKE_AMPLITUDE = 1.2F;
    /** 中间那根活塞与两根外侧活塞的相位差（度）——静止位时一列「中间缩、外边伸」。 */
    private static final float MIDDLE_PHASE = -90.0F;
    /** 外侧活塞的相位（度）。 */
    private static final float OUTER_PHASE = 90.0F;
    /** 方块中心（模型单位的 8）对应 0.5。 */
    private static final double CENTER = 0.5D;
    private static final float MODEL_CENTER = 8.0F;

    public BurningEngineRenderer() {}

    @Override
    public BurningEngineRenderState createRenderState() {
        return new BurningEngineRenderState();
    }

    @Override
    public void extractRenderState(BurningEngineBlockEntity blockEntity, BurningEngineRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        BlockState blockState = blockEntity.getBlockState();
        state.facingY = yawOf(blockState.getValue(BurningEngineBlock.FACING));
        state.angle = blockEntity.angle(partialTicks);
        if (blockEntity.getLevel() instanceof ClientLevel level) {
            BlockPos pos = blockEntity.getBlockPos();
            state.rotor = moving(level, pos, EngineBlocks.BURNING_ENGINE_ROTOR.get());
            state.piston = moving(level, pos, EngineBlocks.BURNING_ENGINE_PISTON.get());
        } else {
            state.rotor = null;
            state.piston = null;
        }
    }

    @Override
    public void submit(BurningEngineRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        // 前端风扇：绕长轴自转
        if (state.rotor != null) {
            poseStack.pushPose();
            poseStack.translate(CENTER, CENTER, CENTER);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingY));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.angle));
            poseStack.translate(-CENTER, -CENTER, -CENTER);
            submitNodeCollector.submitMovingBlock(poseStack, state.rotor);
            poseStack.popPose();
        }
        // 六缸活塞：左右两列各 3 缸，沿各自缸轴往复
        if (state.piston != null) {
            float lane = 1.0F / 16.0F;
            for (int i = 0; i < CYLINDER_X.length; i++) {
                // 同一列内：中间那根与两根外侧反相（静止位时「中间缩、外边伸」）；
                // 左右两列整体再反相 180°，于是运行时交错运动。
                float phase = (i == 1) ? MIDDLE_PHASE : OUTER_PHASE;
                float leftStroke = strokeOf(state.angle, phase);
                float rightStroke = strokeOf(state.angle, phase + 180.0F);
                submitPiston(poseStack, submitNodeCollector, state, CYLINDER_X[i], -lane, -BANK_ANGLE, leftStroke);
                submitPiston(poseStack, submitNodeCollector, state, CYLINDER_X[i], lane, BANK_ANGLE, rightStroke);
            }
        }
    }

    /** 曲轴角 + 相位 → 该活塞的行程（方块单位）。相位 ±90° 时行程取到「基准 ∓ 振幅 / 基准 + 振幅」。 */
    private static float strokeOf(float crankAngle, float phaseDegrees) {
        float travel = STROKE_BASE + STROKE_AMPLITUDE * Mth.sin((float) Math.toRadians(crankAngle + phaseDegrees));
        return travel / 16.0F;
    }

    /** 画单缸活塞：先平移到位（X 定缸、Z 偏进所在缸体），再绕曲轴转到缸体倾角，最后沿缸轴走行程。 */
    private static void submitPiston(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, BurningEngineRenderState state,
                                     float cylinderX, float laneOffset, float bankAngle, float stroke) {
        float dx = (cylinderX - MODEL_CENTER) / 16.0F;
        poseStack.pushPose();
        // 与热机同一套「绕方块中心旋转」的写法：移到中心 → 各项变换 → 再移回角点，
        // 因为 submitMovingBlock 是按「原点＝方块角点」来摆模型的；漏掉最后这句 piston 会整体偏移半个方块。
        poseStack.translate(CENTER, CENTER, CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingY));
        poseStack.translate(dx, 0.0D, laneOffset);
        poseStack.mulPose(Axis.XP.rotationDegrees(bankAngle));
        poseStack.translate(0.0D, stroke, 0.0D);
        poseStack.translate(-CENTER, -CENTER, -CENTER);
        submitNodeCollector.submitMovingBlock(poseStack, state.piston);
        poseStack.popPose();
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

    /** 正面朝向 → 方块状态 {@code y} 旋转角（与 {@code blockstates/burning_engine.json} 一一对应）。 */
    private static float yawOf(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
