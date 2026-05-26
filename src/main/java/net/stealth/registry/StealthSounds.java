package net.stealth.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.stealth.StealthMod;

public class StealthSounds {
    // Erstellt ein Register für Custom Sounds
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, StealthMod.MODID);

    // Registriert deinen "detected" Sound
    public static final RegistryObject<SoundEvent> DETECTED = SOUNDS.register("detected",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(StealthMod.MODID, "detected")));

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}