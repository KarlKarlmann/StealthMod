package net.stealth.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.stealth.StealthMod;

/**
 * Registriert alle verbleibenden Netzwerkpakete des StealthMods.
 * Das alte SyncStealthState- und Visibility-Paket wurden durch das kombinierte HUD-Paket ersetzt.
 */
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
        
        // Packet 0: Sound Noise (Wellen)
        CHANNEL.registerMessage(id++,
            ClientBoundSoundNoisePacket.class,
            ClientBoundSoundNoisePacket::encode,
            ClientBoundSoundNoisePacket::new,
            ClientBoundSoundNoisePacket::handle
        );

        // Packet 1: Kombinierter Player-HUD-Sync (Threat + Visibility + Sound-Suppress)
        CHANNEL.registerMessage(id++,
            ClientBoundPlayerHudPacket.class,
            ClientBoundPlayerHudPacket::encode,
            ClientBoundPlayerHudPacket::new,
            ClientBoundPlayerHudPacket::handle
        );
    }
}