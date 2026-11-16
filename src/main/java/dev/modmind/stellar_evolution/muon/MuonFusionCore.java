package dev.modmind.stellar_evolution.muon;

/**
 * 缪子聚变仪<strong>等离子体账本</strong>——<strong>纯数值、不依赖 Minecraft</strong>，可直接 JUnit 单测。
 *
 * <p>四个属性（温度 / 稳定值 / 约束 / 核心）里，这里负责前两个与点火过程；核心由物品决定、约束由核心决定。
 *
 * <h2>点火（逐级升压）</h2>
 * 未点火时每 tick 按当前档位要求 <b>HP/t</b> 与 <b>DP</b>（热阻 × 有效热流率）：
 * 供得上就把点火进度往上推、温度跟着爬；供不上就<strong>回退</strong>（不会点火）。
 * 进度满 10 档 → 点火成功：稳定值直接 90、温度跳到 2600℃。
 *
 * <h2>运行（自热 + 维持）</h2>
 * 点火后需求<strong>骤降</strong>到维持档（2048 HP/t / 热阻 8）：供得上就<strong>自热</strong>
 * 把温度往「2600 + 3400 × 稳定值%」拉；供不上就降温，稳定值也会掉。
 * 稳定值 90 起步、稳定运行 +0.008/t、出束 −0.010/t；<strong>冷却水</strong>充足时再 +0.004/t
 * （净 +0.002/t，稳定值缓慢回升），一旦<strong>缺水</strong>则 −0.150/t（约 23 秒从 90 掉到停机线 20）。
 * 低于 20 <strong>停机并上锁</strong>：必须把芯核取出重放（或换新芯核）才会重新走点火流程。
 * 超过 7000℃ 会累积损毁，满 100% 熔毁（芯核销毁）。
 */
public final class MuonFusionCore {
    /** 点火档数。 */
    public static final int IGNITION_STEPS = 10;
    /** 点火进度每 tick 增量（档/t）。 */
    public static final double IGNITION_CHARGE_RATE = 0.05D;
    /** 供能不足时每 tick 的回退量（档/t）。 */
    public static final double IGNITION_FALL_RATE = 0.08D;

    /** 点火第 0 档的热流率需求（HP/t）。 */
    public static final double IGNITION_BASE_FLOW = 1024.0D;
    /** 每升一档的热流率倍率（第 9 档 ≈ 29 700 HP/t）。 */
    public static final double IGNITION_FLOW_GROWTH = 1.45D;
    /** 点火第 0 档的热阻。 */
    public static final double IGNITION_BASE_RESISTANCE = 8.0D;
    /** 每升一档的热阻倍率（第 9 档 ≈ 94，峰值占用 ≈ 2.8M DP）。 */
    public static final double IGNITION_RESISTANCE_GROWTH = 1.35D;

    /** 点火后的维持热流率（HP/t）——骤降到点火峰值的约 1/15。 */
    public static final double MAINTENANCE_FLOW = 2048.0D;
    /** 点火后的维持热阻。 */
    public static final double MAINTENANCE_RESISTANCE = 8.0D;

    /** 环境温度（℃）。 */
    public static final double AMBIENT_C = 20.0D;
    /** 点火成功时的等离子体温度（℃）——已越过「过超临界」的 2000℃ 线。 */
    public static final double IGNITION_TEMP_C = 2600.0D;
    /** 稳定值 100 时的自热平衡温度（℃）。 */
    public static final double SELF_HEAT_MAX_C = 6000.0D;
    /** 自热靠近平衡点的速率（每 tick）。 */
    public static final double SELF_HEAT_RATE = 0.003D;
    /** 供能断开后的降温速率（每 tick）。 */
    public static final double COOL_RATE = 0.010D;
    /** 安全温度上限（℃）：超过开始累积损毁。 */
    public static final double SAFE_TEMP_C = 7000.0D;

