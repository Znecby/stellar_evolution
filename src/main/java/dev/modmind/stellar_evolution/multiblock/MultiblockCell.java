package dev.modmind.stellar_evolution.multiblock;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 固定多方块图案里的一格期望。
 *
 * <p>所有实现都是不可变值对象；图案在构造时一次性建好，校验时只做 {@link #matches(BlockState)}。
 * 端口格额外带 {@link MultiblockPartRole}，控制器成型后按角色收集对应的方块实体。
 */
public interface MultiblockCell {
    boolean matches(BlockState state);

    /** 给 Jade / 聊天诊断用的可读描述。 */
    String describe();

    static String blockName(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    record BlockCell(Block block) implements MultiblockCell {
        @Override
        public boolean matches(BlockState state) {
            return state.getBlock() == block;
        }

        @Override
        public String describe() {
            return "方块 " + blockName(block);
        }
    }

    record AnyBlockCell(Set<Block> blocks) implements MultiblockCell {
        public AnyBlockCell {
            blocks = Set.copyOf(blocks);
        }

        @Override
        public boolean matches(BlockState state) {
            return blocks.contains(state.getBlock());
        }

        @Override
        public String describe() {
            return "方块 " + blocks.stream().map(MultiblockCell::blockName).sorted()
                    .collect(Collectors.joining(" / "));
        }
    }

    record AirCell() implements MultiblockCell {
        @Override
        public boolean matches(BlockState state) {
            return state.isAir();
        }

        @Override
        public String describe() {
            return "空气";
        }
    }

    record ControllerCell(Block block) implements MultiblockCell {
        @Override
        public boolean matches(BlockState state) {
            return state.getBlock() == block;
        }

        @Override
        public String describe() {
            return "控制器";
        }
    }
}
