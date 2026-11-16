package dev.znecby.stellar_evolution.farmland;

/**
 * 标记接口：<strong>机械土壤</strong> —— 机械耕地 / 机械沙地 / 机械灵魂沙地 / 机械草地。
 * <p>
 * 四者都是热压差网络的<strong>消耗端</strong>，共用 {@link MechanicalSoilBlockEntity} 自驱
 * （贴铜块接入、催熟 / 自动收获）。热压差网络与 Jade 只认这个接口，不关心具体是哪种土壤。
 * <p>
 * 「能种什么」由各自的<strong>方块标签</strong>决定，与接口无关：
 * 耕地 {@code minecraft:supports_crops}、沙地 {@code minecraft:supports_sugar_cane} /
 * {@code minecraft:supports_cactus}、灵魂沙地 {@code minecraft:supports_nether_wart}。
 */
public interface MechanicalSoil {
}
