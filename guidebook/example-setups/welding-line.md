---
navigation:
  parent: example-setups/example-setups-index.md
  title: 焊接线
  icon: welding_machine
  position: 6
---
# 焊接线

最小闭环：**助焊剂（硼砂 + 水）→ 储罐 → 焊接机 → 坚固钨焊接板**；后期氩弧焊再补氩气。

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:welding_machine" x="0" y="0" z="0" />
  <Block id="stellar_evolution:copper_tank" x="0" y="-1" z="0" />
  <Block id="minecraft:chest" x="0" y="1" z="0" />
  <Block id="stellar_evolution:copper_pipe" x="1" y="0" z="0" p:west="true" p:east="true" />
  <Block id="minecraft:copper_block" x="2" y="0" z="0" />
  <Block id="stellar_evolution:heat_engine" x="3" y="0" z="0" />
  <Block id="minecraft:chest" x="3" y="-1" z="0" />
  <BoxAnnotation color="#5CA7CD" min="0 -1 0" max="1 0 1">助焊剂储罐</BoxAnnotation>
  <BoxAnnotation color="#82CD5C" min="0 1 0" max="1 2 1">产物容器</BoxAnnotation>
  <BoxAnnotation color="#ee3333" min="1 0 0" max="4 1 1">热压差供能</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 步骤

1. **助焊剂**：调浆机 `硼砂 ×1 + 水 500 → 助焊剂 500`，灌进焊接机正下方储罐。
2. 焊接机正上方放箱子收产物。
3. 任意面接铜管 / 铜块供能。
4. 打开 GUI，按固定顺序放：**钨板 / 铜板 / 钨板**。
5. 200t 后产出 **坚固钨焊接板 ×1**，罐内助焊剂 -25 mB。

## 氩弧焊

- 坚固不锈钢 / 高速钢焊接板改用**氩气 25 mB**，周期 1200t。
- 氩气来自不锈钢格栅 + 分馏塔空分：液态空气 5 000 → 氮气 3 840 + 氧气 1 050 + 氩气 100 + 氦气 10。
- 氩气与助焊剂可以各用一台焊接机，或换罐切换。

## 材料来源

- 钨板：钨线加工；铜板：压模机。
- 坚固钨焊接板：用于[开采钻](../machines/industry/mining-drill.md)整机（4 块）。
- 坚固铅 / 不锈钢 / 高速钢焊接板：后期机器与装备。

## 常见错误

- 顺序放错（铜板 / 钨板 / 钨板）→ 不工作。
- 正下方不是储罐 → 不工作。
- 正上方不是容器 / 满了 → 不工作。
- 罐里是水 / 氧气而不是助焊剂 → 不推进，界面显示「已有 / 需要」。

## 相关

- [焊接机](../machines/single-block/welding-machine.md)
- [单方块加工机](../machines/single-block/single-block-machines.md)
- [生化线](bio-line.md)
