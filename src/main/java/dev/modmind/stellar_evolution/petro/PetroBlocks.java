package dev.modmind.stellar_evolution.petro;

import dev.modmind.stellar_evolution.StellarEvolution;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 石化线的<strong>物质方块</strong>注册表 —— 只剩<strong>原油流体方块</strong>（世界石油湖用）。
 *
 * <p>旧的油砂岩方块已删除：原油统一由石油湖世界生成 / 流体方块提供。
 * 机器方块在 {@link PetroMachineBlocks}（2×2×2 整机 + 大机器框架）。
 */
public final class PetroBlocks {
    public static final String CRUDE_OIL = "crude_oil";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StellarEvolution.MOD_ID);

    /**
     * 原油流体方块：世界生成「石油湖」用（属性照水：可流、可载船、会淹溺）。
     * <p>方块本身无物品、无掉落；取油走桶 / 储罐。
     */
    public static final DeferredBlock<LiquidBlock> CRUDE_OIL_BLOCK =
            BLOCKS.register(CRUDE_OIL, () -> new LiquidBlock(PetroFluids.CRUDE_OIL.source().get(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .replaceable()
                            .noCollision()
                            .strength(100.0F)
                            .pushReaction(PushReaction.DESTROY)
                            .noLootTable()
                            .liquid()
                            .sound(SoundType.EMPTY)
                            .setId(ResourceKey.create(Registries.BLOCK, StellarEvolution.id(CRUDE_OIL)))));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private PetroBlocks() {}
}
