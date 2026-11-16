package dev.modmind.stellar_evolution.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 钢制工具功能位掩码的纯数值测试：可用集合、冲突清理、Alt 预设。
 */
class SteelToolFeatureTest {

    @Test
    @DisplayName("工具可用集合：镐矿石、斧伐木、锹连锁破坏、锄收割、剑横扫")
    void toolKindsHaveTheirOwnFeatures() {
        assertTrue(SteelToolFeature.VEIN.supports(SteelToolKind.PICKAXE));
        assertFalse(SteelToolFeature.FELL.supports(SteelToolKind.PICKAXE));

        assertTrue(SteelToolFeature.FELL.supports(SteelToolKind.AXE));
        assertFalse(SteelToolFeature.VEIN.supports(SteelToolKind.AXE));

        assertTrue(SteelToolFeature.BULK.supports(SteelToolKind.SHOVEL));
        assertFalse(SteelToolFeature.HARVEST.supports(SteelToolKind.SHOVEL));

        assertTrue(SteelToolFeature.HARVEST.supports(SteelToolKind.HOE));
        assertFalse(SteelToolFeature.SWEEP.supports(SteelToolKind.HOE));

        assertTrue(SteelToolFeature.SWEEP.supports(SteelToolKind.SWORD));
        assertTrue(SteelToolFeature.LOOTING.supports(SteelToolKind.SWORD));
        assertFalse(SteelToolFeature.AREA.supports(SteelToolKind.SWORD));
    }

    @Test
    @DisplayName("冲突清理：主行动组只保留一个，时运压精准")
    void sanitizeKeepsOneActionAndOneEnchant() {
        int bothActions = SteelToolFeature.AREA.mask() | SteelToolFeature.VEIN.mask();
        assertEquals(SteelToolFeature.AREA.mask(),
                SteelToolFeature.sanitize(bothActions, SteelToolKind.PICKAXE));

        int bothEnchants = SteelToolFeature.FORTUNE.mask() | SteelToolFeature.SILK.mask();
        assertEquals(SteelToolFeature.FORTUNE.mask(),
                SteelToolFeature.sanitize(bothEnchants, SteelToolKind.PICKAXE));

        int unsupported = SteelToolFeature.FELL.mask();
        assertEquals(0, SteelToolFeature.sanitize(unsupported, SteelToolKind.PICKAXE));
    }

    @Test
    @DisplayName("开启某项时只清同组互斥项")
    void conflictMaskIsToolSpecific() {
        assertEquals(SteelToolFeature.VEIN.mask(),
                SteelToolFeature.AREA.conflictMask(SteelToolKind.PICKAXE));
        assertEquals(SteelToolFeature.FELL.mask(),
                SteelToolFeature.AREA.conflictMask(SteelToolKind.AXE));
        assertEquals(SteelToolFeature.HARVEST.mask(),
                SteelToolFeature.AREA.conflictMask(SteelToolKind.HOE));
        assertEquals(SteelToolFeature.FORTUNE.mask(),
                SteelToolFeature.SILK.conflictMask(SteelToolKind.PICKAXE));
        assertEquals(0, SteelToolFeature.SWEEP.conflictMask(SteelToolKind.SWORD));
    }

    @Test
    @DisplayName("Alt 预设：镐不含伐木/连锁破坏/收割，剑覆盖无/横扫/抢夺/横扫+抢夺")
    void presetsFollowToolKind() {
        int next = SteelToolFeature.nextPreset(SteelToolKind.PICKAXE, 0);
        assertEquals(SteelToolFeature.AREA.mask(), next);

        int axeNext = SteelToolFeature.nextPreset(SteelToolKind.AXE, SteelToolFeature.AREA.mask());
        assertEquals(SteelToolFeature.AREA.mask() | SteelToolFeature.FORTUNE.mask(), axeNext);

        assertEquals(SteelToolFeature.SWEEP.mask(),
                SteelToolFeature.nextPreset(SteelToolKind.SWORD, 0));
        assertEquals(SteelToolFeature.LOOTING.mask(),
                SteelToolFeature.nextPreset(SteelToolKind.SWORD, SteelToolFeature.SWEEP.mask()));
        assertEquals(SteelToolFeature.SWEEP.mask() | SteelToolFeature.LOOTING.mask(),
                SteelToolFeature.nextPreset(SteelToolKind.SWORD, SteelToolFeature.LOOTING.mask()));
        assertEquals(0, SteelToolFeature.nextPreset(SteelToolKind.SWORD,
                SteelToolFeature.SWEEP.mask() | SteelToolFeature.LOOTING.mask()));
    }
}
