---
navigation:
  parent: mechanics/logistics/logistics-index.md
  title: 基本概念
  icon: machine_pipe
  position: 1
item_ids:
  - stellar_evolution:machine_pipe
---
# 基本概念

机械管物流网络只服务**大机器**：

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:fermenter" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="0" y="-1" z="0" p:up="true" p:east="true" p:north="true" />
  <Block id="minecraft:copper_chest" x="0" y="-1" z="-1" />
  <Block id="stellar_evolution:copper_tank" x="1" y="-1" z="0" />
  <BoxAnnotation color="#82CD5C" min="0 -1 -1" max="1 0 0">物品容器</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 -1 0" max="2 0 1">流体储罐</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 只认机械管

- 铜管、铜块、引擎、创造差压都不属于这套网络。
- 铜管挨着机械管，也不会把铜块混进物流网络。
- 压力管、束流管同样不参与。

## 一台机器一张管网

- 机械管连通片里出现多台机器时，按**最近机器**分区。
- 新放的管子如果会桥接两台机器，会放成孤立短接头。
- 这样两台机器的容器不会互相串 IO。

## 容器

| 容器 | 接入面 | 用途 |
|---|---|---|
| 铜箱子 | 任意面 | 物品 |
| 储罐（铜 / 钢 / 硬质） | 任意面 | 流体 |
| <ItemLink id="reaction_chamber" /> | 任意面 | 物品，反应堆燃料 |
| 铜格栅 | 任意面 | 虚拟水源 |

**热室**（铜箱子底部垫铜块）不接机械管。

## 下一步

- [连接规则](connections.md)
- [容器与输入输出](io-and-containers.md)
- [配方结算](transactions.md)
