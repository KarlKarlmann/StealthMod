package net.stealth.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.stealth.StealthMod;

public class StealthTags {
    public static class Entities {
        // Tag für Mobs, die das Stealth-System komplett ignorieren (z.B. Bosse)
        public static final TagKey<EntityType<?>> IGNORES_STEALTH = tag("ignores_stealth");
        
        // Tag für Mobs, die im Dunkeln perfekt sehen (ignorieren das Lichtlevel des Spielers)
        public static final TagKey<EntityType<?>> IGNORES_LIGHTLEVEL = tag("ignores_lightlevel");

        private static TagKey<EntityType<?>> tag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(StealthMod.MODID, name));
        }
    }
}