package com.ext.healthextended.client;

import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.BodyPartHealth;
import com.ext.healthextended.data.HediffDef;
import com.ext.healthextended.data.HediffInstance;
import com.ext.healthextended.data.PlayerBodyData;
import com.ext.healthextended.logic.EffectConversionLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Renders the Rimworld-style health side panel to the left of the inventory.
 *
 * Background uses a plain dark fill for the alpha. Text uses vanilla font rendering
 * so it inherits any active resource pack font overrides.
 */
public class HealthTabPanel {

    public static final int PANEL_WIDTH  = 122;
    public static final int PANEL_HEIGHT = 166;

    private static final int ROW_HEIGHT       = 10;
    private static final int CONDITION_INDENT = 6;
    private static final int PADDING         = 4;
    private static final int HEART_SIZE      = 9;
    private static final float LABEL_SCALE   = 0.75f;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MARGIN = 4;
    private static final int SCROLLBAR_HANDLE_HEIGHT = 15;
    private static final int SCROLL_STEP = 14;
    private static final int HEDIFF_BAR_HEIGHT = 3;
    private static final int HEDIFF_BAR_GAP = 2;
    private static final int PART_LABEL_HEIGHT = 8;
    private static final int HEART_ROW_HEIGHT = 11;
    private static final int HEDIFF_ROW_HEIGHT = 13;
    private static final int PART_ROW_SPACING = 2;
    private static final int TOOLTIP_PADDING = 5;
    private static final int TOOLTIP_LINE_GAP = 3;
    private static final int PART_TEXT_INSET = 3;
    private static final int HEDIFF_TEXT_INSET = 3;
    private static final int HEDIFF_BAR_RIGHT_PADDING = 3;
    private static final float HEDIFF_LABEL_SCALE = 0.7f;
    // Horizontal offset where hearts begin within a combined part row
    private static final int HEARTS_X_OFFSET = 50;
    private static final int VANILLA_FRAME_SIZE = 4;
    private static final int VANILLA_TEXTURE_WIDTH = 256;
    private static final int VANILLA_TEXTURE_HEIGHT = 256;
    private static final int INVENTORY_TEXTURE_U = 0;
    private static final int INVENTORY_TEXTURE_V = 0;
    private static final int INVENTORY_TEXTURE_WIDTH = 176;
    private static final int INVENTORY_TEXTURE_HEIGHT = 166;
    private static final ResourceLocation INVENTORY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation HEART_POISONED_FULL = ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full");
    private static final ResourceLocation HEART_POISONED_HALF = ResourceLocation.withDefaultNamespace("hud/heart/poisoned_half");
    private static final ResourceLocation HEART_WITHERED_FULL = ResourceLocation.withDefaultNamespace("hud/heart/withered_full");
    private static final ResourceLocation HEART_WITHERED_HALF = ResourceLocation.withDefaultNamespace("hud/heart/withered_half");
    private static final ResourceLocation SCROLLER_BACKGROUND = ResourceLocation.withDefaultNamespace("widget/scroller_background");
    private static final ResourceLocation SCROLLER_HANDLE = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_HANDLE_DISABLED = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    // ARGB colors
    private static final int COLOR_BORDER    = 0xFF373737;
    private static final int COLOR_HEADER    = 0xFF404040;
    private static final int COLOR_HEDIFF    = 0xFFFF9966;
    private static final int COLOR_BAR_BG    = 0x80675541;
    private static final int COLOR_PANEL_FILL = 0xFFC6C6C6;
    private static final int COLOR_SLOT_FILL = 0xFF8B8B8B;
    private static final int COLOR_SLOT_HOVER = 0xFFC5C5C5;
    private static final int COLOR_SECTION_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_ROW_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_LINKED_ROW_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_DIVIDER_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_DIVIDER_DARK = 0xFF373737;
    private static final int COLOR_TOOLTIP_TEXT = 0xFFFFFFFF;

    @Nullable
    private static BodyPart pendingTooltipPart;
    private static List<Component> pendingTooltipLines = List.of();

