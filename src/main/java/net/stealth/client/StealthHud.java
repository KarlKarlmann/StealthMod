package net.stealth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stealth.StealthMod;
import net.stealth.capability.StealthStateProvider;
import net.stealth.config.StealthConfig;
import net.stealth.registry.StealthSounds;
import net.stealth.util.StealthMath;
import net.stealth.util.StealthTextureHelper;

import java.util.List;

/**
 * ARCHITEKTUR: DYNAMIC STEALTH HUD RENDERER
 * Zeichnet das Auge (Bedrohungsgrad), den Meucheldolch (Backstab-Indikator)
 * und die Schallwellen (Geräuschepegel) auf den Bildschirm des Spielers.
 * Alle Koordinaten beziehen sich linear auf die exakte Bildschirmmitte (CENTER).
 */
@Mod.EventBusSubscriber(modid = StealthMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class StealthHud {

    private static final ResourceLocation TEX_EYE_CLOSED = new ResourceLocation(StealthMod.MODID, "textures/gui/eyeclosed.png");
    private static final ResourceLocation TEX_EYE_HALF = new ResourceLocation(StealthMod.MODID, "textures/gui/eyehalfclosed.png");
    private static final ResourceLocation TEX_EYE_OPEN = new ResourceLocation(StealthMod.MODID, "textures/gui/eyeopen.png");
    private static final ResourceLocation TEX_EYE_WIDE = new ResourceLocation(StealthMod.MODID, "textures/gui/eyewide.png");
    
    private static final ResourceLocation TEX_DAGGER = new ResourceLocation(StealthMod.MODID, "textures/gui/dagger_icon.png");
    
    private static final ResourceLocation TEX_WAVE_1 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_1.png");
    private static final ResourceLocation TEX_WAVE_2 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_2.png");
    private static final ResourceLocation TEX_WAVE_3 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_3.png");

    private static float currentDisplayNoise = 0.0f;
    private static float currentVisibility = 0.0f;
    private static ThreatLevel lastThreatLevel = ThreatLevel.NONE;
    private static boolean configLoaded = false;

    public static void triggerNoise(float volume) {
        currentDisplayNoise = Math.min(2.5f, currentDisplayNoise + volume);
    }

    private enum ThreatLevel {
        NONE(0), WATCHED(1), SUSPICIOUS(2), HUNTED(3);
        final int severity;
        ThreatLevel(int severity) { this.severity = severity; }
    }

    public static void updateVisibility(float visibility) {
        currentVisibility = visibility;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Lädt die Properties-Datei einmalig auf Clientseite aus dem Ressourcen-System
        if (!configLoaded) {
            StealthHudConfig.load();
            configLoaded = true;
        }

        boolean isHudEnabled = true;
        try {
            if (StealthConfig.CLIENT != null && StealthConfig.CLIENT.HUD_ENABLED != null) {
                isHudEnabled = StealthConfig.CLIENT.HUD_ENABLED.get();
            }
        } catch (Exception ignored) {}
        if (!isHudEnabled) return;

        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        
        ThreatLevel currentThreat = getThreatLevel(mc, mc.player);

        // Soundeffekt abspielen, wenn der Spieler entdeckt wird
        if (mc.player.isCrouching() && currentThreat == ThreatLevel.HUNTED && lastThreatLevel != ThreatLevel.HUNTED) {
            mc.player.playSound(StealthSounds.DETECTED.get(), 1.0f, 1.0f);
        }

        lastThreatLevel = currentThreat;

        // Das HUD wird nur angezeigt, wenn der Spieler schleicht (croucht)
        if (!mc.player.isCrouching()) return;

        renderStealthHud(event, mc, mc.player, currentThreat);
    }

    private static void renderStealthHud(RenderGuiOverlayEvent.Post event, Minecraft mc, Player player, ThreatLevel maxThreat) {
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        // ---------------------------------------------------------
        // A. MEUCHEL-DOLCH RENDERER
        // ---------------------------------------------------------
        boolean backstabEnabled = true;
        try {
            backstabEnabled = StealthConfig.COMMON.BACKSTAB_ENABLED.get();
        } catch (Exception ignored) {}

        if (backstabEnabled && mc.crosshairPickEntity instanceof Mob target) {
            if (StealthMath.isBehind(player, target)) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0f, 0.0f, 0.0f, 0.9f); 
                
                // Nutzt die echten Bilddimensionen des Dolch-Icons
                StealthTextureHelper.TextureDimensions dims = StealthTextureHelper.getDimensions(TEX_DAGGER, 16, 16);
                int x = getAbsoluteX(StealthHudConfig.daggerX, dims.width, width);
                int y = getAbsoluteY(StealthHudConfig.daggerY, dims.height, height);
                
                event.getGuiGraphics().blit(TEX_DAGGER, x, y, 0, 0, dims.width, dims.height, dims.width, dims.height);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }

        // ---------------------------------------------------------
        // B. AUGE RENDERER
        // ---------------------------------------------------------
        ResourceLocation currentTexture;
        if (maxThreat == ThreatLevel.HUNTED) {
            currentTexture = TEX_EYE_WIDE;
        } else if (maxThreat == ThreatLevel.SUSPICIOUS) {
            currentTexture = TEX_EYE_OPEN;
        } else if (maxThreat == ThreatLevel.WATCHED) {
            currentTexture = TEX_EYE_HALF;
        } else {
            currentTexture = TEX_EYE_CLOSED;
        }

        float r, g, b;
        if (maxThreat == ThreatLevel.HUNTED) {
            r = 1.0f; g = 0.0f; b = 0.0f; 
        } else {
            if (currentVisibility > 0.7) {
                r = 1.0f; g = 1.0f; b = 1.0f;
            } else if (currentVisibility > 0.35) {
                r = 0.6f; g = 0.6f; b = 0.6f;
            } else {
                r = 0.2f; g = 0.2f; b = 0.5f; 
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, 0.9f);

        // Nutzt die echten Maße des geladenen Auges
        StealthTextureHelper.TextureDimensions eyeDims = StealthTextureHelper.getDimensions(currentTexture, 16, 16);
        int eyeX = getAbsoluteX(StealthHudConfig.eyeX, eyeDims.width, width);
        int eyeY = getAbsoluteY(StealthHudConfig.eyeY, eyeDims.height, height);

        event.getGuiGraphics().blit(currentTexture, eyeX, eyeY, 0, 0, eyeDims.width, eyeDims.height, eyeDims.width, eyeDims.height);

        // ---------------------------------------------------------
        // C. SCHALLWELLEN (Combined PNG Renderer)
        // ---------------------------------------------------------
        if (currentDisplayNoise > 0.0f) {
            currentDisplayNoise = Math.max(0.0f, currentDisplayNoise - 0.03f);

            ResourceLocation waveTex = null;
            if (currentDisplayNoise > 1.6f) {
                waveTex = TEX_WAVE_3;
            } else if (currentDisplayNoise > 0.9f) {
                waveTex = TEX_WAVE_2;
            } else if (currentDisplayNoise > 0.2f) {
                waveTex = TEX_WAVE_1;
            }

            if (waveTex != null) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                
                // Schallwellen-Dimension aus PNG laden (standardmäßig 48x16 px)
                StealthTextureHelper.TextureDimensions sDims = StealthTextureHelper.getDimensions(waveTex, 48, 16);
                int sx = getAbsoluteX(StealthHudConfig.soundX, sDims.width, width);
                int sy = getAbsoluteY(StealthHudConfig.soundY, sDims.height, height);
                
                event.getGuiGraphics().blit(waveTex, sx, sy, 0, 0, sDims.width, sDims.height, sDims.width, sDims.height);
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    // =========================================================================
    // POSITIONS-BERECHNUNG (REIN CENTER-BASIERT)
    // =========================================================================

    /**
     * Berechnet die absolute Pixel-Koordinate auf der X-Achse relativ zur Bildschirmmitte.
     */
    public static int getAbsoluteX(int offset, int imgWidth, int screenWidth) {
        return (screenWidth / 2) - (imgWidth / 2) + offset;
    }

    /**
     * Berechnet die absolute Pixel-Koordinate auf der Y-Achse relativ zur Bildschirmmitte.
     */
    public static int getAbsoluteY(int offset, int imgHeight, int screenHeight) {
        return (screenHeight / 2) - (imgHeight / 2) + offset;
    }

    private static ThreatLevel getThreatLevel(Minecraft mc, Player player) {
        double radius = 30.0;
        AABB area = player.getBoundingBox().inflate(radius);
        List<Mob> nearbyMobs = mc.level.getEntitiesOfClass(Mob.class, area);
        ThreatLevel maxThreat = ThreatLevel.NONE;

        double fov = 80.0;
        try {
            fov = StealthConfig.COMMON.FOV_DEGREES.get();
        } catch(Exception ignored) {}

        for (Mob mob : nearbyMobs) {
            ThreatLevel currentMobThreat = ThreatLevel.NONE;
            float alert = mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY)
                             .map(state -> state.getAlertLevel())
                             .orElse(0.0f);

            boolean isTargetingMe = (mob.getTarget() == player);
            
            if (isTargetingMe || alert >= 0.99f) {
                currentMobThreat = ThreatLevel.HUNTED;
            } 
            else if (alert > 0.5f) {
                currentMobThreat = ThreatLevel.SUSPICIOUS;
            } 
            else {
                 if (StealthMath.isEntityInFieldOfView(mob, player, fov) &&
                     StealthMath.hasLineOfSight(mob, player)) {
                        currentMobThreat = ThreatLevel.WATCHED;
                 }
            }

            if (currentMobThreat.severity > maxThreat.severity) {
                maxThreat = currentMobThreat;
            }
        }
        return maxThreat;
    }
}