    /** 点火成功时的稳定值。 */
    public static final double STABILITY_START = 90.0D;
    /** 稳定值上限。 */
    public static final double STABILITY_MAX = 100.0D;
    /** 低于此值自动停机（需重新点火）。 */
    public static final double STABILITY_MIN = 20.0D;
    /** 稳定运行时每 tick 增加。 */
    public static final double STABILITY_GAIN = 0.008D;
    /** 出束时每 tick 降低（「微微降低」，与 {@link #STABILITY_GAIN} 相抵后净 −0.002/t）。 */
    public static final double STABILITY_EMIT_DRAIN = 0.010D;
    /** 维持供能不足时每 tick 额外降低。 */
    public static final double STABILITY_STARVE_DRAIN = 0.030D;
    /** 冷却水充足时每 tick 额外增加（与 {@link #STABILITY_EMIT_DRAIN} 相抵后净 +0.002/t，稳定值缓升）。 */
    public static final double STABILITY_WATER_GAIN = 0.004D;
    /** 已点火却抽不到冷却水时每 tick 额外降低（约 23 秒从 90 掉到停机线 20）。 */
    public static final double STABILITY_DRY_DRAIN = 0.150D;
    /** 已点火却抽不到缪子流时每 tick 额外降低（约 12 秒从 90 掉到停机线 20）。 */
    public static final double STABILITY_MU_DRY_DRAIN = 0.250D;
    /** 超温时每 tick 额外降低。 */
    public static final double STABILITY_OVERHEAT_DRAIN = 0.050D;

    /** 出束的最低温度（℃）。 */
    public static final double EMIT_MIN_TEMP_C = 2000.0D;
    /** 2000℃ 时的束流强度（单位/t）；温度越高线性越强。 */
    public static final double STREAM_RATE_AT_2000C = 10.0D;
    /** 2000℃ 时的中子副产（单位/t）；用于锂-6 增殖氚，闭合自持。 */
    public static final double NEUTRON_RATE_AT_2000C = 1.0D;
    /** 2000℃ 时的蒸汽量（mB/t）——2600℃ 点火即约 26.6 万 mB/t，接近一根 450 MW 燃料棒。 */
    public static final double STEAM_AT_2000C = 204_800.0D;
    /**
     * 蒸汽温度上限（℃）：<strong>工质侧与等离子体温度解耦</strong>——真实超超临界机组的主蒸汽也就
     * 600~620℃，700℃ 属研发上限；等离子体的几千度不可能变成同等温度的水蒸气（水在 2000℃ 以上已热解离）。
     */
    public static final double STEAM_MAX_C = 700.0D;
    /** 蒸汽温度基准（℃）。 */
    public static final double STEAM_BASE_C = 100.0D;
    /** 等离子体每高出基准 1℃，蒸汽温度上升多少：`100 + (T − 100) × 0.22`，到 700℃ 封顶。 */
    public static final double STEAM_TEMP_RATIO = 0.22D;
    /** 蒸汽基础压力（bar）。 */
    public static final double PRESSURE_BASE_BAR = 200.0D;
    /** 每℃ 增加的压力（bar）。 */
    public static final double PRESSURE_PER_C = 0.12D;
    /** 压力上限（bar）——与蒸汽网络的 DP 加成封顶一致。 */
    public static final double PRESSURE_MAX_BAR = 900.0D;

    /** 超温时每 tick 的损毁增量。 */
    public static final double DAMAGE_RATE = 0.005D;
    /** 未超温时每 tick 的损毁回退。 */
    public static final double DAMAGE_RECOVER = 0.002D;

    /** 点火进度（0..{@link #IGNITION_STEPS}）。 */
    private double charge;
    /** 是否已点火。 */
    private boolean ignited;
    /** 等离子体温度（℃）。 */
    private double temperatureC = AMBIENT_C;
    /** 稳定值（0..100）。 */
    private double stability;
    /** 损毁进度（0..1）。 */
    private double damage;
    /** 累计输出的粒子流总量（单位）。 */
    private double emitted;
    /** 稳定值耗尽后的「已停机」锁：必须取出并重新放入芯核才能再次点火。 */
    private boolean halted;

    /** 一步运行结果。 */
    public record Snapshot(boolean ignited, boolean igniting, boolean halted, double chargeRatio,
                           double temperatureC, double stability, double streamRate,
                           double steamAmount, double steamPressure,
                           double emitted, double damage, boolean meltedDown) {}

    /** 当前点火档位（0..9）。 */
    public int ignitionStage() {
        return (int) Math.max(0.0D, Math.min(IGNITION_STEPS - 1, Math.floor(charge)));
    }

