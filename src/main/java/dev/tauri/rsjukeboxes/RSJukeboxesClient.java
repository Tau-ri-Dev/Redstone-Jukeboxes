package dev.tauri.rsjukeboxes;

import dev.tauri.rsjukeboxes.packet.RSJPacketHandlerClient;
import dev.tauri.rsjukeboxes.registry.BlockEntityRegistry;
import dev.tauri.rsjukeboxes.registry.BlockRegistry;
import dev.tauri.rsjukeboxes.registry.MenuTypeRegistry;
import dev.tauri.rsjukeboxes.renderer.TieredJukeboxRenderer;
import dev.tauri.rsjukeboxes.screen.container.TieredJukeboxGui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class RSJukeboxesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(MenuTypeRegistry.TIERED_JUKEBOX_MENU_TYPE, TieredJukeboxGui::new);

        RSJPacketHandlerClient.init();

        BlockEntityRendererFactories.register(BlockEntityRegistry.TIER1_ADVANCED_JUKEBOX, TieredJukeboxRenderer::new);
        BlockEntityRendererFactories.register(BlockEntityRegistry.TIER2_ADVANCED_JUKEBOX, TieredJukeboxRenderer::new);
        BlockEntityRendererFactories.register(BlockEntityRegistry.TIER3_ADVANCED_JUKEBOX, TieredJukeboxRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.REPEATING_JUKEBOX_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.TIER1_ADVANCED_JUKEBOX_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.TIER2_ADVANCED_JUKEBOX_BLOCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.TIER3_ADVANCED_JUKEBOX_BLOCK, RenderLayer.getCutout());
    }
}
