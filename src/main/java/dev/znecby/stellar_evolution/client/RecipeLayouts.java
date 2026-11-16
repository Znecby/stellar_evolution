package dev.znecby.stellar_evolution.client;

import dev.znecby.stellar_evolution.bio.BioRecipe;
import dev.znecby.stellar_evolution.fission.FissionRecipe;
import dev.znecby.stellar_evolution.hep.CyclotronRecipe;
import dev.znecby.stellar_evolution.machine.MachineRecipe;
import dev.znecby.stellar_evolution.particle.BeamRecipes;
import dev.znecby.stellar_evolution.particle.ParticleComponents;
import dev.znecby.stellar_evolution.particle.ParticleItems;
import dev.znecby.stellar_evolution.particle.ParticleType;
import dev.znecby.stellar_evolution.petro.PetroRecipe;
import dev.znecby.stellar_evolution.petro.PetroRecipes;
import dev.znecby.stellar_evolution.thermal.HotChamberRecipe;
import dev.znecby.stellar_evolution.welding.WeldingRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 各条产线在 JEI 里的<strong>版面数据</strong>（文字行 + 输入 / 输出槽），交给
 * {@link RecipePanelCategory} 统一排版。
 *
 * <p>文字口径照旧：生化写「条件 + 时长」，其余写「时长」；槽位一律「物品在前、流体在后」。
 */
