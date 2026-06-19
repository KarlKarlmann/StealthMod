package net.stealth.registry;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.stealth.StealthMod;

public class StealthAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, StealthMod.MODID);

    // Tarnung: 0.0 = Keine Tarnung (Normal), 1.0 = Unsichtbar.
    public static final RegistryObject<Attribute> CAMOUFLAGE = ATTRIBUTES.register("camouflage",
            () -> new RangedAttribute("attribute.name.stealth.camouflage", 0.0D, -10.0D, 1.0D).setSyncable(true));

    // Dämpfung: 0.0 = Normaler Lärm, 1.0 = Lautlos.
    public static final RegistryObject<Attribute> MUFFLING = ATTRIBUTES.register("muffling",
            () -> new RangedAttribute("attribute.name.stealth.muffling", 0.0D, -10.0D, 1.0D).setSyncable(true));

    // Backstab Flat Bonus: Direkter Bonus-Schaden VOR dem Multiplikator. Standard = 0.0
    public static final RegistryObject<Attribute> BACKSTAB_DAMAGE = ATTRIBUTES.register("backstab_damage",
            () -> new RangedAttribute("attribute.name.stealth.backstab_damage", 0.0D, 0.0D, 1024.0D).setSyncable(true));

    // Backstab Multiplier Bonus: Skaliert den Config-Multiplikator. Standard = 1.0 (100%)
    public static final RegistryObject<Attribute> BACKSTAB_MULTIPLIER = ATTRIBUTES.register("backstab_multiplier",
            () -> new RangedAttribute("attribute.name.stealth.backstab_multiplier", 1.0D, 0.0D, 100.0D).setSyncable(true));

    public static void register(IEventBus eventBus) {
        ATTRIBUTES.register(eventBus);
    }
}