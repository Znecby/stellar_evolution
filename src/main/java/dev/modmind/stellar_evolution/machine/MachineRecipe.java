package dev.modmind.stellar_evolution.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 材料加工机械的一条<strong>内置配方</strong>（输入 → 产物，数量已含在 {@link ItemStack} 里）——
 * 只给 <strong>JEI 摆表</strong>用：配方真值仍在 {@link MachineRecipes} 的三张表里，
 * 这里只是把它们翻成 JEI 认的形状，于是「机器真跑的」与「JEI 显示的」永远一致。
 *
 * @param mold     压模机必需的模具；其它机器为 {@link ItemStack#EMPTY}
 * @param inputTag 输入按物品标签匹配时的标签；{@code null} = 单物品输入
 * @param note     配方附注（例如「不同来源产量不同」）；{@code null} = 无
 */
public record MachineRecipe(ItemStack input, ItemStack output, ItemStack mold,
                            @Nullable TagKey<Item> inputTag, @Nullable Component note) {
    /** 不需要模具 / 标签 / 附注的配方。 */
    public MachineRecipe(ItemStack input, ItemStack output) {
        this(input, output, ItemStack.EMPTY, null, null);
    }

    /** 需要模具、但输入不是标签的配方。 */
    public MachineRecipe(ItemStack input, ItemStack output, ItemStack mold) {
        this(input, output, mold, null, null);
    }
}
