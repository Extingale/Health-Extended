package com.ext.healthextended.client;

import com.ext.healthextended.data.WoundData;
import com.ext.healthextended.registry.ModAttachmentTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// Calls WoundDecalManager.update() each frame so the composite skin stays current. Does no rendering itself.
@OnlyIn(Dist.CLIENT)
public class WoundOverlayLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public WoundOverlayLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            AbstractClientPlayer entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (entity.isInvisible()) return;

        WoundData woundData = entity.getExistingData(ModAttachmentTypes.WOUND_DATA).orElse(null);
        if (woundData == null || woundData.getMarks().isEmpty()) {
            WoundDecalManager.invalidate(entity.getUUID());
            return;
        }

        long gameTime = entity.level().getGameTime();
        WoundDecalManager.update(entity, woundData, gameTime);
        // Rendering is handled by the skin texture -- no draw calls here.
    }
}