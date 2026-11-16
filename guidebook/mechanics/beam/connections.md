---
navigation:
  parent: mechanics/beam/beam-index.md
  title: 连接规则
  icon: beam_pipe
  position: 2
---
# 连接规则

束流管和另外三种管**手感相反**：其他管自动连、剪刀切断；束流管**默认空管且断开，要用剪刀接**。

## 新放的束流管

- 类型：空管（中心显示原版安山岩）。
- 连接：六个方向全部断开。
- 空管不与机器相接，也不与有类型的管相接。

## 剪刀

| 操作 | 效果 |
|---|---|
| 右键某一端 | 接上 / 断开这一端 |
| Shift + 右键 | 整段同类型管线循环切换流类型 |

接管时的类型继承：

- 空管 ↔ 空管：直接连上，方便先铺线。
- 空管 ↔ 有类型管：整段空管一起变成该类型。
- 空管 ↔ 机器：只有**有输出流**的机器能把空管定型；纯消费者（如辐照仓）不生效。
- 不同类型的管：不接。

## 接机器的位置

与压力管、机械管不同：束流管可以接机器**侧面任意一层**（2×2×2 两层都能接），也可以从**正下方**接上来；**顶面不接**。

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:particle_source" x="0" y="0" z="0" />
  <Block id="stellar_evolution:beam_pipe" x="2" y="1" z="0" p:stream="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:beam_pipe" x="3" y="1" z="0" p:stream="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:irradiation_chamber" x="4" y="0" z="0" />
  <BoxAnnotation color="#ee3333" min="2 1 0" max="4 2 1">剪刀接上的束流管</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 常见错误

- 新放的束流管直接贴机器 → 还是空管，不接。
- 用剪刀右键管身中间 → 只切换准星对准的那一端。
- 想换整段类型，却只右键了一下 → 用 Shift + 右键。
- 不同类型硬接 → 不连，网络各算各的。
