---
navigation:
  parent: getting-started/getting-started-index.md
  title: 第一套机械物流
  icon: machine_pipe
  position: 3
---
# 第一套机械物流

大机器不带内部槽 / 罐，物品和流体都要靠 <ItemLink id="machine_pipe" /> 接到外部容器。

## 连接

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:fermenter" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="0" y="-1" z="0" p:up="true" p:east="true" p:north="true" />
  <Block id="minecraft:copper_chest" x="0" y="-1" z="-1" />
  <Block id="stellar_evolution:machine_pipe" x="1" y="-1" z="0" p:west="true" p:east="true" />
  <Block id="stellar_evolution:copper_tank" x="2" y="-1" z="0" />
  <BoxAnnotation color="#82CD5C" min="0 -1 -1" max="1 0 0">物品容器</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2 -1 0" max="3 0 1">流体储罐</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

- 机械管**只接大机器和物流容器**：铜箱子、储罐（铜 / 钢 / 硬质）、<ItemLink id="reaction_chamber" />、铜格栅。
- 不接铜管、铜块、引擎、创造差压。
- 热室（铜箱子底部垫铜块）**不接**机械管。
- 一台机器一张管网；两台机器的管子如果连通，会被拆成各自最近机器的分区。

## 输入 / 输出

容器默认是**输入**。手持剪刀右键容器，切换成**输出**。

- 输入容器：原料从这里被抽走。
- 输出容器：产物往这里放。
- 机器自己执行抽料 / 放料；对外能力也按输入只进、输出只出。

## 常见错误

- 机械管贴到机器侧面中层 → 不接，必须底排 / 正下方。
- 储罐接在机械管任意一面都行；接不上时先检查机械管是否属于这台机器、是否被剪刀剪断。
- 忘了切输出 → 产物继续往输入容器里塞，塞满后机器停机。
- 用铜管做物流 → 铜管只传热压差，不传物品 / 流体。
