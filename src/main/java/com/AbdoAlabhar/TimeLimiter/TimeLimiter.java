package com.AbdoAlabhar.TimeLimiter;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TimeLimiter.MODID)
public class TimeLimiter {

    public static final String MODID = "timelimiter";
    private static final Logger LOGGER = LogUtils.getLogger();

    // singleton notifier (server-side)
    private static TimeNotifier notifier;

    public TimeLimiter() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
        bus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        // register config if you use one elsewhere (optional)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // nothing here
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // nothing here (overlay registered via @EventBusSubscriber)
    }

    /** Called by server start to initialize notifier with the Overworld ServerLevel */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld != null && notifier == null) {
            notifier = new TimeNotifier(overworld);
        }
    }

    /** Register commands using RegisterCommandsEvent to ensure dispatcher is available */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
    }

    public static TimeNotifier getNotifier() {
        return notifier;
    }

    // utility for overlay time
    public static String getRegionTime(String region) {
        java.time.ZonedDateTime time = java.time.ZonedDateTime.now(java.time.ZoneId.of(region));
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        return time.format(f);
    }
}
