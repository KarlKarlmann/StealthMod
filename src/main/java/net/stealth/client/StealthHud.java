package net.stealth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.stealth.StealthMod;
import net.stealth.config.StealthConfig;
import net.stealth.registry.StealthSounds;
import net.stealth.util.StealthMath;
import net.stealth.util.StealthTextureHelper;
import net.stealth.util.ThreatLevel;

/**
 * ARCHITEKTUR: DYNAMIC STEALTH HUD RENDERER (IGuiOverlay Edition)
 * Zeichnet das Auge (Bedrohungsgrad), den Meucheldolch (Backstab-Indikator)
 * und die Schallwellen (Geräuschepegel) auf den Bildschirm des Spielers.
 * Das System holt sich die Bedrohungsdaten nun direkt aus dem gecachten server-seitigen Sync,
 * wodurch Performance-Einbrüche durch client-seitiges Entity-Scanning komplett eliminiert werden.
 */
public class StealthHud {

    private static final ResourceLocation TEX_EYE_CLOSED = new ResourceLocation(StealthMod.MODID, "textures/gui/eyeclosed.png");
    private static final ResourceLocation TEX_EYE_HALF = new ResourceLocation(StealthMod.MODID, "textures/gui/eyehalfclosed.png");
    private static final ResourceLocation TEX_EYE_OPEN = new ResourceLocation(StealthMod.MODID, "textures/gui/eyeopen.png");
    private static final ResourceLocation TEX_EYE_WIDE = new ResourceLocation(StealthMod.MODID, "textures/gui/eyewide.png");
    private static long suppressSoundUntil = 0;

    private static final ResourceLocation TEX_DAGGER = new ResourceLocation(StealthMod.MODID, "textures/gui/dagger_icon.png");
    
    private static final ResourceLocation TEX_WAVE_1 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_1.png");
    private static final ResourceLocation TEX_WAVE_2 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_2.png");
    private static final ResourceLocation TEX_WAVE_3 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_3.png");

    private static float currentDisplayNoise = 0.0f;
    private static float currentVisibility = 0.0f;
    private static ThreatLevel lastThreatLevel = ThreatLevel.NONE;
    private static boolean configLoaded = false;

    private static ThreatLevel currentThreatLevel = ThreatLevel.NONE;
    private static volatile boolean serverVisibilitySyncOff = false;

    public static void triggerNoise(float volume) {
        currentDisplayNoise = Math.min(2.5f, currentDisplayNoise + volume);
    }

    public static void suppressSoundFor(long milliseconds) {
        suppressSoundUntil = System.currentTimeMillis() + milliseconds;
    }   

    public static void updateThreatLevel(ThreatLevel level) {
        currentThreatLevel = level;
    }

    public static void updateVisibility(float visibility) {
        if (visibility < 0f) {
            serverVisibilitySyncOff = true; 
        } else {
            serverVisibilitySyncOff = false;
            currentVisibility = visibility;
        }
    }

    // Das eigenständige Overlay für dein Stealth HUD!
    public static final IGuiOverlay STEALTH_HUD_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

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

        // Lokaler Fallback-Rechner, falls der Server die Berechnung deaktiviert hat
        if (serverVisibilitySyncOff && mc.player.tickCount % 5 == 0) {
            currentVisibility = (float) StealthMath.getVisibilityScore(mc.player);
        }

        ThreatLevel currentThreat = currentThreatLevel; 

        // Soundeffekt abspielen bei Entdeckung
        if (mc.player.isCrouching() && currentThreat == ThreatLevel.HUNTED && lastThreatLevel != ThreatLevel.HUNTED) {
            if (System.currentTimeMillis() > suppressSoundUntil) {
                mc.player.playSound(StealthSounds.DETECTED.get(), 1.0f, 1.0f);
            }
        }

        lastThreatLevel = currentThreat;

        if (!mc.player.isCrouching()) return;

        renderStealthHud(guiGraphics, mc, mc.player, currentThreat, width, height);
    };

    private static void renderStealthHud(GuiGraphics guiGraphics, Minecraft mc, Player player, ThreatLevel maxThreat, int width, int height) {
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
                
                StealthTextureHelper.TextureDimensions dims = StealthTextureHelper.getDimensions(TEX_DAGGER, 16, 16);
                int x = getAbsoluteX(StealthHudConfig.daggerX, dims.width, width);
                int y = getAbsoluteY(StealthHudConfig.daggerY, dims.height, height);
                
                guiGraphics.blit(TEX_DAGGER, x, y, 0, 0, dims.width, dims.height, dims.width, dims.height);
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

        StealthTextureHelper.TextureDimensions eyeDims = StealthTextureHelper.getDimensions(currentTexture, 16, 16);
        int eyeX = getAbsoluteX(StealthHudConfig.eyeX, eyeDims.width, width);
        int eyeY = getAbsoluteY(StealthHudConfig.eyeY, eyeDims.height, height);

        guiGraphics.blit(currentTexture, eyeX, eyeY, 0, 0, eyeDims.width, eyeDims.height, eyeDims.width, eyeDims.height);

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
                
                StealthTextureHelper.TextureDimensions sDims = StealthTextureHelper.getDimensions(waveTex, 48, 16);
                int sx = getAbsoluteX(StealthHudConfig.soundX, sDims.width, width);
                int sy = getAbsoluteY(StealthHudConfig.soundY, sDims.height, height);
                
                guiGraphics.blit(waveTex, sx, sy, 0, 0, sDims.width, sDims.height, sDims.width, sDims.height);
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    public static int getAbsoluteX(int offset, int imgWidth, int screenWidth) {
        return (screenWidth / 2) - (imgWidth / 2) + offset;
    }

    public static int getAbsoluteY(int offset, int imgHeight, int screenHeight) {
        return (screenHeight / 2) - (imgHeight / 2) + offset;
    }
}