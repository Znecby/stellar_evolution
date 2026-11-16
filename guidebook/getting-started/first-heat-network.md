---
navigation:
  parent: getting-started/getting-started-index.md
  title: 第一张热压差网络
  icon: copper_pipe
  position: 1
---
# 第一张热压差网络

目标：让一台消耗端“热压”起来。

## 1. 做铜管和铜块

按 JEI 做出 <ItemLink id="copper_pipe" />。铜块用原版铜块即可，它在这套系统里是**中间导体**。

<Row>
  <ItemImage id="copper_pipe" scale="4" />
  <BlockImage id="minecraft:copper_block" scale="4" />
</Row>

## 2. 放一台生成端

把 <ItemLink id="heat_engine" /> 放在一个**物品容器**上方（箱子 / 木桶），往容器里塞任意熔炉燃料。引擎不再要求下方是点燃的熔炉；燃料烧完才停机。

<BlockImage id="heat_engine" scale="6" />

## 3. 把能量送到消耗端

用铜管从引擎接到**铜块**，再把小型消耗端（熔炉 / 木桶 / 机械土壤）贴着铜块放。小型消耗端**不能直接贴铜管**，必须经过铜块；大机器可以贴铜块或底排贴铜管；单方块加工机（破碎机 / 焊接机 / 钢炉 / 开采钻等）任意面贴铜管即可。

<GameScene zoom="5" background="transparent" interactive={true}>
  <Block id="stellar_evolution:heat_engine" x="0" y="0" z="0" />
  <Block id="minecraft:chest" x="0" y="-1" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="1" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:copper_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="minecraft:copper_block" x="3" y="0" z="0" />
  <Block id="minecraft:furnace" x="4" y="0" z="0" p:lit="true" />
  <BoxAnnotation color="#5CA7CD" min="3 0 0" max="4 1 1">铜块</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="4 0 0" max="5 1 1">消耗端</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

常见的入门消耗端：熔炉 / 烟熏炉 / 高炉、木桶、<ItemLink id="mechanical_farmland" />。

## 4. 看数据

准星对着网络里的方块，Jade 会显示三行：

1. 橙色能量槽：`占用 / 容量 DP`
2. `热流率 N HP/t`
3. 状态（正常 / 热压中 / 停机 / 超载）

也可以执行 `/se thermal`，以准星瞄到的方块为起点扫描整张网络。

## 常见错误

- 消耗端贴着铜管而不是铜块 → 不算接入网络。
- 把**机械管**当成铜管 → 机械管只走物品/流体，不进热压差网络。
- 引擎下方没有容器 / 容器里没有熔炉燃料 → 引擎停机，热流率为 0。
- 网络里消耗端太多 → 占用超过容量，整张网全部停转（超载）。
