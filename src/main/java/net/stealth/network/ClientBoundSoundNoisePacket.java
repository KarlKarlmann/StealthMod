package net.stealth.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientBoundSoundNoisePacket {
    private final float volume;

    // Konstruktor zum Erstellen (Server-Seite)
    public ClientBoundSoundNoisePacket(float volume) {
        this.volume = volume;
    }

    // Konstruktor zum Lesen (Client-Seite)
    public ClientBoundSoundNoisePacket(FriendlyByteBuf buffer) {
        this.volume = buffer.readFloat();
    }

    // Schreiben (Server -> Netzwerk)
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(this.volume);
    }

    // Ausführen (Client-Seite)
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // FIX: DistExecutor stellt sicher, dass Server-Instanzen nicht versuchen,
            // Client-Klassen wie 'StealthHud' zu laden (was das Spiel crashen würde).
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.stealth.client.StealthHud.triggerNoise(volume));
        });
        
        // FIX: Dies MUSS aufgerufen werden, sonst droppt Forge das Paket intern fehlerhaft!
        context.setPacketHandled(true);
    }
}