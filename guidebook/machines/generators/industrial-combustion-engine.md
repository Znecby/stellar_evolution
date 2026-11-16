---
navigation:
  parent: machines/machines-index.md
  title: 工业内燃机组
  icon: industrial_combustion_engine
  position: 6
categories:
  - machines
item_ids:
  - stellar_evolution:industrial_combustion_engine
---
# 工业内燃机组

2×2×2 大型热压差生成端：**中 HP / 高 DP**，只吃 MEDIUM / HIGH 液体燃料（LOW 留给早期燃烧引擎），可加氧气助燃。

<BlockImage id="industrial_combustion_engine" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:industrial_combustion_engine" x="0" y="0" z="0" />
  <Block id="stellar_evolution:industrial_combustion_engine_shell" x="1" y="0" z="0" />
  <Block id="stellar_evolution:industrial_combustion_engine_shell" x="0" y="1" z="0" />
  <Block id="stellar_evolution:industrial_combustion_engine_shell" x="1" y="1" z="0" />
  <Block id="stellar_evolution:industrial_combustion_engine_shell" x="0" y="0" z="1" />
  <Block id="stellar_evolution:industrial_combustion_engine_shell" x="1" y="0" z="1" />
  <Block id="stellar_evolution:industrial_combustion_engine_shell" x="0" y="1" z="1" />
  <Block id="stellar_evolution:industrial_combustion_engine_shell" x="1" y="1" z="1" />
  <Block id="stellar_evolution:copper_tank" x="3" y="0" z="0" />
  <Block id="stellar_evolution:copper_tank" x="0" y="0" z="3" />
  <Block id="stellar_evolution:copper_pipe" x="0" y="-1" z="0" p:up="true" p:east="true" />
  <Block id="minecraft:copper_block" x="1" y="-1" z="0" />
  <BoxAnnotation color="#82CD5C" min="3 0 0" max="4 1 1">燃料储罐</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 3" max="1 1 4">氧气储罐</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="0 -1 0" max="2 0 1">铜管输出</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 数值

| 项 | 内容 |
|---|---|
| 最大 DP | 32 000 |
| 最大 HP/t | 4 000 |
| 出力 | `DP = 32 000 × (0.6 + 0.4 × 转速)`；`HP = 4 000 × 转速` |
| 燃料 | MEDIUM / HIGH，1 mB/t（不吃 LOW） |
| 氧气 | 2 mB/t；转速上限 1.25，HP ×1.25、DP ×1.15 |
| 升转 | 0 → 1.0 约 10 秒；断料 0.02/t 降转 |
| 大气 | 真空维度不能燃烧 |

## 燃料从哪来

- 六面相邻的**输入储罐**，或机械管网里的输入储罐。
- 燃料与氧气可以是不同储罐；机器会按等级挑可用燃料。
- 配方 / 燃料数值见[燃料表](../../reference/fuels.md)和 JEI「工业内燃机组」分类。

## 注意

- 没有内部储罐；储罐必须贴着机器或接到同一张输入管网。
- 氧气不是必须，但加上后出力提升明显。
- 月球等真空维度完全停转。
