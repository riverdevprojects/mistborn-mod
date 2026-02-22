package com.mistborn.block;

import com.mistborn.block.entity.VialFillerBlockEntity;
import com.mistborn.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Vial Filler block.
 *
 * <p>Acts like a brewing stand specifically for Allomantic vials.
 * Interacting opens a GUI with three slots:</p>
 * <ol>
 *   <li>Vial slot – the vial being filled (input/output).</li>
 *   <li>Fuel slot – coal or other valid furnace fuel.</li>
 *   <li>Ingredient slot – metal flakes to brew into the vial.</li>
 * </ol>
 *
 * <p>When cooking completes, <em>all</em> flakes in the ingredient slot are consumed
 * at once and combined into the vial. The vial remains in slot 0 with its new
 * metal contents stored as NBT.</p>
 */
public class VialFillerBlock extends BaseEntityBlock {

    public VialFillerBlock(Properties properties) {
        super(properties);
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VialFillerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.VIAL_FILLER.get(),
                VialFillerBlockEntity::serverTick);
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof VialFillerBlockEntity filler)) return InteractionResult.PASS;

        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.mistborn.vial_filler");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return filler.createMenu(id, inv);
                }
            }, pos);
        }
        return InteractionResult.CONSUME;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VialFillerBlockEntity filler) {
                filler.dropContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
