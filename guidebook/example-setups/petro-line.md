---
navigation:
  parent: example-setups/example-setups-index.md
  title: 石化线
  icon: minecraft:bucket
  position: 3
---
# 石化线

最小骨架：

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:distillation_tower" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="0" y="-1" z="0" p:up="true" p:north="true" />
  <Block id="stellar_evolution:copper_tank" x="0" y="-1" z="-1" />
  <Block id="stellar_evolution:cracking_furnace" x="3" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="3" y="-1" z="0" p:up="true" p:east="true" />
  <Block id="stellar_evolution:copper_tank" x="4" y="-1" z="0" />
  <Block id="stellar_evolution:polymerizer" x="6" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="6" y="-1" z="0" p:up="true" p:north="true" />
  <Block id="minecraft:copper_chest" x="6" y="-1" z="-1" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 接线

- 原油来源：见 JEI / 基岩资源线。
- 大机器统一：铜块供能、机械管接铜箱子 / 储罐。
- 流体中间体用储罐暂存；固体产物用铜箱子。

## 液态空气分馏

分馏塔不只炼原油，也做空气分离：

1. 不锈钢板 ×4 + 不锈钢杆 ×4 → **不锈钢格栅 ×4**。
2. 格栅贴机械管、仅主世界、任意一面贴空气方块 → 提供液态空气。
3. 分馏塔：`液态空气 5 000 → 氮气 3 840 + 氧气 1 050 + 氩气 100 + 氦气 10`。
4. 去向：氮气 → 合成氨；氩气 → 不锈钢 / 高速钢氩弧焊；氦气 → 裂变线氦-3。

格栅供气随**暴露空气面**倍增：1 面 16 B/s，6 面 512 B/s。

## 新燃料链

- 汽油：石脑油 → 裂解 → 普通汽油；重整油 + 苯 + 普通汽油 → 高辛烷汽油 → 含铅汽油。
- 柴油：柴油 → 低硫油 → 高十六烷柴油（四硝基甲烷改进剂）。
- 火箭：航空煤油 + 氧气 → 火箭燃料；液甲烷 + 氧气 → 甲烷氧火箭燃料；偏二甲肼 + 四氧化二氮 → 自燃火箭燃料。

## 提示

- 石化机器多用流体，先把储罐和机械管布好。
- 塑料 / 橡胶是后续电路、密封件、管件的材料基础。
- 配方见 JEI「石化 / 煤化工」分类。
