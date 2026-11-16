package dev.znecby.stellar_evolution.large;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * <strong>大型机器</strong>框架的方块实体接口（「特殊类」标记 + 几何 / 物流钩子）。
 *
 * <p>做成接口是为了<strong>方便以后把其它机器也纳入这套框架</strong>：机器的方块实体实现本接口、
 * 并在 {@link #isLargeMachine()} 里自报家门即可，框架（{@code ThermalNetwork} 的接入判定、
 * {@code LargeMachines} 的端口 / 物流遍历、Jade 的多方块显示）全部按接口走，不再认识具体机器。
 *
 * <p>约定：
 * <ul>
 *   <li><strong>能源</strong>：<strong>消耗端</strong>大机器可经铜块，或经<strong>底排铜管</strong>接入热压差网络；
 *       <strong>生成端</strong>大机器（蒸汽轮机）同样可经<strong>铜管</strong>接入。</li>
 *   <li><strong>物流</strong>：端口上的<strong>机械管</strong>（及相连的机械管）组成<strong>大机器物流网络</strong>，
 *       网络里的铜箱子（物品）与储罐（流体）成为这台机器的输入 / 输出容器。
 *       （普通铜管与铜块都不属于物流网络。）</li>
 * </ul>
 *
 * <p>{@link #isLargeMachine()} 为 false 的实现（例如同类的其它生化机器）<strong>完全不进框架</strong>：
 * 既不是热压差网络成员，也不参与物流遍历。
 */
public interface LargeMachine {
    /** 这台方块实体是不是一台「大型机器」——false 则整个大机器框架都不参与。 */
    boolean isLargeMachine();

    /** 机器所在世界（方块实体尚未进世界时为 null）。 */
    Level level();

    /** 机器<strong>原点</strong>（主方块所在格）。 */
    BlockPos origin();

    /** 机器实际占据的全部格（含主方块与所有填充格）——「任意一面贴铜块 / 接管」等几何判定用。 */
    List<BlockPos> occupiedCells();

    /** 某容器当前的物流方向（默认 {@link LargeMachineIo#INPUT}）。 */
    LargeMachineIo ioMode(BlockPos container);

    /** 设置某容器的物流方向（剪刀右键切换时调用）。 */
    void setIoMode(BlockPos container, LargeMachineIo mode);

    /**
     * Jade 用：本机<strong>工作中</strong>的「剩余配方时长」（刻）。
     * <p>{@code -1} = 没在工作（客户端不画那条绿色「配方」槽）；{@code 0} = 已饱和（每刻至少结算一条，
     * 客户端显示「满速」）。默认不参与（非大机器 / 不提供进度）。
     */
    default int remainingRecipeTicks() {
        return -1;
    }

    /** Jade 用：当前周期进度 0..1（绿色「配方」槽的填充比例）。 */
    default double recipeProgress() {
        return 0.0D;
    }
}
