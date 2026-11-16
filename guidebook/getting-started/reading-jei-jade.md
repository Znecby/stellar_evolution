---
navigation:
  parent: getting-started/getting-started-index.md
  title: 读 JEI 与 Jade
  icon: minecraft:spyglass
  position: 4
---
# 读 JEI 与 Jade

## JEI

机器的配方都在 JEI 的对应分类里，按产线分：

- 生化、石化、煤化工、选冶、裂变、聚变、高能物理
- 束流配方会把“粒子流类型”作为特殊 ingredient 显示

书里不重复配方表；看到“配方见 JEI”就以 JEI 为准。

## Jade

对着热压差网络里的方块，Jade 显示三行：

1. **能量槽**：`占用 / 容量 DP`
2. **热流率**：连接件显示网络值，生成端显示输出值，消耗端显示有效值
3. **状态**：正常 / 热压中 / 停机 / 超载

## 命令

- `/se thermal`：以准星方块为起点扫描热压差网络
- `/se large`：查看大机器的热压差接入和物流容器
- `/se large recipes`：列出当前机器可用的配方
- `/se petro recipes` / `/se petro machine`：石化 / 煤化工配方与机器状态
- `/se hep recipes` / `/se hep machine`：回旋加速器配方与机器状态

## 这本书

- 左侧目录按“入门 → 机制 → 机器 → 示例 → 参考”排列。
- 物品 tooltip 上按住 **G** 跳到对应页。
- 书内的搜索框可以全文搜索；中文按中文分词索引。
