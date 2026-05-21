package com.ext.healthextended.mixin.client;

import com.ext.healthextended.client.WoundDecalManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link PlayerRenderer#getTextureLocation(AbstractClientPlayer)} to
 * redirect to the wound-composite texture when this player has active wound marks.
 *
 * <p>The composite texture is a full copy of the player’s skin with wound decals
 * alpha-blended on top, so the hurt flash and all other skin effects apply
 * naturally to wound pixels without a separate render pass.</p>
 *
 * <p>Compatibility notes:
 * <ul>
 *   <li>Uses {@code @Inject} (not {@code @Overwrite}) to remain cooperative.</li>
 *   <li>Only replaces the return value when a composite texture actually exists
 *       (i.e. this player currently has wounds), so the mixin is a no-op
 *       otherwise.</li>
 * </ul>
 * </p>
 */
@Mixin(PlayerRenderer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN"), cancellable = true)
    private void healthextended$injectWoundSkin(AbstractClientPlayer player,
                                                CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation composite = WoundDecalManager.getCompositeSkinLocation(player.getUUID());
        if (composite != null) {
            cir.setReturnValue(composite);
        }
    }
}
