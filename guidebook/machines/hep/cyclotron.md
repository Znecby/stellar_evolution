---
navigation:
  parent: machines/machines-index.md
  title: 回旋加速器
  icon: cyclotron
  position: 30
categories:
  - machines
item_ids:
  - stellar_evolution:cyclotron
---
# 回旋加速器

不占 DP 的大机器：按网络热流率“差速充能”，充满后执行一次配方。

<BlockImage id="cyclotron" scale="6" />

## 运行

```
每 tick 充能 = 网络 HP/t ÷ 64
充能上限   = 当前配方差速需求
```

- 差速达到配方需求 → 执行一次，差速清零。
- 并行量 = `floor(网络 DP / 配方需求)`，保底 1，最大 64。
- 受粉量与输出空间限制。

## 用途

把粉加工成同位素 / 氚等。配方见 JEI「回旋加速器」分类。

## 注意

- 回旋加速器不占 DP，但并行量看网络 DP。
- 网络 HP/t 决定充能速度；没热压差就充不动。
