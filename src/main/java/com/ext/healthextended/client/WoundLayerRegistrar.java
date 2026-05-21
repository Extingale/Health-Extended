package com.ext.healthextended.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import static com.ext.healthextended.HealthExtended.MODID;

/**
 * Registers {@link WoundOverlayLayer} onto every player skin renderer so that
 * wound marks are composited for both the in-world model and the inventory paperdoll.
 */
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WoundLayerRegistrar {

    private WoundLayerRegistrar() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skinModel : event.getSkins()) {
            net.minecraft.client.renderer.entity.EntityRenderer<? extends Player> renderer = event.getSkin(skinModel);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new WoundOverlayLayer(playerRenderer));
            }
        }
    }
}
