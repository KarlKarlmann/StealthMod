package net.stealth.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stealth.StealthMod;
import net.stealth.capability.IStealthState;
import net.stealth.capability.StealthState;
import net.stealth.capability.StealthStateProvider;

@Mod.EventBusSubscriber(modid = StealthMod.MODID)
public class ModCapabilityEvents {

    // Registriert das Interface beim Start
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IStealthState.class);
    }

    // Klebt die Daten an jede lebende Entität (Spieler, Zombies, Schweine...)
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            if (!event.getObject().getCapability(StealthStateProvider.STEALTH_CAPABILITY).isPresent()) {
                event.addCapability(new ResourceLocation(StealthMod.MODID, "stealth_state"), new StealthStateProvider());
            }
        }
    }
}