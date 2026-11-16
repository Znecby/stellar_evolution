---
navigation:
  parent: mechanics/beam/beam-index.md
  title: 九种粒子流
  icon: particle_source
  position: 3
---
# 九种粒子流

| 流 | id | 主要用途 |
|---|---|---|
| 缪子流 | `muon` | 缪子相关工艺 / 对撞机输出 |
| 质子流 | `proton` | 粒子源基础流 |
| 氘核流 | `deuteron` | 聚变相关 |
| α 粒子流 | `alpha` | 辐照 / 核反应 |
| 中子流 | `neutron` | 辐照、增殖、嬗变的主用流 |
| 铅离子流 | `lead_ion` | 重离子碰撞 |
| 铀离子流 | `uranium_ion` | 最重的离子束 |
| 极化质子流 | `polarized_proton` | 高能物理专用 |
| 放射性离子流 | `radioactive_ion` | 放射性离子工艺 |

## 谁产谁用

- 源：<ItemLink id="particle_source" />（按压力罐的粒子类型出流）、<ItemLink id="collider" />（对撞后喷缪子流）、<ItemLink id="muon_fusion" />（聚变副产中子流）。
- 消费者：<ItemLink id="irradiation_chamber" />、<ItemLink id="collider" />、<ItemLink id="muon_fusion" />（吃缪子流）。

具体某台机器吃哪种流、产哪种流，以机器页和 JEI 为准；网络只负责把同类型的管接在一起。

## 提示

- 束流管单管单流，同一段管网不要混类型。
- 想换类型，Shift + 右键整段切换；不要让空管长期混在已定型管网里。
