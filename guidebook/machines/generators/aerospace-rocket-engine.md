---
navigation:
  parent: machines/machines-index.md
  title: 航空航天火箭引擎
  icon: aerospace_rocket_engine
  position: 8
categories:
  - machines
item_ids:
  - stellar_evolution:aerospace_rocket_engine
---
# 航空航天火箭引擎

2×2×2 大型热压差生成端：**中高 DP / 中高 HP**，燃料自带氧化剂，**真空可用**。

<BlockImage id="aerospace_rocket_engine" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:aerospace_rocket_engine" x="0" y="0" z="0" />
  <Block id="stellar_evolution:aerospace_rocket_engine_shell" x="1" y="0" z="0" />
  <Block id="stellar_evolution:aerospace_rocket_engine_shell" x="0" y="1" z="0" />
  <Block id="stellar_evolution:aerospace_rocket_engine_shell" x="1" y="1" z="0" />
  <Block id="stellar_evolution:aerospace_rocket_engine_shell" x="0" y="0" z="1" />
  <Block id="stellar_evolution:aerospace_rocket_engine_shell" x="1" y="0" z="1" />
  <Block id="stellar_evolution:aerospace_rocket_engine_shell" x="0" y="1" z="1" />
  <Block id="stellar_evolution:aerospace_rocket_engine_shell" x="1" y="1" z="1" />
  <Block id="stellar_evolution:copper_tank" x="3" y="0" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="0" y="-1" z="0" p:up="true" p:east="true" />
  <BoxAnnotation color="#82CD5C" min="3 0 0" max="4 1 1">ROCKET 燃料储罐</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="0 -1 0" max="2 0 1">铜管输出</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 数值

| 项 | 内容 |
|---|---|
| 最大 DP | 26 000 |
| 最大 HP/t | 7 000 |
| 燃料 | ROCKET 级，4 mB/t；氧化剂已预混 |
| 室压 | 0 → 1 约 1 秒；出力正比于室压 |
| 大气 | **真空可用** |

## 尾焰

- 8 格锥形尾焰会吹飞、点燃并破甲秒杀；不要对着基地、玩家或易燃物试车。
- 引擎本体可以朝任意方向；尾焰方向由朝向决定。

## 燃料

ROCKET 级：火箭燃料、松节油-硝酸、乙醇氧、甲烷氧、硝基苯推进剂、铝化煤油浆、糠醇-硝酸、自燃推进剂。完整数值见[燃料表](../../reference/fuels.md)与 JEI「航空航天火箭引擎」分类。

## 注意

- 不需要额外氧气；燃料配方里已带氧化剂。
- 燃料储罐贴近机器或接到机械管网输入侧。
- 适合月球 / 太空基地，不依赖大气。
