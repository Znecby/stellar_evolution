---
navigation:
  parent: example-setups/example-setups-index.md
  title: 裂变电站
  icon: steam_turbine_unit
  position: 1
---
# 裂变电站

最小闭环：

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:fission_reactor" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="2" y="0" z="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:reaction_chamber" x="3" y="0" z="1" />
  <Block id="stellar_evolution:pressure_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:pressure_pipe" x="3" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:pressure_pipe" x="4" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:steam_turbine_unit" x="5" y="0" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="8" y="0" z="0" p:east="true" p:west="true" />
  <Block id="minecraft:copper_block" x="9" y="0" z="0" />
  <Block id="minecraft:furnace" x="10" y="0" z="0" p:lit="true" />
  <BoxAnnotation color="#82CD5C" min="2 0 1" max="4 1 2">反应仓</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2 0 0" max="5 1 1">压力管</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="8 0 0" max="11 1 1">热压差输出</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 步骤

1. 搭 2×2×2 <ItemLink id="fission_reactor" />，底排接 <ItemLink id="pressure_pipe" />。
2. 用 <ItemLink id="machine_pipe" /> 把 <ItemLink id="reaction_chamber" /> 接到堆上，装入燃料棒 / 石墨棒 / 反射棒；一台堆只认 1 个仓。
3. 给反应堆供水（物流输入罐 / 铜格栅）。
4. 压力管接到 <ItemLink id="steam_turbine_unit" /> 底排。
5. 蒸汽涡轮机组底排贴铜管（或任意一格贴铜块），接热压差网络和消耗端。
6. 乏燃料取出，放进[乏燃料池](../machines/fission/spent-fuel-pool.md)冷却，再后处理。

## 常见错误

- 用铜管接反应堆 → 铜管不传蒸汽。
- 反应堆没有水 → 温度失控。
- 压力管没有蒸汽涡轮机组 → 末端排气，能量浪费。
- 反应仓放了两个 → 反应堆停机。
- 蒸汽涡轮机组没接热压差 → 有转速但不输出。

## 相关

- [裂变堆](../machines/fission/fission-reactor.md)
- [蒸汽网络](../mechanics/steam/steam-index.md)
- [蒸汽涡轮机组](../machines/generators/steam-turbine.md)
