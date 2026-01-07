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
    private static final ResourceLocation TEXTURE = new ResourceLocation("timelimiter", "textures/gui/time_bg.png");
    private static final Minecraft mc = Minecraft.getInstance();

    private static volatile long clientRemainingMillis = -1;
    private static volatile long clientAccumulatedMillis = 0;
    private static volatile long clientMaxAccumulatedMillis = 0;
    private static volatile long lastRenderTime = System.currentTimeMillis();
    private static volatile String timezone = "UTC";
    private static volatile long baseMillis = 3600 * 1000L;
    private static volatile boolean isFrozen = false;
    private static volatile boolean isFirstSync = true;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (clientRemainingMillis < 0 || mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastRenderTime;
        lastRenderTime = currentTime;

        if (!isFrozen && !mc.isPaused()) {
            clientRemainingMillis = Math.max(0, clientRemainingMillis - elapsed);
        }

        if (clientRemainingMillis <= 0) return;

        double progress = 1.0;
        if (clientAccumulatedMillis > 0) {
            progress = Math.min(1.0, (double) clientRemainingMillis / clientAccumulatedMillis);
        }

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

        float hue = (float) (0.33f * progress);
        int color = getColorFromHue(hue, 1.0f, 1.0f);
        int fillWidth = (int) (innerWidth * progress);
        g.fill(innerX, innerY, innerX + fillWidth, innerY + innerHeight, color);

        if (clientAccumulatedMillis > baseMillis) {
            double accumulationRatio = Math.min(1.0, (double) (clientAccumulatedMillis - baseMillis) / (clientMaxAccumulatedMillis - baseMillis));
            int accumulationBarHeight = 2;
            int accumulationBarY = innerY - accumulationBarHeight - 1;
            int accumulationWidth = (int) (innerWidth * accumulationRatio);
            g.fill(innerX, accumulationBarY, innerX + accumulationWidth, accumulationBarY + accumulationBarHeight, 0xFF00FF00);
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.systemDefault();
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        String frozenIndicator = (isFrozen || mc.isPaused()) ? " (FROZEN)" : "";
        String regionTime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " (" + timezone + ")" + frozenIndicator;

        float scale = 0.9f;
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.drawString(mc.font, Component.literal(regionTime), (int) ((bgX + 6) / scale), (int) ((bgY + 4) / scale), 0xFFFFFF, true);
        g.pose().popPose();

        long countdownMillis = clientRemainingMillis;
        long totalSeconds = countdownMillis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long millis = countdownMillis % 1000;

        String countdown;
        if (hours > 0) {
            countdown = String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        } else if (minutes > 0) {
            countdown = String.format("%02d:%02d.%03d", minutes, seconds, millis);
        } else {
            countdown = String.format("%02d.%03d", seconds, millis);
        }

        if (clientAccumulatedMillis > baseMillis) {
            long accHours = clientAccumulatedMillis / (1000 * 3600);
            long accMinutes = (clientAccumulatedMillis % (1000 * 3600)) / (1000 * 60);
            long accSeconds = (clientAccumulatedMillis % (1000 * 60)) / 1000;
            countdown += String.format(" (%d:%02d:%02d)", accHours, accMinutes, accSeconds);
        }

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.drawString(mc.font, Component.literal(countdown), (int) ((bgX + 10) / scale), (int) ((bgY + 12) / scale), 0xFFFFFF, true);
        g.pose().popPose();
    }

    private static int getColorFromHue(float hue, float saturation, float brightness) {
        java.awt.Color color = java.awt.Color.getHSBColor(hue, saturation, brightness);
        return (0xFF << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    public static synchronized void syncWithServer(long serverRemainingMillis, String tz, long base, boolean frozen) {
        if (isFirstSync) {
            clientRemainingMillis = serverRemainingMillis;
            isFirstSync = false;
        } else {
            clientRemainingMillis = Math.min(clientRemainingMillis, serverRemainingMillis);
        }

        timezone = (tz == null || tz.isEmpty()) ? "UTC" : tz;
        baseMillis = base > 0 ? base : 3600 * 1000L;
        clientAccumulatedMillis = serverRemainingMillis;
        clientMaxAccumulatedMillis = baseMillis * 3;
        isFrozen = frozen;
        lastRenderTime = System.currentTimeMillis();
    }

    public static void setAccumulationInfo(long accumulated, long maxAccumulated) {
        clientAccumulatedMillis = accumulated;
        clientMaxAccumulatedMillis = maxAccumulated;
    }

    public static void resetOnDisconnect() {
        isFirstSync = true;
    }

    public static long getCurrentClientTime() {
        return clientRemainingMillis;
    }
}