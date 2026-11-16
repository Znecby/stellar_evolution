package dev.znecby.stellar_evolution.tank;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * 储罐（tank）：外观取自存档结构 {@code tank.nbt}（见开发文档 §6.4）。
 * <p>
 * 版式：金属底板 + 金属顶板 + 四角金属柱，四面玻璃墙，内部 {@code 8 × 8 × 8} 空腔存流体。
 * <p><strong>三档</strong>（2026-11-07）：同一个方块类服务铜 / 钢 / 硬质三档，
 * 由 {@link TankTier} 决定单格容量与模型贴图（见 {@link TankBlocks}）。
 * <ul>
 *   <li><strong>无界面</strong>：手持容器（桶 / 其它模组的流体容器）右键即交换流体 ——
 *       先试「罐 → 容器」（空桶接流体），再试「容器 → 罐」（满桶倒入）；非容器一律放行，不挡放方块。</li>
 *   <li><strong>存储</strong>：每格 <b>8 / 32 / 128 桶</b>（按档位，见 {@link TankTier}）。</li>
 *   <li><strong>合并</strong>：照机械动力的口径，同档位的上下叠 / 3×3 会拼成一个大罐（共享存储、容量按格数叠），
 *       外观随之变化 —— 顶 / 底盖只在最上 / 最下一层、罐内的隔墙与金属柱自动隐去、3×3 只在四条边中间开窗。
 *       方块状态里的 {@code top / bottom / north / east / south / west / window} 就是这套外观的开关，
 *       由 {@link TankMultiblock} 算出后写回（见 {@link #shape}）。</li>
 *   <li><strong>能力</strong>：{@code Capabilities.Fluid.BLOCK} 暴露整罐（部件转发给控制器），管道可接管。</li>
 *   <li><strong>流体看得见</strong>：渲染由 {@code client.TankRenderer} 画在玻璃后面，按整个大罐的尺寸铺满。</li>
 * </ul>
 */
public class TankBlock extends BaseEntityBlock {
    /** 本格是否为所属大罐的最上一层（画顶盖）。 */
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    /** 本格是否为所属大罐的最下一层（画底盖）。 */
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    /** 本格是否开窗（{@code false} = 该面向外是金属板）。 */
    public static final BooleanProperty WINDOW = BooleanProperty.create("window");
    /** 该面是否是罐内（有同属一个大罐的邻居）—— 是则整面隐去，实现「合并后看不见隔墙」。 */
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    private final TankTier tier;

    public TankBlock(TankTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState()
                .setValue(TOP, true)
                .setValue(BOTTOM, true)
                .setValue(WINDOW, true)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        // 档位由注册实例固定，编解码只认「这一个方块」（不按 properties 重新构造）
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, BOTTOM, WINDOW, NORTH, EAST, SOUTH, WEST);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankBlockEntity(pos, state);
    }

    /** 本档位（铜 / 钢 / 硬质）。 */
    public TankTier tier() {
        return tier;
    }

    /** 本档位单格容量（mB）。 */
    public int capacityPerBlock() {
        return tier.capacityPerBlock();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 不挡光：罐身是「镂空的整方块」（碰撞 / 选择形状仍是 1×1×1，但内部是 8px 空腔 + 玻璃墙）。
     * <p>按默认整格口径会挡 15 级光，于是合并成大罐后，中间那些只剩空腔的格子光照是 0；
     * 而方块模型的<strong>内壁面</strong>取的正是「相邻那格」的光（网格器对整格碰撞形状走
     * {@code faceCubic} → 邻居坐标），于是隔着玻璃看内壁一片黑。
     * 置 0 后光可穿过罐体，内壁与流体都按真实环境照亮（与 {@code BioMachineShellBlock} 填充格同一口径）。
     */
    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    /** 天光可穿透罐体：玻璃墙不该给罐内投下阴影。 */
    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    /** 碰撞形状是整方块，默认会把面压到 0.2 亮度；罐内壁要按玻璃那样满亮。 */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** 服务端 tick：只用来把「放置 / 破坏 / 载入后」的多方块重算推迟一刻（避免在改方块时递归）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, TankBlocks.TANK_ENTITY.get(), TankBlockEntity::tick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // 只改属性（同类方块）时不重算，避免刷新外观时自激
        if (level.isClientSide() || oldState.getBlock() == state.getBlock()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
            tank.scheduleConnectivity();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof TankBlockEntity tank) {
                tank.scheduleConnectivity();
            }
        }
    }

    /**
     * 破坏储罐时把罐里的流体带进掉落物 —— 玩家可以靠搬运储罐来大规模转移流体。
     * <p>规则：
     * <ul>
     *   <li>流体只存在<strong>控制器</strong>（最小角那格）上，部件只是转发；</li>
     *   <li>拆<strong>控制器</strong>时，先把「同一个大罐剩余格子数 × 单格容量」能装下的部分拆回这些格子
     *       （严格按本罐的长方体范围，见 {@link TankMultiblock#cellsOf}，绝不碰相邻的独立储罐）；</li>
     *   <li>拆<strong>部件</strong>时控制器还在，只把「缩小后新容量装不下」的部分从控制器抽出来带走；</li>
     *   <li>带走的量写进掉落物组件（最多单格容量），放下时再灌回去（见 {@link #setPlacedBy}）。</li>
     * </ul>
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TankBlockEntity broken)
                || !(params.getLevel() instanceof ServerLevel level)) {
            return drops;
        }
        boolean breakingController = broken.controllerPos() == null;
        TankBlockEntity controller = breakingController ? broken : broken.controller();
        FluidStack stored = controller == null ? FluidStack.EMPTY : controller.fluid();
        if (stored.isEmpty()) {
            return drops;
        }
        // 同一个大罐剩余的格子（按控制器记的宽 / 高遍历长方体）；隔壁独立储罐不在其中
        List<TankBlockEntity> remaining = TankMultiblock.cellsOf(level, controller, broken.getBlockPos());
        int budget = remaining.size() * capacityPerBlock();
        FluidResource resource = FluidResource.of(stored.getFluid());

        // 拆的是部件时控制器还在：先把流体整份从控制器里抽出来，否则下面再往剩余格子里灌 = 凭空复制。
        // 拆的是控制器时方块实体已随方块消失，它的存量本来就只会进掉落物 / 剩余格子，不需要抽。
        int amount = stored.getAmount();
        if (!breakingController) {
            try (Transaction transaction = Transaction.openRoot()) {
                int got = controller.rawFluids().extract(0, resource, amount, transaction);
                if (got > 0) {
                    transaction.commit();
                }
                amount = got;
            }
        }
        if (amount <= 0) {
            return drops;
        }
        // 先尽量留在本大罐的剩余格子里（下一 tick 会重新并成小罐），剩下的才随掉落物走
        int keep = Math.min(amount, budget);
        int placed = 0;
        for (TankBlockEntity cell : remaining) {
            if (placed >= keep) {
                break;
            }
            placed += cell.acceptForSplit(resource, Math.min(keep - placed, capacityPerBlock()));
        }
        int excess = amount - placed;
        if (excess <= 0) {
            return drops;
        }
        int carried = Math.min(excess, capacityPerBlock());
        for (ItemStack drop : drops) {
            if (drop.is(this.asItem())) {
                // 用 applyFluid：顺便把掉落物压成「不可堆叠」，避免流体被整叠共享
                TankBlockItem.applyFluid(drop, TankComponents.TankFluid.of(stored.copyWithAmount(carried)));
                break;
            }
        }
        return drops;
    }

    /** 放下「装着流体的储罐物品」时把流体灌回本格（若与其它罐并在一起，多方块重算会再汇入控制器）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        TankComponents.TankFluid stored = stack.get(TankComponents.FLUID.get());
        FluidStack fluid = stored == null ? FluidStack.EMPTY : stored.toStack();
        if (!fluid.isEmpty() && level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
            tank.restoreFluid(fluid);
            tank.scheduleConnectivity();
        }
    }

    /**
     * 按「所属长方体」的位置算出外观方块状态：顶 / 底盖看层号，四周看邻居是否同属一个大罐（同属则隐面），
     * 中间那块铜板 / 玻璃由开窗开关决定。
     */
    public static BlockState shape(BlockState state, BlockPos pos, BlockPos origin, int width, int height, boolean window) {
        return state
                .setValue(TOP, pos.getY() == origin.getY() + height - 1)
                .setValue(BOTTOM, pos.getY() == origin.getY())
                .setValue(WINDOW, window)
                .setValue(NORTH, inside(pos, origin, width, Direction.NORTH))
                .setValue(EAST, inside(pos, origin, width, Direction.EAST))
                .setValue(SOUTH, inside(pos, origin, width, Direction.SOUTH))
                .setValue(WEST, inside(pos, origin, width, Direction.WEST));
    }

    /** 邻格是否落在同一个长方体（同属一个大罐 → 该面是内部面）。 */
    private static boolean inside(BlockPos pos, BlockPos origin, int width, Direction direction) {
        BlockPos neighbor = pos.relative(direction);
        return neighbor.getX() >= origin.getX() && neighbor.getX() < origin.getX() + width
                && neighbor.getZ() >= origin.getZ() && neighbor.getZ() < origin.getZ() + width;
    }

    /**
     * 右键手持容器交换流体。搬运本身交给 NeoForge transfer API（与反应釜菜单同一套）：
     * 容器能力来自 {@link Capabilities.Fluid#ITEM}，两个方向各一次 {@link ResourceHandlerUtil#move}。
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof TankBlockEntity tank)) {
            return InteractionResult.PASS;
        }
        // 手持储罐物品：一次性批量转移（服务端执行，客户端只回 swing，防止用客户端作弊刷流体）
        if (stack.getItem() instanceof TankBlockItem) {
            return useTankItem(level, pos, player, stack, tank);
        }
        if (level.isClientSide()) {
            // 客户端：手持桶就接管这次交互（别让它走到原版「把水倒在地上」那条路）；
            // 其它模组的容器服务端照常认，这里只是不做客户端预判
            return stack.getItem() instanceof BucketItem ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ItemAccess access = ItemAccess.forPlayerInteraction(serverPlayer, hand).oneByOne();
        if (access.getCapability(Capabilities.Fluid.ITEM) == null) {
            return InteractionResult.PASS;
        }
        // 只要是流体容器就吃掉这次交互：即使两边都搬不动，也不要让水桶顺手把水倒在地
        exchange(access, tank, serverLevel, pos, serverPlayer);
        return InteractionResult.SUCCESS;
    }

    /**
     * 手持<strong>储罐物品</strong>右键储罐方块：一次性批量转移（不支持的零碎「一桶一倒」）。
     *
     * <p>校验链（任何一步不过都直接返回，不做任何数据修改）：
     * <ol>
     *   <li>权限：旁观者 / 领地或出生点保护（{@link Level#mayInteract}）；</li>
     *   <li>目标有效：调用方已确认是 {@link TankBlockEntity}；</li>
     *   <li>流体类型：往非空罐里倒时要求注册名一致；空罐接受任意流体；</li>
     *   <li>容量：倒入「装满即止」（余量留在物品），取出以物品单格容量为上限。</li>
     * </ol>
     * 客户端只做输入转发（返回 SUCCESS 阻止放方块），全部数据修改在服务端完成。
     */
    private InteractionResult useTankItem(Level level, BlockPos pos, Player player, ItemStack stack,
                                          TankBlockEntity tank) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }
        TankComponents.TankFluid held = stack.get(TankComponents.FLUID.get());
        boolean itemHasFluid = held != null && held.amount() > 0;
        if (!itemHasFluid && stack.getCount() > 1) {
            // 空罐叠：拆一个出来装（写组件时会把它压成不可堆叠，不会把流体“复制”给整叠）
            return fillOneFromStack(serverLevel, pos, player, stack, tank);
        }
        if (stack.getCount() != 1) {
            // 带流体的叠只可能来自旧存档：让玩家先拆开，避免组件被整叠共享
            return InteractionResult.PASS;
        }
        FluidStack tankFluid = tank.fluid();
        if (itemHasFluid) {
            // ① 物品 → 方块：类型一致 + 装满即止
            FluidStack pouring = held.toStack();
            if (pouring.isEmpty()) {
                return InteractionResult.SUCCESS;
            }
            String tankType = tankFluid.isEmpty() ? null
                    : BuiltInRegistries.FLUID.getKey(tankFluid.getFluid()).toString();
            if (!TankItemTransfer.typeMatches(tankType, held.fluid())) {
                failFeedback(serverLevel, pos);
                return InteractionResult.SUCCESS;
            }
            int moved = insertFluid(tank.fluids(), FluidResource.of(pouring.getFluid()), pouring.getAmount());
            if (moved <= 0) {
                failFeedback(serverLevel, pos);   // 目标已满
                return InteractionResult.SUCCESS;
            }
            int remaining = held.amount() - moved;
            TankBlockItem.applyFluid(stack, remaining <= 0
                    ? null : new TankComponents.TankFluid(held.fluid(), remaining));
            successFeedback(serverLevel, player, pos, tank, SoundEvents.BUCKET_EMPTY);
            return InteractionResult.SUCCESS;
        }
        // ② 方块 → 物品：手持空罐，取到物品单格容量为止
        if (tankFluid.isEmpty()) {
            return InteractionResult.SUCCESS;
        }
        int itemCapacity = itemCapacity(stack, tank);
        int movable = TankItemTransfer.amountToMove(tankFluid.getAmount(), itemCapacity);
        int moved = movable <= 0 ? 0
                : extractFluid(tank.fluids(), FluidResource.of(tankFluid.getFluid()), movable);
        if (moved <= 0) {
            failFeedback(serverLevel, pos);
            return InteractionResult.SUCCESS;
        }
        String type = BuiltInRegistries.FLUID.getKey(tankFluid.getFluid()).toString();
        TankBlockItem.applyFluid(stack, new TankComponents.TankFluid(type, moved));
        successFeedback(serverLevel, player, pos, tank, SoundEvents.BUCKET_FILL);
        return InteractionResult.SUCCESS;
    }

    /**
     * 手持<strong>一叠空储罐</strong>右键储罐：拆出一个来装流体。
     * <p>写入流体组件时会把这一格压成「不可堆叠」，所以不会出现「一整叠都带上同一份流体」的复制问题；
     * 装好的单罐优先回背包，背包满则掉在脚下。
     */
    private InteractionResult fillOneFromStack(ServerLevel level, BlockPos pos, Player player, ItemStack stack,
                                               TankBlockEntity tank) {
        FluidStack tankFluid = tank.fluid();
        if (tankFluid.isEmpty()) {
            return InteractionResult.SUCCESS;   // 空罐碰空罐：不消耗物品
        }
        ItemStack single = stack.copyWithCount(1);
        int movable = TankItemTransfer.amountToMove(tankFluid.getAmount(), itemCapacity(single, tank));
        int moved = movable <= 0 ? 0
                : extractFluid(tank.fluids(), FluidResource.of(tankFluid.getFluid()), movable);
        if (moved <= 0) {
            failFeedback(level, pos);
            return InteractionResult.SUCCESS;
        }
        TankBlockItem.applyFluid(single, new TankComponents.TankFluid(
                BuiltInRegistries.FLUID.getKey(tankFluid.getFluid()).toString(), moved));
        stack.shrink(1);
        if (!player.getInventory().add(single)) {
            player.drop(single, false);
        }
        successFeedback(level, player, pos, tank, SoundEvents.BUCKET_FILL);
        return InteractionResult.SUCCESS;
    }

    /** 这个储罐物品的单格容量（按物品自身档位；认不出时退回目标方块档位）。 */
    private static int itemCapacity(ItemStack stack, TankBlockEntity tank) {
        return stack.getItem() instanceof TankBlockItem item && item.getBlock() instanceof TankBlock itemTank
                ? itemTank.capacityPerBlock() : tank.capacityPerBlock();
    }

    /** 成功反馈：音效 + 粒子 + 方块同步 + 玩家物品栏立即刷新。 */
    private static void successFeedback(ServerLevel level, Player player, BlockPos pos, TankBlockEntity tank,
                                        net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CLOUD,
                pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                6, 0.25D, 0.08D, 0.25D, 0.01D);
        tank.pushUpdate();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
        }
    }

    /** 失败反馈：机械「拒绝」音效，数据完全不动。 */
    private static void failFeedback(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    /** 往储罐里灌（由处理器按容量截断 → 天然「装满即止」）；返回实际灌入量。 */
    private static int insertFluid(ResourceHandler<FluidResource> handler, FluidResource resource, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(0, resource, amount, transaction);
            if (moved > 0) {
                transaction.commit();
            }
            return moved;
        }
    }

    /** 从储罐里抽；返回实际抽到的量。 */
    private static int extractFluid(ResourceHandler<FluidResource> handler, FluidResource resource, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.extract(0, resource, amount, transaction);
            if (moved > 0) {
                transaction.commit();
            }
            return moved;
        }
    }

    /** 容器 ↔ 罐 双向搬运：先「罐 → 容器」，再「容器 → 罐」。 */
    private static void exchange(ItemAccess access, TankBlockEntity tank, ServerLevel level, BlockPos pos, Player player) {
        ResourceHandler<FluidResource> container = access.getCapability(Capabilities.Fluid.ITEM);
        if (container == null) {
            return;
        }
        // 部件也能点：fluids() 会转发到所属大罐的控制器
        ResourceHandler<FluidResource> tankView = tank.fluids();

        FluidResource inTank = tankView.getResource(0);
        if (!inTank.isEmpty()
                && ResourceHandlerUtil.move(tankView, container, resource -> true, Integer.MAX_VALUE, null) > 0) {
            FluidUtil.triggerSoundAndGameEvent(inTank, level, Vec3.atCenterOf(pos), player, true);
            return;
        }
        FluidResource inContainer = container.getResource(0);
        if (!inContainer.isEmpty()
                && ResourceHandlerUtil.move(container, tankView, resource -> true, Integer.MAX_VALUE, null) > 0) {
            FluidUtil.triggerSoundAndGameEvent(inContainer, level, Vec3.atCenterOf(pos), player, false);
        }
    }
}
