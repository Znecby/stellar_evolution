---
navigation:
  parent: machines/machines-index.md
  title: 热力引擎
  icon: heat_engine
  position: 1
categories:
  - machines
item_ids:
  - stellar_evolution:heat_engine
---
# 热力引擎

最早拿到的热压差生成端：**1024 DP / 128 HP/t**。

<BlockImage id="heat_engine" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:heat_engine" x="0" y="0" z="0" />
  <Block id="minecraft:chest" x="0" y="-1" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="1" y="0" z="0" p:west="true" p:east="true" />
  <Block id="minecraft:copper_block" x="2" y="0" z="0" />
  <BoxAnnotation color="#82CD5C" min="0 -1 0" max="1 0 1">正下方容器放燃料</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2 0 0" max="3 1 1">铜块 / 铜管接网络</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 用法

- 正下方放**物品容器**（箱子 / 木桶等），里面放任意熔炉燃料（煤、木炭、木板等）。
- **不再要求下方是点燃的熔炉**；旧口径已作废。
- 有燃料 → 转子转动、输出 **1024 DP / 128 HP/t**；燃料烧完 → 停机。
- 燃料池 800 点；**每 10t 扣 1 点**，同一份燃料只能烧燃烧引擎的一半时长。

## 取料规则

- 只在**整份燃料都装得下**时取料，不会因为缺 1 点吞掉一整块煤。
- 1 点 = 20t 燃烧时长；一块煤通常 80 点。

## 工作台配方

<RecipeFor id="heat_engine" />

## 注意

- 只提供 128 HP/t；多台并联只加容量，不提速（热流率取最大）。
- 想提速，换[燃烧引擎](burning-engine.md)或[蒸汽涡轮机组](steam-turbine.md)。