final class RecipeLayouts {
    /** 材料加工机械：一件进、一件出；标签输入合并为一个槽，压模机额外显示必需模具。 */
    static final RecipePanelCategory.Layout<MachineRecipe> MACHINE = new RecipePanelCategory.Layout<>() {
        @Override
        public List<Component> lines(MachineRecipe recipe) {
            List<Component> lines = new ArrayList<>();
            if (recipe.note() != null) {
                lines.add(recipe.note());
            }
            if (!recipe.mold().isEmpty()) {
                lines.add(Component.translatable("stellar_evolution.jei.machine.mold_not_consumed"));
            }
            return lines;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> inputs(MachineRecipe recipe) {
            List<RecipePanelCategory.Ingredient> list = new ArrayList<>();
            if (recipe.inputTag() != null) {
                list.add(RecipePanelCategory.Ingredient.items(
                        tagStacks(recipe.inputTag(), recipe.input().getCount(), recipe.input())));
            } else {
                list.add(RecipePanelCategory.Ingredient.item(recipe.input()));
            }
            if (!recipe.mold().isEmpty()) {
                list.add(RecipePanelCategory.Ingredient.item(recipe.mold()));
            }
            return list;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> outputs(MachineRecipe recipe) {
            return List.of(RecipePanelCategory.Ingredient.item(recipe.output()));
        }
    };

    /** 焊接机：固定顺序的物品输入（最多 3）+ 可选 1 种流体。 */
    static final RecipePanelCategory.Layout<WeldingRecipes.Recipe> WELDING = new RecipePanelCategory.Layout<>() {
        @Override
        public List<Component> lines(WeldingRecipes.Recipe recipe) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("stellar_evolution.jei.welding.duration", recipe.duration()));
            if (recipe.minFlowRate() > 0.0D) {
                lines.add(Component.translatable("stellar_evolution.jei.welding.min_hp",
                        (int) recipe.minFlowRate()));
            }
            if (Math.abs(recipe.baseFlowRate() - 64.0D) > 1.0E-6D) {
                lines.add(Component.translatable("stellar_evolution.jei.welding.base_hp",
                        (int) recipe.baseFlowRate()));
            }
            return lines;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> inputs(WeldingRecipes.Recipe recipe) {
            List<RecipePanelCategory.Ingredient> list = new ArrayList<>();
            for (ItemStack stack : recipe.items()) {
                list.add(RecipePanelCategory.Ingredient.item(stack));
            }
            if (!recipe.fluid().isEmpty()) {
                list.add(RecipePanelCategory.Ingredient.fluid(recipe.fluid()));
            }
            return list;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> outputs(WeldingRecipes.Recipe recipe) {
            return List.of(RecipePanelCategory.Ingredient.item(recipe.output()));
        }
    };

    /** 热室：只吃物品，没有流体；标签输入在一个槽里轮换显示全部可选物品。 */
    static final RecipePanelCategory.Layout<HotChamberRecipe> HOT_CHAMBER = new RecipePanelCategory.Layout<>() {
        @Override
        public List<Component> lines(HotChamberRecipe recipe) {
            return List.of(
                    Component.translatable("stellar_evolution.jei.hot_chamber.duration", recipe.duration()),
                    Component.translatable("stellar_evolution.jei.hot_chamber.pressure", recipe.pressure()));
        }

        @Override
        public List<RecipePanelCategory.Ingredient> inputs(HotChamberRecipe recipe) {
            List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
            for (HotChamberRecipe.TagInput tag : recipe.tagInputs()) {
                out.add(RecipePanelCategory.Ingredient.items(tagStacks(tag)));
            }
            out.addAll(items(recipe.itemInputs()));
            return out;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> outputs(HotChamberRecipe recipe) {
            return items(recipe.itemOutputs());
        }
    };

    /** 标签输入 → 全部成员（每个按需求数量），JEI 槽内轮换显示。 */
    private static List<ItemStack> tagStacks(TagKey<Item> tag, int count, ItemStack display) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            stacks.add(new ItemStack(holder.value(), count));
        }
        if (stacks.isEmpty()) {
            stacks.add(display.copyWithCount(count));
        }
        return stacks;
    }

    /** 标签输入 → 全部成员（每个按需求数量），JEI 槽内轮换显示。 */
    private static List<ItemStack> tagStacks(HotChamberRecipe.TagInput tag) {
        return tagStacks(tag.tag(), tag.count(), tag.display());
    }

    /** 生化：条件行 + 时长行 + 催化剂行；物品 / 流体混排。 */
    static final RecipePanelCategory.Layout<BioRecipe> BIO = new RecipePanelCategory.Layout<>() {
        @Override
        public List<Component> lines(BioRecipe recipe) {
            String conditions = recipe.describeConditions();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(conditions.isEmpty() ? "无条件" : conditions));
            lines.add(Component.literal("时长 " + recipe.baseTicks() + " t"));
            if (recipe.catalyst() != null) {
                lines.add(Component.translatable("stellar_evolution.jei.petro.catalyst",
                        new ItemStack(recipe.catalyst()).getHoverName()));
            }
            return lines;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> inputs(BioRecipe recipe) {
            List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
            if (recipe.catalyst() != null) {
                out.add(RecipePanelCategory.Ingredient.item(new ItemStack(recipe.catalyst())));
            }
            for (BioRecipe.ItemIngredient input : recipe.itemInputs()) {
                out.add(RecipePanelCategory.Ingredient.item(input.stack()));
            }
            for (BioRecipe.FluidIngredient input : recipe.fluidInputs()) {
                out.add(RecipePanelCategory.Ingredient.fluid(input.stack()));
            }
            return out;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> outputs(BioRecipe recipe) {
            List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
            for (BioRecipe.ItemIngredient output : recipe.itemOutputs()) {
                out.add(RecipePanelCategory.Ingredient.item(output.stack(), output.chance()));
            }
            for (BioRecipe.FluidIngredient output : recipe.fluidOutputs()) {
                out.add(RecipePanelCategory.Ingredient.fluid(output.stack()));
            }
            return out;
        }
    };

    /** 石化：时长行；物品 / 流体混排（不显示备注文案）。 */
    static final RecipePanelCategory.Layout<PetroRecipe> PETRO = new RecipePanelCategory.Layout<>() {
        @Override
        public List<Component> lines(PetroRecipe recipe) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("时长 " + PetroRecipes.DEFAULT_TICKS + " t"));
            if (recipe.catalyst() != null) {
                lines.add(Component.translatable("stellar_evolution.jei.petro.catalyst",
                        new ItemStack(recipe.catalyst()).getHoverName()));
            }
            return lines;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> inputs(PetroRecipe recipe) {
            List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
            if (recipe.catalyst() != null) {
                out.add(RecipePanelCategory.Ingredient.item(new ItemStack(recipe.catalyst())));
            }
            for (PetroRecipe.ItemIngredient input : recipe.itemInputs()) {
                if (input.tag() != null) {
                    out.add(RecipePanelCategory.Ingredient.items(
                            tagStacks(input.tag(), input.count(), input.stack())));
                } else {
                    out.add(RecipePanelCategory.Ingredient.item(input.stack()));
                }
            }
            for (PetroRecipe.FluidIngredient input : recipe.fluidInputs()) {
                out.add(RecipePanelCategory.Ingredient.fluid(input.stack()));
            }
            return out;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> outputs(PetroRecipe recipe) {
            List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
            for (PetroRecipe.ItemIngredient output : recipe.itemOutputs()) {
                out.add(RecipePanelCategory.Ingredient.item(output.stack()));
            }
            for (PetroRecipe.FluidIngredient output : recipe.fluidOutputs()) {
                out.add(RecipePanelCategory.Ingredient.fluid(output.stack()));
            }
            return out;
        }
    };

    /** 裂变：时长行 + 温度带行（罐装机）；物品 / 流体混排。 */
    static final RecipePanelCategory.Layout<FissionRecipe> FISSION = new RecipePanelCategory.Layout<>() {
        @Override
        public List<Component> lines(FissionRecipe recipe) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("时长 " + recipe.ticks() + " t"));
            if (recipe.requiredBand() != null) {
                String band = switch (recipe.requiredBand()) {
                    case ANAEROBIC -> "厌氧（供能铜块 1 块）";
                    case MESOPHILIC -> "中温（供能铜块 2–3 块）";
                    case THERMOPHILIC -> "高温（供能铜块 ≥4 块）";
                };
                lines.add(Component.literal("温度带：" + band));
            }
            return lines;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> inputs(FissionRecipe recipe) {
            List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
            for (FissionRecipe.ItemIngredient input : recipe.itemInputs()) {
                out.add(RecipePanelCategory.Ingredient.item(input.stack()));
            }
            for (FissionRecipe.FluidIngredient input : recipe.fluidInputs()) {
                out.add(RecipePanelCategory.Ingredient.fluid(input.stack()));
            }
            return out;
        }

        @Override
        public List<RecipePanelCategory.Ingredient> outputs(FissionRecipe recipe) {
            List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
            for (FissionRecipe.ItemIngredient output : recipe.itemOutputs()) {
                out.add(RecipePanelCategory.Ingredient.item(output.stack()));
            }
            for (FissionRecipe.FluidIngredient output : recipe.fluidOutputs()) {
                out.add(RecipePanelCategory.Ingredient.fluid(output.stack()));
            }
            return out;
        }
    };

    private static List<RecipePanelCategory.Ingredient> items(List<net.minecraft.world.item.ItemStack> stacks) {
        List<RecipePanelCategory.Ingredient> out = new ArrayList<>(stacks.size());
        for (net.minecraft.world.item.ItemStack stack : stacks) {
            out.add(RecipePanelCategory.Ingredient.item(stack));
        }
        return out;
    }

    /** 回旋加速器：粉 + 差速需求 → 物品 / 流体。 */
    static final RecipePanelCategory.Layout<CyclotronRecipe> CYCLOTRON = new RecipePanelCategory.Layout<>() {
        @Override
        public List<Component> lines(CyclotronRecipe recipe) {
            return List.of(
                    Component.literal("需求差速 " + (long) recipe.demand()),
                    Component.literal("2048 HP 下约 " + Math.round(3.2D * recipe.demand() / 2_048.0D) + " 秒"),
                    Component.literal("单轮最多 64 个"));
        }

        @Override
        public List<RecipePanelCategory.Ingredient> inputs(CyclotronRecipe recipe) {
            return List.of(RecipePanelCategory.Ingredient.item(
                    new ItemStack(recipe.input(), recipe.inputCount())));
        }

        @Override
        public List<RecipePanelCategory.Ingredient> outputs(CyclotronRecipe recipe) {
            List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
            for (CyclotronRecipe.ItemOutput item : recipe.itemOutputs()) {
                out.add(RecipePanelCategory.Ingredient.item(new ItemStack(item.item(), item.count())));
            }
            for (CyclotronRecipe.FluidOutput fluid : recipe.fluidOutputs()) {
                out.add(RecipePanelCategory.Ingredient.fluid(new FluidStack(fluid.fluid(), fluid.amount())));
            }
            return out;
        }
    };

    /** 粒子源：带粒子类型的压力罐 → 空罐 + 束流。 */
    static final RecipePanelCategory.Layout<ParticleType> PARTICLE_SOURCE = new RecipePanelCategory.Layout<>() {
        @Override
        public List<Component> lines(ParticleType type) {
            return List.of(
                    Component.literal("输出 " + type.rate() + " /t"),
                    Component.literal("要求 DP " + (long) type.requiredDp()
                            + " · HP " + (long) type.requiredHp()),
                    Component.literal("压力罐耐久 1000"));
        }

        @Override
        public List<RecipePanelCategory.Ingredient> inputs(ParticleType type) {
            ItemStack tank = new ItemStack(ParticleItems.PRESSURE_TANK.get());
            tank.set(ParticleComponents.PARTICLE_TYPE.get(), type);
            return List.of(RecipePanelCategory.Ingredient.item(tank));
        }

        @Override
        public List<RecipePanelCategory.Ingredient> outputs(ParticleType type) {
            return List.of(RecipePanelCategory.Ingredient.stream(type.stream()));
        }
    };

    /** 束流机器（辐照仓 / 对撞机）：靶料 + 一路或两路粒子流 → 产物（对撞机还会喷出输出流）。 */
    static final RecipePanelCategory.Layout<BeamRecipes.Recipe> BEAM =
            new RecipePanelCategory.Layout<>() {
                @Override
                public List<Component> lines(BeamRecipes.Recipe recipe) {
                    List<Component> lines = new ArrayList<>();
                    for (BeamRecipes.StreamDemand demand : recipe.demands()) {
                        lines.add(Component.translatable(demand.type().nameKey())
                                .append(Component.literal(" " + (long) demand.required() + " 单位（"
                                        + demand.demand() + " /t）")));
                    }
                    if (recipe.outputStream() != null) {
                        lines.add(Component.literal("输出 ")
                                .append(Component.translatable(recipe.outputStream().nameKey()))
                                .append(Component.literal(" " + recipe.outputRate() + " /t")));
                    }
                    if (recipe.minRingLevel() > 0) {
                        lines.add(Component.literal("需要加速环段 ×" + recipe.minRingLevel()));
                    }
                    return lines;
                }

                @Override
                public List<RecipePanelCategory.Ingredient> inputs(BeamRecipes.Recipe recipe) {
                    List<RecipePanelCategory.Ingredient> in = new ArrayList<>();
                    if (recipe.input() != null) {
                        in.add(RecipePanelCategory.Ingredient.item(
                                new ItemStack(recipe.input(), recipe.inputCount())));
                    }
                    for (BeamRecipes.FluidInput input : recipe.fluidInputs()) {
                        in.add(RecipePanelCategory.Ingredient.fluid(
                                new net.neoforged.neoforge.fluids.FluidStack(input.fluid(), input.amount())));
                    }
                    for (BeamRecipes.StreamDemand demand : recipe.demands()) {
                        in.add(RecipePanelCategory.Ingredient.stream(demand.type()));
                    }
                    return in;
                }

                @Override
                public List<RecipePanelCategory.Ingredient> outputs(BeamRecipes.Recipe recipe) {
                    List<RecipePanelCategory.Ingredient> out = new ArrayList<>();
                    for (BeamRecipes.Output output : recipe.outputs()) {
                        out.add(RecipePanelCategory.Ingredient.item(
                                new ItemStack(output.item(), output.count())));
                    }
                    for (BeamRecipes.FluidOutput output : recipe.fluidOutputs()) {
                        out.add(RecipePanelCategory.Ingredient.fluid(
                                new FluidStack(output.fluid(), output.amount())));
                    }
                    return out;
                }
            };

    private RecipeLayouts() {}
}
