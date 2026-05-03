package com.ext.healthextended;

import com.ext.healthextended.config.ClientConfig;
import com.ext.healthextended.event.PlayerEventHandler;
import com.ext.healthextended.registry.ModAttachmentTypes;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(HealthExtended.MODID)
public class HealthExtended {

    public static final String MODID = "healthextended";

    public HealthExtended(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachmentTypes.ATTACHMENT_TYPES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        NeoForge.EVENT_BUS.register(new PlayerEventHandler());
    }
}
