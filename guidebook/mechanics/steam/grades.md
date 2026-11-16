---
navigation:
  parent: mechanics/steam/steam-index.md
  title: 蒸汽等级
  icon: steam_turbine_unit
  position: 3
---
# 蒸汽等级

温度与压力同时过线才算升级：

| 等级 | 条件 |
|---|---|
| 无蒸汽 | 温度 < 100℃ 或量为 0 |
| 饱和蒸汽 | ≥ 100℃ |
| 过热蒸汽 | ≥ 227℃ |
| 超临界蒸汽 | ≥ 374℃ 且 ≥ 221 bar |
| 过超临界蒸汽 | > 600℃ 且 > 500 bar |

## 压力对 DP 的加成

蒸汽涡轮机组的 DP 出力会乘一个压力倍率：

```
压力倍率 = 1 + 压力 / 500
```

- 500 bar → ×2.0
- 900 bar → ×2.8（封顶 +180%）
- HP/t 不受压力影响，只由 RPM 决定。

## 谁能到过超临界

- 裂变堆的 MOX 压力硬顶是 500 bar，**严格大于 500** 才算过超临界，所以裂变堆永远差一点。
- 缪子聚变仪压力可到 900 bar、工质温度封顶 700℃，只有它能进这一档。

## 相关

- [反应堆 → 蒸汽涡轮机组](reactor-to-turbine.md)
- [蒸汽涡轮机组](../../machines/generators/steam-turbine.md)
