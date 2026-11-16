---
navigation:
  parent: machines/machines-index.md
  title: 辐照仓
  icon: irradiation_chamber
  position: 32
categories:
  - machines
item_ids:
  - stellar_evolution:irradiation_chamber
---
# 辐照仓

吃粒子流 + 物品，按配方产出；兼做增殖包层。

<BlockImage id="irradiation_chamber" scale="6" />

## 运行

- 机器不预设吃哪种流：旁边接了几种束流管，就把那几路都算作来源。
- 按配方表挑一条“输入够 + 所有需求流都接着”的配方，并粘住它。
- 每一路需求各自累计进度；所有路都吃满才扣料出产物。
- 同一种流接了两段互不相连的管网时，只取供流最多的那一段，不叠加。

## 用途

- 中子流：辐照 / 增殖 / 嬗变。
- 质子流、铀离子、铅离子等：对应配方。
- 增殖包层：锂粉 → 氚。

## 注意

- 没接对应类型的束流管 → 配方不启动。
- 束流供给不足 → 进度慢，不会凭空出产物。
- 配方见 JEI「辐照仓」分类。
