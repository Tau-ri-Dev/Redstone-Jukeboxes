package dev.tauri.rsjukeboxes.block;

import dev.tauri.rsjukeboxes.blockentity.AbstractRSJukeboxBE;
import dev.tauri.rsjukeboxes.item.ITabbedItem;
import dev.tauri.rsjukeboxes.registry.TabRegistry;
import dev.tauri.rsjukeboxes.util.ITickable;
import dev.tauri.rsjukeboxes.util.ItemHelper;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.stat.Stats;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static net.minecraft.state.property.Properties.HORIZONTAL_FACING;

@SuppressWarnings("deprecation")
public abstract class AbstractRSJukebox extends JukeboxBlock implements ITabbedItem {
    public AbstractRSJukebox() {
        super(AbstractBlock.Settings.copy(Blocks.JUKEBOX).nonOpaque());
        setDefaultState(getDefaultState().with(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        ItemHelper.applyGenericToolTip(getTranslationKey(), tooltip, options);
    }

    @Override
    public Supplier<ItemGroup> getTab() {
        return TabRegistry.TAB_JUKEBOXES;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        Direction facing = placer == null ? Direction.NORTH : placer.getHorizontalFacing().getOpposite();
        world.setBlockState(pos, state.with(HORIZONTAL_FACING, facing), 3);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> pBuilder) {
        super.appendProperties(pBuilder);
        pBuilder.add(HORIZONTAL_FACING);
    }

    @Override
    public ActionResult onUse(BlockState state, World pLevel, BlockPos pPos, PlayerEntity pPlayer, Hand pHand, BlockHitResult hit) {
        if (!pLevel.isClient()) {
            BlockEntity blockentity = pLevel.getBlockEntity(pPos);
            if (blockentity instanceof AbstractRSJukeboxBE jukebox) {
                var item = pPlayer.getStackInHand(pHand);
                if (jukebox.hasPlayableItem()) {
                    jukebox.popOutRecord(0);
                    jukebox.markDirty();
                    return ActionResult.success(false);
                }
                if (item.isIn(ItemTags.MUSIC_DISCS) && !item.isEmpty()) {
                    jukebox.itemStackHandler.setStack(0, item.copy());
                    item.decrement(1);
                    jukebox.setChanged();
                    pPlayer.incrementStat(Stats.PLAY_RECORD);
                    return ActionResult.success(false);
                }
            }
        }

        return ActionResult.PASS;
    }

    @Override
    public void onStateReplaced(BlockState pState, World pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.isOf(pNewState.getBlock())) {
            BlockEntity blockentity = pLevel.getBlockEntity(pPos);
            if (blockentity instanceof AbstractRSJukeboxBE jukebox) {
                jukebox.popOutRecords();
            }

            super.onStateReplaced(pState, pLevel, pPos, pNewState, pIsMoving);
        }
    }

    @Override
    public boolean emitsRedstonePower(BlockState pState) {
        return true;
    }

    public AbstractRSJukeboxBE getJukeboxBE(BlockView level, BlockPos pos) {
        if (level == null) return null;
        if (pos == null) return null;
        var tile = level.getBlockEntity(pos);
        if (tile instanceof AbstractRSJukeboxBE jukebox)
            return jukebox;
        return null;
    }

    @Override
    public int getWeakRedstonePower(BlockState pState, BlockView pLevel, BlockPos pPos, Direction direction) {
        if (pLevel instanceof World w && w.isClient) return 0;
        var jukeboxBE = getJukeboxBE(pLevel, pPos);
        if (jukeboxBE == null) return 0;
        var blockDirection = pState.get(HORIZONTAL_FACING);
        var directionRotated = Direction.fromRotation(blockDirection.asRotation() + direction.asRotation());
        if (blockDirection.getAxis() == Direction.Axis.Z) directionRotated = directionRotated.getOpposite();
        return getOutputSignal(pState, pLevel, pPos, direction.getAxis() == Direction.Axis.Y ? direction : directionRotated, jukeboxBE);
    }

    @Override
    public void neighborUpdate(BlockState pState, World pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
        super.neighborUpdate(pState, pLevel, pPos, pBlock, pFromPos, pIsMoving);
        if (pLevel.isClient) return;
        var jukeboxBE = getJukeboxBE(pLevel, pPos);
        if (jukeboxBE == null) return;
        var blockDirection = pState.get(HORIZONTAL_FACING);
        var signals = new HashMap<Direction, Integer>();
        for (var direction : Direction.values()) {
            if (!pPos.offset(direction).equals(pFromPos)) continue;
            var directionRotated = Direction.fromRotation(blockDirection.asRotation() + direction.asRotation());
            if (blockDirection.getAxis() == Direction.Axis.Z) directionRotated = directionRotated.getOpposite();
            signals.put(direction.getAxis() == Direction.Axis.Y ? direction : directionRotated, pLevel.getEmittedRedstonePower(pPos.offset(direction), direction));
        }
        processInputSignal(pState, pLevel, pPos, pFromPos, signals, jukeboxBE);
    }


    public int getOutputSignal(BlockState state, BlockView level, BlockPos pos, Direction direction, AbstractRSJukeboxBE jukeboxBE) {
        if (jukeboxBE.isPlaying()) {
            return 15;
        }
        return 0;
    }

    public void processInputSignal(BlockState state, BlockView level, BlockPos pos, BlockPos changedPos, Map<Direction, Integer> signals, AbstractRSJukeboxBE jukeboxBE) {
    }

    @Override
    public boolean hasComparatorOutput(BlockState pState) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState pBlockState, World pLevel, BlockPos pPos) {
        BlockEntity blockentity = pLevel.getBlockEntity(pPos);
        if (blockentity instanceof AbstractRSJukeboxBE jukebox) {
            Item item = jukebox.getPlayingItem().getItem();
            if (item instanceof MusicDiscItem recorditem) {
                return recorditem.getComparatorOutput();
            }
        }

        return 0;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pPos, BlockState pState) {
        return newBlockEntity(pPos, pState);
    }

    public abstract BlockEntity newBlockEntity(BlockPos pPos, BlockState pState);

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return ITickable.getTickerHelper();
    }
}
