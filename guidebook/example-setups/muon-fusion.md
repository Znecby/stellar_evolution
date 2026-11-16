---
navigation:
  parent: example-setups/example-setups-index.md
  title: 缪子聚变
  icon: muon_fusion
  position: 5
---
# 缪子聚变

最小骨架：

<GameScene zoom="4" background="transparent" interactive={true}>
  <Block id="stellar_evolution:muon_fusion" x="0" y="0" z="0" />
  <Block id="stellar_evolution:machine_pipe" x="2" y="0" z="1" p:east="true" p:west="true" />
  <Block id="stellar_evolution:reaction_chamber" x="3" y="0" z="1" />
  <Block id="stellar_evolution:pressure_pipe" x="2" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:pressure_pipe" x="3" y="0" z="0" p:east="true" p:west="true" />
  <Block id="stellar_evolution:steam_turbine_unit" x="4" y="0" z="0" />
  <Block id="stellar_evolution:beam_pipe" x="2" y="1" z="1" p:stream="0" p:east="true" p:west="true" />
  <BoxAnnotation color="#82CD5C" min="2 0 1" max="4 1 2">反应仓</BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="2 0 0" max="4 1 1">压力管</BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 步骤

1. 搭 <ItemLink id="muon_fusion" />，用 <ItemLink id="machine_pipe" /> 把 <ItemLink id="reaction_chamber" /> 接到聚变仪，放入聚变芯核。
2. 用铜块或底排铜管接热压差网络，先保证能点火（逐级升压）。
3. 接冷却水：物流输入罐或铜格栅。
4. 底排接 <ItemLink id="pressure_pipe" /> 去蒸汽涡轮机组。
5. 束流管接缪子流输入；聚变仪副产的中子流可以再接去辐照仓 / 其它消费者。

## 注意

- 缺水 → 不出蒸汽，稳定值快速下跌。
- 稳定值低于 20 → 停机并上锁，取出芯核再放回才能重新点火。
- 芯核耐久归零 → 销毁。
- 正常自热上限 6000℃，低于 7000℃ 熔毁线。
