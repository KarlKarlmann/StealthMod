package net.stealth.events;

import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stealth.StealthMod;
import net.stealth.registry.StealthAttributes;

@Mod.EventBusSubscriber(modid = StealthMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventBusEvents {

    @SubscribeEvent
    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.PLAYER, StealthAttributes.CAMOUFLAGE.get())) {
            event.add(EntityType.PLAYER, StealthAttributes.CAMOUFLAGE.get());
        }
        if (!event.has(EntityType.PLAYER, StealthAttributes.MUFFLING.get())) {
            event.add(EntityType.PLAYER, StealthAttributes.MUFFLING.get());
        }
        if (!event.has(EntityType.PLAYER, StealthAttributes.BACKSTAB_DAMAGE.get())) {
            event.add(EntityType.PLAYER, StealthAttributes.BACKSTAB_DAMAGE.get());
        }
        if (!event.has(EntityType.PLAYER, StealthAttributes.BACKSTAB_MULTIPLIER.get())) {
            event.add(EntityType.PLAYER, StealthAttributes.BACKSTAB_MULTIPLIER.get());
        }
    }
}