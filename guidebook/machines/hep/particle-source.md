---
navigation:
  parent: machines/machines-index.md
  title: 粒子源
  icon: particle_source
  position: 31
categories:
  - machines
item_ids:
  - stellar_evolution:particle_source
  - stellar_evolution:pressure_tank
---
# 粒子源

把带粒子类型的压力罐打成束流，直接接 <ItemLink id="beam_pipe" />；没有靶站。

<BlockImage id="particle_source" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:particle_source" x="0" y="0" z="0" />
  <Block id="stellar_evolution:beam_pipe" x="2" y="1" z="0" p:stream="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:irradiation_chamber" x="3" y="0" z="0" />
  <BoxAnnotation color="#ee3333" min="2 1 0" max="3 2 1">束流管</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 输入 / 输出

- 输入容器：带 `particle_type` 的 <ItemLink id="pressure_tank" />。
- 输出容器：空压力罐。
- 束流管：侧面 / 正下方接入，输出对应类型的粒子流。

## 运行

- 每 20t 一轮，压力罐耐久 −磨损值；耐久归零 → 移除组件，空罐进输出。
- 供能不足（DP 或 HP 低于配方要求）→ **完全停机**，不耗罐。
- 缓冲上限 10 000；满仓停产，但存量仍可发货。
- **不随 HP 变速**：要么全速，要么不跑。
- 增压模块：束流强度 ×1.5，能耗门槛 ×1.5。

## 注意

- 没有输出容器 → 找不到放空罐的地方，直接停机。
- 粒子类型由压力罐决定；换罐就换流。
- 束流管默认空管断开，记得用剪刀接。
