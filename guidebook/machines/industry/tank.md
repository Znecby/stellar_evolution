---
navigation:
  parent: machines/machines-index.md
  title: 储罐
  icon: copper_tank
  position: 43
categories:
  - machines
item_ids:
  - stellar_evolution:copper_tank
  - stellar_evolution:steel_tank
  - stellar_evolution:hardened_tank
---
# 储罐

三档储罐共用同一套合罐 / 存取逻辑，只差单格容量与外观：**铜储罐 / 钢储罐 / 硬质储罐**。

<BlockImage id="copper_tank" scale="6" />

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:copper_tank" x="0" y="0" z="0" />
  <Block id="stellar_evolution:steel_tank" x="2" y="0" z="0" />
  <Block id="stellar_evolution:hardened_tank" x="4" y="0" z="0" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="1 1 1">铜：8 桶/格</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="2 0 0" max="3 1 1">钢：32 桶/格</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="4 0 0" max="5 1 1">硬质：128 桶/格</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 三档

| 档位 | id | 单格容量 | 3×3×32 最大容量 | 工作台配方 |
|---|---|---|---|---|
| 铜储罐 | `copper_tank` | 8 桶（8 000 mB） | 2 304 桶 | 铜板 ×4 + 玻璃 ×4 |
| 钢储罐 | `steel_tank` | 32 桶（32 000 mB） | 9 216 桶 | 钢板 ×4 + 玻璃 ×4 |
| 硬质储罐 | `hardened_tank` | 128 桶（128 000 mB） | 36 864 桶 | 钨铜硬质合金板 ×4 + 玻璃 ×4 |

- 三档**不会互相合并**：铜罐挨着钢罐，仍是两个独立罐。
- 单格灌满后不再进；有流体时能透过玻璃看到液面。
- 无 GUI：拿流体桶右键灌入，拿空桶右键取出一桶。

## 合并

- 只做 **1×1** 与 **3×3** 方形底；**2×2 不合并**。
- 竖直可以叠 **1–32 层**。
- 容量 = 格数 × 本档位单格容量；控制器是最小角那一格，部件把流体汇入控制器。
- 3×3 时四角与正中是金属板，只有四条边的中间格开窗；顶 / 底盖只在最上 / 最下一层。
- 拆掉任意一格后自动重算；拆小后超过新容量的部分会丢弃。

## 物流

- <ItemLink id="machine_pipe" /> 可接**任意一面**。
- **铜管不接储罐**：储罐不参与热压差网络，流体物流走机械管或第三方流体管道。
- 可接第三方流体管道（NeoForge 流体能力）。

## 工作台配方

<RecipeFor id="copper_tank" />
<RecipeFor id="steel_tank" />
<RecipeFor id="hardened_tank" />

## 注意

- 旧 `stellar_evolution:tank` 已改名为 `copper_tank`；旧存档里的旧储罐会变成空气，需要重新放置。
