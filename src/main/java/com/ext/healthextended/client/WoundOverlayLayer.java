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

/**
 * Driver layer that keeps {@link WoundDecalManager} up to date every frame.
 *
 * <p>This layer does NO rendering itself. It exists solely so that
 * {@link WoundDecalManager#update} is called once per rendered player per
 * frame, which triggers a rebuild of the composite skin texture (real skin +
 * wound decals). The composite is then used automatically by
 * {@code AbstractClientPlayerMixin} which redirects
 * {@code getSkinTextureLocation()} to return the composite.</p>
 *
 * <p>All visual effects (hurt flash, death fade, etc.) apply to wounds for
 * free because they operate on the skin texture that now contains wound pixels.</p>
 */
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