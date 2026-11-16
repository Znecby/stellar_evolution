---
navigation:
  parent: mechanics/steam/steam-index.md
  title: 基本概念
  icon: pressure_pipe
  position: 1
item_ids:
  - stellar_evolution:pressure_pipe
---
# 基本概念

蒸汽网络传的是一组**数据**，不是流体：

| 字段 | 单位 | 含义 |
|---|---|---|
| 量 | mB/t | 每 tick 有多少蒸汽 |
| 温度 | ℃ | 决定蒸汽等级 |
| 压力 | bar | 决定蒸汽涡轮机组最高转速和 DP 倍率 |

## 端点

| 端点 | 角色 | 例子 |
|---|---|---|
| <ItemLink id="fission_reactor" /> | 生成端 | 向压力管写蒸汽数据 |
| <ItemLink id="steam_turbine_unit" /> | 消耗端 | 吃蒸汽，换算成热压差 |
| <ItemLink id="muon_fusion" /> | 生成端 | 聚变仪也向压力管送蒸汽 |
| <ItemLink id="combined_cycle_gas_turbine" /> | 生成端 | 铜格栅供水，产出 8 000 mB/t、300℃、30 bar 蒸汽 |

## 两条硬规则

1. **只有压力管能承载蒸汽**。铜管、机械管、束流管都不传蒸汽。
2. **反应堆不往储罐灌蒸汽**。蒸汽流体只是 JEI 展示；实际能量走压力管。

## 下一步

- [连接规则](connections.md)
- [蒸汽等级](grades.md)
- [反应堆 → 蒸汽涡轮机组](reactor-to-turbine.md)
