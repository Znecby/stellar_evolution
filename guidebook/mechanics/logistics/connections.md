---
navigation:
  parent: mechanics/logistics/logistics-index.md
  title: 连接规则
  icon: machine_pipe
  position: 2
---
# 连接规则

## 机器

机械管只在机器的**底排**接管：

- 侧面最底一层
- 正下方
- 顶面不接

2×2×2 的任意一格都算机器本体，但管子必须落在底排。

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:fermenter" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="0" y="-1" z="0" p:up="true" p:east="true" p:north="true" />
  <Block id="minecraft:copper_chest" x="0" y="-1" z="-1" />
  <Block id="stellar_evolution:copper_tank" x="1" y="-1" z="0" />
  <BoxAnnotation color="#82CD5C" min="0 -1 -1" max="1 0 0">物品容器</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 -1 0" max="2 0 1">储罐：任意面</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 容器

| 容器 | 接入面 |
|---|---|
| 铜箱子 | 任意一面 |
| 储罐（铜 / 钢 / 硬质） | 任意面 |
| <ItemLink id="reaction_chamber" /> | 任意一面 |
| 铜格栅 | 任意一面 |
| 机械物流仓 | 正面（铜块覆盖层） |

## 仓室外壳

- 机械物流仓未接入结构时基底是**不锈钢块**。
- 多方块成型后，基底**跟随控制器外壳材质**：焦化炉 → 红砖块，质量投射轨道炮 → 高负荷机械方块。
- 结构失效（缺砖 / 缺仓 / 控制器被拆）后恢复不锈钢；可以用外壳变化快速判断结构是否成型。

## 不接

- 铜管、铜块、引擎、创造差压
- 热室（铜箱子底部垫铜块）
- 普通机器（非大机器）

## 剪刀

手持剪刀右键**容器**，切换输入 / 输出；不是切管子。默认输入。

## 常见错误

- 机械管贴机器侧面中层 → 不接。
- 储罐接不上 → 检查机械管是否属于这台机器、是否被剪刀剪断；接哪一面都可以。
- 两台机器的管子连在一起 → 会被分区；不要故意桥接。
- 热室想接物流 → 热室不是物流容器。
