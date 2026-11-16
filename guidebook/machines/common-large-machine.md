---
navigation:
  parent: machines/machines-index.md
  title: 通用大机器
  icon: reactor
  position: 0
categories:
  - machines
item_ids:
  - stellar_evolution:slurry_mixer
  - stellar_evolution:fermenter
  - stellar_evolution:decomposer
  - stellar_evolution:sterilizer
  - stellar_evolution:crystallizer
  - stellar_evolution:reactor
  - stellar_evolution:multiblock_builder
  - stellar_evolution:ore_leacher
  - stellar_evolution:fission_centrifuge
  - stellar_evolution:canning_machine
  - stellar_evolution:reprocessor
---
# 通用大机器

生化、石化、煤化工、裂变燃料加工、后处理等**大机器**都是同一套 **2×2×2 整机**：1 个主方块 + 7 个填充格。填充格和主方块等价，任意一格都算机器本体。

单方块的破碎机 / 压模机 / 轧线 / 钢炉 / 焊接机 / 开采钻另看[单方块加工机](single-block/single-block-machines.md)。

<BlockImage id="reactor" scale="6" />

## 结构

- 主方块 + 7 个填充格，共 8 格。
- 12 个接管口只是外观，不参与判定。
- 手里有 <ItemLink id="multiblock_builder" /> 可以一键搭出整机。
- 机器没有 GUI；状态看 Jade。

## 能源

消耗端大机器**任意一格、任意一面贴铜块**，**或**在**底排**贴铜管，即接入热压差网络。

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:slurry_mixer" x="0" y="0" z="0" />
  <Block id="minecraft:copper_block" x="3" y="0" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="4" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:heat_engine" x="5" y="0" z="0" />
  <Block id="minecraft:chest" x="5" y="-1" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="0" y="-1" z="0" p:up="true" p:north="true" />
  <Block id="minecraft:copper_chest" x="0" y="-1" z="-1" />
  <BoxAnnotation color="#5CA7CD" min="3 0 0" max="4 1 1">铜块供能</BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="0 -1 -1" max="1 0 1">机械管物流</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

- 既没贴铜块、底排也没接铜管 → 不是网络成员，Jade 不显示热压差。
- 铜块必须接上铜管 / 生成端，否则网络是孤立的；底排铜管也要接到生成端。
- 热阻 / 基准热流率按机种不同；固定 8 并行。

## 物流

<ItemLink id="machine_pipe" /> 只在机器**底排**接管：

- 侧面最底一层
- 正下方
- 顶面不接

容器：

| 容器 | 接入面 |
|---|---|
| 铜箱子 | 任意面 |
| 储罐（铜 / 钢 / 硬质） | 任意面 |
| <ItemLink id="reaction_chamber" /> | 任意面 |
| 铜格栅 | 任意面 |

容器默认输入；手持剪刀右键容器切输出。

## 配方

- 每个周期取第一条“条件满足 + 料够 + 放得下”的配方。
- 原料从输入容器抽、产物进输出容器，同一事务，失败整体回滚。
- 热压差只决定倍速，不改产率；满速时一个周期最多 8 条配方。
- 配方看 JEI 对应分类。

## 常见故障

| 现象 | 原因 | 处理 |
|---|---|---|
| 不转 | 没贴铜块 / 底排没接铜管 / 网络无生成端 | 检查能源接入 |
| 不抽料 | 机械管不在底排 | 挪到底排 / 正下方 |
| 不产物 | 输出容器满 / IO 反了 | 清空输出 / 剪刀切输出 |
| 很慢 | 网络热流率低于基准 | 换更高 HP/t 的生成端 |
| 全停 | 网络超载 | 增加容量或减少消耗端 |

## 常见机器

| 线 | 机器 |
|---|---|
| 生化 | <ItemLink id="slurry_mixer" />、<ItemLink id="fermenter" />、<ItemLink id="decomposer" />、<ItemLink id="sterilizer" />、<ItemLink id="crystallizer" />、<ItemLink id="reactor" /> |
| 石化 / 煤化工 | 石化 4 台 + 煤化工 2 台（气化炉 / 合成塔），见 JEI 分类；焦化炉是 3×3×4 多方块，见[焦化炉](industry/coke-oven.md) |
| 裂变燃料 | <ItemLink id="ore_leacher" />、<ItemLink id="fission_centrifuge" />、<ItemLink id="canning_machine" />、<ItemLink id="reprocessor" /> |

## 特殊机器

下面这些不吃通用规则，单独看页：

- [裂变堆](fission/fission-reactor.md)
- [蒸汽涡轮机组](generators/steam-turbine.md)
- [工业内燃机组](generators/industrial-combustion-engine.md)
- [联合燃气机组](generators/combined-cycle-gas-turbine.md)
- [航空航天火箭引擎](generators/aerospace-rocket-engine.md)
- [焦化炉](industry/coke-oven.md)
- [缪子聚变仪](fusion/muon-fusion.md)
- [粒子源](hep/particle-source.md)
- [对撞机](hep/collider.md)
- [辐照仓](hep/irradiation-chamber.md)
