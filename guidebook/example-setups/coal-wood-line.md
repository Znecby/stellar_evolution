---
navigation:
  parent: example-setups/example-setups-index.md
  title: 煤 / 木化工线
  icon: coke
  position: 7
---
# 煤 / 木化工线

最小闭环：**焦化炉干馏 → 焦炭 / 焦炉煤气 → 气化炉 → 合成气 → 联合燃气机组**。

<GameScene zoom="3" background="transparent" interactive={true}>
  <Block id="stellar_evolution:gasifier" x="0" y="0" z="0" />
  <Block id="stellar_evolution:gasifier_shell" x="1" y="0" z="0" />
  <Block id="stellar_evolution:gasifier_shell" x="0" y="1" z="0" />
  <Block id="stellar_evolution:gasifier_shell" x="1" y="1" z="0" />
  <Block id="stellar_evolution:gasifier_shell" x="0" y="0" z="1" />
  <Block id="stellar_evolution:gasifier_shell" x="1" y="0" z="1" />
  <Block id="stellar_evolution:gasifier_shell" x="0" y="1" z="1" />
  <Block id="stellar_evolution:gasifier_shell" x="1" y="1" z="1" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine" x="4" y="0" z="0" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="5" y="0" z="0" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="4" y="1" z="0" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="5" y="1" z="0" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="4" y="0" z="1" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="5" y="0" z="1" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="4" y="1" z="1" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="5" y="1" z="1" />
  <Block id="stellar_evolution:copper_tank" x="7" y="0" z="0" />
  <Block id="stellar_evolution:pressure_pipe" x="3" y="0" z="0" p:west="true" p:east="true" />
  <Block id="minecraft:copper_grate" x="4" y="0" z="3" />
  <BoxAnnotation color="#82CD5C" min="0 0 0" max="2 2 2">气化炉</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="4 0 0" max="6 2 2">联合燃气机组</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="3 0 0" max="4 1 1">合成气 / 燃料</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 步骤

1. **焦化炉**：煤 ×4 → 焦炭 ×2 + 煤焦油 + 焦炉煤气；原木 ×4 → 木炭 ×3 + 木焦油 + 木醋液 + 松节油。焦化炉直接吃整块煤，不需要先磨粉。
2. **磨粉**：煤炭 / 木炭 → 破碎机 → 煤炭粉 / 木炭粉（1 出 1）。
3. **气化**：焦炭 / 煤炭粉 / 煤焦油 + 水 → 合成气；焦炭 / 木炭粉 → 发生炉煤气（空气气化）。
4. 合成气 / 焦炉煤气 / 发生炉煤气灌进储罐，接**联合燃气机组**。
5. 煤焦油 → 分馏塔 → 苯 / 苯酚 / 萘 / 沥青；木醋液 → 分馏 → 醋酸 / 甲醇 / 丙酮。
6. 焦炭 + 方解石 → 热室电石 → 乙炔 → PVC。
7. **后期**：煤炭粉 + 水 → 煤水浆 → 加氢 → 煤液化油 → 分馏塔，替代原油。

## 常见错误

- 燃料等级错：燃气轮机只吃 GAS / AERO，不吃 HIGH 柴油，也不吃 MEDIUM 乙醇。
- 焦化炉缺砖 / 半砖 / 物流仓 → 不成型。
- 气化炉没有水 → 只能空气气化出发生炉煤气，出不了合成气。
- 直接把煤炭 / 木炭塞进气化炉 → 气化只认粉料，先过破碎机。
- 联合燃气机组没有铜格栅水 → 能发电但不出蒸汽。

## 相关

- [焦化炉](../machines/industry/coke-oven.md)
- [化工总览](../reference/chemistry.md)
- [燃料表](../reference/fuels.md)
- [联合燃气机组](../machines/generators/combined-cycle-gas-turbine.md)
