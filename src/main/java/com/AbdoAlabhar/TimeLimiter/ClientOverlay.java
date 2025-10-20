package com.AbdoAlabhar.TimeLimiter;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "timelimiter", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientOverlay {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        if (mc.player == null) return;

        TimeNotifier notifier = TimeLimiter.getNotifier();
        if (notifier == null) return;

        long remainingMillis = notifier.getRemainingMillis(mc.player.getUUID());
        long baseMillis = notifier.getCountdownSeconds() * 1000L;

        // Extra time above base
        long extraMillis = Math.max(0, remainingMillis - baseMillis);

        // --- Background ---
        ResourceLocation TEXTURE = new ResourceLocation("timelimiter", "textures/gui/time_bg.png");
        int bgX = 5;
        int bgY = mc.getWindow().getGuiScaledHeight() - 26;
        int regionWidth = 69;
        int regionHeight = 27;
        RenderSystem.setShaderTexture(0, TEXTURE);
        g.blit(TEXTURE, bgX, bgY, 0, 0, regionWidth, regionHeight, 800, 800);

        // Inner bar dimensions
        int innerX = bgX + 3;
        int innerY = bgY + 3;
        int innerWidth = 63;
        int innerHeight = 21;

        // --- Base progress bar (green -> red) ---
        double baseProgress = Math.min(1.0, (double) Math.min(remainingMillis, baseMillis) / baseMillis);
        float hue = (float) (0.33f * baseProgress); // 0.33 green -> 0 red
        java.awt.Color colorObj = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
        int baseColor = (0xFF << 24) | (colorObj.getRed() << 16) | (colorObj.getGreen() << 8) | colorObj.getBlue();
        int baseFill = (int) (innerWidth * baseProgress);
        g.fill(innerX, innerY, innerX + baseFill, innerY + innerHeight, baseColor);

        // --- Extra time overlay (dark blue -> dark green) ---
        if (extraMillis > 0) {
            double extraProgress = Math.min(1.0, (double) extraMillis / baseMillis);
            int overlayWidth = (int) (innerWidth * extraProgress);

            // Dark blue → dark green gradient
            float overlayHue = (float) (0.6f - 0.27f * extraProgress); // 0.6 = dark blue, 0.33 = dark green
            float saturation = 1.0f;
            float brightness = 0.5f; // darker
            java.awt.Color overlayColorObj = java.awt.Color.getHSBColor(overlayHue, saturation, brightness);
            int overlayColor = (0xFF << 24) | (overlayColorObj.getRed() << 16) | (overlayColorObj.getGreen() << 8) | overlayColorObj.getBlue();

            g.fill(innerX, innerY, innerX + overlayWidth, innerY + innerHeight, overlayColor);
        }


        // --- Region time ---
        float scale = 0.9f;
        String regionTime = TimeLimiter.getRegionTime("Asia/Tokyo");
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.drawString(mc.font, Component.literal(regionTime), (int) ((bgX + 6) / scale), (int) ((bgY + 4) / scale), 0xFFFFFF, true);
        g.pose().popPose();

        // --- Countdown text ---
        long countdownMillis = remainingMillis;
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

}
