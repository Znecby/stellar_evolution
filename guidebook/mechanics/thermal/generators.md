---
navigation:
  parent: mechanics/thermal/thermal-index.md
  title: 生成端
  icon: heat_engine
  position: 4
---
# 生成端

| 生成端 | 容量 DP | 热流率 HP/t | 条件 |
|---|---|---|---|
| <ItemLink id="heat_engine" /> | 1024 | 128 | 正下方容器里有熔炉燃料 |
| <ItemLink id="burning_engine" /> 固体 | 4096 | 512 | 正下方物品容器 |
| <ItemLink id="burning_engine" /> 液体 | 5120–12800 | 1280–3200 | 正下方储罐 |
| <ItemLink id="industrial_combustion_engine" /> | 最高 32,000（满转） | 最高 4,000 | MEDIUM / HIGH；氧气助燃 |
| <ItemLink id="combined_cycle_gas_turbine" /> | 20,000 | 最高 8,000 | GAS / AERO；氧气 + 水产汽 |
| <ItemLink id="aerospace_rocket_engine" /> | 最高 26,000 | 最高 7,000 | ROCKET；燃料自带氧化剂，真空可用 |
| <ItemLink id="steam_turbine_unit" /> | RPM × 64 × 压力倍率 | RPM × 2 | 转速 > 100 RPM |
| <ItemLink id="creative_pressure" /> | 极大 | 可调 | 调试用 |

## 热力引擎

正下方放物品容器并塞入任意熔炉燃料；**不再要求点燃的熔炉**。燃料池 800 点、每 10t 扣 1 点，同一份燃料只烧燃烧引擎的一半时长。

## 燃烧引擎

双模：

- 正下方物品容器 → 固体 4096 DP / 512 HP/t。
- 正下方储罐 → 液体燃料，按 `EngineFuels` 表 5120–12800 DP / 1280–3200 HP/t；只认 LOW / MEDIUM / HIGH，不烧 GAS / AERO / ROCKET。

具体燃料和数值见[燃料表](../../reference/fuels.md)、JEI / Jade。

## 三台大机组

- **工业内燃机组**：MEDIUM / HIGH 活塞燃料，1 mB/t（LOW 只给燃烧引擎）；氧气 2 mB/t 助燃（HP ×1.25、DP ×1.15）；10 秒升转，真空停转。
- **联合燃气机组**：GAS / AERO，2 mB/t；氧气 4 mB/t（HP +10%）；铜格栅供水 8,000 mB/t → 8,000 mB/t、300℃、30 bar 蒸汽；约 33 秒升转，真空停转。
- **航空航天火箭引擎**：ROCKET 级，4 mB/t；燃料自带氧化剂，不抽氧气；约 1 秒建立室压，真空可用；8 格尾焰危险。

完整燃料数值见[燃料表](../../reference/fuels.md)；每台机组的细节见各自页面。

## 蒸汽涡轮机组

不烧燃料，吃蒸汽网络的蒸汽，把转速换算成 DP / HP。详见[蒸汽网络](../steam/steam-index.md)和[蒸汽涡轮机组](../../machines/generators/steam-turbine.md)。

## 聚合规则

- 容量：全部生成端**累加**。
- 热流率：取**最大值**，不叠加。

所以“多放几台热力引擎”只加容量，不会让网络更快；要提速得换更高 HP/t 的生成端。
