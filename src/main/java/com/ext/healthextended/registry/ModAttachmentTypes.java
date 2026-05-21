package com.ext.healthextended.registry;

import com.ext.healthextended.HealthExtended;
import com.ext.healthextended.data.PlayerBodyData;
import com.ext.healthextended.data.WoundData;
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

    /**
     * Ephemeral wound-mark data. Not serialized to disk (marks are cosmetic and
     * temporary), synced only to the owning player.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WoundData>> WOUND_DATA =
            ATTACHMENT_TYPES.register("wound_data", () ->
                    AttachmentType.builder(WoundData::createDefault)
                            .sync(ModAttachmentTypes::shouldSyncToPlayer, WoundData.STREAM_CODEC)
                            .build()
            );

    private static boolean shouldSyncToPlayer(IAttachmentHolder holder, ServerPlayer to) {
        return holder == to;
    }
}
