package com.AbdoAlabhar.LimitedTime;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Mod.EventBusSubscriber(modid = "timelimiter", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientOverlay {

    private static long clientRemainingMillis = 0;
    private static long lastRenderTime = System.currentTimeMillis();
    private static String timezone = "UTC";
    private static long baseMillis = 3600 * 1000L;
    private static boolean isFrozen = false; // NEW: Track frozen state
    private static Minecraft mc = Minecraft.getInstance();

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        GuiGraphics g = event.getGuiGraphics();
        if (mc.player == null) return;

        // Calculate elapsed time since last render
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastRenderTime;
        lastRenderTime = currentTime;

        // Only update countdown if NOT frozen AND game is not paused (singleplayer)
        if (!isFrozen && !mc.isPaused()) {
            if (clientRemainingMillis > 0) {
                clientRemainingMillis = Math.max(0, clientRemainingMillis - elapsed);
            }
        }

        // Don't render if no time left and no valid timezone
        if (clientRemainingMillis <= 0 && timezone.equals("UTC")) {
            return;
        }

        // Your existing rendering code (unchanged)...
        long extraMillis = Math.max(0, clientRemainingMillis - baseMillis);

        ResourceLocation TEXTURE = new ResourceLocation("timelimiter", "textures/gui/time_bg.png");
        int bgX = 5;
        int bgY = mc.getWindow().getGuiScaledHeight() - 26;
        int regionWidth = 95;
        int regionHeight = 27;
        RenderSystem.setShaderTexture(0, TEXTURE);
        g.blit(TEXTURE, bgX, bgY, 0, 0, regionWidth, regionHeight, 800, 800);

        int innerX = bgX + 3;
        int innerY = bgY + 3;
        int innerWidth = 89;
        int innerHeight = 21;
        double baseProgress = Math.min(1.0, (double) Math.min(clientRemainingMillis, baseMillis) / baseMillis);
        float hue = (float) (0.33f * baseProgress);
        java.awt.Color colorObj = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
        int baseColor = (0xFF << 24) | (colorObj.getRed() << 16) | (colorObj.getGreen() << 8) | colorObj.getBlue();
        int baseFill = (int) (innerWidth * baseProgress);
        g.fill(innerX, innerY, innerX + baseFill, innerY + innerHeight, baseColor);

        if (extraMillis > 0) {
            double extraProgress = Math.min(1.0, (double) extraMillis / baseMillis);
            int overlayWidth = (int) (innerWidth * extraProgress);
            float overlayHue = (float) (0.33f - 0.6f * extraProgress);
            float saturation = 1.0f;
            float brightness = 0.5f;
            java.awt.Color overlayColorObj = java.awt.Color.getHSBColor(overlayHue, saturation, brightness);
            int overlayColor = (0xFF << 24) | (overlayColorObj.getRed() << 16) | (overlayColorObj.getGreen() << 8) | overlayColorObj.getBlue();
            g.fill(innerX, innerY, innerX + overlayWidth, innerY + innerHeight, overlayColor);
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.systemDefault();
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        // Add "(FROZEN)" indicator when time is frozen
        String frozenIndicator = isFrozen || mc.isPaused() ? " (FROZEN)" : "";
        String regionTime = now.format(DateTimeFormatter.ofPattern("HH:mm")) + " (" + timezone + ")" + frozenIndicator;

        float scale = 0.9f;
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.drawString(mc.font, Component.literal(regionTime), (int) ((bgX + 6) / scale), (int) ((bgY + 4) / scale), 0xFFFFFF, true);
        g.pose().popPose();

        long countdownMillis = clientRemainingMillis;
        long seconds = countdownMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds %= 60;
        minutes %= 60;
        long millis = countdownMillis % 1000;
        String countdown = String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.drawString(mc.font, Component.literal(countdown), (int) ((bgX + 10) / scale), (int) ((bgY + 12) / scale), 0xFFFFFF, true);
        g.pose().popPose();
    }

    // Method to set the initial time from server packet
    public static void setInitialTime(long initialMillis, String tz, long base, boolean frozen) {
        clientRemainingMillis = initialMillis;
        timezone = tz;
        baseMillis = base;
        isFrozen = frozen; // NEW
        lastRenderTime = System.currentTimeMillis();
    }

    // Get current client-side remaining time
    public static long getCurrentClientTime() {
        return clientRemainingMillis;
    }
}