package com.ext.healthextended.event;

import com.ext.healthextended.client.HealthTabPanel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

import static com.ext.healthextended.HealthExtended.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TooltipComponentRegistrar {

    private TooltipComponentRegistrar() {
    }

    @SubscribeEvent
    public static void onRegisterTooltipComponentFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(HealthTabPanel.BodyPartTooltipVisual.class, HealthTabPanel::createBodyPartTooltipComponent);
    }
}
