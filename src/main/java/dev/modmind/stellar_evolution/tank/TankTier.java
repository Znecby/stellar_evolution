package dev.modmind.stellar_evolution.tank;

/**
 * 储罐档位（2026-11-07）：铜 / 钢 / 硬质。
 *
 * <p>三档<strong>机制完全一致</strong>（同一套 {@link TankBlock} / {@link TankBlockEntity} /
 * {@link TankMultiblock}），只差<strong>单格容量</strong>与<strong>外观材质</strong>：
 * <ul>
 *   <li>铜储罐：每格 8 桶（8 000 mB）；</li>
 *   <li>钢储罐：每格 32 桶（4 × 铜）；</li>
 *   <li>硬质储罐：每格 128 桶（4 × 钢，材质为钨铜硬质合金）。</li>
 * </ul>
 * 合并成大罐时**只与同档位**的储罐拼接，容量 = 格数 × 本档位单格容量。
 */
public enum TankTier {
    COPPER("copper_tank", 8_000),
    STEEL("steel_tank", 32_000),
    HARDENED("hardened_tank", 128_000);

    private final String id;
    private final int capacityPerBlock;

    TankTier(String id, int capacityPerBlock) {
        this.id = id;
        this.capacityPerBlock = capacityPerBlock;
    }

    /** 注册 id（方块 / 物品 / 模型 / 方块状态同名）。 */
    public String id() {
        return id;
    }

    /** 单格容量（mB）。 */
    public int capacityPerBlock() {
        return capacityPerBlock;
    }
}
