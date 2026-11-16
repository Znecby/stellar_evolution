package dev.modmind.stellar_evolution.particle;

import dev.modmind.stellar_evolution.fission.FissionMachines;
import dev.modmind.stellar_evolution.hep.HepAdvancements;
import dev.modmind.stellar_evolution.steam.SteamEndpoint;
import dev.modmind.stellar_evolution.steam.SteamState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <strong>ADS</strong>（加速器驱动次临界系统，二期 D1）：在束流机器的基础上多一个身份 ——
 * <strong>蒸汽生成端</strong>。
 *
 * <p>嬗变本身完全复用 {@link BeamMachineBlockEntity} 的「接哪几路流就吃哪几路」逻辑
 * （配方见 {@code BeamRecipes} 的 {@code FissionMachineKind.ADS} 段）；
 * 这里只补 {@link SteamEndpoint}：只要在跑，就向压力管网络送一股中温蒸汽。
 */
public class AdsBlockEntity extends BeamMachineBlockEntity implements SteamEndpoint {
    /** 运行时的蒸汽输出（mB/t）。 */
    public static final double STEAM_RATE = 8.0D;
    /** 蒸汽温度（℃）：过热档，低于聚变、高于普通裂变堆。 */
    public static final double STEAM_TEMPERATURE_C = 320.0D;
    /** 蒸汽压力（bar）。 */
    public static final double STEAM_PRESSURE_BAR = 100.0D;

    public AdsBlockEntity(BlockPos pos, BlockState state) {
        super(FissionMachines.ADS_ENTITY.get(), pos, state);
    }

    @Override
    public boolean steamGenerator() {
        return true;
    }

    @Override
    public SteamState steamOutput() {
        return working ? new SteamState(STEAM_RATE, STEAM_TEMPERATURE_C, STEAM_PRESSURE_BAR) : SteamState.NONE;
    }

    @Override
    public List<BlockPos> steamCells() {
        return occupiedCells();
    }

    @Override
    public BlockPos steamOrigin() {
        return origin();
    }

    @Override
    public SteamStatus steamStatus() {
        return working ? SteamStatus.DELIVERING : SteamStatus.IDLE;
    }

    /** 首次成功嬗变时授予「首次 ADS 嬗变」成就。 */
    @Override
    protected void onCrafted(BeamRecipes.Recipe recipe) {
        if (getLevel() instanceof ServerLevel serverLevel) {
            HepAdvancements.awardNearby(serverLevel, worldPosition, HepAdvancements.ADS, 64.0D);
        }
    }
}
