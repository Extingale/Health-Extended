package com.ext.healthextended.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import static com.ext.healthextended.HealthExtended.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public final class EffectUiSuppressor {

    private EffectUiSuppressor() {
    }

    @SubscribeEvent
    public static void onRenderInventoryMobEffects(ScreenEvent.RenderInventoryMobEffects event) {
        if (!(event.getScreen() instanceof InventoryScreen)) {
            return;
        }

        if (Minecraft.getInstance().player == null) {
            return;
        }

        event.setCanceled(true);
    }
}