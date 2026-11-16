---
navigation:
  parent: mechanics/thermal/thermal-index.md
  title: 连接规则
  icon: minecraft:copper_block
  position: 2
item_ids:
  - stellar_evolution:copper_pipe
  - stellar_evolution:flow_limiter
---
# 连接规则

热压差网络按**方块相邻**做 6 面连通，和管臂外观无关。

## 算网络成员

| 方块 | 作用 |
|---|---|
| <ItemLink id="copper_pipe" /> | 传递件，只导通 |
| 铜块 | 中间导体，只导通 |
| <ItemLink id="flow_limiter" /> | 闸门，只导通 |
| 贴着铜块的消耗端 | 记账 |
| 实现了热压差角色的大机器 | 记账；填充格也算机器本体 |
| 机械物流仓（焦化炉 / 质量炮） | 代理所在多方块记账；控制器本身不算 |
| 单方块热压差机器 | 任意面贴铜管或铜块即可（破碎机 / 压模机 / 轧线 / 钢炉 / 焊接机 / 开采钻） |

## 不算网络成员

- <ItemLink id="machine_pipe" />
- <ItemLink id="pressure_pipe" />
- <ItemLink id="beam_pipe" />

## 生成端 / 消耗端的接法不同

- **消耗端大机器**：任意一格、任意一面贴**铜块**，**或**在底排贴**铜管**直接接入。
- **固定多方块**（焦化炉 / 质量炮）：铜块或底排铜管贴**机械物流仓**；控制器本身不是接入点。
- **生成端（蒸汽涡轮机组）**：**底排铜管**或铜块都可以。
- **小型消耗端**（熔炉 / 木桶 / 机械土壤 / 热室）：必须贴着**铜块**，不能直接贴铜管。
- **单方块热压差机器**：任意面贴铜管或铜块即可；它们有内部 3×3 料仓，不走机械管。
- **储罐不接铜管**：储罐只走机械管 / 流体管道，不进热压差网络。

<GameScene zoom="5" background="transparent" interactive={true}>
  <Block id="stellar_evolution:heat_engine" x="0" y="0" z="0" />
  <Block id="minecraft:chest" x="0" y="-1" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="1" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:copper_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="minecraft:copper_block" x="3" y="0" z="0" />
  <Block id="minecraft:furnace" x="4" y="0" z="0" p:lit="true" />
  <Block id="minecraft:barrel" x="3" y="1" z="0" />
  <BoxAnnotation color="#5CA7CD" min="3 0 0" max="4 1 1">铜块</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="4 0 0" max="5 1 1">消耗端</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="3 1 0" max="4 2 1">木桶也贴铜块</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 常见错误

- 熔炉直接贴铜管 → 不算接入（小型消耗端必须贴铜块）。
- 大机器只贴了侧面中层 / 顶面的铜管 → 不算接入（大机器只认底排铜管或铜块）。
- 大机器旁边贴了铜块，但铜块没接铜管 → 网络是孤立的。
- 用机械管给机器“供能” → 机械管只走物流。
- 以为剪刀切断管臂会断开网络 → 网络只看方块相邻，管臂外观不影响。

相关内容：[负载与超载](load-and-overload.md) · [消耗端](consumers.md)
