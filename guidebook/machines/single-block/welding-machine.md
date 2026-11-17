---
navigation:
  parent: machines/machines-index.md
  title: 焊接机
  icon: welding_machine
  position: 6
categories:
  - machines
item_ids:
  - stellar_evolution:welding_machine
  - stellar_evolution:rugged_tungsten_welding_plate
  - stellar_evolution:rugged_lead_welding_plate
  - stellar_evolution:rugged_stainless_steel_welding_plate
  - stellar_evolution:rugged_high_speed_steel_welding_plate
  - stellar_evolution:flux_bucket
  - stellar_evolution:argon_bucket
---
# 焊接机

单方块、带 GUI 的热压差消耗端：**机内 3 格按固定顺序放料，正下方储罐供流体，正上方容器收产物**。

<BlockImage id="welding_machine" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:welding_machine" x="0" y="0" z="0" />
  <Block id="stellar_evolution:copper_tank" x="0" y="-1" z="0" />
  <Block id="minecraft:chest" x="0" y="1" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="1" y="0" z="0" p:west="true" p:east="true" />
  <BoxAnnotation color="#5CA7CD" min="0 -1 0" max="1 0 1">正下方：助焊剂 / 氩气</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="0 1 0" max="1 2 1">正上方：容器</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="1 0 0" max="2 1 1">任意面接铜管</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 数值与结构

| 项 | 内容 |
|---|---|
| 输入 | 机内 3 格（1×3），**固定顺序**匹配；多余槽必须为空 |
| 流体 | 从**正下方储罐**抽，最多 1 种 |
| 输出 | **正上方物品容器**；没有容器 / 放不下就停 |
| 周期 | 200t（助焊剂配方）/ 1200t（氩弧焊配方） |
| 热压差 | 热阻 2、基准 64 HP/t，满速上限 12 800 HP/t；**不并行**，一次一条 |
| 接入 | 任意面贴铜管或铜块；不用机械管 |

## 配方

| 槽 0 | 槽 1 | 槽 2 | 流体 | 产物 | 周期 |
|---|---|---|---|---|---|
| 钨板 | 铜板 | 钨板 | **助焊剂 25 mB** | 坚固钨焊接板 | 200t |
| 铅板 | 铜板 | 铅板 | **助焊剂 25 mB** | 坚固铅焊接板 | 200t |
| 高速钢板 | 铜板 | 高速钢板 | **氩气 25 mB** | 坚固高速钢焊接板 | 1200t |
| 不锈钢板 | 铜板 | 不锈钢板 | **氩气 25 mB** | 坚固不锈钢焊接板 | 1200t |
| 电阻 ×2 | 铜板 | 电容 ×2 | **助焊剂 25 mB** | 控制模块 | 200t |
| 电容 ×4 | 电阻 ×4 | 电路基板 | **助焊剂 25 mB** | 电路板 | 200t |

- 顺序敏感；多余槽必须为空。
- 扣料前先抽流体，抽不到就不动料，不会白吃材料。
- 氧气已**不再**用于焊接。

## 助焊剂 / 氩气

- **助焊剂**：调浆机 `硼砂 ×1 + 水 500 → 助焊剂 500`。
- **氩气**：不锈钢格栅供气 + 分馏塔空分 `液态空气 5 000 → 氮气 3 840 + 氧气 1 050 + 氩气 100 + 氦气 10`。
- 两种流体都走储罐 / 机械管；焊接机只认正下方储罐。

## 生存获取

<RecipeFor id="welding_machine" />

- 焊接板 / 坚固焊接板由本机产出；焊接板是材料模板件，目前没有独立生存配方。
- 坚固钨焊接板用于[开采钻](../industry/mining-drill.md)整机（4 块）。
- 坚固不锈钢 / 高速钢焊接板用于后期机器与装备。

## 注意

- 正下方必须是储罐，正上方必须是容器；缺一个就完全不动。
- 三格料仓内容会随方块被破坏掉出。
- 机内料仓可通过物品能力灌入 / 抽出，但产物只进上方容器。
