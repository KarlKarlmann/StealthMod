package net.stealth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stealth.StealthMod;
import net.stealth.util.StealthTextureHelper;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * ARCHITEKTUR: DECOUPLED PROPERTIES ENGINE (100% Resourcepack Driven)
 * Verwaltet die Positionierungskoordinaten des HUDs ausschließlich über das
 * active Ressourcen-System von Minecraft relativ zur Bildschirmmitte.
 */
@Mod.EventBusSubscriber(modid = StealthMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class StealthHudConfig {

    // Absolute Java-Fallbacks (Zentriert mit leichten Offsets)
    private static final int FALLBACK_EYE_X = 0;
    private static final int FALLBACK_EYE_Y = -24;

    private static final int FALLBACK_DAGGER_X = 0;
    private static final int FALLBACK_DAGGER_Y = 14;

    private static final int FALLBACK_SOUND_X = 0;
    private static final int FALLBACK_SOUND_Y = -24;

    // Gecachter Session-Zustand (Offsets von der Mitte)
    public static int eyeX = FALLBACK_EYE_X;
    public static int eyeY = FALLBACK_EYE_Y;

    public static int daggerX = FALLBACK_DAGGER_X;
    public static int daggerY = FALLBACK_DAGGER_Y;

    public static int soundX = FALLBACK_SOUND_X;
    public static int soundY = FALLBACK_SOUND_Y;

    /**
     * Lädt die HUD-Koordinaten aus dem ResourceManager.
     */
    public static void load() {
        Properties activeDefaults = loadActiveDefaults();
        applyProperties(activeDefaults);
    }

    private static Properties loadActiveDefaults() {
        Properties props = new Properties();
        ResourceLocation loc = new ResourceLocation("stealth", "config/default_hud.properties");
        
        try {
            var manager = Minecraft.getInstance().getResourceManager();
            Optional<Resource> resourceVal = manager.getResource(loc);
            
            if (resourceVal.isPresent()) {
                try (InputStream in = resourceVal.get().open()) {
                    props.load(in);
                    return props;
                }
            }
        } catch (Exception ignored) {}

        try (InputStream in = StealthHudConfig.class.getResourceAsStream("/assets/stealth/config/default_hud.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            System.err.println("[Stealth] Interner default_hud.properties-Fallback in der JAR fehlgeschlagen.");
        }
        return props;
    }

    private static void applyProperties(Properties p) {
        eyeX = Integer.parseInt(p.getProperty("eye_x", String.valueOf(FALLBACK_EYE_X)));
        eyeY = Integer.parseInt(p.getProperty("eye_y", String.valueOf(FALLBACK_EYE_Y)));

        daggerX = Integer.parseInt(p.getProperty("dagger_x", String.valueOf(FALLBACK_DAGGER_X)));
        daggerY = Integer.parseInt(p.getProperty("dagger_y", String.valueOf(FALLBACK_DAGGER_Y)));

        soundX = Integer.parseInt(p.getProperty("sound_x", String.valueOf(FALLBACK_SOUND_X)));
        soundY = Integer.parseInt(p.getProperty("sound_y", String.valueOf(FALLBACK_SOUND_Y)));
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            StealthHudConfig.load();
            StealthTextureHelper.clearCache();
        });
    }
}