    /** 当前档位要求的热流率（HP/t）；已点火时是维持值。 */
    public double requiredFlow() {
        return ignited ? MAINTENANCE_FLOW : IGNITION_BASE_FLOW * Math.pow(IGNITION_FLOW_GROWTH, ignitionStage());
    }

    /** 当前档位要求的热阻；已点火时是维持值。 */
    public double requiredResistance() {
        return ignited
                ? MAINTENANCE_RESISTANCE
                : IGNITION_BASE_RESISTANCE * Math.pow(IGNITION_RESISTANCE_GROWTH, ignitionStage());
    }

    /**
     * 推进 {@code dt} 个 tick。
     *
     * @param hasCore 反应仓里有没有聚变芯核
     * @param supplyOk 本 tick 热压差供得上（HP/t 与热阻都满足）
     * @param waterOk 本 tick 冷却水抽到了（已点火时才有意义）
     * @param dt tick 数
     */
    public Snapshot step(boolean hasCore, boolean supplyOk, boolean waterOk, double dt) {
        return step(hasCore, supplyOk, waterOk, true, dt);
    }

    /**
     * 推进 {@code dt} 个 tick（带缪子供给）。
     *
     * @param muOk 本 tick 是否抽到了足够的缪子流；false 时已点火的稳定值会快速下降
     */
    public Snapshot step(boolean hasCore, boolean supplyOk, boolean waterOk, boolean muOk, double dt) {
        boolean melted = false;
        if (!hasCore) {
            shutdown();
            halted = false;   // 芯核被取走 / 销毁：清掉停机锁，新芯核可以重新点火
            temperatureC += (AMBIENT_C - temperatureC) * COOL_RATE * dt;
            return snapshot(false);
        }
        if (halted) {
            // 稳定值耗尽后的停机：必须取出并重新放入芯核（或换新芯核）才能再次点火
            temperatureC += (AMBIENT_C - temperatureC) * COOL_RATE * dt;
            return snapshot(false);
        }
        if (!ignited) {
            charge = clamp(charge + (supplyOk ? IGNITION_CHARGE_RATE : -IGNITION_FALL_RATE) * dt,
                    0.0D, IGNITION_STEPS);
            temperatureC = AMBIENT_C + (IGNITION_TEMP_C - AMBIENT_C) * charge / IGNITION_STEPS;
            if (charge >= IGNITION_STEPS) {
                ignited = true;
                stability = STABILITY_START;
                temperatureC = IGNITION_TEMP_C;
            }
            return snapshot(false);
        }

        double target = supplyOk ? selfHeatTarget() : AMBIENT_C;
        double rate = supplyOk ? SELF_HEAT_RATE : COOL_RATE;
        temperatureC += (target - temperatureC) * rate * dt;

        boolean emitting = emitting();
        double delta = supplyOk ? STABILITY_GAIN : -STABILITY_STARVE_DRAIN;
        if (emitting) {
            delta -= STABILITY_EMIT_DRAIN;
        }
        // 冷却水：抽得到就缓慢回升，抽不到就迅速崩（点火后没有水等于没有冷却）
        delta += waterOk ? STABILITY_WATER_GAIN : -STABILITY_DRY_DRAIN;
        // 缪子：聚变是 μ 子消费者；抽不到 μ 子时等离子体无法维持
        if (!muOk) {
            delta -= STABILITY_MU_DRY_DRAIN;
        }
        if (temperatureC > SAFE_TEMP_C) {
            delta -= STABILITY_OVERHEAT_DRAIN;
        }
        stability = clamp(stability + delta * dt, 0.0D, STABILITY_MAX);

        if (temperatureC > SAFE_TEMP_C) {
            damage = Math.min(1.0D, damage + DAMAGE_RATE * dt);
        } else {
            damage = Math.max(0.0D, damage - DAMAGE_RECOVER * dt);
        }
        if (damage >= 1.0D) {
            melted = true;
            damage = 0.0D;
            shutdown();
        } else if (stability < STABILITY_MIN) {
            halt();
        } else if (emitting) {
            emitted += streamRate() * dt;
        }
        return snapshot(melted);
    }

