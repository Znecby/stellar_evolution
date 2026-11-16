---
navigation:
  parent: example-setups/example-setups-index.md
  title: 生化线
  icon: slurry_mixer
  position: 2
---
# 生化线

最小骨架：

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:slurry_mixer" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="0" y="-1" z="0" p:up="true" p:north="true" />
  <Block id="minecraft:copper_chest" x="0" y="-1" z="-1" />
  <Block id="stellar_evolution:fermenter" x="3" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="3" y="-1" z="0" p:up="true" p:east="true" />
  <Block id="stellar_evolution:copper_tank" x="4" y="-1" z="0" />
  <Block id="stellar_evolution:reactor" x="6" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="6" y="-1" z="0" p:up="true" p:north="true" />
  <Block id="minecraft:copper_chest" x="6" y="-1" z="-1" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 接线

- 机械土壤：贴铜块接入热压差，产物进正下方容器。
- 大机器：铜块或底排铜管供能，机械管接铜箱子 / 储罐。
- 调浆机：物品 + 水 → 浆料。
- 发酵罐：浆料 → 发酵液 / 乙醇 / 沼气。
- 生物反应釜：酶 / 培养基 / 抗生素；还可跑**水电解** `水 1 000 → 氢气 1 000 + 氧气 500`，供内燃 / 燃气助燃、硝酸与火箭燃料；焊接线改用助焊剂 / 氩气。

## 提示

- 先用一条线跑通，再复制。
- 输出容器记得用剪刀切输出。
- 配方见 JEI「生化」分类。
