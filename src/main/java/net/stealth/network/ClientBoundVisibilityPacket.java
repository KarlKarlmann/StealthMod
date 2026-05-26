package net.stealth.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientBoundVisibilityPacket {
    private final float visibility;

    public ClientBoundVisibilityPacket(float visibility) {
        this.visibility = visibility;
    }

    // Decoder (wird vom Channel aufgerufen)
    public ClientBoundVisibilityPacket(FriendlyByteBuf buf) {
        this.visibility = buf.readFloat();
    }

    // Encoder
    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.visibility);
    }

    // Handler
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // FIX: Auch hier DistExecutor einbauen, um einen Dedicated-Server Crash zu verhindern!
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.stealth.client.StealthHud.updateVisibility(this.visibility));
        });
        ctx.get().setPacketHandled(true);
    }
}