package net.stealth.events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.stealth.StealthMod;

@Mod.EventBusSubscriber(modid = StealthMod.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    // Aktuell leer, da:
    // 1. Das HUD in 'StealthHud.java' gerendert wird.
    // 2. Sound-Events über das Netzwerk vom Server kommen ('ModCommonEvents' -> Packet -> HUD).
    
    // Hier können später Keybindings oder Fog-Render-Events rein.

}