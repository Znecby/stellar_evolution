package dev.znecby.stellar_evolution.guide;

import dev.znecby.stellar_evolution.StellarEvolution;
import guideme.Guide;
import net.minecraft.resources.Identifier;

/**
 * GuideME 手册注册。
 *
 * <p>本类直接引用 GuideME 的类，因此只能在确认 {@code guideme} 已加载后再触碰它
 * （见 {@link StellarEvolution} 里的 {@code ModList.isLoaded("guideme")} 守卫）。
 *
 * <p>页面源目录：项目根 {@code guidebook/}；打包时由 {@code processResources} 复制到
 * {@code assets/stellar_evolution/guidebook/}。开发期用 {@code gradlew runGuide} 直接读源目录并热重载。
 */
public final class StellarGuide {

    /** 手册 ID：{@code /guideme give @s stellar_evolution:guide} 用它。 */
    public static final Identifier GUIDE_ID = StellarEvolution.id("guide");
    /** 起始页。 */
    public static final Identifier START_PAGE = StellarEvolution.id("index.md");

    public static void register() {
        Guide.builder(GUIDE_ID)
                .folder("guidebook")
                // 底本为中文：让全文搜索按中文分词索引；英文以后放 _en_us/。
                .defaultLanguage("zh_cn")
                .startPage(START_PAGE)
                .build();
        StellarEvolution.LOGGER.info("GuideME guide registered: {}", GUIDE_ID);
    }

    private StellarGuide() {}
}
