package dev.modmind.stellar_evolution.massdriver;

import dev.modmind.stellar_evolution.hatch.HatchBlocks;
import dev.modmind.stellar_evolution.multiblock.MultiblockPattern;
import dev.modmind.stellar_evolution.multiblock.MultiblockPatternBuilder;

/**
 * 质量投射轨道炮固定图案（客户端保存的结构 {@code 114514.nbt}，15 × 19 × 15）。
 *
 * <p>来源文件：{@code run/saves/新的世界/generated/minecraft/structures/114514.nbt}；
 * 保存时控制器朝西，这里绕 Y 顺时针 90° 转成图案的正面朝北，控制器仍在局部 (0,0,0)。
 * 原结构里的 3 个物流仓保持为固定的机械物流仓，成型后作为大机器物流端口。
 */
public final class MassDriverStructure {
    private MassDriverStructure() {}

    public static MultiblockPattern create() {
        return new MultiblockPatternBuilder("mass_driver")
                .skipAir()
                .whereController('C', MassDriverBlocks.CONTROLLER.get())
                .where('H', HatchBlocks.HIGH_LOAD_CASING.get())
                .where('R', HatchBlocks.RUGGED_SUPPORT_CASING.get())
                .where('S', HatchBlocks.SUPERCONDUCTING_COIL.get())
                .where('P', HatchBlocks.LOGISTICS.get())
                .layer(
                        "    RRRRRRR    ",
                        "  RRRHHHHHRRR  ",
                        " RRHHHHHHHHHRR ",
                        " RHHHHHHHHHHHR ",
                        "RRHHRRRRRRRHHRR",
                        "RHHHRHHHHHRHHHR",
                        "RHHHRHHHHHRHHHR",
                        "RHHHRHHHHHRHHHR",
                        "RHHHRHHHHHRHHHR",
                        "RHHHRHHHHHRHHHR",
                        "RRHHRRRRRRRHHRR",
                        " RHHHHHHHHHHHR ",
                        " RRHHHHHHHHHRR ",
                        "  RRRHHHHHRRR  ",
                        "    RRRRRRR    "
                )
                .layer(
                        "               ",
                        "               ",
                        "     R   R     ",
                        "     R   R     ",
                        "    RRHCHRR    ",
                        "  RRRRS SRRRR  ",
                        "    HS   SH    ",
                        "    P     P    ",
                        "    HS   SH    ",
                        "  RRRRS SRRRR  ",
                        "    RRHPHRR    ",
                        "     R   R     ",
                        "     R   R     ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "     R   R     ",
                        "     R   R     ",
                        "    RRH HRR    ",
                        "  RRRRS SRRRR  ",
                        "    HS   SH    ",
                        "               ",
                        "    HS   SH    ",
                        "  RRRRS SRRRR  ",
                        "    RRH HRR    ",
                        "     R   R     ",
                        "     R   R     ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "     R   R     ",
                        "    RRH HRR    ",
                        "   RRRS SRRR   ",
                        "    HS   SH    ",
                        "               ",
                        "    HS   SH    ",
                        "   RRRS SRRR   ",
                        "    RRH HRR    ",
                        "     R   R     ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "     R   R     ",
                        "     RH HR     ",
                        "   RRRS SRRR   ",
                        "    HS   SH    ",
                        "               ",
                        "    HS   SH    ",
                        "   RRRS SRRR   ",
                        "     RH HR     ",
                        "     R   R     ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "     RH HR     ",
                        "    RRS SRR    ",
                        "    HS   SH    ",
                        "               ",
                        "    HS   SH    ",
                        "    RRS SRR    ",
                        "     RH HR     ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "     RH HR     ",
                        "    RRS SRR    ",
                        "    HS   SH    ",
                        "               ",
                        "    HS   SH    ",
                        "    RRS SRR    ",
                        "     RH HR     ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "     RS SR     ",
                        "    HS   SH    ",
                        "               ",
                        "    HS   SH    ",
                        "     RS SR     ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "     RS SR     ",
                        "    HS   SH    ",
                        "               ",
                        "    HS   SH    ",
                        "     RS SR     ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "     RS SR     ",
                        "    HS   SH    ",
                        "               ",
                        "    HS   SH    ",
                        "     RS SR     ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "     RH HR     ",
                        "     HS SH     ",
                        "               ",
                        "     HS SH     ",
                        "     RH HR     ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "     HS SH     ",
                        "               ",
                        "     HS SH     ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "     HS SH     ",
                        "               ",
                        "     HS SH     ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "     HS SH     ",
                        "               ",
                        "     HS SH     ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .layer(
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "      H H      ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               ",
                        "               "
                )
                .build();
    }
}

