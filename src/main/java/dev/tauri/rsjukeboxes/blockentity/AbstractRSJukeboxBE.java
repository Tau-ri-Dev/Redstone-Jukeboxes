package dev.tauri.rsjukeboxes.blockentity;

import dev.tauri.rsjukeboxes.RSJukeboxes;
import dev.tauri.rsjukeboxes.packet.RSJPacketHandler;
import dev.tauri.rsjukeboxes.packet.packets.StateUpdatePacketToClient;
import dev.tauri.rsjukeboxes.packet.packets.StateUpdateRequestToServer;
import dev.tauri.rsjukeboxes.renderer.GenericRSJukeboxRendererState;
import dev.tauri.rsjukeboxes.state.State;
import dev.tauri.rsjukeboxes.state.StateProviderInterface;
import dev.tauri.rsjukeboxes.state.StateTypeEnum;
import dev.tauri.rsjukeboxes.util.ITickable;
import dev.tauri.rsjukeboxes.util.JukeboxPlayableUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

public class AbstractRSJukeboxBE extends BlockEntity implements ITickable, ICapabilityProvider, StateProviderInterface {
    public AbstractRSJukeboxBE(BlockEntityType<?> type, BlockPos pPos, BlockState pBlockState) {
        super(type, pPos, pBlockState);
    }

    public long getDelayBetweenRecords() {
        return 20L;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean hasPlayableItem() {
        return hasPlayableItem != null && hasPlayableItem;
    }

    protected boolean isPlaying = false;
    protected Boolean hasPlayableItem = null;
    public long playingStarted;
    public long playingStopped;

    protected int currentSlotPlaying = 0;

    public int getCurrentSlotPlaying() {
        return Math.max(0, Math.min(getContainerSize() - 1, currentSlotPlaying));
    }

    protected GenericRSJukeboxRendererState rendererState = new GenericRSJukeboxRendererState();

    public GenericRSJukeboxRendererState getRendererState() {
        return rendererState;
    }

    public void popOutRecords() {
        for (int i = 0; i < getContainerSize(); i++) {
            popOutRecord(i);
        }
    }

    public void popOutRecord(int slot) {
        if (this.level != null && !this.level.isClientSide) {
            BlockPos blockpos = this.getBlockPos();
            ItemStack stack = itemStackHandler.getStackInSlot(slot).copy();
            itemStackHandler.setStackInSlot(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                Vec3 vec3 = Vec3.atLowerCornerWithOffset(blockpos, 0.5D, 1.01D, 0.5D).offsetRandom(this.level.random, 0.7F);
                ItemEntity itementity = new ItemEntity(this.level, vec3.x(), vec3.y(), vec3.z(), stack);
                itementity.setDefaultPickUpDelay();
                this.level.addFreshEntity(itementity);
            }
            if (slot == getCurrentSlotPlaying()) stopPlaying();
        }
    }

    public void spawnMusicParticles(Level pLevel, BlockPos pPos, BlockState pState) {
        if (pLevel instanceof ServerLevel serverlevel) {
            Vec3 vec3 = Vec3.atBottomCenterOf(pPos).add(0.0D, 1.2F, 0.0D);
            float f = (float) pLevel.getRandom().nextInt(4) / 24.0F;
            serverlevel.sendParticles(ParticleTypes.NOTE, vec3.x(), vec3.y(), vec3.z(), 0, f, 0.0D, 0.0D, 1.0D);
            pLevel.gameEvent(GameEvent.JUKEBOX_PLAY, pPos, GameEvent.Context.of(pState));
        }
    }

    protected boolean shouldRecordStopPlaying() {
        if (!isPlaying) return false;
        if (getPlayingItem().isEmpty()) return true;
        if (level == null) return true;
        var song = JukeboxPlayableUtils.getSong(level, getPlayingItem());
        if (song == null) return true;
        return level.getGameTime() >= this.playingStarted + (long) song.lengthInTicks() + getDelayBetweenRecords();
    }

    protected void setHasRecordBlockState(boolean pHasRecord) {
        if (Objects.requireNonNull(this.level).getBlockState(this.getBlockPos()) == this.getBlockState()) {
            this.level.setBlock(this.getBlockPos(), this.getBlockState().setValue(JukeboxBlock.HAS_RECORD, pHasRecord), 2);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(null, this.getBlockState()));
        }
    }

