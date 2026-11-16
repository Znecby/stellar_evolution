---
navigation:
  parent: machines/machines-index.md
  title: 对撞机
  icon: collider
  position: 33
categories:
  - machines
item_ids:
  - stellar_evolution:collider
---
# 对撞机

唯一要求**两路束流同时供能**的机器，运行时还会向外喷出缪子流。

<BlockImage id="collider" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:collider" x="0" y="0" z="0" />
  <Block id="stellar_evolution:beam_pipe" x="2" y="1" z="0" p:stream="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:beam_pipe" x="2" y="1" z="2" p:stream="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:accelerator_ring" x="0" y="2" z="0" />
  <BoxAnnotation color="#ee3333" min="2 1 0" max="3 2 3">两路束流</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 2 0" max="1 3 1">加速环</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 运行

- 束-束对撞：配方要求两路流同时接上并吃满。
- 每一路需求各自累计进度；两路都满才扣料出产物。
- 同时作为 `StreamEndpoint` 向外输出缪子流，可以接束流管继续用。

## 加速环

贴着对撞机放 <ItemLink id="accelerator_ring" />，会被扫描计数（上限 16）。数量达到配方门槛后，解锁“批量奇异物质 / 批量反物质”配方。

## 注意

- 只接一路 → 配方不启动。
- 两路类型不对 → 配方不匹配。
- 对撞机既是消费者又是源；Jade 会同时显示需求和输出。
