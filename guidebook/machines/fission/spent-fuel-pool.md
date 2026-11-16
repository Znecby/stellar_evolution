---
navigation:
  parent: machines/machines-index.md
  title: 乏燃料池
  icon: spent_fuel_pool
  position: 12
categories:
  - machines
item_ids:
  - stellar_evolution:spent_fuel_pool
---
# 乏燃料池

9 格（3×3）容器，用来给带余热的乏燃料降温。

<BlockImage id="spent_fuel_pool" scale="6" />

## 运行

- 每 20t 结算一次。
- 周围 8 格水源越多，降温越快；8 个水源满速（约 2 温度/秒）。
- 没有水源就不降温。
- 没有带余热的乏燃料时直接跳过。

## 用法

1. 反应堆燃尽燃料棒 → 乏燃料。
2. 取出放进乏燃料池。
3. 冷却完成后送后处理厂。

## 注意

- 它是普通容器，不是大机器；不接铜块、不接机械管。
- 水源数量在放置 / 邻居变化时缓存，改水池后重新放置或触发邻居更新。
