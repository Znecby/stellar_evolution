package dev.znecby.stellar_evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.znecby.stellar_evolution.drill.DrillBlock;
import dev.znecby.stellar_evolution.drill.DrillBlockEntity;
import dev.znecby.stellar_evolution.drill.DrillBlocks;
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
import org.jspecify.annotations.Nullable;

/**
 * 开采钻渲染器：把「隐藏载体方块」的烘焙模型画在方块外，做两件动效 ——
 * <ul>
 *   <li><strong>中央钻杆</strong>：{@code mining_drill_shaft} 绕竖轴（Y 轴、方块中心）匀速自转，
 *       杆身带四条凸棱与几圈斜置的螺旋环，转起来能看出螺旋；</li>
 *   <li><strong>正面绞盘</strong>：{@code mining_drill_winch} 绕 Z 轴（垂直于正面）匀速自转。</li>
 * </ul>
 * 手法与 {@code BurningEngineRenderer} / {@code MachineRenderer} 一致：{@code submitMovingBlock} 画隐藏载体方块的
 * 烘焙模型，并按那条坑取「自身 + 六邻居」最亮处采样光照（非整方块造型自身光照为 0）。
 * <p>
 * 是否播动画由服务端算好同步过来（{@code working}）：没有容器 / 没热压差 / 产物放不下 / 挖到底时停转。
 */
public class DrillRenderer implements BlockEntityRenderer<DrillBlockEntity, DrillRenderState> {
    /** 方块中心（模型单位的 8）对应 0.5。 */
    private static final double CENTER = 0.5D;
    /** 钻杆转速（度/tick）。 */
    private static final float SHAFT_SPEED = 6.0F;
    /** 绞盘转速（度/tick）。 */
    private static final float WINCH_SPEED = 9.0F;
    /** 绞盘枢轴（模型单位）：与 {@code mining_drill_winch} 的轮心一致。 */
    private static final float WINCH_PIVOT_X = 6.0F;
    private static final float WINCH_PIVOT_Y = 12.5F;
    private static final float WINCH_PIVOT_Z = 3.9F;
    /** 模型单位 → 方块单位。 */
    private static final float PX = 1.0F / 16.0F;

    public DrillRenderer() {}

    @Override
    public DrillRenderState createRenderState() {
        return new DrillRenderState();
    }

    @Override
    public void extractRenderState(DrillBlockEntity blockEntity, DrillRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.facingY = yawOf(blockEntity.getBlockState().getValue(DrillBlock.FACING));
        state.age = blockEntity.age(partialTicks);
        if (blockEntity.getLevel() instanceof ClientLevel level) {
            BlockPos pos = blockEntity.getBlockPos();
            state.shaft = moving(level, pos, DrillBlocks.SHAFT.get());
            state.winch = moving(level, pos, DrillBlocks.WINCH.get());
        } else {
            state.shaft = null;
            state.winch = null;
        }
    }

    @Override
    public void submit(DrillRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        // 中央钻杆：绕方块中心的竖轴自转
        if (state.shaft != null) {
            poseStack.pushPose();
            poseStack.translate(CENTER, CENTER, CENTER);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingY));
            poseStack.mulPose(Axis.YP.rotationDegrees(state.age * SHAFT_SPEED));
            poseStack.translate(-CENTER, -CENTER, -CENTER);
            submitNodeCollector.submitMovingBlock(poseStack, state.shaft);
            poseStack.popPose();
        }
        // 正面绞盘：绕自身轮心的 Z 轴自转
        if (state.winch != null) {
            float px = WINCH_PIVOT_X * PX;
            float py = WINCH_PIVOT_Y * PX;
            float pz = WINCH_PIVOT_Z * PX;
            poseStack.pushPose();
            // 先套整机朝向（绕方块中心转、再转回角点），然后才在「方块局部坐标」里绕轮心转 ——
            // 顺序不能反：把 -CENTER 留到最后会让绞盘变成绕「方块中心 + 枢轴」公转（空中飞轮）。
            poseStack.translate(CENTER, CENTER, CENTER);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingY));
            poseStack.translate(-CENTER, -CENTER, -CENTER);
            poseStack.translate(px, py, pz);
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.age * WINCH_SPEED));
            poseStack.translate(-px, -py, -pz);
            submitNodeCollector.submitMovingBlock(poseStack, state.winch);
            poseStack.popPose();
        }
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

    /** 正面朝向 → 方块状态 {@code y} 旋转角（与 {@code blockstates/mining_drill.json} 一一对应）。 */
    private static float yawOf(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
