package dev.modmind.stellar_evolution.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 创造差压的方块实体：热压差网络的<strong>生成端</strong>。
 * <ul>
 *   <li><strong>容量</strong>恒为 {@link Long#MAX_VALUE} DP —— 相当于「无限差压」，网络永不超载；</li>
 *   <li><strong>热流率</strong>可调（空手右键 / 左键，见 {@code CreativePressureBlock}），落在 [0, {@link #MAX_FLOW_RATE}]；</li>
 *   <li>热流率<strong>存盘</strong>（{@code ValueOutput} / {@code ValueInput}），重进世界仍在。</li>
 * </ul>
 * 它不 tick：网络要数据时（{@link ThermalGenerator#capacity()} / {@link ThermalGenerator#flowRate()}）现取即可。
 */
public class CreativePressureBlockEntity extends BlockEntity implements ThermalGenerator {
    /** 差压容量（DP）：long 上限。 */
    public static final double CAPACITY = (double) Long.MAX_VALUE;
    /** 普通步进（HP/t）。 */
    public static final long STEP_SMALL = 64L;
    /** 蹲下时的步进（HP/t）。 */
    public static final long STEP_LARGE = 1024L;
    /** 热流率上限（调试够用，也防止手滑点到天上去）。 */
    public static final long MAX_FLOW_RATE = 1_000_000L;

    /** 当前热流率（HP/t）；默认 0，靠右键往上加。 */
    private long flowRate;

    public CreativePressureBlockEntity(BlockPos pos, BlockState state) {
        super(ThermalBlocks.CREATIVE_PRESSURE_ENTITY.get(), pos, state);
    }

    @Override
    public double capacity() {
        return CAPACITY;
    }

    @Override
    public double flowRate() {
        return this.flowRate;
    }

    /** 加减热流率并夹到 [0, {@link #MAX_FLOW_RATE}]；返回夹取后的新值。 */
    public long addFlowRate(long delta) {
        long next = Math.max(0L, Math.min(MAX_FLOW_RATE, this.flowRate + delta));
        if (next != this.flowRate) {
            this.flowRate = next;
            setChanged();
        }
        return this.flowRate;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("flow_rate", this.flowRate);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.flowRate = input.getLongOr("flow_rate", 0L);
    }
}
