package com.ext.healthextended.registry;

import com.ext.healthextended.HealthExtended;
import com.ext.healthextended.data.PlayerBodyData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachmentTypes {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HealthExtended.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerBodyData>> PLAYER_BODY_DATA =
            ATTACHMENT_TYPES.register("player_body_data", () ->
                    AttachmentType.builder(PlayerBodyData::createDefault)
                            .serialize(PlayerBodyData.CODEC)
                            .sync(ModAttachmentTypes::shouldSyncToPlayer, PlayerBodyData.STREAM_CODEC)
                            .build()
            );

    private static boolean shouldSyncToPlayer(IAttachmentHolder holder, ServerPlayer to) {
        return holder == to;
    }
}
