---
navigation:
  parent: machines/machines-index.md
  title: 开采钻
  icon: mining_drill
  position: 40
categories:
  - machines
item_ids:
  - stellar_evolution:mining_drill
  - stellar_evolution:drill_bit
---
# 开采钻

热压差消耗端，从机器正下方开始逐层向下开采，产物放进**正上方容器**。

<BlockImage id="mining_drill" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:mining_drill" x="0" y="0" z="0" />
  <Block id="minecraft:chest" x="0" y="1" z="0" />
  <Block id="minecraft:copper_block" x="1" y="0" z="0" />
  <BoxAnnotation color="#82CD5C" min="0 1 0" max="1 2 1">产物容器</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 0 0" max="2 1 1">铜块供能</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 数值

| 模式 | 基准 | 5×5 / 3×3 占用 | 周期 | 满速上限 |
|---|---|---|---|---|
| 范围破坏 | 256 HP/t、热阻 4（线性） | 5×5 = 1 024 DP | 200t × (N/5)² | 1 024 HP/t（4×） |
| 精准采集 | 2 048 HP/t、热阻 8（3×3；1×1 减半） | 3×3 = 16 384 DP | 260t × N/3 | 该范围基准的 4× |

- 到世界底部后停机；每层产物进正上方容器。

## 两种模式

| 模式 | 范围 | 行为 |
|---|---|---|
| 范围破坏 | N×N 方块（5–63，奇数） | 挖掉一切可破坏、非流体、无方块实体的方块，按无附魔下界合金镐掉落 |
| 精准采集 | N×N 区块（1–15，奇数） | 只采 `c:ores` 标签 + 远古残骸 / 萤石等，产出方块本身，矿洞回填圆石 |

## 配方

- 整机走**工作台**：传感器 / 铜框架 / 传感器 + **坚固钨焊接板** / 电动马达 / **坚固钨焊接板** + **坚固钨焊接板** / 钻头 / **坚固钨焊接板**。
- **钻头**在热室做：钨铜硬质合金板 ×4 + 杆 ×6 + 钢齿轮 ×4，350t。
- 开采钻整机的热室配方已删除，只有工作台配方。

<RecipeFor id="mining_drill" />

## 注意

- 正上方没有容器 → 停机等待。
- 模式 / 范围 / 当前层 Y 会存盘；进度不存盘，重进世界会重扫当前层。
- 铜管或铜块都能接入热压差网络；它不是大机器，不用机械管。
