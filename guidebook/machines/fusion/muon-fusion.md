---
navigation:
  parent: machines/machines-index.md
  title: 缪子聚变仪
  icon: muon_fusion
  position: 20
categories:
  - machines
item_ids:
  - stellar_evolution:muon_fusion
  - stellar_evolution:dt_fusion_core
---
# 缪子聚变仪

用 <ItemLink id="machine_pipe" /> 把 <ItemLink id="reaction_chamber" /> 接到聚变仪，放入聚变芯核；点火后自热，向压力管送蒸汽，并**消耗缪子流、副产中子流**。

<BlockImage id="muon_fusion" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:muon_fusion" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="2" y="0" z="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:reaction_chamber" x="3" y="0" z="1" />
  <Block id="stellar_evolution:pressure_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:pressure_pipe" x="3" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:steam_turbine_unit" x="4" y="0" z="0" />
  <Block id="stellar_evolution:beam_pipe" x="2" y="1" z="1" p:stream="0" p:east="true" p:west="true" />
  <BoxAnnotation color="#82CD5C" min="2 0 1" max="4 1 2">反应仓</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2 0 0" max="4 1 1">压力管</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 点火

- 未点火时按 10 档逐级升压，热流率与热阻逐档上涨。
- 供能不足 → 进度回退，不点火。
- 点火成功：温度跳到 2600℃、稳定值 90，需求降到维持档（约 2048 HP/t / 热阻 8）。

## 运行

- 温度自热，向 `2600 + 3400 × 稳定值%` 靠拢，最高 6000℃。
- 稳定值 0–100：运行 +、出束 −、冷却水充足再 +；缺水快速下降。
- 稳定值低于 20 → 停机并上锁；取出芯核再放回才能重新点火。
- 芯核耐久 24 000，运行 0.2/t；耐久归零直接销毁。

## 冷却水

- 蒸汽量与冷却水 1:1；水从物流输入罐 / 铜格栅取，取到即销毁。
- 抽不到水 → 不出蒸汽，稳定值快速下跌。
- 2600℃ 约需 5 325 B/s；7000℃ 约需 14 336 B/s。

## 输出

- **蒸汽**：压力管，`204 800 × 温度/2000` mB/t；工质温度封顶 700℃，压力最高 900 bar。
- **束流**：输入**缪子流**，输出**中子流**；两种类型的束流管都能接。
- 中子只在点火且缪子充足时产出；缺 μ 时反应无法维持，副产为 0。
- 过超临界蒸汽只有聚变仪能到。

## 风险

- 温度 > 7000℃ 累积损毁（0.005/t，满 100% 熔毁）：芯核销毁 + 停机。
- 当前正常自热上限 6000℃ < 7000℃，正常运行不会超温熔毁。

## 相关

- [蒸汽网络](../../mechanics/steam/steam-index.md)
- [束流网络](../../mechanics/beam/beam-index.md)
- [反应仓](../fission/reaction-chamber.md)
