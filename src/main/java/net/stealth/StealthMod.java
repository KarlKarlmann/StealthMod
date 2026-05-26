package net.stealth;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.stealth.config.StealthConfig;
import net.stealth.events.ModCommonEvents;
import net.stealth.network.StealthNetwork;
import net.stealth.registry.StealthAttributes;
import net.stealth.registry.StealthSounds;
import org.slf4j.Logger;

@Mod(StealthMod.MODID)
public class StealthMod {
    public static final String MODID = "stealth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StealthMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, StealthConfig.SPEC);
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, StealthConfig.CLIENT_SPEC);

        StealthAttributes.register(modEventBus); 
        StealthSounds.register(modEventBus);

        modEventBus.addListener(this::setup);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ModCommonEvents());
    }

    private void setup(final FMLCommonSetupEvent event) {
        StealthNetwork.register();
    }
}