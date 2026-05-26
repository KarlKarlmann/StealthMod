package net.stealth.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.stealth.capability.StealthStateProvider;

import java.util.function.Supplier;

public class ClientBoundSyncStealthPacket {
    private final int entityId;
    private final float alertLevel;

    public ClientBoundSyncStealthPacket(int entityId, float alertLevel) {
        this.entityId = entityId;
        this.alertLevel = alertLevel;
    }

    public ClientBoundSyncStealthPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readInt();
        this.alertLevel = buffer.readFloat();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeFloat(alertLevel);
    }

    public static void handle(ClientBoundSyncStealthPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-seitige Ausführung sicherstellen
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(ClientBoundSyncStealthPacket msg) {
        // Wir suchen die Entity in der Client-Welt
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(msg.entityId);
            if (entity instanceof LivingEntity living) {
                // Wir aktualisieren die lokalen Daten (Capability)
                living.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
                    state.setAlertLevel(msg.alertLevel);
                });
            }
        }
    }
}