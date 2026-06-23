package net.stealth.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.stealth.client.StealthHud;
import net.stealth.util.ThreatLevel;

import java.util.function.Supplier;

/**
 * ARCHITEKTUR: KONSOLIDIERTES PLAYER-HUD-SYNC PAKET
 * Ersetzt den alten mob-zentrischen Sync und überträgt Sichtbarkeit, 
 * Bedrohungslevel und Audio-Dämpfungs-Flags in einer einzigen Netzwerkoperation.
 */
public class ClientBoundPlayerHudPacket {
    // Sentinel-Wert: Zeigt dem Client an, dass die Visibility lokal geschätzt werden soll
    public static final float NO_SERVER_VISIBILITY = -1.0f;

    private final float visibility;
    private final int threatOrdinal;
    private final boolean suppressSound;

    public ClientBoundPlayerHudPacket(float visibility, ThreatLevel threat, boolean suppressSound) {
        this.visibility = visibility;
        this.threatOrdinal = threat.ordinal();
        this.suppressSound = suppressSound;
    }

    public ClientBoundPlayerHudPacket(FriendlyByteBuf buf) {
        this.visibility = buf.readFloat();
        this.threatOrdinal = buf.readVarInt();
        this.suppressSound = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.visibility);
        buf.writeVarInt(this.threatOrdinal);
        buf.writeBoolean(this.suppressSound);
    }

    public static void handle(ClientBoundPlayerHudPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(ClientBoundPlayerHudPacket msg) {
        if (msg.suppressSound) {
            StealthHud.suppressSoundFor(1000);
        }
        StealthHud.updateThreatLevel(ThreatLevel.values()[msg.threatOrdinal]);
        StealthHud.updateVisibility(msg.visibility);
    }
}