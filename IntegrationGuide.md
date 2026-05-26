Stealth Mod Integration Guide

Der Mod registriert zwei Attribute zur Steuerung der Entdeckungsmechanik.

Attribute

1. stealth:camouflage

Steuert die visuelle Sichtbarkeit.

Basis-Wert: 0.0

Logik: Sichtbarkeit = 1.0 - Camouflage

Werte:

+0.2 = 20% schwerer zu sehen.

+1.0 = Unsichtbar.

-0.5 = 50% leichter zu sehen (Leuchtend).

2. stealth:muffling

Steuert die Lautstärke von Spieler-Geräuschen.

Basis-Wert: 0.0

Logik: Lautstärke = 1.0 - Muffling

Werte:

+0.5 = Schritte sind halb so laut / halber Radius.

+1.0 = Lautlos.

Integration via Datapacks / JSON

Du kannst Items, Rüstungen oder Potion-Effekte erstellen, die diese Attribute modifizieren.

Beispiel: Tarn-Rüstung (Item Modifier)

{
  "function": "minecraft:set_attributes",
  "modifiers": [
    {
      "attribute": "stealth:camouflage",
      "amount": 0.25,
      "operation": "addition",
      "slot": "chest",
      "name": "Camo Bonus"
    }
  ]
}


Beispiel: Schalldämpfer-Stiefel (Item Modifier)

{
  "function": "minecraft:set_attributes",
  "modifiers": [
    {
      "attribute": "stealth:muffling",
      "amount": 0.5,
      "operation": "addition",
      "slot": "feet",
      "name": "Muffling Bonus"
    }
  ]
}


Java Integration

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

// Attribute sicher holen (Soft Dependency)
Attribute camoAttr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("stealth", "camouflage"));
Attribute muffAttr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("stealth", "muffling"));

if (camoAttr != null) {
    // Attribut verwenden...
}