    @Nullable
    public static BodyPart render(GuiGraphics guiGraphics, PlayerBodyData bodyData, int panelX, int panelY, int scrollOffset, double mouseX, double mouseY, @Nullable BodyPart emphasizedPart) {
        Font font = Minecraft.getInstance().font;
        pendingTooltipPart = null;
        pendingTooltipLines = List.of();

        renderPanelFrame(guiGraphics, panelX, panelY);

        int y = panelY + PADDING + 1;

        // Header
        String headerText = "Health";
        int headerX = panelX + (PANEL_WIDTH - font.width(headerText)) / 2;
        guiGraphics.drawString(font, headerText, headerX, y, COLOR_HEADER, false);
        y += ROW_HEIGHT + 2;

        y += 2;

        int contentTop = y;
        int contentBottom = panelY + PANEL_HEIGHT - PADDING;
        int contentLeft = panelX + PADDING;
        int contentHeight = contentBottom - contentTop;
        int maxScroll = getMaxScroll(bodyData);
        boolean showScrollbar = maxScroll > 0;
        int contentRight = getContentRight(panelX, showScrollbar);
        int clampedScroll = Math.max(0, Math.min(scrollOffset, maxScroll));
        BodyPart hoveredPart = null;
        boolean hoveringHediff = false;
        int outlinePad = 2;
        int scissorLeft = Math.max(panelX + 1, contentLeft - outlinePad);
        int scissorTop = Math.max(panelY + 1, contentTop - 1);
        int scissorRight = Math.min(panelX + PANEL_WIDTH - 1, contentRight + 1);
        int scissorBottom = Math.min(panelY + PANEL_HEIGHT - 1, contentBottom + 1);

        guiGraphics.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom);

        int contentY = contentTop - clampedScroll;

        for (BodyPart part : BodyPart.values()) {
            BodyPartHealth health = bodyData.getHealth(part);
            // hediffs stack, your kneecaps are dust
            List<DisplayHediff> hediffs = getDisplayHediffs(health);
            int sectionTop = contentY;
            int sectionHeight = getPartSectionHeight(health);
            int sectionBottom = contentY + sectionHeight - PART_ROW_SPACING;
            int heartsBottom = contentY + HEART_ROW_HEIGHT;
            boolean hoveredPartSummary = isPointInRect(mouseX, mouseY, contentLeft - 2, sectionTop - 1, contentRight, heartsBottom);
            boolean highlightLinked = emphasizedPart == part;
            boolean highlightSection = highlightLinked || hoveredPartSummary || (hoveringHediff && hoveredPart == part);
            drawSlotField(guiGraphics, contentLeft - 1, sectionTop, contentRight - 1, heartsBottom, hoveredPartSummary ? COLOR_SLOT_HOVER : COLOR_SLOT_FILL);

            float pct = health.getHealthPercent();
            int partColor = darkenColor(HealthColorHelper.getArgb(pct), 0.75f);
            // Vertically center the scaled label within the heart row
            int labelYOffset = (HEART_ROW_HEIGHT - Math.round(PART_LABEL_HEIGHT * LABEL_SCALE)) / 2;
            drawScaledString(guiGraphics, font, part.getDisplayName(), contentLeft + PART_TEXT_INSET, contentY + labelYOffset, partColor, LABEL_SCALE, true);
            drawHearts(guiGraphics, contentLeft + HEARTS_X_OFFSET, contentY + 1, health);
            contentY += HEART_ROW_HEIGHT;

            for (DisplayHediff hediff : hediffs) {
                int hediffX = contentLeft + CONDITION_INDENT + HEDIFF_TEXT_INSET;
                int rowTop = contentY;
                int rowBottom = contentY + HEDIFF_ROW_HEIGHT;
                boolean hoveredRow = isPointInRect(mouseX, mouseY, hediffX - 2, rowTop - 1, contentRight, rowBottom);
                drawSlotField(guiGraphics, contentLeft - 1, rowTop, contentRight - 1, rowBottom, hoveredRow ? COLOR_SLOT_HOVER : COLOR_SLOT_FILL);
                drawScaledString(guiGraphics, font, "- " + hediff.label(), hediffX, contentY + 1, COLOR_HEDIFF, HEDIFF_LABEL_SCALE, false);
                int barTop = contentY + 8;
                drawSeverityBar(guiGraphics, hediffX, barTop, contentRight - hediffX - HEDIFF_BAR_RIGHT_PADDING, hediff.severity());
                if (highlightLinked) {
                    drawOutline(guiGraphics, hediffX - 2, rowTop - 1, contentRight, rowBottom, COLOR_LINKED_ROW_HIGHLIGHT);
                }
                if (hoveredRow) {
                    hoveredPart = part;
                    pendingTooltipLines = hediff.tooltipLines();
                    pendingTooltipPart = null;
                    hoveringHediff = true;
                    drawOutline(guiGraphics, hediffX - 2, rowTop - 1, contentRight, rowBottom, COLOR_ROW_HIGHLIGHT);
                }
                contentY += HEDIFF_ROW_HEIGHT;
            }

            if (highlightSection) {
                int outlineBottom = Math.max(sectionTop + 1, contentY);
                drawOutline(guiGraphics, contentLeft - 2, sectionTop - 1, contentRight, outlineBottom, hoveredPartSummary || hoveredPart == part ? COLOR_ROW_HIGHLIGHT : COLOR_SECTION_HIGHLIGHT);
            }
            if (!hoveringHediff && hoveredPartSummary) {
                hoveredPart = part;
                pendingTooltipPart = part;
                pendingTooltipLines = List.of();
            }

            contentY += PART_ROW_SPACING;
        }

