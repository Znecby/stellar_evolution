---
navigation:
  parent: machines/machines-index.md
  title: 焦化炉
  icon: coke_oven_controller
  position: 45
categories:
  - machines
item_ids:
  - stellar_evolution:coke_oven_controller
---
# 焦化炉

3×3×4 砖块多方块：煤 / 原木**绝氧干馏**，同时产出焦炭 / 木炭、焦油和煤气。

<BlockImage id="coke_oven_controller" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="minecraft:bricks" x="0" y="0" z="0" />
  <Block id="minecraft:bricks" x="1" y="0" z="0" />
  <Block id="minecraft:bricks" x="2" y="0" z="0" />
  <Block id="minecraft:bricks" x="0" y="0" z="1" />
  <Block id="minecraft:bricks" x="1" y="0" z="1" />
  <Block id="minecraft:bricks" x="2" y="0" z="1" />
  <Block id="minecraft:bricks" x="0" y="0" z="2" />
  <Block id="minecraft:bricks" x="1" y="0" z="2" />
  <Block id="minecraft:bricks" x="2" y="0" z="2" />
  <Block id="minecraft:bricks" x="0" y="1" z="0" />
  <Block id="stellar_evolution:coke_oven_controller" x="1" y="1" z="0" />
  <Block id="minecraft:bricks" x="2" y="1" z="0" />
  <Block id="stellar_evolution:machine_logistics_hatch" x="0" y="1" z="1" />
  <Block id="stellar_evolution:machine_logistics_hatch" x="2" y="1" z="1" />
  <Block id="stellar_evolution:machine_logistics_hatch" x="1" y="1" z="2" />
  <Block id="minecraft:bricks" x="0" y="1" z="2" />
  <Block id="minecraft:bricks" x="2" y="1" z="2" />
  <Block id="minecraft:bricks" x="0" y="2" z="0" />
  <Block id="minecraft:bricks" x="1" y="2" z="0" />
  <Block id="minecraft:bricks" x="2" y="2" z="0" />
  <Block id="minecraft:bricks" x="0" y="2" z="1" />
  <Block id="minecraft:bricks" x="2" y="2" z="1" />
  <Block id="minecraft:bricks" x="0" y="2" z="2" />
  <Block id="minecraft:bricks" x="1" y="2" z="2" />
  <Block id="minecraft:bricks" x="2" y="2" z="2" />
  <Block id="minecraft:brick_slab" x="0" y="3" z="0" />
  <Block id="minecraft:brick_slab" x="1" y="3" z="0" />
  <Block id="minecraft:brick_slab" x="2" y="3" z="0" />
  <Block id="minecraft:brick_slab" x="0" y="3" z="1" />
  <Block id="minecraft:brick_slab" x="2" y="3" z="1" />
  <Block id="minecraft:brick_slab" x="0" y="3" z="2" />
  <Block id="minecraft:brick_slab" x="1" y="3" z="2" />
  <Block id="minecraft:brick_slab" x="2" y="3" z="2" />
  <BoxAnnotation color="#ee3333" min="1 1 0" max="2 2 1">控制器</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="0 1 1" max="1 2 2">物流仓</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 3 1" max="2 4 2">烟囱口</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 结构

- **3×3×4**：3 层原版红砖块 + 顶层砖半砖围边。
- 3×3 中心中空；顶面中心与半砖层中心也留空，形成直通烟囱口。
- 控制器放在某一侧面的**正中心**；其余三个侧面的正中心是**机械物流仓**。
- 砖块 / 半砖玩家自备；控制器在热室做，配方见 JEI「热室」。
- <ItemLink id="multiblock_builder" /> 支持该结构：蹲下右键控制器自动补齐。
- **成型可视化**：成型后物流仓基底从**不锈钢块**变成**红砖块**（跟随控制器外壳）；结构失效自动恢复不锈钢。

## 供热与物流

- 控制器**不是**热压差成员；热量由三个**物流仓**转发给网络。
- 铜块或**底排铜管**贴着物流仓即可接入热压差。
- 机械管只接物流仓的**正面**（铜块覆盖层）；整台机器走大机器物流框架，不走 2×2×2 机械管。
- 8 并行；热阻 4、基准 320 HP/t、周期 400t。

## 配方

| 输入 | 产出 |
|---|---|
| 煤 ×4 | 焦炭 ×2 + 煤焦油 500 mB + 焦炉煤气 800 mB |
| 原木 ×4（`logs_that_burn`） | 木炭 ×3 + 木焦油 300 + 木醋液 500 + 松节油 200 |

- 煤 / 原木任一即可；原木不含菌柄。
- 配方与后续分支见[化工总览](../../reference/chemistry.md)。

## 注意

- 结构缺砖 / 半砖 / 物流仓 → 不成型，控制器不工作。
- 三个物流仓都能供热，但只取容量最大的那个账本。
- 焦炉煤气可直接烧[联合燃气机组](../generators/combined-cycle-gas-turbine.md)。
