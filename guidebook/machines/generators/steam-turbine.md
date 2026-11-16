---
navigation:
  parent: machines/machines-index.md
  title: 蒸汽涡轮机组
  icon: steam_turbine_unit
  position: 3
categories:
  - machines
item_ids:
  - stellar_evolution:steam_turbine_unit
---
# 蒸汽涡轮机组

裂变 / 聚变线的正式“发电”环节：**蒸汽网络消耗端 + 热压差生成端**。

<BlockImage id="steam_turbine_unit" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:steam_turbine_unit" x="0" y="0" z="0" />
  <Block id="stellar_evolution:pressure_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:copper_pipe" x="3" y="0" z="1" p:east="true" p:west="true" />
  <Block id="minecraft:copper_block" x="4" y="0" z="1" />
  <BoxAnnotation color="#5CA7CD" min="2 0 0" max="3 1 1">压力管</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="3 0 1" max="5 1 2">铜管输出</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 接法

- 底排接 <ItemLink id="pressure_pipe" />，吃蒸汽。
- 任意一格贴铜块，或底排贴铜管，向热压差网络输出 DP / HP。
- 不产凝结水。

## 出力

```
DP   = RPM × 64 × 压力倍率
HP/t = RPM × 2
```

满速 20 000 RPM、500 bar：**2 560 000 DP / 40 000 HP/t**；过超临界（>600℃ 且 >500 bar）可到 **25 000 RPM**，900 bar 时约 **4 480 000 DP / 50 000 HP/t**。

## 转速

- 饱和 1 000 / 过热 3 000 / 超临界 8 000–20 000 / 过超临界 25 000 RPM。
- 转速 > 100 RPM 才输出。
- 普通蒸汽升速 +1 RPM/t，超临界 +10 RPM/t。

## 相关

- [蒸汽网络](../../mechanics/steam/steam-index.md)
- [反应堆 → 蒸汽涡轮机组](../../mechanics/steam/reactor-to-turbine.md)
- [裂变堆](../fission/fission-reactor.md)
