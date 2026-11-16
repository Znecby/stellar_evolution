---
navigation:
  parent: reference/reference-index.md
  title: 命令
  icon: minecraft:command_block
  position: 2
---
# 命令

## 热压差

- `/se thermal`：以准星方块为起点扫描热压差网络，列出生成端、容量、热流率、消耗端、占用、状态。

## 大机器

- `/se large`：瞄机器或物流容器，查看机器名、热压差接入、容器清单和 IO 方向。
- `/se large recipes`：列出当前机器可用的配方。

## 石化 / 煤化工

- `/se petro recipes`：逐机种列出石化 / 煤化工配方（含焦化炉）。
- `/se petro machine`：瞄石化机器，查看机种、状态与进度。

## 高能物理

- `/se hep recipes`：列出回旋加速器配方。
- `/se hep machine`：瞄回旋加速器，查看状态与粒子数。

## 手册

- `/guideme give @s stellar_evolution:guide`：给自己一本手册（需要 OP）。
- `/guidemec open stellar_evolution:guide`：客户端直接打开手册。
- `/guidemec open stellar_evolution:guide machines/common-large-machine.md`：打开指定页。
- `/guidemec export`：导出静态站点到 `guideme_exports/`（客户端）。

## 提示

- 物品 tooltip 上按住 **G** 可以跳到对应页，不需要命令。
- `/se` 命令都支持中英文反馈。
