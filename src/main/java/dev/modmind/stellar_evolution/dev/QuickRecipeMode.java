package dev.modmind.stellar_evolution.dev;

/** 快速配方书的记录模式：工作台 / 热室。 */
public enum QuickRecipeMode {
    CRAFTING_TABLE("crafting_table",
            "screen.stellar_evolution.quick_recipe",
            "screen.stellar_evolution.quick_recipe.hint"),
    HOT_CHAMBER("hot_chamber",
            "screen.stellar_evolution.quick_recipe.hot_chamber",
            "screen.stellar_evolution.quick_recipe.hot_chamber.hint");

    private final String id;
    private final String titleKey;
    private final String hintKey;

    QuickRecipeMode(String id, String titleKey, String hintKey) {
        this.id = id;
        this.titleKey = titleKey;
        this.hintKey = hintKey;
    }

    /** 写进文本的稳定 id。 */
    public String id() {
        return this.id;
    }

    public String titleKey() {
        return this.titleKey;
    }

    public String hintKey() {
        return this.hintKey;
    }
}
