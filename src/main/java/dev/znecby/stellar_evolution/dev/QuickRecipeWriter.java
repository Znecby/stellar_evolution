package dev.znecby.stellar_evolution.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 快速配方添加书的文件写入器。
 *
 * <p>文件写到<strong>项目 docs 目录</strong>（不是模组 jar 内）：{@code docs/quick_recipes/quick_recipe_<时间>.txt}。
 * 记录模式（工作台 / 热室）、3×3 输入槽 + 1 个输出槽；只用于开发期记录配方，读取后应删除。
 */
public final class QuickRecipeWriter {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    public static void save(ServerPlayer player, QuickRecipeMenu menu) {
        if (!DevConfig.developerMode()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < QuickRecipeMenu.SLOT_COUNT; i++) {
            ItemStack stack = menu.container().getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        if (stacks.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("message.stellar_evolution.quick_recipe.empty"), false);
            return;
        }

        Path dir = resolveDocsDir(level);
        if (dir == null) {
            player.sendSystemMessage(
                    Component.translatable("message.stellar_evolution.quick_recipe.failed"), false);
            return;
        }

        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("quick_recipe_" + LocalDateTime.now().format(STAMP) + ".txt");
            Files.writeString(file, format(player, menu), StandardCharsets.UTF_8);
            player.sendSystemMessage(
                    Component.translatable("message.stellar_evolution.quick_recipe.saved", file.toString()), false);
        } catch (IOException exception) {
            player.sendSystemMessage(
                    Component.translatable("message.stellar_evolution.quick_recipe.failed"), false);
        }
    }

    private static String format(ServerPlayer player, QuickRecipeMenu menu) {
        StringBuilder text = new StringBuilder();
        text.append("# Stellar Evolution quick recipe\n");
        text.append("# saved: ").append(LocalDateTime.now()).append('\n');
        text.append("# player: ").append(player.getGameProfile().name()).append('\n');
        text.append("# mode: ").append(menu.mode().id()).append('\n');
        text.append("# grid: slots 0..8 = 3x3 inputs (left-to-right, top-to-bottom); slot 9 = output\n");
        text.append("# inputs:");
        boolean first = true;
        for (int i = 0; i < QuickRecipeMenu.GRID_SIZE; i++) {
            ItemStack stack = menu.container().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            text.append(first ? " " : "; ").append(itemId(stack)).append(" x").append(stack.getCount());
            first = false;
        }
        text.append('\n');
        ItemStack output = menu.container().getItem(QuickRecipeMenu.OUTPUT_SLOT);
        text.append("# output: ")
                .append(output.isEmpty() ? "(empty)" : itemId(output) + " x" + output.getCount())
                .append('\n');
        text.append("# ----------------------------------------\n");
        for (int i = 0; i < QuickRecipeMenu.SLOT_COUNT; i++) {
            ItemStack stack = menu.container().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            text.append("slot ").append(i).append(" = ").append(itemId(stack))
                    .append(" x").append(stack.getCount()).append('\n');
        }
        text.append("# dev-only; delete after reading.\n");
        return text.toString();
    }

    private static String itemId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString();
    }

    /** 从游戏目录 / 工作目录向上找项目根下的 docs；找不到返回 null。 */
    private static Path resolveDocsDir(ServerLevel level) {
        Path directory = level.getServer().getServerDirectory().toAbsolutePath();
        Path found = findDocs(directory);
        if (found != null) {
            return found.resolve("quick_recipes");
        }
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        found = findDocs(workingDir);
        return found == null ? null : found.resolve("quick_recipes");
    }

    private static Path findDocs(Path start) {
        Path directory = start;
        for (int i = 0; i < 5 && directory != null; i++) {
            Path docs = directory.resolve("docs");
            if (Files.isDirectory(docs)) {
                return docs;
            }
            directory = directory.getParent();
        }
        return null;
    }

    private QuickRecipeWriter() {}
}
