---
navigation:
  parent: mechanics/steam/steam-index.md
  title: 连接规则
  icon: pressure_pipe
  position: 2
---
# 连接规则

## 谁和谁连

<ItemLink id="pressure_pipe" /> 只连：

- 同类压力管
- 蒸汽端点：裂变堆、蒸汽涡轮机组、缪子聚变仪

**不连**铜管、机械管、束流管。

## 接机器的位置

与机械管一致：**只在机器底排接管**。

- 侧面最底一层可以接。
- 正下方可以接。
- **顶面不接**。

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:fission_reactor" x="0" y="0" z="0" />
  <Block id="stellar_evolution:pressure_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:pressure_pipe" x="3" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:pressure_pipe" x="4" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:steam_turbine_unit" x="5" y="0" z="0" />
  <BoxAnnotation color="#5CA7CD" min="2 0 0" max="5 1 1">压力管：底排接管</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 剪刀

手持剪刀右键某段管臂，切断 / 恢复该端连接。和机械管、铜管一样。

## 末端排气

如果压力管接了反应堆，但网络里**没有消耗端**，末端压力管会喷白色蒸汽粒子，蒸汽全部丢弃。这是“有汽没处去”的提示，不是 bug。

## 常见错误

- 压力管接到机器侧面中层 → 不接，必须底排 / 正下方。
- 用铜管接反应堆 → 铜管不传蒸汽。
- 反应堆旁没有压力管 → Jade 显示“无法输出蒸汽”。
- 有压力管但没有蒸汽涡轮机组 → 末端排气，能量浪费。
