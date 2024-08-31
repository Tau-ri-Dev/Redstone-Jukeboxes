package dev.tauri.rsjukeboxes.registry;

import dev.tauri.rsjukeboxes.block.RSJukeboxBlock;
import dev.tauri.rsjukeboxes.block.RepeatingJukeboxBlock;
import dev.tauri.rsjukeboxes.item.RSJBlockItem;
import dev.tauri.rsjukeboxes.item.ITabbedItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static dev.tauri.rsjukeboxes.RSJukeboxes.MOD_ID;

public class BlockRegistry {
    public static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);


    public static final RegistryObject<Block> RS_JUKEBOX_BLOCK = REGISTER.register("rs_jukebox_block", RSJukeboxBlock::new);
    public static final RegistryObject<Block> REPEATING_JUKEBOX_BLOCK = REGISTER.register("repeating_jukebox_block", RepeatingJukeboxBlock::new);


    public static void register(IEventBus bus) {
        for (RegistryObject<Block> i : BlockRegistry.REGISTER.getEntries().stream().toList()) {
            ItemRegistry.REGISTER.register(i.getId().getPath(),
                    () -> {
                        RegistryObject<CreativeModeTab> tab = null;
                        if (i.get() instanceof ITabbedItem t) {
                            tab = t.getTab();
                        }
                        return new RSJBlockItem(i.get(), new Item.Properties(), tab);
                    });
        }
        REGISTER.register(bus);
    }
}
