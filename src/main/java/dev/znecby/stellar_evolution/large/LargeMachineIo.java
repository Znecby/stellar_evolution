package dev.znecby.stellar_evolution.large;

/**
 * 大机器网络上某个容器（铜箱子 / 储罐）的<strong>物流方向</strong>。
 *
 * <p>口径（对「大机器对外能力」而言）：
 * <ul>
 *   <li>{@link #INPUT}：<strong>输入箱</strong>——外部（漏斗 / 管道 / 玩家）只可<strong>往这里放</strong>，
 *       机器自己从这里<strong>取料</strong>；</li>
 *   <li>{@link #OUTPUT}：<strong>输出箱</strong>——外部只可<strong>从这里拿</strong>，机器自己往这里<strong>放产物</strong>。</li>
 * </ul>
 * 手持<strong>剪刀</strong>右键该容器即切换方向（见 {@code LargeMachineEvents}）。
 */
public enum LargeMachineIo {
    INPUT,
    OUTPUT;

    /** 反向切换：输入 ⇄ 输出。 */
    public LargeMachineIo toggle() {
        return this == INPUT ? OUTPUT : INPUT;
    }
}