        guiGraphics.disableScissor();
        if (showScrollbar) {
            renderScrollbar(guiGraphics, panelX, contentTop, contentHeight, clampedScroll, maxScroll);
        }
        return hoveredPart;
    }

    public static void renderPendingTooltip(GuiGraphics guiGraphics, Font font, PlayerBodyData bodyData, int mouseX, int mouseY) {
        if (pendingTooltipPart != null) {
            renderBodyPartTooltip(guiGraphics, font, bodyData, pendingTooltipPart, mouseX, mouseY);
        } else if (!pendingTooltipLines.isEmpty()) {
            guiGraphics.renderComponentTooltip(font, pendingTooltipLines, mouseX, mouseY);
        }
    }

    public static void renderBodyPartTooltip(GuiGraphics guiGraphics, Font font, PlayerBodyData bodyData, BodyPart part, int mouseX, int mouseY) {
        BodyPartHealth health = bodyData.getHealth(part);
        guiGraphics.renderTooltip(
                font,
                List.of(Component.literal(part.getDisplayName())),
                Optional.of(new BodyPartTooltipVisual(health.getCurrentHp(), health.getMaxHp(), getHeartVariant(health))),
                mouseX,
                mouseY
        );
    }

    public static boolean isMouseOverPanel(double mouseX, double mouseY, int panelX, int panelY) {
        return mouseX >= panelX && mouseX < panelX + PANEL_WIDTH && mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT;
    }

    public static int getMaxScroll(PlayerBodyData bodyData) {
        int headerHeight = PADDING + ROW_HEIGHT + 2 + 4 + 1 + 4 + PADDING;
        int contentHeight = getContentHeight(bodyData);
        int viewportHeight = PANEL_HEIGHT - headerHeight;
        return Math.max(0, contentHeight - viewportHeight);
    }

    public static int applyScroll(PlayerBodyData bodyData, int currentScroll, double scrollDeltaY) {
        int nextScroll = currentScroll - (int) Math.signum(scrollDeltaY) * SCROLL_STEP;
        return Math.max(0, Math.min(nextScroll, getMaxScroll(bodyData)));
    }

    public static int ensureBodyPartVisible(PlayerBodyData bodyData, int currentScroll, BodyPart part) {
        int sectionTop = 0;
        for (BodyPart candidate : BodyPart.values()) {
            int sectionHeight = getPartSectionHeight(bodyData.getHealth(candidate));
            if (candidate == part) {
                int viewportHeight = getViewportHeight();
                int sectionBottom = sectionTop + sectionHeight;
                if (sectionTop < currentScroll) {
                    return Math.max(0, Math.min(sectionTop, getMaxScroll(bodyData)));
                }
                if (sectionBottom > currentScroll + viewportHeight) {
                    return Math.max(0, Math.min(sectionBottom - viewportHeight, getMaxScroll(bodyData)));
                }
                return currentScroll;
            }
            sectionTop += sectionHeight;
        }
        return currentScroll;
    }

    private static void drawHearts(GuiGraphics guiGraphics, int x, int y, BodyPartHealth health) {
        int containers = (health.getMaxHp() + 1) / 2;
        int fullHearts = health.getCurrentHp() / 2;
        boolean halfHeart = (health.getCurrentHp() & 1) == 1;
        HeartVariant heartVariant = getHeartVariant(health);

        for (int index = 0; index < containers; index++) {
            int heartX = x + index * (HEART_SIZE - 1);
            guiGraphics.blitSprite(HEART_CONTAINER, heartX, y, HEART_SIZE, HEART_SIZE);
        }

        for (int index = 0; index < fullHearts; index++) {
            int heartX = x + index * (HEART_SIZE - 1);
            guiGraphics.blitSprite(heartVariant.fullSprite(), heartX, y, HEART_SIZE, HEART_SIZE);
        }
        if (halfHeart) {
            int heartX = x + fullHearts * (HEART_SIZE - 1);
            guiGraphics.blitSprite(heartVariant.halfSprite(), heartX, y, HEART_SIZE, HEART_SIZE);
        }
    }

    private static HeartVariant getHeartVariant(BodyPartHealth health) {
        boolean poisoned = false;
        boolean withered = false;

        for (HediffInstance hediff : health.getHediffs()) {
            poisoned |= EffectConversionLogic.isPoisonStatusEffect(hediff);
            withered |= EffectConversionLogic.isWitherStatusEffect(hediff);
        }

        if (poisoned) {
            return HeartVariant.POISONED;
        }
        if (withered) {
            return HeartVariant.WITHERED;
        }
        return HeartVariant.NORMAL;
    }

    private static void drawSeverityBar(GuiGraphics guiGraphics, int x, int y, int width, float severity) {
        int clampedWidth = Math.max(0, width);
        if (clampedWidth <= 0) {
            return;
        }

        guiGraphics.fill(x, y, x + clampedWidth, y + HEDIFF_BAR_HEIGHT, COLOR_BAR_BG);
        int filledWidth = Math.round(clampedWidth * Math.max(0.0f, Math.min(1.0f, severity)));
        if (filledWidth > 0) {
            int color = HealthColorHelper.getArgb(1.0f - severity);
            guiGraphics.fill(x, y, x + filledWidth, y + HEDIFF_BAR_HEIGHT, color);
        }
    }

    private static void renderScrollbar(GuiGraphics guiGraphics, int panelX, int contentTop, int contentHeight, int scrollOffset, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }

        int scrollbarX = panelX + PANEL_WIDTH - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
        guiGraphics.blitSprite(SCROLLER_BACKGROUND, scrollbarX, contentTop, SCROLLBAR_WIDTH, contentHeight);

        int knobHeight = SCROLLBAR_HANDLE_HEIGHT;
        int travel = Math.max(1, contentHeight - knobHeight);
        int knobY = contentTop + Math.round((scrollOffset / (float) maxScroll) * travel);
        guiGraphics.blitSprite(SCROLLER_HANDLE, scrollbarX, knobY, SCROLLBAR_WIDTH, knobHeight);
    }

    private static void renderPanelFrame(GuiGraphics guiGraphics, int panelX, int panelY) {
        int innerWidth = PANEL_WIDTH - (VANILLA_FRAME_SIZE * 2);
        int innerHeight = PANEL_HEIGHT - (VANILLA_FRAME_SIZE * 2);

        drawInventorySlice(guiGraphics, panelX, panelY, INVENTORY_TEXTURE_U, INVENTORY_TEXTURE_V, VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE);
        drawInventorySlice(guiGraphics, panelX + PANEL_WIDTH - VANILLA_FRAME_SIZE, panelY, INVENTORY_TEXTURE_U + INVENTORY_TEXTURE_WIDTH - VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_V, VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE);
        drawInventorySlice(guiGraphics, panelX, panelY + PANEL_HEIGHT - VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_U, INVENTORY_TEXTURE_V + INVENTORY_TEXTURE_HEIGHT - VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE);
        drawInventorySlice(guiGraphics, panelX + PANEL_WIDTH - VANILLA_FRAME_SIZE, panelY + PANEL_HEIGHT - VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_U + INVENTORY_TEXTURE_WIDTH - VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_V + INVENTORY_TEXTURE_HEIGHT - VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE);

        tileInventoryRegion(guiGraphics, panelX + VANILLA_FRAME_SIZE, panelY, innerWidth, VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_U + VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_V, 1, VANILLA_FRAME_SIZE);
        tileInventoryRegion(guiGraphics, panelX + VANILLA_FRAME_SIZE, panelY + PANEL_HEIGHT - VANILLA_FRAME_SIZE, innerWidth, VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_U + VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_V + INVENTORY_TEXTURE_HEIGHT - VANILLA_FRAME_SIZE, 1, VANILLA_FRAME_SIZE);
        tileInventoryRegion(guiGraphics, panelX, panelY + VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE, innerHeight, INVENTORY_TEXTURE_U, INVENTORY_TEXTURE_V + VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE, 1);
        tileInventoryRegion(guiGraphics, panelX + PANEL_WIDTH - VANILLA_FRAME_SIZE, panelY + VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE, innerHeight, INVENTORY_TEXTURE_U + INVENTORY_TEXTURE_WIDTH - VANILLA_FRAME_SIZE, INVENTORY_TEXTURE_V + VANILLA_FRAME_SIZE, VANILLA_FRAME_SIZE, 1);
        guiGraphics.fill(panelX + VANILLA_FRAME_SIZE, panelY + VANILLA_FRAME_SIZE, panelX + PANEL_WIDTH - VANILLA_FRAME_SIZE, panelY + PANEL_HEIGHT - VANILLA_FRAME_SIZE, COLOR_PANEL_FILL);
        drawVanillaDivider(guiGraphics, panelX + VANILLA_FRAME_SIZE, panelY + 17, panelX + PANEL_WIDTH - VANILLA_FRAME_SIZE);
    }

    private static void drawInventorySlice(GuiGraphics guiGraphics, int x, int y, int u, int v, int width, int height) {
        guiGraphics.blit(INVENTORY_TEXTURE, x, y, u, v, width, height, VANILLA_TEXTURE_WIDTH, VANILLA_TEXTURE_HEIGHT);
    }

    private static void tileInventoryRegion(GuiGraphics guiGraphics, int x, int y, int width, int height, int u, int v, int tileWidth, int tileHeight) {
        for (int offsetY = 0; offsetY < height; offsetY += tileHeight) {
            int drawHeight = Math.min(tileHeight, height - offsetY);
            for (int offsetX = 0; offsetX < width; offsetX += tileWidth) {
                int drawWidth = Math.min(tileWidth, width - offsetX);
                guiGraphics.blit(INVENTORY_TEXTURE, x + offsetX, y + offsetY, u, v, drawWidth, drawHeight, VANILLA_TEXTURE_WIDTH, VANILLA_TEXTURE_HEIGHT);
            }
        }
    }

    private static void drawVanillaDivider(GuiGraphics guiGraphics, int x1, int y, int x2) {
        if (x2 <= x1) {
            return;
        }
        guiGraphics.fill(x1, y, x2, y + 1, COLOR_DIVIDER_LIGHT);
        guiGraphics.fill(x1, y + 1, x2, y + 2, COLOR_DIVIDER_DARK);
    }

    private static void drawSlotField(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int fillColor) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        guiGraphics.fill(x1, y1, x2, y2, fillColor);
        guiGraphics.fill(x1, y1, x2, y1 + 1, COLOR_DIVIDER_DARK);
        guiGraphics.fill(x1, y1 + 1, x1 + 1, y2, COLOR_DIVIDER_LIGHT);
        guiGraphics.fill(x1 + 1, y1 + 1, x1 + 2, y2, COLOR_DIVIDER_DARK);
        guiGraphics.fill(x1 + 1, y2 - 1, x2, y2, COLOR_DIVIDER_LIGHT);
        guiGraphics.fill(x2 - 1, y1 + 1, x2, y2, COLOR_DIVIDER_LIGHT);
    }

    private static int darkenColor(int argb, float factor) {
        float clampedFactor = Math.max(0.0f, Math.min(1.0f, factor));
        int alpha = (argb >>> 24) & 0xFF;
        int red = Math.round(((argb >>> 16) & 0xFF) * clampedFactor);
        int green = Math.round(((argb >>> 8) & 0xFF) * clampedFactor);
        int blue = Math.round((argb & 0xFF) * clampedFactor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int getContentHeight(PlayerBodyData bodyData) {
        int total = 0;
        for (BodyPart part : BodyPart.values()) {
            total += getPartSectionHeight(bodyData.getHealth(part));
        }
        return total;
    }

    private static int getPartSectionHeight(BodyPartHealth health) {
        return HEART_ROW_HEIGHT + getDisplayHediffs(health).size() * HEDIFF_ROW_HEIGHT + PART_ROW_SPACING;
    }

    private static List<DisplayHediff> getDisplayHediffs(BodyPartHealth health) {
        List<DisplayHediff> displayHediffs = new ArrayList<>();
        List<HediffInstance> prioritized = new ArrayList<>();
        Map<HediffDef, List<HediffInstance>> mergedWounds = new LinkedHashMap<>();

        for (HediffInstance hediff : health.getHediffs()) {
            if (isPrioritizedDisplayHediff(hediff)) {
                prioritized.add(hediff);
                continue;
            }

            mergedWounds.computeIfAbsent(hediff.getDefinition(), ignored -> new ArrayList<>()).add(hediff);
        }

        prioritized.sort(Comparator.comparingLong(HediffInstance::getDisplayOrder));
        for (HediffInstance hediff : prioritized) {
            displayHediffs.add(new DisplayHediff(buildHediffLabel(hediff), hediff.getSeverity(), buildHediffTooltip(buildHediffLabel(hediff), List.of(hediff))));
        }

        for (Map.Entry<HediffDef, List<HediffInstance>> entry : mergedWounds.entrySet()) {
            displayHediffs.add(buildMergedWoundDisplay(entry.getKey(), entry.getValue(), health.getMaxHp()));
        }

        return displayHediffs;
    }

    private static DisplayHediff buildMergedWoundDisplay(HediffDef definition, List<HediffInstance> instances, int partMaxHp) {
        String label = definition.getDisplayName();
        if (instances.size() > 1) {
            label += " (" + instances.size() + "x)";
        }

        int totalConsumed = 0;
        int totalMaxConsumable = 0;
        for (HediffInstance instance : instances) {
            totalConsumed += instance.getConsumedHp(partMaxHp);
            totalMaxConsumable += instance.getMaxConsumableHp(partMaxHp);
        }

        float severity = totalMaxConsumable <= 0 ? 0.0f : Math.min(1.0f, totalConsumed / (float) totalMaxConsumable);
        return new DisplayHediff(label, severity, buildHediffTooltip(label, instances));
    }

    private static boolean isPrioritizedDisplayHediff(HediffInstance hediff) {
        return hediff.isStatusEffect()
                || hediff.getDefinition() == HediffDef.STARVATION
                || hediff.getDefinition() == HediffDef.SUFFOCATION
                || hediff.getDefinition() == HediffDef.HEART_ATTACK
                || hediff.getDefinition() == HediffDef.BROKEN;
    }

    private static String buildHediffLabel(HediffInstance hediff) {
        String label = hediff.getDisplayName();
        if (!hediff.isStatusEffect()) {
            return label;
        }

        return label + " (" + (hediff.getAmplifier() + 1) + ") " + formatDurationTicks(hediff.getRemainingDurationTicks());
    }

    private static String formatDurationTicks(int ticks) {
        if (ticks >= Integer.MAX_VALUE / 2) {
            return "inf";
        }
        int totalSeconds = Math.max(0, ticks) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private static int getContentRight(int panelX, boolean showScrollbar) {
        if (showScrollbar) {
            return panelX + PANEL_WIDTH - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH - 3;
        }
        return panelX + PANEL_WIDTH - PADDING;
    }

    private static int getViewportHeight() {
        int headerHeight = PADDING + ROW_HEIGHT + 2 + 4 + 1 + 4 + PADDING;
        return PANEL_HEIGHT - headerHeight;
    }

    private static int getHeartRowWidth(BodyPartHealth health) {
        int containers = (health.getMaxHp() + 1) / 2;
        if (containers <= 0) {
            return 0;
        }
        return ((containers - 1) * (HEART_SIZE - 1)) + HEART_SIZE;
    }

    private static void drawScaledString(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color, float scale, boolean shadow) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0f);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(font, text, 0, 0, color, shadow);
        guiGraphics.pose().popPose();
    }

    private static List<Component> buildHediffTooltip(String label, List<HediffInstance> instances) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(label));

        Set<String> sources = new LinkedHashSet<>();
        for (HediffInstance instance : instances) {
            String source = instance.getSourceDescription();
            if (source == null || source.isBlank()) {
                continue;
            }
            for (String line : source.split("\\n")) {
                if (!line.isBlank()) {
                    sources.add(line);
                }
            }
        }

        for (String source : sources) {
            // Render merged causes as a single readable line when possible.
            if (sources.size() > 1) {
                tooltip.add(Component.literal(buildCombinedCauseLine(new ArrayList<>(sources))));
                return tooltip;
            }
            tooltip.add(Component.literal(source));
        }
        return tooltip;
    }

    private static String buildCombinedCauseLine(List<String> sources) {
        if (sources.isEmpty()) {
            return "";
        }
        if (sources.size() == 1) {
            return sources.getFirst();
        }

        final String causedByPrefix = "Caused by ";
        List<String> causeFragments = new ArrayList<>();
        boolean allCausedBy = true;
        for (String source : sources) {
            if (source.startsWith(causedByPrefix) && source.length() > causedByPrefix.length()) {
                causeFragments.add(source.substring(causedByPrefix.length()));
            } else {
                allCausedBy = false;
                break;
            }
        }

        if (allCausedBy) {
            return causedByPrefix + joinWithAnd(causeFragments);
        }
        return joinWithAnd(sources);
    }

    private static String joinWithAnd(List<String> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        if (entries.size() == 1) {
            return entries.getFirst();
        }
        if (entries.size() == 2) {
            return entries.get(0) + " and " + entries.get(1);
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                builder.append(index == entries.size() - 1 ? ", and " : ", ");
            }
            builder.append(entries.get(index));
        }
        return builder.toString();
    }

    private static boolean isPointInRect(double mouseX, double mouseY, int x1, int y1, int x2, int y2) {
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        guiGraphics.fill(x1, y1, x2, y1 + 1, color);
        guiGraphics.fill(x1, y2 - 1, x2, y2, color);
        guiGraphics.fill(x1, y1, x1 + 1, y2, color);
        guiGraphics.fill(x2 - 1, y1, x2, y2, color);
    }

    private record DisplayHediff(String label, float severity, List<Component> tooltipLines) {
    }

    public record BodyPartTooltipVisual(int currentHp, int maxHp, HeartVariant heartVariant) implements TooltipComponent {
    }

    public static ClientTooltipComponent createBodyPartTooltipComponent(BodyPartTooltipVisual tooltip) {
        return new ClientTooltipComponent() {
            @Override
            public int getHeight() {
                return HEART_SIZE + TOOLTIP_LINE_GAP;
            }

            @Override
            public int getWidth(Font font) {
                return getHeartRowWidth(tooltip.maxHp());
            }

            @Override
            public void renderText(Font font, int mouseX, int mouseY, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
            }

            @Override
            public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
                drawHearts(guiGraphics, x, y + TOOLTIP_LINE_GAP, tooltip.currentHp(), tooltip.maxHp(), tooltip.heartVariant());
            }
        };
    }

    private enum HeartVariant {
        NORMAL(HEART_FULL, HEART_HALF),
        POISONED(HEART_POISONED_FULL, HEART_POISONED_HALF),
        WITHERED(HEART_WITHERED_FULL, HEART_WITHERED_HALF);

        private final ResourceLocation fullSprite;
        private final ResourceLocation halfSprite;

        HeartVariant(ResourceLocation fullSprite, ResourceLocation halfSprite) {
            this.fullSprite = fullSprite;
            this.halfSprite = halfSprite;
        }

        public ResourceLocation fullSprite() {
            return fullSprite;
        }

        public ResourceLocation halfSprite() {
            return halfSprite;
        }
    }

    private static void drawHearts(GuiGraphics guiGraphics, int x, int y, int currentHp, int maxHp, HeartVariant heartVariant) {
        int containers = (maxHp + 1) / 2;
        int fullHearts = currentHp / 2;
        boolean halfHeart = (currentHp & 1) == 1;

        for (int index = 0; index < containers; index++) {
            int heartX = x + index * (HEART_SIZE - 1);
            guiGraphics.blitSprite(HEART_CONTAINER, heartX, y, HEART_SIZE, HEART_SIZE);
        }

        for (int index = 0; index < fullHearts; index++) {
            int heartX = x + index * (HEART_SIZE - 1);
            guiGraphics.blitSprite(heartVariant.fullSprite(), heartX, y, HEART_SIZE, HEART_SIZE);
        }
        if (halfHeart) {
            int heartX = x + fullHearts * (HEART_SIZE - 1);
            guiGraphics.blitSprite(heartVariant.halfSprite(), heartX, y, HEART_SIZE, HEART_SIZE);
        }
    }

    private static int getHeartRowWidth(int maxHp) {
        int containers = (maxHp + 1) / 2;
        if (containers <= 0) {
            return 0;
        }
        return ((containers - 1) * (HEART_SIZE - 1)) + HEART_SIZE;
    }
}
