package com.ext.healthextended.event;

import com.ext.healthextended.client.HealthTabPanel;
import com.ext.healthextended.client.PaperdollHighlighter;
import com.ext.healthextended.config.ClientConfig;
import com.ext.healthextended.data.BodyPart;
import com.ext.healthextended.data.PlayerBodyData;
import com.ext.healthextended.registry.ModAttachmentTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import static com.ext.healthextended.HealthExtended.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class InventoryGuiHandler {

    // Inventory screen dimensions (vanilla constants, width=176, height=166)
    private static final int INV_WIDTH  = 176;
    private static final int INV_HEIGHT = 166;
    private static int panelScrollOffset = 0;

    @SubscribeEvent
    public static void onInventoryRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player instanceof AbstractClientPlayer clientPlayer)) return;

        int leftPos = screen.getGuiLeft();
        int topPos = screen.getGuiTop();

        PlayerBodyData bodyData = clientPlayer.getData(ModAttachmentTypes.PLAYER_BODY_DATA);

        // ── Health tab panel (left or right of inventory) ─────────────
        int panelX = getPanelX(leftPos);
        int panelY = topPos;
        panelScrollOffset = Math.max(0, Math.min(panelScrollOffset, HealthTabPanel.getMaxScroll(bodyData)));

        // ── Paperdoll highlight on hover ────────────────────────────────
        GuiGraphics guiGraphics = event.getGuiGraphics();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        BodyPart paperdollHovered = PaperdollHighlighter.getHoveredBodyPart(mouseX, mouseY, leftPos, topPos);
        if (paperdollHovered != null) {
            panelScrollOffset = HealthTabPanel.ensureBodyPartVisible(bodyData, panelScrollOffset, paperdollHovered);
        }

        BodyPart panelHovered = HealthTabPanel.render(guiGraphics, bodyData, panelX, panelY, panelScrollOffset, mouseX, mouseY, paperdollHovered);
        BodyPart highlighted = panelHovered != null ? panelHovered : paperdollHovered;
        if (highlighted != null) {
            float hpPercent = bodyData.getHealth(highlighted).getHealthPercent();
            PaperdollHighlighter.renderHighlight(
                    guiGraphics, clientPlayer, highlighted, hpPercent,
                    leftPos, topPos, mouseX, mouseY);
        }
        if (paperdollHovered != null && !HealthTabPanel.isMouseOverPanel(mouseX, mouseY, panelX, panelY)) {
            HealthTabPanel.renderBodyPartTooltip(guiGraphics, mc.font, bodyData, paperdollHovered, (int) mouseX, (int) mouseY);
        } else {
            HealthTabPanel.renderPendingTooltip(guiGraphics, mc.font, bodyData, (int) mouseX, (int) mouseY);
        }
    }

    @SubscribeEvent
    public static void onInventoryMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player instanceof AbstractClientPlayer clientPlayer)) return;

        int leftPos = screen.getGuiLeft();
        int topPos = screen.getGuiTop();
        int panelX = getPanelX(leftPos);
        int panelY = topPos;

        if (!HealthTabPanel.isMouseOverPanel(event.getMouseX(), event.getMouseY(), panelX, panelY)) {
            return;
        }

        PlayerBodyData bodyData = clientPlayer.getData(ModAttachmentTypes.PLAYER_BODY_DATA);
        int nextScroll = HealthTabPanel.applyScroll(bodyData, panelScrollOffset, event.getScrollDeltaY());
        if (nextScroll != panelScrollOffset) {
            panelScrollOffset = nextScroll;
            event.setCanceled(true);
        }
    }

    private static int getPanelX(int leftPos) {
        return ClientConfig.anchorPanelRight()
                ? leftPos + INV_WIDTH + 4
                : leftPos - HealthTabPanel.PANEL_WIDTH - 4;
    }
}
