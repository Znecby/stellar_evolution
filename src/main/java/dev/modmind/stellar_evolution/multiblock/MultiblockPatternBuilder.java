package dev.modmind.stellar_evolution.multiblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * 固定多方块图案构造器。
 *
 * <p>用法（具体机器在 {@code createPattern()} 里调用）：
 * <pre>{@code
 * return new MultiblockPatternBuilder("example")
 *         .whereController('C', MY_CONTROLLER)
 *         .where('X', CASING)
 *         .whereAir(' ')
 *         .wherePort('I', MultiblockPartRole.ITEM_INPUT, ITEM_INPUT_PORT)
 *         .layer("XXX", "X X", "XCX")
 *         .repeat(3, "XXX", "X X", "XXX")
 *         .build();
 * }</pre>
 *
 * <p>约定：每一层从下往上；每层的第一行是局部 z=0，行内第一个字符是局部 x=0。
 * 控制器字符必须恰好出现一次；局部坐标最终以控制器为原点。
 *
 * <p>大图案用 {@link #repeat(int, String...)} 生成重复层，不要手写几十层切片。
 */
public final class MultiblockPatternBuilder {
    private final String id;
    private final List<String[]> layers = new ArrayList<>();
    private final Map<Character, MultiblockCell> mappings = new HashMap<>();
    private Character controllerSymbol;
    private boolean skipAir;

    public MultiblockPatternBuilder(String id) {
        this.id = id;
    }

    /** 追加一层（从下往上）。每个字符串是一行（z 从 0 开始）。 */
    public MultiblockPatternBuilder layer(String... rows) {
        if (rows == null || rows.length == 0) {
            throw new IllegalArgumentException("层不能为空: " + id);
        }
        layers.add(rows.clone());
        return this;
    }

    /** 连续追加 {@code count} 层相同图案。 */
    public MultiblockPatternBuilder repeat(int count, String... rows) {
        if (count <= 0) {
            throw new IllegalArgumentException("重复次数必须 > 0: " + id);
        }
        for (int i = 0; i < count; i++) {
            layer(rows);
        }
        return this;
    }

    /** 一个符号匹配一个或多个方块。 */
    public MultiblockPatternBuilder where(char symbol, Block... blocks) {
        requireBlocks(blocks);
        mappings.put(symbol, blocks.length == 1
                ? new MultiblockCell.BlockCell(blocks[0])
                : new MultiblockCell.AnyBlockCell(Set.of(blocks)));
        return this;
    }

    /** 空格 / 指定符号必须是空气。 */
    public MultiblockPatternBuilder whereAir(char symbol) {
        mappings.put(symbol, new MultiblockCell.AirCell());
        return this;
    }

    /**
     * 忽略图案里的空气格（空格）：只为实体方块建期望格。
     *
     * <p>大图案整块包围盒可能有几万格空气，逐格校验没有意义；忽略后校验只跑实体格，
     * 一键搭建工具也只会摆放实体格。
     */
    public MultiblockPatternBuilder skipAir() {
        this.skipAir = true;
        return this;
    }

    /** 控制器格；必须恰好出现一次。 */
    public MultiblockPatternBuilder whereController(char symbol, Block controllerBlock) {
        if (controllerBlock == null) {
            throw new IllegalArgumentException("控制器方块不能为 null: " + id);
        }
        controllerSymbol = symbol;
        mappings.put(symbol, new MultiblockCell.ControllerCell(controllerBlock));
        return this;
    }

    /** 固定端口格：位置、角色、可选方块全部写死。 */
    public MultiblockPattern build() {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("图案至少需要一层: " + id);
        }
        if (controllerSymbol == null) {
            throw new IllegalArgumentException("图案缺少控制器符号（whereController）: " + id);
        }

        int depth = layers.get(0).length;
        int width = layers.get(0)[0].length();
        for (int y = 0; y < layers.size(); y++) {
            String[] rows = layers.get(y);
            if (rows.length != depth) {
                throw new IllegalArgumentException("第 " + y + " 层行数不一致: " + id);
            }
            for (String row : rows) {
                if (row.length() != width) {
                    throw new IllegalArgumentException("第 " + y + " 层行宽不一致: " + id);
                }
            }
        }

        int controllerX = -1;
        int controllerY = -1;
        int controllerZ = -1;
        for (int y = 0; y < layers.size(); y++) {
            String[] rows = layers.get(y);
            for (int z = 0; z < rows.length; z++) {
                int x = rows[z].indexOf(controllerSymbol);
                while (x >= 0) {
                    if (controllerX >= 0) {
                        throw new IllegalArgumentException("控制器符号出现多次: " + id);
                    }
                    controllerX = x;
                    controllerY = y;
                    controllerZ = z;
                    x = rows[z].indexOf(controllerSymbol, x + 1);
                }
            }
        }
        if (controllerX < 0) {
            throw new IllegalArgumentException("图案里找不到控制器符号: " + id);
        }

        Map<BlockPos, MultiblockCell> cells = new LinkedHashMap<>();
        for (int y = 0; y < layers.size(); y++) {
            String[] rows = layers.get(y);
            for (int z = 0; z < rows.length; z++) {
                String row = rows[z];
                for (int x = 0; x < row.length(); x++) {
                    char symbol = row.charAt(x);
                    MultiblockCell cell = mappings.get(symbol);
                    if (cell == null) {
                        if (symbol == ' ') {
                            if (skipAir) {
                                continue;
                            }
                            cell = new MultiblockCell.AirCell();
                        } else {
                            throw new IllegalArgumentException("符号 '" + symbol + "' 没有映射: " + id);
                        }
                    }
                    BlockPos local = new BlockPos(x - controllerX, y - controllerY, z - controllerZ);
                    if (cells.put(local, cell) != null) {
                        throw new IllegalArgumentException("局部坐标重复: " + local + " (" + id + ")");
                    }
                }
            }
        }
        return new MultiblockPattern(id, cells);
    }

    private void requireBlocks(Block[] blocks) {
        if (blocks == null || blocks.length == 0) {
            throw new IllegalArgumentException("方块列表不能为空: " + id);
        }
        for (Block block : blocks) {
            if (block == null) {
                throw new IllegalArgumentException("方块不能为 null: " + id);
            }
        }
    }
}
