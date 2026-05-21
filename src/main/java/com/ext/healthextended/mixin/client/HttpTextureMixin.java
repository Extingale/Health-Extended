package com.ext.healthextended.mixin.client;

import com.ext.healthextended.client.WoundDecalManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@link HttpTexture#upload(NativeImage)} to capture a CPU-side copy
 * of the skin pixels before vanilla auto-closes the NativeImage.
 *
 * HttpTexture calls {@code image.upload(0, 0, 0, true)} — autoClose=true frees
 * the NativeImage immediately after GPU upload, so we snapshot it here first.
 * The copy is stored in {@link WoundDecalManager} and used as Strategy 1.5 in
 * {@code copySkinPixels} to build the wound-decal composite for custom skins.
 */
@Mixin(HttpTexture.class)
public class HttpTextureMixin {

    @Inject(
            method = "upload(Lcom/mojang/blaze3d/platform/NativeImage;)V",
            at = @At("HEAD")
    )
    private void healthextended$captureForWounds(NativeImage image, CallbackInfo ci) {
        if (image != null) {
            // location is protected in SimpleTexture (parent) — access via accessor mixin
            ResourceLocation loc = ((SimpleTextureAccessor) (Object) this).healthextended$getLocation();
            WoundDecalManager.captureSkinPixels(loc, image);
        }
    }
}

