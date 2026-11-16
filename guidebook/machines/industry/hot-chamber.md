---
navigation:
  parent: machines/machines-index.md
  title: 热室
  icon: minecraft:copper_chest
  position: 42
categories:
  - machines
---
# 热室

**铜箱子底部垫铜块**组成的特殊结构，用来处理放射性物品。

<GameScene zoom="5" background="transparent" interactive={true}>
  <Block id="minecraft:copper_chest" x="0" y="1" z="0" />
  <Block id="minecraft:copper_block" x="0" y="0" z="0" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="1 1 1">铜块</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 结构

- 单个铜箱子：底部 1 格铜块。
- 双联大铜箱子：两格底部都要有铜块；只垫一格 → 结构缺失，整箱停摆。
- 结构完整时按**一台**消耗端记账，热阻 4。

## 接法

- **不接铜管**：铜管不会连到热室。
- **不接机械管**：机械管在热室旁不生管臂。
- 只通过底部铜块接入热压差网络。

## 加工

- 走单独的热室配方类型，只吃物品、不吃流体。
- 网络热流率决定倍速。
- 典型配方：**燃烧引擎**（铜框架 + 活塞组件 + 电容 + 轴承，800t）、**开采钻钻头**（钨铜硬质合金板 + 杆 + 钢齿轮，350t）。
- 具体配方见 JEI「热室」分类。

## 注意

- 普通铜箱子（没垫铜块）仍然是物流容器，可以接机械管。
- 大铜箱子只垫一格铜块时，Jade 会提示结构缺失。
