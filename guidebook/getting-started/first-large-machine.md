---
navigation:
  parent: getting-started/getting-started-index.md
  title: 第一台大机器
  icon: slurry_mixer
  position: 2
---
# 第一台大机器

大多数**大机器**都是同一套 **2×2×2 整机**：1 个主方块 + 7 个填充格。手里有 <ItemLink id="multiblock_builder" /> 时，可以一键搭出整机；没有就按 JEI 里的机器配方手动摆。

<BlockImage id="slurry_mixer" scale="6" />

## 能源：铜块或底排铜管

消耗端大机器**任意一格、任意一面**贴铜块，**或**在**底排**贴铜管，就算接入热压差网络。填充格也算机器本体，所以铜块贴在 2×2×2 的任意一格都生效。

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:slurry_mixer" x="0" y="0" z="0" />
  <Block id="minecraft:copper_block" x="3" y="0" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="4" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:heat_engine" x="5" y="0" z="0" />
  <Block id="minecraft:chest" x="5" y="-1" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="0" y="-1" z="0" p:up="true" p:north="true" />
  <Block id="minecraft:copper_chest" x="0" y="-1" z="-1" />
  <BoxAnnotation color="#5CA7CD" min="3 0 0" max="4 1 1">铜块供能</BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="0 -1 -1" max="1 0 1">机械管物流</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 物流：底排接机械管

<ItemLink id="machine_pipe" /> 只在机器的**底排**（侧面最底一层 + 正下方）接管，顶面不接。管子另一头接：

- 铜箱子：任意一面
- 储罐（铜 / 钢 / 硬质）：任意一面
- <ItemLink id="reaction_chamber" />：任意一面
- 铜格栅：任意一面

手持**剪刀**右键容器，可以切换输入 / 输出。默认是输入。

## 配方：输入 → 输出

机器按自己的配方表，从输入容器抽料、把产物放进输出容器；一次结算的取料和放料是同一个事务，不会出现“料没了产物也没出”。

- 配方看 JEI 对应的机器分类。
- 大机器没有 GUI，状态看 Jade；单方块加工机有 GUI。
- 普通大机器固定 **8 并行**；热压差网络只决定倍速，不改产率。

## 常见错误

- 铜块贴到了机器旁边的空气 / 管子上，而不是机器本体。
- 机械管贴到了机器侧面中层或顶面 → 不接。
- 容器默认是输入，忘了用剪刀切输出 → 产物堆在输入容器里。
- 热压差网络超载 → 机器倍速变 0，看起来像“不工作”。

## 特殊机器

裂变堆、蒸汽涡轮机组、粒子源、对撞机、缪子聚变仪等不吃这套通用规则，见[机器](../machines/machines-index.md)一章。
