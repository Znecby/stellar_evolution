package dev.modmind.stellar_evolution.client;

import dev.modmind.stellar_evolution.StellarEvolution;
import dev.modmind.stellar_evolution.stream.StreamType;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IModIngredientRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.TooltipFlag;

/**
 * 粒子流作为 <strong>JEI 一等 ingredient</strong>：可以搜到，也能右键「用途 / 来源」在各分类间跳转。
 *
 * <p>图标是原版岩浆的动画贴图按流类型换色（{@code textures/block/stream_<id>.png} + {@code .mcmeta}，
 * 由 {@code tools/gen_stream_icons.ps1} 生成）。每帧从 blocks 图集取当前 sprite 再画，
 * 因此图标会像熔岩一样持续流动。
 */
@JeiPlugin
public final class StreamJeiPlugin implements IModPlugin {
    /** 自定义 ingredient 类型（UID 唯一，JEI 据此跨分类匹配）。 */
    static final IIngredientType<StreamType> TYPE = new IIngredientType<>() {
        @Override
        public Class<? extends StreamType> getIngredientClass() {
            return StreamType.class;
        }

        @Override
        public String getUid() {
            return "stellar_evolution:stream";
        }
    };

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(StellarEvolution.MOD_ID, "stream_jei");
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        registration.register(TYPE, List.of(StreamType.values()),
                new Helper(), new Renderer(), StreamType.CODEC);
    }

    private static final class Helper implements IIngredientHelper<StreamType> {
        @Override
        public IIngredientType<StreamType> getIngredientType() {
            return TYPE;
        }

        @Override
        public String getDisplayName(StreamType type) {
            return Component.translatable(type.nameKey()).getString();
        }

        @Override
        public Object getUid(StreamType type, UidContext context) {
            return type.id();
        }

        @Override
        public Identifier getIdentifier(StreamType type) {
            return StellarEvolution.id("stream/" + type.id());
        }

        @Override
        public StreamType copyIngredient(StreamType type) {
            return type;
        }

        @Override
        public String getErrorInfo(StreamType type) {
            return String.valueOf(type);
        }
    }

    private static final class Renderer implements IIngredientRenderer<StreamType> {
        private static final int SIZE = 16;

        @Override
        public void render(GuiGraphicsExtractor graphics, StreamType type) {
            render(graphics, type, 0, 0);
        }

        @Override
        public void render(GuiGraphicsExtractor graphics, StreamType type, int x, int y) {
            // MC 26.1 的 AtlasManager 用 AtlasIds.*（minecraft:blocks）作键，
            // 不是 TextureAtlas.LOCATION_BLOCKS（minecraft:textures/atlas/blocks.png），后者会抛 Invalid atlas id。
            TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            TextureAtlasSprite sprite = atlas.getSprite(StellarEvolution.id("block/stream_" + type.id()));
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, SIZE, SIZE);
        }

        @Override
        public List<Component> getTooltip(StreamType type, TooltipFlag flag) {
            return List.of(
                    Component.translatable(type.nameKey()).withStyle(ChatFormatting.AQUA),
                    Component.translatable("stellar_evolution.jei.stream.unit",
                            Component.translatable(type.unitKey())).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public int getWidth() {
            return SIZE;
        }

        @Override
        public int getHeight() {
            return SIZE;
        }
    }
}
