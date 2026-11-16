---
navigation:
  parent: mechanics/steam/steam-index.md
  title: 反应堆 → 蒸汽涡轮机组
  icon: steam_turbine_unit
  position: 4
---
# 反应堆 → 蒸汽涡轮机组

## 反应堆输出

裂变堆把燃料棒的热量写成蒸汽数据：

- **温度**取堆芯温度。
- **压力**随功率升高，并受燃料族上限限制（铀约 300 bar，MOX 约 500 bar）。
- 没有相邻压力管 → “无法输出蒸汽”；有管但无蒸汽涡轮机组 → 末端排气。

## 蒸汽涡轮机组转速档位

| 蒸汽等级 | 最高 RPM |
|---|---|
| 饱和 | 1 000 |
| 过热 | 3 000 |
| 超临界 221 bar | 8 000 |
| 超临界 300 bar | 12 000 |
| 超临界 400 bar | 16 000 |
| 超临界 500 bar | 20 000 |
| 过超临界（>600℃ 且 >500 bar） | 25 000 |

转速 **> 100 RPM** 才开始输出。

## 出力

```
DP   = RPM × 64 × 压力倍率
HP/t = RPM × 2
```

满速 20 000 RPM、500 bar：

- DP = 20 000 × 64 × 2.0 = **2 560 000 DP**
- HP/t = 20 000 × 2 = **40 000 HP/t**

过超临界 25 000 RPM、900 bar：

- DP = 25 000 × 64 × 2.8 = **4 480 000 DP**
- HP/t = 25 000 × 2 = **50 000 HP/t**

## 蒸汽消耗

```
需求 = 38.4 × (1 + RPM) mB/t
```

25 000 RPM 约 **960 000 mB/t**。蒸汽被直接消耗，**不产凝结水**。

## 典型布置

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:fission_reactor" x="0" y="0" z="0" />
  <Block id="stellar_evolution:pressure_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:pressure_pipe" x="3" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:steam_turbine_unit" x="4" y="0" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="7" y="0" z="0" p:east="true" p:west="true" />
  <Block id="minecraft:copper_block" x="8" y="0" z="0" />
  <Block id="minecraft:furnace" x="9" y="0" z="0" p:lit="true" />
  <BoxAnnotation color="#5CA7CD" min="2 0 0" max="4 1 1">压力管</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="7 0 0" max="10 1 1">热压差输出</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

蒸汽涡轮机组是热压差**生成端**：底排贴铜管，或任意一格贴铜块；反应堆只出蒸汽，不接热压差。

## 相关

- [裂变堆](../../machines/fission/fission-reactor.md)
- [蒸汽涡轮机组](../../machines/generators/steam-turbine.md)
- [蒸汽等级](grades.md)
