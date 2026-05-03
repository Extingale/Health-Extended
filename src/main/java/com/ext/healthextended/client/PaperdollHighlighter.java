package com.ext.healthextended.client;

import com.ext.healthextended.data.BodyPart;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.ext.healthextended.HealthExtended.MODID;

/**
 * vanilla paperdoll region: x=[leftPos+26, leftPos+75], y=[topPos+8, topPos+93], scale=30.
 */
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class PaperdollHighlighter {

    // Vanilla paperdoll offsets from (leftPos, topPos)
    private static final int PD_X1    = 26,  PD_X2 = 75;
    private static final int PD_Y1    = 8,   PD_Y2 = 78;
    private static final int PD_SCALE = 30;
    private static final float OUTLINE_SCALE = 1.08f; // per-part scale-up from own pivot
    private static final float OUTLINE_BRIGHTNESS = 3.2f;

    private static final ThreadLocal<RenderDirective> ACTIVE_DIRECTIVE = new ThreadLocal<>();
    private static final ThreadLocal<RenderRestore> ACTIVE_RESTORE = new ThreadLocal<>();

    // [x1, y1, x2, y2] offsets from (leftPos, topPos) — straight-on view bounding boxes
    private static final Map<BodyPart, int[]> BOUNDS = new EnumMap<>(BodyPart.class);

    static {
        BOUNDS.put(BodyPart.HEAD,      new int[]{43, 10, 59, 28});
        BOUNDS.put(BodyPart.TORSO,     new int[]{43, 28, 59, 55});
        BOUNDS.put(BodyPart.RIGHT_ARM, new int[]{30, 28, 43, 55}); // player right = screen left
        BOUNDS.put(BodyPart.LEFT_ARM,  new int[]{59, 28, 72, 55}); // player left  = screen right
        BOUNDS.put(BodyPart.RIGHT_LEG, new int[]{43, 55, 52, 85});
        BOUNDS.put(BodyPart.LEFT_LEG,  new int[]{52, 55, 61, 85});
    }

    @Nullable
    public static BodyPart getHoveredBodyPart(double mouseX, double mouseY, int leftPos, int topPos) {
        double rx = mouseX - leftPos;
        double ry = mouseY - topPos;
        for (BodyPart part : BodyPart.values()) {
            int[] b = BOUNDS.get(part);
            if (rx >= b[0] && rx <= b[2] && ry >= b[1] && ry <= b[3]) {
                return part;
            }
        }
        return null;
    }

    /**
     * Renders a translucent tinted overlay over the specified body part.
     *
     * Replicates the exact transform vanilla uses for
     * InventoryScreen.renderEntityInInventoryFollowsMouse so the overlay sits
     * perfectly on top of the paperdoll and tracks with the cursor.
     */
    public static void renderHighlight(GuiGraphics guiGraphics, AbstractClientPlayer player,
                                       BodyPart bodyPart, float hpPercent,
                                       int leftPos, int topPos,
                                       double mouseX, double mouseY) {
        int[] hoverBounds = BOUNDS.get(bodyPart);
        if (hoverBounds == null) return;
        int clipX1 = leftPos + PD_X1 - 3;
        int clipY1 = topPos + PD_Y1 - 3;
        int clipX2 = leftPos + PD_X2 + 3;
        int clipY2 = topPos + PD_Y2 + 12;

        try {
            guiGraphics.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            guiGraphics.enableScissor(clipX1, clipY1, clipX2, clipY2);

            // Pass 1: enlarged shell for hovered part(s) only.
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0, 0.0, 200.0);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(OUTLINE_BRIGHTNESS, OUTLINE_BRIGHTNESS, OUTLINE_BRIGHTNESS, 0.95f);
            renderWithDirective(player, bodyPart, OUTLINE_SCALE, () ->
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            guiGraphics,
                            leftPos + PD_X1,
                            topPos + PD_Y1,
                            leftPos + PD_X2,
                            topPos + PD_Y2,
                            PD_SCALE,
                            0.0625f,
                            (float) mouseX,
                            (float) mouseY,
                            player
                    )
            );
            guiGraphics.pose().popPose();

                // Pass 2: health-tinted overlay (hovered part only).
                // This intentionally overlays the hovered part with body-part health color.
                float[] tint = getHealthTint(hpPercent);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0, 0.0, 201.0);
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(tint[0], tint[1], tint[2], tint[3]);
                renderWithDirective(player, bodyPart, 1.0f, () ->
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                        guiGraphics,
                        leftPos + PD_X1,
                        topPos + PD_Y1,
                        leftPos + PD_X2,
                        topPos + PD_Y2,
                        PD_SCALE,
                        0.0625f,
                        (float) mouseX,
                        (float) mouseY,
                        player
                    )
                );
            guiGraphics.pose().popPose();

                // No redraw pass here: vanilla paperdoll remains the base layer.
        } finally {
            guiGraphics.disableScissor();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        }
    }

    private static void renderWithDirective(AbstractClientPlayer player, BodyPart bodyPart, float scale, Runnable renderCall) {
        ACTIVE_DIRECTIVE.set(new RenderDirective(player.getId(), bodyPart, scale));
        try {
            renderCall.run();
        } finally {
            ACTIVE_DIRECTIVE.remove();
            ACTIVE_RESTORE.remove();
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        RenderDirective directive = ACTIVE_DIRECTIVE.get();
        if (directive == null || event.getEntity().getId() != directive.playerId()) {
            return;
        }

        if (!(event.getRenderer().getModel() instanceof HumanoidModel<?> model)) {
            return;
        }

        List<ModelPart> allParts = collectAllModelParts(model);
        IdentityHashMap<ModelPart, Boolean> visibility = new IdentityHashMap<>();
        IdentityHashMap<ModelPart, float[]> scales = new IdentityHashMap<>();
        for (ModelPart part : allParts) {
            visibility.put(part, part.visible);
            scales.put(part, new float[]{part.xScale, part.yScale, part.zScale});
            part.visible = false;
            part.xScale = 1.0f;
            part.yScale = 1.0f;
            part.zScale = 1.0f;
        }

        List<ModelPart> targetParts = resolveModelParts(model, directive.bodyPart());
        for (ModelPart part : targetParts) {
            part.visible = true;
            part.xScale = directive.scale();
            part.yScale = directive.scale();
            part.zScale = directive.scale();
        }

        ACTIVE_RESTORE.set(new RenderRestore(allParts, visibility, scales));
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        RenderDirective directive = ACTIVE_DIRECTIVE.get();
        if (directive == null || event.getEntity().getId() != directive.playerId()) {
            return;
        }

        RenderRestore restore = ACTIVE_RESTORE.get();
        if (restore == null) {
            return;
        }

        for (ModelPart part : restore.allParts) {
            Boolean wasVisible = restore.visibility.get(part);
            float[] s = restore.scales.get(part);
            if (wasVisible != null) {
                part.visible = wasVisible;
            }
            if (s != null && s.length == 3) {
                part.xScale = s[0];
                part.yScale = s[1];
                part.zScale = s[2];
            }
        }
        ACTIVE_RESTORE.remove();
    }

    private record RenderDirective(int playerId, BodyPart bodyPart, float scale) {}

    private static float[] getHealthTint(float hpPercent) {
        float hp = Math.max(0.0f, Math.min(1.0f, hpPercent));

        // Green (healthy) -> Yellow -> Red (critical).
        float r;
        float g;
        if (hp >= 0.5f) {
            float t = (1.0f - hp) * 2.0f;
            r = t;
            g = 1.0f;
        } else {
            float t = hp * 2.0f;
            r = 1.0f;
            g = t;
        }

        float b = 0.10f;
        float a = 0.58f;
        return new float[]{r, g, b, a};
    }

    private static final class RenderRestore {
        final List<ModelPart> allParts;
        final IdentityHashMap<ModelPart, Boolean> visibility;
        final IdentityHashMap<ModelPart, float[]> scales;

        RenderRestore(List<ModelPart> allParts, IdentityHashMap<ModelPart, Boolean> visibility, IdentityHashMap<ModelPart, float[]> scales) {
            this.allParts = allParts;
            this.visibility = visibility;
            this.scales = scales;
        }
    }

    /**
     * Returns all ModelParts for the given body part, including
     * outer-layer parts (hat, jacket, sleeves, pants) for player models.
     */
    private static List<ModelPart> resolveModelParts(HumanoidModel<?> model, BodyPart bodyPart) {
        List<ModelPart> parts = new ArrayList<>();
        switch (bodyPart) {
            case HEAD -> {
                parts.add(model.head);
                parts.add(model.hat);
            }
            case TORSO -> {
                parts.add(model.body);
                if (model instanceof PlayerModel<?> pm) parts.add(pm.jacket);
            }
            case RIGHT_ARM -> {
                parts.add(model.rightArm);
                if (model instanceof PlayerModel<?> pm) parts.add(pm.rightSleeve);
            }
            case LEFT_ARM -> {
                parts.add(model.leftArm);
                if (model instanceof PlayerModel<?> pm) parts.add(pm.leftSleeve);
            }
            case RIGHT_LEG -> {
                parts.add(model.rightLeg);
                if (model instanceof PlayerModel<?> pm) parts.add(pm.rightPants);
            }
            case LEFT_LEG -> {
                parts.add(model.leftLeg);
                if (model instanceof PlayerModel<?> pm) parts.add(pm.leftPants);
            }
        }
        return parts;
    }

    private static List<ModelPart> collectAllModelParts(HumanoidModel<?> model) {
        List<ModelPart> parts = new ArrayList<>();
        parts.add(model.head);
        parts.add(model.hat);
        parts.add(model.body);
        parts.add(model.rightArm);
        parts.add(model.leftArm);
        parts.add(model.rightLeg);
        parts.add(model.leftLeg);
        if (model instanceof PlayerModel<?> pm) {
            parts.add(pm.jacket);
            parts.add(pm.rightSleeve);
            parts.add(pm.leftSleeve);
            parts.add(pm.rightPants);
            parts.add(pm.leftPants);
        }
        return parts;
    }

}
