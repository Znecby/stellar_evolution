---
navigation:
  parent: reference/reference-index.md
  title: 化工总览
  icon: coke
  position: 7
item_ids:
  - stellar_evolution:coke
  - stellar_evolution:coal_dust
  - stellar_evolution:charcoal_dust
  - stellar_evolution:urea
  - stellar_evolution:calcium_carbide
  - stellar_evolution:calcium_hydroxide
  - stellar_evolution:activated_carbon
  - stellar_evolution:needle_coke
  - stellar_evolution:graphite_electrode
  - stellar_evolution:pvc
  - stellar_evolution:rosin
  - stellar_evolution:cellulose_acetate
  - stellar_evolution:nitrocellulose
---
# 化工总览

煤化工 / 木化工共用现有的焦化炉、气化炉、分馏塔、重整器、合成塔、聚合器、反应器和热室。JEI 里有「化工流程总览」信息页，下面是精简版。

<GameScene zoom="3" background="transparent" interactive={true}>
  <Block id="stellar_evolution:coke_oven_controller" x="0" y="0" z="0" />
  <Block id="stellar_evolution:gasifier" x="3" y="0" z="0" />
  <Block id="stellar_evolution:gasifier_shell" x="4" y="0" z="0" />
  <Block id="stellar_evolution:gasifier_shell" x="3" y="1" z="0" />
  <Block id="stellar_evolution:gasifier_shell" x="4" y="1" z="0" />
  <Block id="stellar_evolution:gasifier_shell" x="3" y="0" z="1" />
  <Block id="stellar_evolution:gasifier_shell" x="4" y="0" z="1" />
  <Block id="stellar_evolution:gasifier_shell" x="3" y="1" z="1" />
  <Block id="stellar_evolution:gasifier_shell" x="4" y="1" z="1" />
  <Block id="stellar_evolution:distillation_tower" x="6" y="0" z="0" />
  <Block id="stellar_evolution:distillation_tower_shell" x="7" y="0" z="0" />
  <Block id="stellar_evolution:distillation_tower_shell" x="6" y="1" z="0" />
  <Block id="stellar_evolution:distillation_tower_shell" x="7" y="1" z="0" />
  <Block id="stellar_evolution:distillation_tower_shell" x="6" y="0" z="1" />
  <Block id="stellar_evolution:distillation_tower_shell" x="7" y="0" z="1" />
  <Block id="stellar_evolution:distillation_tower_shell" x="6" y="1" z="1" />
  <Block id="stellar_evolution:distillation_tower_shell" x="7" y="1" z="1" />
  <Block id="stellar_evolution:synthesis_tower" x="9" y="0" z="0" />
  <Block id="stellar_evolution:synthesis_tower_shell" x="10" y="0" z="0" />
  <Block id="stellar_evolution:synthesis_tower_shell" x="9" y="1" z="0" />
  <Block id="stellar_evolution:synthesis_tower_shell" x="10" y="1" z="0" />
  <Block id="stellar_evolution:synthesis_tower_shell" x="9" y="0" z="1" />
  <Block id="stellar_evolution:synthesis_tower_shell" x="10" y="0" z="1" />
  <Block id="stellar_evolution:synthesis_tower_shell" x="9" y="1" z="1" />
  <Block id="stellar_evolution:synthesis_tower_shell" x="10" y="1" z="1" />
  <BoxAnnotation color="#ee3333" min="0 0 0" max="1 1 1">焦化炉</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="3 0 0" max="5 2 2">气化炉</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="6 0 0" max="8 2 2">分馏塔</BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="9 0 0" max="11 2 2">合成塔</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 煤化工

| 段 | 输入 → 输出 | 机器 |
|---|---|---|
| 干馏 | 煤 ×4 → 焦炭 ×2 + 煤焦油 + 焦炉煤气 | [焦化炉](../machines/industry/coke-oven.md) |
| 气化 | 焦炭 / 煤炭粉 / 煤焦油 + 水 → 合成气；焦炭 / 木炭粉 → 发生炉煤气 | 气化炉 |
| 煤液化 | 煤炭粉 + 水 → 煤水浆 → 煤液化油 → 分馏（后期替代原油） | 调浆机 / 合成塔 / 分馏塔 |
| 焦油精制 | 煤焦油 → 苯 + 苯酚 + 萘 + 沥青 | 分馏塔 |
| 焦化 | 沥青 → 针状焦 + 轻油 | 裂解炉 |
| 石墨化 | 针状焦 → <ItemLink id="graphite_electrode" /> | 热室 |
| 电石 | 焦炭 + 方解石 → <ItemLink id="calcium_carbide" /> | 热室 |
| 乙炔 | 电石 + 水 → 乙炔 + <ItemLink id="calcium_hydroxide" /> | 合成塔 |
| PVC | 乙炔 + 盐酸 → 氯乙烯 → <ItemLink id="pvc" /> | 合成塔 → 聚合器 |
| 合成气延伸 | 合成气 → 净化合成气 / 甲醇 / SNG / 合成航煤 / 尿素 | 重整器 / 合成塔 |

- 活性炭由焦炭 / 煤炭粉 / 木炭粉活化，用于净化合成气与费托催化。
- 煤炭 / 木炭先经破碎机磨粉（1 出 1）；气化、水浆、活性炭都吃粉，不是整块。
- 尿素由氨水 + 二氧化碳合成，可回田作肥料。
- 焦炉煤气是 GAS 燃料，直接烧[联合燃气机组](../machines/generators/combined-cycle-gas-turbine.md)。

## 木化工

| 段 | 输入 → 输出 | 机器 |
|---|---|---|
| 干馏 | 原木 ×4 → 木炭 ×3 + 木焦油 + 木醋液 + 松节油 | [焦化炉](../machines/industry/coke-oven.md) |
| 木焦油分馏 | 木焦油 → 杂酚油 + 沥青 + <ItemLink id="rosin" /> | 分馏塔 |
| 木醋液分馏 | 木醋液 → 醋酸 + 甲醇 + 丙酮 | 分馏塔 |
| 纤维素 | 木屑粉 + 醋酸 → <ItemLink id="cellulose_acetate" />；+ 硝酸 → <ItemLink id="nitrocellulose" /> | 反应器 |
| 糠醛 | 木屑粉 + 硫酸 → 糠醛 → 糠醇 | 反应器 |
| 助焊剂 | 松香 + 乙醇 → 助焊剂 | 反应器 |
| 燃料 | 松节油 → 内燃机 MEDIUM；松节油 + 硝酸 → 松节油-硝酸（火箭） | 合成塔 |

- 木煤气并入发生炉煤气，不单独注册。
- 松节油除木干馏外，还能由「石脑油 + 原木」在合成塔制取（同样不含菌柄）。
- 碳纤维预留给装备改造线，当前不展开。

## 燃料

29 种燃料按等级分给燃烧引擎、工业内燃机组、联合燃气机组、航空航天火箭引擎；完整数值见[燃料表](fuels.md)。

## 示例

- [煤 / 木化工线](../example-setups/coal-wood-line.md)：焦化炉 → 气化炉 → 联合燃气机组。

## 提示

- 煤 / 木两条线共用焦化炉，差别只在输入。
- 没有下游的中间体（醋酸乙烯、杂酚油、糠醛等）先保留，不要硬找用途。
- 具体配方、产率和周期以 JEI 为准。
