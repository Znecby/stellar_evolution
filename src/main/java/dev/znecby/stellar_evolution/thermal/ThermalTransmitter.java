package dev.znecby.stellar_evolution.thermal;

/**
 * <strong>传递件</strong>（热压差网络的导线）：只把网络连通起来，<strong>不消耗、不占压、不生成</strong>。
 *
 * <p>目前唯一的传递件是<strong>铜管</strong>（{@code pipe.CopperPipeBlock} 实现了本接口）。
 * 让方块的 {@code Block} 实现本接口即可成为传递件，无需方块实体。
 *
 * <p>连通规则由 {@link ThermalNetwork} 负责：从任意机器/传递件出发做 6 面相邻的广度优先遍历，
 * 沿途的传递件与机器都算网络成员（机器本身也<strong>导通</strong>，因此「生成端—铜管—消耗端—铜管—消耗端」
 * 这样的链条算同一张网络）。
 */
public interface ThermalTransmitter {
}
