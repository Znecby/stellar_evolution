---
navigation:
  parent: machines/machines-index.md
  title: 燃烧引擎
  icon: burning_engine
  position: 2
categories:
  - machines
item_ids:
  - stellar_evolution:burning_engine
---
# 燃烧引擎

中期主力生成端，双模：

| 模式 | 出力 | 燃料来源 |
|---|---|---|
| 固体 | **4096 DP / 512 HP/t** | 正下方物品容器里的任意熔炉燃料 |
| 液体 | **5120–12800 DP / 1280–3200 HP/t** | 正下方储罐里的流体燃料 |

<BlockImage id="burning_engine" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:burning_engine" x="0" y="0" z="0" />
  <Block id="minecraft:chest" x="0" y="-1" z="0" />
  <Block id="stellar_evolution:burning_engine" x="3" y="0" z="0" />
  <Block id="stellar_evolution:copper_tank" x="3" y="-1" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="1" y="0" z="0" p:west="true" p:east="true" />
  <BoxAnnotation color="#82CD5C" min="0 -1 0" max="1 0 1">固体：正下方容器</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3 -1 0" max="4 0 1">液体：正下方储罐</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 双模切换

- 正下方是**物品容器** → 固体模式，按原版熔炉燃料表取燃料。
- 正下方是**储罐** → 液体模式，按 `EngineFuels` 表取活塞燃料：丙酮、乙醇、普通汽油、生物柴油、柴油、高十六烷柴油、高辛烷汽油、含铅汽油、松节油、二甲醚、硝基苯。完整数值见[燃料表](../../reference/fuels.md)。
- 沼气 / 石油气 / 合成气 / 焦炉煤气 / 煤油 / 航空煤油 / 合成航煤 / 火箭燃料属于燃气或火箭等级，**燃烧引擎不烧**。
- 丁醇 / 甲醇 / 萘已退出燃料表，只作化工中间体。
- 其它 / 没有 → 停机。

## 燃料池

- 固体池 1600 点；**每 20t 扣 1 点**（1 点 = 20t）。
- 液体模式按燃料表决定单周期时长与出力，每周期固定消耗 1 mB。
- 取料同样只在整份燃料装得下时进行，不浪费半份燃料。

## 生存获取

工作台配方已删除，改由**热室**制造：铜框架 ×1 + 活塞组件 ×6 + 电容 ×8 + 轴承 ×1，800t。

## 注意

- 固体 512 HP/t 是中期分水岭：一台就能让不少大机器跑起来，但满速 8 并行的生化机仍要两台。
- 液体模式明显强于固体，后期优先液体。
- 更高一级是[蒸汽涡轮机组](steam-turbine.md)。
