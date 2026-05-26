package net.stealth.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.stealth.StealthMod;

public class StealthNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StealthMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
        );

        int id = 0;
        
        // Packet 0: Sync Stealth State (Auge)
        CHANNEL.registerMessage(id++,
            ClientBoundSyncStealthPacket.class,
            ClientBoundSyncStealthPacket::encode,
            ClientBoundSyncStealthPacket::new, 
            ClientBoundSyncStealthPacket::handle
        );

        // Packet 1: Sound Noise (Wellen) - NEU!
        CHANNEL.registerMessage(id++,
            ClientBoundSoundNoisePacket.class,
            ClientBoundSoundNoisePacket::encode,
            ClientBoundSoundNoisePacket::new, // Nutzt den Decoder-Konstruktor
            ClientBoundSoundNoisePacket::handle
        );
		
		CHANNEL.registerMessage(id++,
			ClientBoundVisibilityPacket.class,
			ClientBoundVisibilityPacket::encode,
			ClientBoundVisibilityPacket::new,
			ClientBoundVisibilityPacket::handle
		);
    }
}