package net.stealth.events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stealth.StealthMod;
import net.stealth.client.StealthHud;

/**
 * Registriert alle client-seitigen Events, die über den MOD Event Bus laufen müssen.
 */
@Mod.EventBusSubscriber(modid = StealthMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModBusEvents {

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        // Registriert dein eigenes HUD-Layer.
        // registerAboveAll sorgt dafür, dass es ganz oben gerendert wird und nicht von 
        // anderen Mod-Overlays verdeckt wird. Unabhängig vom Vanilla Fadenkreuz!
        event.registerAboveAll("stealth_hud", StealthHud.STEALTH_HUD_OVERLAY);
    }
}