package com.AbdoAlabhar.LimitedTime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(LimitedTime.MODID)
public class LimitedTime {

    public static final String MODID = "timelimiter";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static TimeManager timeManager;

    public LimitedTime() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        LimitedTimeNetwork.register();

        LOGGER.info("TimeLimiter mod initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TimeLimiterGameRules.register();
            LOGGER.info("TimeLimiter game rules have been registered");
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);

        if (overworld != null) {
            timeManager = new TimeManager(overworld);
            LOGGER.info("TimeManager initialized for server");
        } else {
            LOGGER.error("Failed to get Overworld dimension - TimeManager not initialized!");
        }
    }

    public static TimeManager getNotifier() {
        return timeManager;
    }
}