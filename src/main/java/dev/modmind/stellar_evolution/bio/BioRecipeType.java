package dev.modmind.stellar_evolution.bio;

import java.util.Locale;

/**
 * 生化配方类型（《生化线》§5.3）：<strong>一台机器一种类型</strong>，与 {@link BioMachineKind} 一一对应。
 * <p>类型 id（{@link #id}）既用于 JEI 分类的 uid，也用于 lang 键 {@code jei.stellar_evolution.<id>}。
 */
public enum BioRecipeType {
    /** 调浆机：固体 → 浆料。 */
    SLURRYING(BioMachineKind.SLURRY_MIXER),
    /** 发酵罐：发酵（温度带决定产物）。 */
    FERMENTING(BioMachineKind.FERMENTER),
    /** 分解仪：固液分离。 */
    DECOMPOSING(BioMachineKind.DECOMPOSER),
    /** 灭菌罐：高温水解 / 灭菌（吃热流率）。 */
    STERILIZING(BioMachineKind.STERILIZER),
    /** 结晶机：液 → 固。 */
    CRYSTALLIZING(BioMachineKind.CRYSTALLIZER),
    /** 反应器：多进多出精密合成（萃取 / 电解 / 中和）。 */
    BIOREACTION(BioMachineKind.REACTOR);

    private final BioMachineKind machine;

    BioRecipeType(BioMachineKind machine) {
        this.machine = machine;
    }

    /** 用这类配方的机器。 */
    public BioMachineKind machine() {
        return machine;
    }

    /** 配方类型 id（slurrying / fermenting / …）。 */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 反过来：这台机器用哪类配方。 */
    public static BioRecipeType of(BioMachineKind kind) {
        for (BioRecipeType type : values()) {
            if (type.machine == kind) {
                return type;
            }
        }
        throw new IllegalArgumentException("没有对应配方类型的机器：" + kind);
    }
}