    /** 自热平衡温度：稳定值越高越热。 */
    private double selfHeatTarget() {
        double factor = clamp(stability / STABILITY_MAX, 0.0D, 1.0D);
        return IGNITION_TEMP_C + (SELF_HEAT_MAX_C - IGNITION_TEMP_C) * factor;
    }

    /** 当前是否在出束。 */
    public boolean emitting() {
        return streamRate() > 0.0D;
    }

    /** 当前束流强度（单位/t）。 */
    public double streamRate() {
        return ignited && temperatureC >= EMIT_MIN_TEMP_C
                ? STREAM_RATE_AT_2000C * temperatureC / 2000.0D : 0.0D;
    }

    /** 当前中子副产（单位/t）——只有点火且越过出束温度才产生。 */
    public double neutronRate() {
        return ignited && temperatureC >= EMIT_MIN_TEMP_C
                ? NEUTRON_RATE_AT_2000C * temperatureC / 2000.0D : 0.0D;
    }

    /** 当前蒸汽量（mB/t）。 */
    public double steamAmount() {
        return ignited && temperatureC >= 100.0D
                ? STEAM_AT_2000C * temperatureC / 2000.0D : 0.0D;
    }

    /**
     * 当前蒸汽温度（℃）——<strong>与等离子体温度解耦</strong>：
     * `100 + (T − 100) × 0.22`，封顶 {@link #STEAM_MAX_C}。点火（2600℃）时约 650℃，到 5000℃ 左右封顶 700℃。
     * <p>这样蒸汽落回现实工质区间，而“档位”仍能靠压力（{@code >500 bar}）保住过超临界 —— 数值不动。
     */
    public double steamTemperatureC() {
        if (!ignited || temperatureC < 100.0D) {
            return AMBIENT_C;
        }
        double steam = STEAM_BASE_C + (temperatureC - STEAM_BASE_C) * STEAM_TEMP_RATIO;
        return Math.min(STEAM_MAX_C, steam);
    }

    /** 当前蒸汽压力（bar）——压力随温度。 */
    public double steamPressure() {
        return ignited
                ? Math.min(PRESSURE_MAX_BAR, PRESSURE_BASE_BAR + temperatureC * PRESSURE_PER_C) : 0.0D;
    }

    /** 停机（重新点火才能再运行）。 */
    public void shutdown() {
        ignited = false;
        charge = 0.0D;
        stability = 0.0D;
    }

    /** 稳定值耗尽：停机并上锁，直到芯核被取出重放。 */
    public void halt() {
        shutdown();
        halted = true;
    }

    /** 芯核被取走 / 销毁后清锁（由方块实体在「没有芯核」的那些 tick 调用）。 */
    public void clearHalt() {
        halted = false;
    }

    public boolean halted() {
        return halted;
    }

    /** 载入存档用：直接恢复全部运行态。 */
    public void restore(boolean ignited, double charge, double temperatureC, double stability,
                        double damage, double emitted) {
        restore(ignited, false, charge, temperatureC, stability, damage, emitted);
    }

    /** 载入存档用（带停机锁）。 */
    public void restore(boolean ignited, boolean halted, double charge, double temperatureC,
                        double stability, double damage, double emitted) {
        this.ignited = ignited;
        this.halted = halted;
        this.charge = clamp(charge, 0.0D, IGNITION_STEPS);
        this.temperatureC = Math.max(AMBIENT_C, temperatureC);
        this.stability = clamp(stability, 0.0D, STABILITY_MAX);
        this.damage = clamp(damage, 0.0D, 1.0D);
        this.emitted = Math.max(0.0D, emitted);
    }

    private Snapshot snapshot(boolean meltedDown) {
        return new Snapshot(ignited, !ignited && !halted && charge > 0.0D, halted, charge / IGNITION_STEPS,
                temperatureC, stability, streamRate(), steamAmount(), steamPressure(),
                emitted, damage, meltedDown);
    }

    public boolean ignited() {
        return ignited;
    }

    public double charge() {
        return charge;
    }

    public double temperatureC() {
        return temperatureC;
    }

    public double stability() {
        return stability;
    }

    public double damage() {
        return damage;
    }

    public double emitted() {
        return emitted;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