    public void startPlaying() {
        if (isPlaying) return;
        isPlaying = true;
        if (getPlayingItem().isEmpty()) return;
        playingStarted = Objects.requireNonNull(level).getGameTime();
        this.setChanged();
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        var song = JukeboxPlayableUtils.getSong(level, getPlayingItem());
        if (song != null) {
            int songId = level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).getId(song);
            this.level.levelEvent(LevelEvent.SOUND_PLAY_JUKEBOX_SONG, getBlockPos(), songId);
        }
        sendUpdate();
    }

    public void stopPlaying() {
        if (!isPlaying) return;
        isPlaying = false;
        Objects.requireNonNull(this.level).gameEvent(GameEvent.JUKEBOX_STOP_PLAY, this.getBlockPos(), GameEvent.Context.of(this.getBlockState()));
        this.playingStopped = level.getGameTime();
        this.setChanged();
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.level.levelEvent(LevelEvent.SOUND_STOP_JUKEBOX_SONG, getBlockPos(), 0);
        sendUpdate();
    }

    public int getContainerSize() {
        return 1;
    }

    public final ItemStackHandler itemStackHandler = new ItemStackHandler(getContainerSize()) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return JukeboxPlayableUtils.test(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sendUpdate();
        }
    };

    public ItemStack getPlayingItem() {
        return itemStackHandler.getStackInSlot(getCurrentSlotPlaying());
    }

    @Override
    public void tick() {
        if (level == null || level.isClientSide) return;
        if (shouldRecordStopPlaying()) {
            stopPlaying();
        }
        if (isPlaying && level.getGameTime() % 20 == 0) {
            spawnMusicParticles(level, getBlockPos(), getBlockState());
        }
        boolean hasPlayableItem = !getPlayingItem().isEmpty();
        if (this.hasPlayableItem == null || hasPlayableItem != this.hasPlayableItem) {
            setHasRecordBlockState(hasPlayableItem);
            this.hasPlayableItem = hasPlayableItem;
        }
    }

    /**
     * Server-side method. Called on {@link BlockEntity} to get specified {@link State}.
     *
     * @param stateType {@link StateTypeEnum} State to be collected/returned
     * @return {@link State} instance
     */
    @Override
    public State getState(StateTypeEnum stateType) {
        rendererState.discInserted = !getPlayingItem().isEmpty();
        rendererState.discItemId = Item.getId(getPlayingItem().getItem());
        rendererState.playing = isPlaying;
        rendererState.playingStarted = playingStarted;
        rendererState.playingStopped = playingStopped;
        rendererState.selectedSlot = currentSlotPlaying;
        return rendererState;
    }

    /**
     * Client-side method. Called on {@link BlockEntity} to get specified {@link State} instance
     * to recreate State by deserialization
     *
     * @param stateType {@link StateTypeEnum} State to be deserialized
     * @return deserialized {@link State}
     */
    @Override
    public State createState(StateTypeEnum stateType) {
        return new GenericRSJukeboxRendererState();
    }

    /**
     * Client-side method. Sets appropriate fields in client-side tile entity for it
     * to mirror the server-side tile entity
     *
     * @param stateType {@link StateTypeEnum} State to be applied
     * @param state     {@link State} instance obtained from packet
     */
    @Override
    public void setState(StateTypeEnum stateType, State state) {
        if (state instanceof GenericRSJukeboxRendererState newState) {
            rendererState = newState;
            setChanged();
        }
    }

    protected PacketDistributor.TargetPoint targetPoint;

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) return;
        if (!level.isClientSide) {
            var pos = getBlockPos();
            targetPoint = new PacketDistributor.TargetPoint(pos.getX(), pos.getY(), pos.getZ(), 512, level.dimension());
        }
        sendUpdate();
        sendRequest();
    }

    public void sendUpdate() {
        if (level == null) return;
        if (level.isClientSide) return;
        sendState(StateTypeEnum.JUKEBOX_UPDATE, getState(StateTypeEnum.JUKEBOX_UPDATE));
    }

    public void sendRequest() {
        if (level == null) return;
        if (!level.isClientSide) return;
        RSJPacketHandler.sendToServer(new StateUpdateRequestToServer(getBlockPos(), StateTypeEnum.JUKEBOX_UPDATE));
    }

    public void sendState(StateTypeEnum type, State state) {
        if (level == null || level.isClientSide) return;

        if (targetPoint != null) {
            RSJPacketHandler.sendToClient(new StateUpdatePacketToClient(getBlockPos(), type, state), targetPoint);
        } else {
            RSJukeboxes.logger.debug("targetPoint was null trying to send " + type + " from " + this.getClass().getCanonicalName());
        }
    }


    @Override
    @ParametersAreNonnullByDefault
    public void loadAdditional(CompoundTag compound, HolderLookup.Provider pRegistries) {
        super.loadAdditional(compound, pRegistries);
        itemStackHandler.deserializeNBT(pRegistries, compound.getCompound("itemStackHandler"));
        isPlaying = compound.getBoolean("isPlaying");
        playingStarted = compound.getLong("playingStarted");
        playingStopped = compound.getLong("playingStopped");
        currentSlotPlaying = compound.getInt("currentSlotPlaying");
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void saveAdditional(CompoundTag compound, HolderLookup.Provider pRegistries) {
        super.saveAdditional(compound, pRegistries);
        compound.put("itemStackHandler", itemStackHandler.serializeNBT(pRegistries));
        compound.putBoolean("isPlaying", isPlaying);
        compound.putLong("playingStarted", playingStarted);
        compound.putLong("playingStopped", playingStopped);
        compound.putInt("currentSlotPlaying", currentSlotPlaying);
    }
}
