package dev.tauri.rsjukeboxes.block;

import dev.tauri.rsjukeboxes.blockentity.AbstractRSJukeboxBE;
import dev.tauri.rsjukeboxes.item.ITabbedItem;
import dev.tauri.rsjukeboxes.registry.TabRegistry;
import dev.tauri.rsjukeboxes.util.ITickable;
import dev.tauri.rsjukeboxes.util.ItemHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

@SuppressWarnings("deprecation")
public abstract class AbstractRSJukebox extends JukeboxBlock implements ITabbedItem {
    public AbstractRSJukebox(Properties properties) {
        super(properties.isRedstoneConductor((pState, pLevel, pPos) -> false).noOcclusion());
        this.registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack itemStack, @Nullable BlockGetter blockGetter, @NotNull List<Component> components, @NotNull TooltipFlag tooltipFlag) {
        ItemHelper.applyGenericToolTip(getDescriptionId(), components, tooltipFlag);
    }

    @Override
    public RegistryObject<CreativeModeTab> getTab() {
        return TabRegistry.TAB_JUKEBOXES;
    }

    @Override
    @ParametersAreNonnullByDefault
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        Direction facing = pPlacer == null ? Direction.NORTH : pPlacer.getDirection().getOpposite();
        pLevel.setBlock(pPos, pState.setValue(HORIZONTAL_FACING, facing), 3);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(HORIZONTAL_FACING);
    }

    @ParametersAreNonnullByDefault
    public @NotNull InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (!pLevel.isClientSide) {
            BlockEntity blockentity = pLevel.getBlockEntity(pPos);
            if (blockentity instanceof AbstractRSJukeboxBE jukebox) {
                var item = pPlayer.getItemInHand(pHand);
                if (jukebox.hasPlayableItem()) {
                    jukebox.popOutRecord(0);
                    jukebox.setChanged();
                    return InteractionResult.sidedSuccess(false);
                }
                if (item.is(ItemTags.MUSIC_DISCS) && !item.isEmpty()) {
                    jukebox.itemStackHandler.setStackInSlot(0, item.copy());
                    item.shrink(1);
                    jukebox.setChanged();
                    pPlayer.awardStat(Stats.PLAY_RECORD);
                    return InteractionResult.sidedSuccess(false);
                }
            }
        }

        return InteractionResult.PASS;
    }

    @ParametersAreNonnullByDefault
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock())) {
            BlockEntity blockentity = pLevel.getBlockEntity(pPos);
            if (blockentity instanceof AbstractRSJukeboxBE jukebox) {
                jukebox.popOutRecords();
            }

            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
        }
    }

    @ParametersAreNonnullByDefault
    public boolean isSignalSource(BlockState pState) {
        return true;
    }

    public AbstractRSJukeboxBE getJukeboxBE(BlockGetter level, BlockPos pos) {
        if (level == null) return null;
        if (pos == null) return null;
        var tile = level.getBlockEntity(pos);
        if (tile instanceof AbstractRSJukeboxBE jukebox)
            return jukebox;
        return null;
    }

    @Override
    @ParametersAreNonnullByDefault
    public int getSignal(BlockState pState, BlockGetter pLevel, BlockPos pPos, Direction direction) {
        if (pLevel instanceof ClientLevel) return 0;
        var jukeboxBE = getJukeboxBE(pLevel, pPos);
        if (jukeboxBE == null) return 0;
        var blockDirection = pState.getValue(HORIZONTAL_FACING);
        var directionRotated = Direction.fromYRot(blockDirection.toYRot() + direction.toYRot());
        if (blockDirection.getAxis() == Direction.Axis.Z) directionRotated = directionRotated.getOpposite();
        return getOutputSignal(pState, pLevel, pPos, direction.getAxis() == Direction.Axis.Y ? direction : directionRotated, jukeboxBE);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
        super.neighborChanged(pState, pLevel, pPos, pBlock, pFromPos, pIsMoving);
        if (pLevel.isClientSide) return;
        var jukeboxBE = getJukeboxBE(pLevel, pPos);
        if (jukeboxBE == null) return;
        var blockDirection = pState.getValue(HORIZONTAL_FACING);
        var signals = new HashMap<Direction, Integer>();
        for (var direction : Direction.values()) {
            if (!pPos.offset(direction.getNormal()).equals(pFromPos)) continue;
            var directionRotated = Direction.fromYRot(blockDirection.toYRot() + direction.toYRot());
            if (blockDirection.getAxis() == Direction.Axis.Z) directionRotated = directionRotated.getOpposite();
            signals.put(direction.getAxis() == Direction.Axis.Y ? direction : directionRotated, pLevel.getSignal(pPos.offset(direction.getNormal()), direction));
        }
        processInputSignal(pState, pLevel, pPos, pFromPos, signals, jukeboxBE);
    }


    @ParametersAreNonnullByDefault
    public int getOutputSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction, AbstractRSJukeboxBE jukeboxBE) {
        if (jukeboxBE.isPlaying()) {
            return 15;
        }
        return 0;
    }

    @ParametersAreNonnullByDefault
    public void processInputSignal(BlockState state, BlockGetter level, BlockPos pos, BlockPos changedPos, Map<Direction, Integer> signals, AbstractRSJukeboxBE jukeboxBE) {
    }

    @ParametersAreNonnullByDefault
    public boolean hasAnalogOutputSignal(BlockState pState) {
        return true;
    }

    @ParametersAreNonnullByDefault
    public int getAnalogOutputSignal(BlockState pBlockState, Level pLevel, BlockPos pPos) {
        BlockEntity blockentity = pLevel.getBlockEntity(pPos);
        if (blockentity instanceof AbstractRSJukeboxBE jukebox) {
            Item item = jukebox.getPlayingItem().getItem();
            if (item instanceof RecordItem recorditem) {
                return recorditem.getAnalogOutput();
            }
        }

        return 0;
    }

    @Override
    @ParametersAreNonnullByDefault
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return createBlockEntity(pPos, pState);
    }

    public abstract BlockEntity createBlockEntity(BlockPos pPos, BlockState pState);

    @Nullable
    @Override
    @ParametersAreNonnullByDefault
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return ITickable.getTickerHelper();
    }
}
