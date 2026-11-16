---
navigation:
  parent: mechanics/beam/beam-index.md
  title: 基本概念
  icon: beam_pipe
  position: 1
item_ids:
  - stellar_evolution:beam_pipe
---
# 基本概念

束流网络传的是**粒子流速率**（单位/t），不是物品也不是流体。

| 角色 | 接口 | 例子 |
|---|---|---|
| 源 | `StreamEndpoint` | 粒子源、对撞机、聚变仪 |
| 消费者 | `StreamConsumer` | 辐照仓、对撞机、聚变仪 |
| 载体 | <ItemLink id="beam_pipe" /> | 单管单流类型 |

## 三条硬规则

1. **一条管只走一种流**。不同类型的束流管相邻也不连接。
2. **网络不存粒子**。源这 tick 产多少，消费者这 tick 按需求分多少，不攒库存。
3. **一台机器可以同时是源和消费者**。对撞机吃两路、喷一路。

## 分配

多个消费者抢同一股流时，按 **max-min 公平**分配：

- 供给 10、需求 [2, 10] → [2, 8]
- 供给 3、需求 [10, 10, 10] → [1, 1, 1]

没有中央调度器；每个消费者拿到的份额由网络快照同一 tick 算一次。

## 下一步

- [连接规则](connections.md)
- [九种粒子流](types.md)
- [分配规则](allocation.md)
