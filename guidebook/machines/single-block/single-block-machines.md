---
navigation:
  parent: machines/machines-index.md
  title: 单方块加工机
  icon: crusher
  position: 5
categories:
  - machines
item_ids:
  - stellar_evolution:crusher
  - stellar_evolution:die_press
  - stellar_evolution:rolling_mill
  - stellar_evolution:steel_furnace
---
# 单方块加工机

破碎机 / 压模机 / 线材轧机 / 钢炉是同一套**单方块**框架：右键打开 3×3 料仓，产物放进**正上方容器**，不吃机械管，只吃热压差。

<BlockImage id="crusher" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:crusher" x="0" y="0" z="0" />
  <Block id="minecraft:chest" x="0" y="1" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="1" y="0" z="0" p:west="true" p:east="true" />
  <Block id="minecraft:copper_block" x="2" y="0" z="0" />
  <BoxAnnotation color="#82CD5C" min="0 1 0" max="1 2 1">产物容器</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 0 0" max="3 1 1">任意面接铜管</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 共同规则

- **单方块**，不是 2×2×2；右键打开机内 3×3 料仓。
- 机内 3×3 是**输入**；每个周期每格处理 1 次，最多 **9 并行**。
- **正上方必须是容器**；没有容器 / 放不下 → 整机停在「等待空间」，不扣料。
- **热压差**：热阻 2、基准 64 HP/t；热流率越高越快。满速上限 = 64 × 各自周期：破碎 15 360、压模 20 480、轧线 17 920、钢炉 12 800 HP/t。
- **任意面贴铜管或铜块**就能接入；**不用机械管**，料从 GUI 或物品能力进机内。
- 配方看 JEI；机器同时会播动画。

## 四台机器

| 机器 | 周期 | 配方来源 |
|---|---|---|
| <ItemLink id="crusher" /> | 240t | 代码内置粉碎表：矿石 → 粗矿、粗矿 → 粉、石头链等；煤炭 / 木炭 → 煤炭粉 / 木炭粉（1 出 1） |
| <ItemLink id="die_press" /> | 320t | 代码内置压模表：装模具后，锭 → 板 / 杆 / 齿轮（见下） |
| <ItemLink id="rolling_mill" /> | 280t | 代码内置轧线表：铜 / 金 / 钨 / 铅 / 6 种合金锭 → 线 ×8 |
| <ItemLink id="steel_furnace" /> | 200t | **原版熔炼配方**，9 格并行；JEI 熔炉页直接以钢炉为催化剂 |

## 压模机与模具

压模机界面比其它三台多一个**模具槽**（3×3 第一行左侧），只能放 1 个模具，**不消耗**：

| 模具 | 配方 |
|---|---|
| <ItemLink id="plate_mold" /> | 锭 ×1 → 板 ×1 |
| <ItemLink id="rod_mold" /> | 锭 ×2 → 杆 ×2 |
| <ItemLink id="gear_mold" /> | 锭 ×2 → 齿轮 ×1（铜 / 钢 / 钨 / 铅 / 6 种合金） |

- 板覆盖：铜 / 铁 / 金 / 下界合金 / 钨 / 铅 / 钢 / 6 种合金 / 稀土合金。
- 杆覆盖：铜 / 下界合金 / 钨 / 铅 / 钢 / 6 种合金 / 稀土合金。
- 模具在**切石机**用 **1 钢板 → 1 个** 制作（原版切石机每次固定消耗 1 个输入）。
- 模具槽空着 → 压模机不工作；悬停空槽会显示可放模具的提示。

## 钢炉

- 3×3 料仓放可烧物，按原版熔炉配方 9 格同时烧。
- 产物进正上方容器；没有容器不工作。
- 生存配方：钢板 ×3 + 钢板 / 高炉 / 钢板 + 铜杆 / 铜框架 / 铜杆（见 JEI）。

## 破碎机与煤化工

- **煤炭 → 煤炭粉 ×1**、**木炭 → 木炭粉 ×1**（1 出 1，不增不减）。
- 煤水浆、煤气化、活性炭、木炭空气气化都改吃粉；焦化炉仍直接吃整块煤 / 原木。

## 工作台配方

<RecipeFor id="crusher" />
<RecipeFor id="die_press" />
<RecipeFor id="rolling_mill" />
<RecipeFor id="steel_furnace" />

## 焊接机

焊接机虽然也是单方块，但多了「固定顺序 + 下方储罐 + 上方容器」的特殊规则，单独看[焊接机](welding-machine.md)。
