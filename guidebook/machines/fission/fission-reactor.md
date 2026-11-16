---
navigation:
  parent: machines/machines-index.md
  title: 裂变堆
  icon: fission_reactor
  position: 10
categories:
  - machines
item_ids:
  - stellar_evolution:fission_reactor
---
# 裂变堆

燃料棒 + 冷却水 → 蒸汽数据。反应堆**不直接接热压差**，热压差由后级蒸汽涡轮机组产生。

<BlockImage id="fission_reactor" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:fission_reactor" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="2" y="0" z="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:reaction_chamber" x="3" y="0" z="1" />
  <Block id="stellar_evolution:pressure_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:pressure_pipe" x="3" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:steam_turbine_unit" x="4" y="0" z="0" />
  <BoxAnnotation color="#82CD5C" min="2 0 1" max="4 1 2">反应仓</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2 0 0" max="4 1 1">压力管</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 结构

- 2×2×2；底排接 <ItemLink id="pressure_pipe" />。
- 用 <ItemLink id="machine_pipe" /> 把 <ItemLink id="reaction_chamber" /> 接到堆上；燃料棒 / 石墨棒 / 反射棒放在仓里。
- 每台堆最多识别 **1 个反应仓**；多于 1 个会阻止运行。
- 铜箱子里的燃料棒不参与反应堆逻辑。
- 没有 GUI；全部状态看 Jade。

## 运行

- 燃料棒提供功率，石墨与反射棒调整通量 / 效率 / 蒸汽压力。
- 三层温度：堆壳（蒸汽温度）、包壳、燃料芯。
- 堆壳 > 1500℃ 累积损毁，≥ 1800℃ 瞬间熔毁。
- 包壳 > 2000℃ 破壳，该棒报废。
- 燃料芯 > 2800℃ 整堆熔毁；损毁 100% 也熔毁。

## 水与蒸汽

- 基础冷却水与蒸汽约 1:1；温度越高，额外冷却水越多（额外水不转蒸汽）。
- 没有压力管 → “无法输出蒸汽”。
- 有压力管但没有蒸汽涡轮机组 → 末端排气，蒸汽丢弃。
- 蒸汽温度取堆壳温度；压力随功率升高，并受燃料族上限限制（铀约 300 bar，MOX 约 500 bar）。

## 安全

- 缺水会快速升温；先保证水源和压力管，再点火。
- 熔毁会停机并造成后果；不要在基地核心区做首次试堆。
- 乏燃料先放进[乏燃料池](spent-fuel-pool.md)降温，再后处理。

## 相关

- [反应仓](reaction-chamber.md)
- [蒸汽网络](../../mechanics/steam/steam-index.md)
- [蒸汽涡轮机组](../generators/steam-turbine.md)
