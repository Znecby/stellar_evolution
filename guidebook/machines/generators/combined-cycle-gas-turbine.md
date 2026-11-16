---
navigation:
  parent: machines/machines-index.md
  title: 联合燃气机组
  icon: combined_cycle_gas_turbine
  position: 7
categories:
  - machines
item_ids:
  - stellar_evolution:combined_cycle_gas_turbine
---
# 联合燃气机组

2×2×2 大型机组：**高 HP / 中 DP**，只吃 GAS / AERO 燃料，并且能用铜格栅的水产蒸汽。

<BlockImage id="combined_cycle_gas_turbine" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:combined_cycle_gas_turbine" x="0" y="0" z="0" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="1" y="0" z="0" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="0" y="1" z="0" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="1" y="1" z="0" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="0" y="0" z="1" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="1" y="0" z="1" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="0" y="1" z="1" />
  <Block id="stellar_evolution:combined_cycle_gas_turbine_shell" x="1" y="1" z="1" />
  <Block id="stellar_evolution:copper_tank" x="3" y="0" z="0" />
  <Block id="minecraft:copper_grate" x="0" y="0" z="3" />
  <Block id="stellar_evolution:pressure_pipe" x="3" y="0" z="2" p:west="true" p:east="true" />
  <Block id="stellar_evolution:copper_pipe" x="0" y="-1" z="0" p:up="true" p:east="true" />
  <BoxAnnotation color="#82CD5C" min="3 0 0" max="4 1 1">燃料储罐</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 3" max="1 1 4">铜格栅供水</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="3 0 2" max="4 1 3">压力管出蒸汽</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 数值

| 项 | 内容 |
|---|---|
| DP | 20 000（固定） |
| 最大 HP/t | 8 000 |
| 燃料 | GAS / AERO，2 mB/t |
| 氧气 | 4 mB/t；温度 +0.2、HP +10% |
| 水 | 8 000 mB/t（铜格栅）→ 8 000 mB/t、300℃、30 bar 蒸汽 |
| 升转 | 约 33 秒到 1.0；断料缓慢掉转 |
| 大气 | 真空维度不能燃烧 |

## 双输出

- 热压差：底排铜管或铜块输出 DP / HP。
- 蒸汽：压力管接 `SteamEndpoint`，蒸汽可作为裂变 / 聚变线的补充汽源。
- 水必须从**原版铜格栅**取；没有水仍可发电，但不出蒸汽。

## 燃料

GAS：发生炉煤气、沼气、合成气、净化合成气、焦炉煤气、石油气、合成天然气。
AERO：煤油、航空煤油、合成航煤。
乙醇 / 汽油 / 松节油这些 MEDIUM 液体**灌不进去**，留给工业内燃机组或燃烧引擎。
完整数值见[燃料表](../../reference/fuels.md)与 JEI「联合燃气机组」分类。

## 注意

- 燃料储罐贴近机器，或接到机械管网输入侧。
- 氧气提升的是 HP 与温度，不是 DP。
- 真空维度完全停转。
