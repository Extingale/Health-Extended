package com.ext.healthextended.mixin.client;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the protected {@code location} field from {@link SimpleTexture} to mixins. */
@Mixin(SimpleTexture.class)
public interface SimpleTextureAccessor {

    @Accessor("location")
    ResourceLocation healthextended$getLocation();
}
