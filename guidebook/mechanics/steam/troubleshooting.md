---
navigation:
  parent: mechanics/steam/steam-index.md
  title: 排错
  icon: minecraft:redstone_torch
  position: 5
---
# 排错

| 现象 | 常见原因 | 处理 |
|---|---|---|
| 反应堆“无法输出蒸汽” | 机器底排没有压力管 | 在底排 / 正下方接压力管 |
| 末端压力管喷白汽 | 网络里没有蒸汽涡轮机组 | 接蒸汽涡轮机组，或先切断管子 |
| 蒸汽涡轮机组不转 | 蒸汽量 / 温度 / 压力不够 | 看 Jade 蒸汽条和等级 |
| 蒸汽涡轮机组转速上不去 | 压力档位不够 | 提高堆功率 / 换 MOX / 用过超临界蒸汽 |
| 蒸汽涡轮机组 0 RPM 但网络有汽 | 需求没被分配 / 管子没接上 | 检查底排接管、剪刀切断 |
| 裂变堆温度失控 | 没水 / 没燃料 / 没冷却 | 见[裂变堆](../../machines/fission/fission-reactor.md) |

## Jade

压力管和蒸汽涡轮机组会显示蒸汽条、等级、RPM、蒸汽消耗和状态。先看 Jade，再用 `/se thermal` 看蒸汽涡轮机组作为生成端的 DP / HP。
