/**
 * <strong>固定多方块框架</strong>（2026-11-02 起）。
 *
 * <p>目标：在不引入 GT 整套多方块框架的前提下，支持<strong>静态、固定尺寸</strong>的大型结构。
 * 不支持的：动态模块、可扩展尺寸、上下翻转、轿厢运动改变结构、跨维度运输、JEI 多方块预览。
 *
 * <h2>具体机器接入步骤</h2>
 * <ol>
 *   <li>继承 {@link dev.modmind.stellar_evolution.multiblock.MultiblockControllerBlock}，
 *       实现 {@code newBlockEntity}、{@code blockEntityType()}、{@code codec()}；</li>
 *   <li>继承 {@link dev.modmind.stellar_evolution.multiblock.MultiblockControllerBlockEntity}，
 *       实现 {@code createPattern()}，可选覆盖 {@code onFormed / onUnformed / tickFormed}；</li>
 *   <li>用 {@link dev.modmind.stellar_evolution.multiblock.MultiblockPatternBuilder}
 *       写切片图案；大图案用 {@code repeat(...)}，不要手写几十层；</li>
 *   <li>注册方块 / 方块实体 / 物品 / 模型 / 双语 lang（框架本身不注册任何方块）；</li>
 *   <li>一键搭建工具 {@link dev.modmind.stellar_evolution.multiblock.MultiblockBuilderItem}
 *       对任意控制器生效，不需要每个机器单独实现。</li>
 * </ol>
 *
 * <h2>校验口径</h2>
 * <ul>
 *   <li>放置 / 旋转 / 世界加载 / 区块加载 / 结构内方块变化时校验；</li>
 *   <li>成型后缓存结构坐标；方块变化只排一次延迟校验，不每 tick 全量扫描；</li>
 *   <li>拆任意结构方块 2 tick 内取消成型；补回方块后自动重新校验；</li>
 *   <li>结构可跨区块，但不会常驻强制加载；区块加载事件会安排重新校验。</li>
 * </ul>
 */
package dev.modmind.stellar_evolution.multiblock;
