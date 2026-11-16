---
navigation:
  parent: example-setups/example-setups-index.md
  title: 束流链
  icon: beam_pipe
  position: 4
---
# 束流链

最小骨架：

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:particle_source" x="0" y="0" z="0" />
  <Block id="stellar_evolution:beam_pipe" x="2" y="1" z="0" p:stream="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:beam_pipe" x="3" y="1" z="0" p:stream="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:collider" x="4" y="0" z="0" />
  <Block id="stellar_evolution:accelerator_ring" x="4" y="2" z="0" />
  <Block id="stellar_evolution:irradiation_chamber" x="7" y="0" z="0" />
  <BoxAnnotation color="#ee3333" min="2 1 0" max="4 2 1">剪刀接上的束流管</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="4 2 0" max="5 3 1">加速环</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 步骤

1. 给 <ItemLink id="particle_source" /> 输入带粒子类型的压力罐，输出容器放空罐。
2. 用剪刀把 <ItemLink id="beam_pipe" /> 接到粒子源的侧面 / 正下方。
3. 管子接到 <ItemLink id="irradiation_chamber" /> 或 <ItemLink id="collider" />。
4. 对撞机要求两路束流；贴 <ItemLink id="accelerator_ring" /> 可解锁批量配方。
5. 对撞机输出的缪子流可以继续接束流管。

## 常见错误

- 束流管还是空管 → 用剪刀接。
- 接了两路不同类型的管 → 网络各算各的。
- 粒子源没有空罐输出位置 → 停机。
- 对撞机只接一路 → 不启动。
