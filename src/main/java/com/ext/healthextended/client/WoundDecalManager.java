package com.ext.healthextended.client;

import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.HitFace;
import com.ext.healthextended.data.WoundData;
import com.ext.healthextended.data.WoundMark;
import com.ext.healthextended.data.WoundVisualType;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages per-player composite skin textures that bake wound decals directly
 * onto a copy of the player's own skin.
 *
 * <p>Because wounds are embedded into the skin texture rather than drawn on a
 * separate render layer, they automatically participate in the hurt-flash red
 * tint and any other effects that Minecraft applies to the skin texture.</p>
 *
 * <p>Every frame that a player has active wounds:
 * <ol>
 *   <li>The player's real skin pixels are copied into a per-player
 *       {@link DynamicTexture}.</li>
 *   <li>Each wound's decal PNG is alpha-composited on top at the UV coordinates
 *       corresponding to the struck face of the body part.</li>
 *   <li>The composite texture is uploaded to the GPU and registered under a
 *       stable per-player {@link ResourceLocation}.</li>
 * </ol>
 *
 * <p>The mixin {@code AbstractClientPlayerMixin} redirects
 * {@code getSkinTextureLocation()} to return this composite location whenever
 * {@link #getCompositeSkinLocation(UUID)} returns non-null.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WoundDecalManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Player skin textures are always 64×64 in Minecraft. */
    private static final int SKIN_SIZE = 64;

    /** Fade durations in game ticks. */
    private static final float FADE_IN_TICKS  = 8.0f;
    private static final float FADE_OUT_TICKS = 50.0f;

    /**
     * UV region for every face of every body part in the 64×64 player skin texture.
     * Outer key: body part. Inner key: which face was struck (relative to the victim).
     * Array layout: {u, v, width, height} in pixels.
     *
     * <p>Face assignment convention (victim-relative):
     * <ul>
     *   <li>FRONT  = south face of model (direction victim looks)</li>
     *   <li>BACK   = north face of model</li>
     *   <li>RIGHT  = west face of model  (victim’s own right)</li>
     *   <li>LEFT   = east face of model  (victim’s own left)</li>
     * </ul>
     */
    private static final Map<BodyPart, Map<HitFace, int[]>> FACE_UV = new EnumMap<>(BodyPart.class);
    static {
        // HEAD (8×8×8 box, UV offset 0,0)
        Map<HitFace, int[]> head = new EnumMap<>(HitFace.class);
        head.put(HitFace.FRONT, new int[]{ 8,  8, 8, 8});
        head.put(HitFace.BACK,  new int[]{24,  8, 8, 8});
        head.put(HitFace.RIGHT, new int[]{ 0,  8, 8, 8});
        head.put(HitFace.LEFT,  new int[]{16,  8, 8, 8});
        FACE_UV.put(BodyPart.HEAD, head);

        // TORSO (8×12×4 box, UV offset 16,16)
        Map<HitFace, int[]> torso = new EnumMap<>(HitFace.class);
        torso.put(HitFace.FRONT, new int[]{20, 20, 8, 12});
        torso.put(HitFace.BACK,  new int[]{32, 20, 8, 12});
        torso.put(HitFace.RIGHT, new int[]{16, 20, 4, 12});
        torso.put(HitFace.LEFT,  new int[]{28, 20, 4, 12});
        FACE_UV.put(BodyPart.TORSO, torso);

        // RIGHT_ARM (4×12×4 box, UV offset 40,16)
        Map<HitFace, int[]> rightArm = new EnumMap<>(HitFace.class);
        rightArm.put(HitFace.FRONT, new int[]{44, 20, 4, 12});
        rightArm.put(HitFace.BACK,  new int[]{52, 20, 4, 12});
        rightArm.put(HitFace.RIGHT, new int[]{40, 20, 4, 12}); // outer
        rightArm.put(HitFace.LEFT,  new int[]{48, 20, 4, 12}); // inner
        FACE_UV.put(BodyPart.RIGHT_ARM, rightArm);

        // LEFT_ARM (4×12×4 box, UV offset 32,48)
        Map<HitFace, int[]> leftArm = new EnumMap<>(HitFace.class);
        leftArm.put(HitFace.FRONT, new int[]{36, 52, 4, 12});
        leftArm.put(HitFace.BACK,  new int[]{44, 52, 4, 12});
        leftArm.put(HitFace.RIGHT, new int[]{32, 52, 4, 12}); // inner
        leftArm.put(HitFace.LEFT,  new int[]{40, 52, 4, 12}); // outer
        FACE_UV.put(BodyPart.LEFT_ARM, leftArm);

        // RIGHT_LEG (4×12×4 box, UV offset 0,16)
        Map<HitFace, int[]> rightLeg = new EnumMap<>(HitFace.class);
        rightLeg.put(HitFace.FRONT, new int[]{ 4, 20, 4, 12});
        rightLeg.put(HitFace.BACK,  new int[]{12, 20, 4, 12});
        rightLeg.put(HitFace.RIGHT, new int[]{ 0, 20, 4, 12}); // outer
        rightLeg.put(HitFace.LEFT,  new int[]{ 8, 20, 4, 12}); // inner
        FACE_UV.put(BodyPart.RIGHT_LEG, rightLeg);

        // LEFT_LEG (4×12×4 box, UV offset 16,48)
        Map<HitFace, int[]> leftLeg = new EnumMap<>(HitFace.class);
        leftLeg.put(HitFace.FRONT, new int[]{20, 52, 4, 12});
        leftLeg.put(HitFace.BACK,  new int[]{28, 52, 4, 12});
        leftLeg.put(HitFace.RIGHT, new int[]{16, 52, 4, 12}); // inner
        leftLeg.put(HitFace.LEFT,  new int[]{24, 52, 4, 12}); // outer
        FACE_UV.put(BodyPart.LEFT_LEG, leftLeg);
    }

    /** Per-player composited overlay textures. */
    private static final Map<UUID, PlayerOverlay> overlays = new HashMap<>();

    /**
     * Pixels captured from downloaded skins just before HttpTexture auto-closes them.
     * Keyed by skin ResourceLocation. Populated by HttpTextureMixin, cleared on world unload.
     */
    private static final Map<ResourceLocation, NativeImage> capturedSkinCache = new HashMap<>();

    /** Decal NativeImages loaded lazily from the resource pack, keyed by texture ResourceLocation. */
    private static final Map<ResourceLocation, NativeImage> decalCache = new HashMap<>();
    /** Locations that failed to load — suppresses repeated log spam. */
    private static final Set<ResourceLocation> failedDecals = new HashSet<>();

    private WoundDecalManager() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Called every frame from {@code WoundOverlayLayer} when a player has wounds.
     * Stores the player’s real skin location on first call, then rebuilds the
     * composite (skin + decals) and marks it ready for the mixin to use.
     */
    public static void update(AbstractClientPlayer player, WoundData woundData, long gameTime) {
        PlayerOverlay overlay = overlays.computeIfAbsent(
                player.getUUID(), WoundDecalManager::createOverlay);
        if (overlay == null) return;

        List<WoundMark> marks = woundData.getMarks();
        if (marks.isEmpty()) {
            overlay.hasWounds = false;
            return;
        }

        // Update skin location every frame and detect changes (e.g. custom skin
        // downloading after default Steve was shown).  On the very first wound frame
        // hasWounds==false so the mixin leaves getTextureLocation() untouched,
        // meaning player.getSkin().texture() still returns the real skin location.
        ResourceLocation currentSkinLoc = player.getSkin().texture();
        if (!currentSkinLoc.equals(overlay.originalSkinLocation)) {
            overlay.originalSkinLocation = currentSkinLoc;
            // Invalidate the resource-manager cache so it is re-read for the new location.
            if (overlay.fallbackSkinPixels != null) {
                overlay.fallbackSkinPixels.close();
                overlay.fallbackSkinPixels = null;
            }
        }

        rebuild(overlay, marks, gameTime);
    }

    /**
     * Returns the composite skin {@link ResourceLocation} for {@code playerUUID}
     * if that player currently has active wound marks, or {@code null} otherwise.
     *
     * <p>Called by {@code AbstractClientPlayerMixin} every time
     * {@code getSkinTextureLocation()} is invoked.</p>
     */
    @Nullable
    public static ResourceLocation getCompositeSkinLocation(UUID playerUUID) {
        PlayerOverlay overlay = overlays.get(playerUUID);
        if (overlay == null || !overlay.hasWounds) return null;
        return overlay.textureLocation;
    }

    /**
     * Releases the GPU and CPU texture resources for a player.
     * Call this when a player's entity is removed or they log out.
     */
    public static void invalidate(UUID playerUUID) {
        PlayerOverlay overlay = overlays.remove(playerUUID);
        if (overlay != null) overlay.close();
    }

    /**
     * Releases all cached overlay textures.
     * Call this on world unload / client disconnect.
     */
    public static void invalidateAll() {
        overlays.values().forEach(PlayerOverlay::close);
        overlays.clear();
        capturedSkinCache.values().forEach(NativeImage::close);
        capturedSkinCache.clear();
    }

    /**
     * Called by {@code HttpTextureMixin} immediately before vanilla auto-closes the
     * NativeImage after GPU upload.  Stores a 64×64 copy so that
     * {@link #copySkinPixels} can read it later (Strategy 1.5).
     *
     * Capes (64×32) and other non-skin-sized textures are ignored.
     */
    public static void captureSkinPixels(ResourceLocation location, NativeImage image) {
        if (image.getWidth() != SKIN_SIZE || image.getHeight() != SKIN_SIZE) return;
        NativeImage copy = new NativeImage(SKIN_SIZE, SKIN_SIZE, false);
        for (int x = 0; x < SKIN_SIZE; x++)
            for (int y = 0; y < SKIN_SIZE; y++)
                copy.setPixelRGBA(x, y, image.getPixelRGBA(x, y));
        NativeImage old = capturedSkinCache.put(location, copy);
        if (old != null) old.close();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private static PlayerOverlay createOverlay(UUID playerUUID) {
        try {
            DynamicTexture texture = new DynamicTexture(SKIN_SIZE, SKIN_SIZE, false);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    "healthextended", "dynamic/wound/" + playerUUID);
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            return new PlayerOverlay(texture, loc);
        } catch (Exception e) {
            LOGGER.error("Failed to allocate wound overlay texture for player {}", playerUUID, e);
            return null;
        }
    }

    /**
     * Copies the player’s real skin pixels into the composite image, then
     * alpha-composites each wound decal on top.
     *
     * <p>If the real skin is not yet loaded as a {@link DynamicTexture} (e.g.
     * still downloading), we suppress composite rendering until it is ready.
     */
    private static void rebuild(PlayerOverlay overlay, List<WoundMark> marks, long gameTime) {
        NativeImage image = overlay.dynamicTexture.getPixels();
        if (image == null) return;

        // ---- Pass 1: copy real skin pixels -----------------------------------
        if (!copySkinPixels(image, overlay)) {
            // Skin not accessible this frame — suppress composite to avoid making
            // the player invisible (transparent canvas + mixin redirect = invisible player).
            overlay.hasWounds = false;
            return;
        }

        // ---- Pass 2: alpha-composite wound decals ----------------------------
        boolean anyVisible = false;
        for (WoundMark mark : marks) {
            float alpha = computeAlpha(mark, gameTime);
            if (alpha < 0.01f) continue;
            anyVisible = true;

            long seed = mark.createdTick() ^ ((long) mark.part().ordinal() * 31L);
            ResourceLocation variantLoc = mark.type().pickVariant(seed);
            NativeImage decal = loadDecal(variantLoc);
            if (decal == null) continue;

            Map<HitFace, int[]> faceUVs = FACE_UV.get(mark.part());
            if (faceUVs == null) continue;
            int[] uv = faceUVs.getOrDefault(mark.face(), faceUVs.get(HitFace.FRONT));
            if (uv == null) continue;

            int faceU = uv[0], faceV = uv[1], faceW = uv[2], faceH = uv[3];
            int dW = decal.getWidth();
            int dH = decal.getHeight();

            // Horizontal: scatter across the face using the wound seed.
            int xRange = Math.max(0, faceW - dW);
            int destX  = faceU + (xRange > 0
                    ? (int) ((seed & 0x7FFFFFFFFFFFFFFFL) % (xRange + 1))
                    : Math.max(0, (faceW - dW) / 2));

            // Vertical: localV maps bottom(0) → top(1) within the face.
            // In UV space V increases downward, so top = faceV, bottom = faceV+faceH.
            int yRange = Math.max(0, faceH - dH);
            int destY  = faceV + Math.round((1.0f - mark.localV()) * yRange);

            // Clamp so the decal never escapes the face’s UV region.
            destX = Math.max(faceU, Math.min(faceU + faceW - dW, destX));
            destY = Math.max(faceV, Math.min(faceV + faceH - dH, destY));

            blitDecalOnSkin(image, decal, destX, destY, dW, dH, alpha);
        }

        overlay.hasWounds = anyVisible;
        if (anyVisible) {
            overlay.dynamicTexture.upload();
        }
    }

    /**
     * Copies the player’s original skin pixels into {@code target}.
     *
     * <p>Two strategies are tried in order:
     * <ol>
     *   <li><b>DynamicTexture</b> – custom downloaded skins registered in the texture
     *       manager as {@link DynamicTexture} (including {@code PlayerSkinTexture}).
     *       Re-read every frame so changes are reflected immediately.</li>
     *   <li><b>Resource manager</b> – default bundled skins (Steve / Alex) that are
     *       not {@link DynamicTexture}.  Loaded once per overlay and cached in
     *       {@code overlay.fallbackSkinPixels}.</li>
     * </ol>
     *
     * @return {@code true} if pixels were successfully copied into {@code target}
     */
    private static boolean copySkinPixels(NativeImage target, PlayerOverlay overlay) {
        ResourceLocation skinLoc = overlay.originalSkinLocation;
        if (skinLoc == null) return false;

        // Strategy 1: DynamicTexture (custom / downloaded skin)
        AbstractTexture rawTex = Minecraft.getInstance()
                .getTextureManager().getTexture(skinLoc);
        if (rawTex instanceof DynamicTexture skinTex) {
            NativeImage skin = skinTex.getPixels();
            if (skin != null) {
                for (int x = 0; x < SKIN_SIZE; x++)
                    for (int y = 0; y < SKIN_SIZE; y++)
                        target.setPixelRGBA(x, y, skin.getPixelRGBA(x, y));
                return true;
            }
        }

        // Strategy 1.5: HttpTexture captured pixels (downloaded skins whose NativeImage
        // is auto-closed with autoClose=true after GPU upload — see HttpTextureMixin).
        NativeImage captured = capturedSkinCache.get(skinLoc);
        if (captured != null) {
            for (int x = 0; x < SKIN_SIZE; x++)
                for (int y = 0; y < SKIN_SIZE; y++)
                    target.setPixelRGBA(x, y, captured.getPixelRGBA(x, y));
            return true;
        }

        // Strategy 2: Resource manager (default / bundled skin — load once, cache)
        if (overlay.fallbackSkinPixels == null) {
            try (var stream = Minecraft.getInstance().getResourceManager().open(skinLoc)) {
                overlay.fallbackSkinPixels = NativeImage.read(stream);
            } catch (IOException e) {
                return false; // unavailable this frame — try again next frame
            }
        }
        NativeImage fallback = overlay.fallbackSkinPixels;
        if (fallback == null) return false;
        int w = Math.min(fallback.getWidth(),  SKIN_SIZE);
        int h = Math.min(fallback.getHeight(), SKIN_SIZE);
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                target.setPixelRGBA(x, y, fallback.getPixelRGBA(x, y));
        return true;
    }

    /**
     * Alpha-composites {@code decal} over the existing skin pixels in
     * {@code target} at ({@code tx}, {@code ty}).
     *
     * <p>{@link NativeImage} uses ABGR packing:
     * alpha=bits 24–31, R=bits 0–7, G=bits 8–15, B=bits 16–23.</p>
     *
     * <p>Transparent skin pixels (alpha=0) are never overwritten—wounds only
     * appear where the player’s skin is opaque.</p>
     */
    private static void blitDecalOnSkin(NativeImage target, NativeImage decal,
                                        int tx, int ty, int dW, int dH, float alpha) {
        for (int dy = 0; dy < dH; dy++) {
            int py = ty + dy;
            if (py < 0 || py >= SKIN_SIZE) continue;
            for (int dx = 0; dx < dW; dx++) {
                int px = tx + dx;
                if (px < 0 || px >= SKIN_SIZE) continue;

                int src  = decal.getPixelRGBA(dx, dy);
                int srcA = (src >> 24) & 0xFF;
                if (srcA == 0) continue;

                int dst  = target.getPixelRGBA(px, py);
                int dstA = (dst >> 24) & 0xFF;
                if (dstA == 0) continue; // never paint over transparent skin areas

                int effectiveA = Math.round(srcA * alpha);
                if (effectiveA == 0) continue;

                float fA  = effectiveA / 255.0f;
                float fIA = 1.0f - fA;
                // ABGR: R=bits 0-7, G=bits 8-15, B=bits 16-23
                int outR = Math.round(((src >>  0) & 0xFF) * fA + ((dst >>  0) & 0xFF) * fIA);
                int outG = Math.round(((src >>  8) & 0xFF) * fA + ((dst >>  8) & 0xFF) * fIA);
                int outB = Math.round(((src >> 16) & 0xFF) * fA + ((dst >> 16) & 0xFF) * fIA);
                target.setPixelRGBA(px, py, (255 << 24) | (outR << 0) | (outG << 8) | (outB << 16));
            }
        }
    }

    /**
     * Alpha multiplier in [0, 1] based on wound age.
     * Fades in over {@link #FADE_IN_TICKS}, holds for {@code severity * 2400} ticks,
     * then fades out over {@link #FADE_OUT_TICKS}.
     */
    private static float computeAlpha(WoundMark mark, long gameTime) {
        float age          = gameTime - mark.createdTick();
        float holdTicks    = mark.severity() * 2400.0f;
        float fadeOutStart = FADE_IN_TICKS + holdTicks;
        float fadeOutEnd   = fadeOutStart  + FADE_OUT_TICKS;

        if (age < 0.0f)          return 0.0f;
        if (age < FADE_IN_TICKS) return age / FADE_IN_TICKS;
        if (age < fadeOutStart)  return 1.0f;
        if (age > fadeOutEnd)    return 0.0f;
        return 1.0f - (age - fadeOutStart) / FADE_OUT_TICKS;
    }

    /** Loads and caches a decal {@link NativeImage} by texture location, or returns {@code null} on failure. */
    private static NativeImage loadDecal(ResourceLocation location) {
        if (failedDecals.contains(location)) return null;

        NativeImage cached = decalCache.get(location);
        if (cached != null) return cached;

        try (var stream = Minecraft.getInstance()
                .getResourceManager().open(location)) {
            NativeImage img = NativeImage.read(stream);
            decalCache.put(location, img);
            return img;
        } catch (IOException e) {
            LOGGER.warn("Could not load wound decal '{}': {}", location, e.getMessage());
            failedDecals.add(location);
            return null;
        }
    }

    // =========================================================================

    private static final class PlayerOverlay {
        final DynamicTexture dynamicTexture;
        final ResourceLocation textureLocation;
        /** The real skin texture location, updated every frame via {@link #update}. */
        @Nullable ResourceLocation originalSkinLocation = null;
        /** Cached pixels for bundled/default skins loaded via the resource manager. */
        @Nullable NativeImage fallbackSkinPixels = null;
        /** True when a valid composite has been built and the mixin should redirect. */
        boolean hasWounds = false;

        PlayerOverlay(DynamicTexture texture, ResourceLocation loc) {
            this.dynamicTexture = texture;
            this.textureLocation = loc;
        }

        void close() {
            dynamicTexture.close();
            if (fallbackSkinPixels != null) {
                fallbackSkinPixels.close();
                fallbackSkinPixels = null;
            }
        }
    }
}
