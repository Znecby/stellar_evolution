---
navigation:
  parent: machines/machines-index.md
  title: 机械土壤
  icon: mechanical_farmland
  position: 44
categories:
  - machines
item_ids:
  - stellar_evolution:mechanical_farmland
  - stellar_evolution:mechanical_sand
  - stellar_evolution:mechanical_soul_sand
  - stellar_evolution:mechanical_grass
---
# 机械土壤

四兄弟：机械耕地 / 沙地 / 灵魂沙地 / 草地。通电后自动催熟 + 自动收割，产物进正下方容器。

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:mechanical_farmland" x="0" y="0" z="0" />
  <Block id="minecraft:copper_block" x="1" y="0" z="0" />
  <Block id="minecraft:chest" x="0" y="-1" z="0" />
  <BoxAnnotation color="#5CA7CD" min="1 0 0" max="2 1 1">铜块供能</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="0 -1 0" max="1 0 1">产物落入下方容器</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 接入

- 贴着**铜块**接入热压差网络，不能直接贴铜管。
- 占用：前三种 16 DP @ 8 HP/t；机械草地 32 DP @ 8 HP/t。

## 运行

- 通电后作物 / 植物自动成熟并收割。
- 产物进正下方容器；没有容器就停。
- 机械草地缺底部容器时 Jade 提示「底部缺失容器」。

## 用途

- 生化线的生物质来源。
- 用物流（机械管 / 第三方管道）把产物送进调浆机。

## 注意

- 它不是大机器，不用机械管接自身；机械管接的是它下面的容器。
- 四类土壤共用同一个 `MechanicalSoil` 接口，热压差和 Jade 只认接口。
