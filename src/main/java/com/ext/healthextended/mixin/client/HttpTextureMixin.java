package com.ext.healthextended.mixin.client;

import com.ext.healthextended.client.WoundDecalManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@link HttpTexture#upload(NativeImage)} to capture a CPU-side copy
 * of the skin pixels before vanilla auto-closes the NativeImage.
 *
 * HttpTexture (used for all player skins downloaded from the session server)
 * calls {@code image.upload(0, 0, 0, true)} — the {@code true} flag means the
 * NativeImage is freed immediately after the GPU upload, leaving
 * {@code DynamicTexture.getPixels()} unusable.  We need these pixels to build
 * the wound-decal composite, so we snapshot them here first.
 *
 * The captured image is stored in {@link WoundDecalManager} keyed by the skin
 * ResourceLocation and is used as Strategy 1.5 inside
 * {@code WoundDecalManager.copySkinPixels}.
 */
@Mixin(HttpTexture.class)
public class HttpTextureMixin {

    // Protected field inherited from SimpleTexture — identifies which skin this is.
    @Shadow
    protected ResourceLocation location;

    @Inject(
            method = "upload(Lcom/mojang/blaze3d/platform/NativeImage;)V",
            at = @At("HEAD")
    )
    private void healthextended$captureForWounds(NativeImage image, CallbackInfo ci) {
        if (image != null) {
            WoundDecalManager.captureSkinPixels(this.location, image);
        }
    }
}
