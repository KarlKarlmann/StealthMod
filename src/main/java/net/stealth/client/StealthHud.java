package net.stealth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
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

import java.util.List;

@Mod.EventBusSubscriber(modid = StealthMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class StealthHud {

    // Texturen
    private static final ResourceLocation TEX_EYE_CLOSED = new ResourceLocation(StealthMod.MODID, "textures/gui/eyeclosed.png");
    private static final ResourceLocation TEX_EYE_HALF = new ResourceLocation(StealthMod.MODID, "textures/gui/eyehalfclosed.png");
    private static final ResourceLocation TEX_EYE_OPEN = new ResourceLocation(StealthMod.MODID, "textures/gui/eyeopen.png");
    private static final ResourceLocation TEX_EYE_WIDE = new ResourceLocation(StealthMod.MODID, "textures/gui/eyewide.png");
    
    private static final ResourceLocation TEX_DAGGER = new ResourceLocation(StealthMod.MODID, "textures/gui/dagger_icon.png");
    
    private static final ResourceLocation TEX_WAVE_1 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_1.png");
    private static final ResourceLocation TEX_WAVE_2 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_2.png");
    private static final ResourceLocation TEX_WAVE_3 = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_3.png");
    private static final ResourceLocation TEX_WAVE_1_L = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_1_l.png");
    private static final ResourceLocation TEX_WAVE_2_L = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_2_l.png");
    private static final ResourceLocation TEX_WAVE_3_L = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_3_l.png");

    private static float currentDisplayNoise = 0.0f;
    private static float currentVisibility = 0.0f;
    
    // NEU: Wir merken uns das ThreatLevel vom letzten Frame, um Übergänge zu erkennen
    private static ThreatLevel lastThreatLevel = ThreatLevel.NONE;

    public static void triggerNoise(float volume) {
        currentDisplayNoise = Math.min(2.5f, currentDisplayNoise + volume);
    }

    private enum ThreatLevel {
        NONE(0),       
        WATCHED(1),    
        SUSPICIOUS(2), 
        HUNTED(3);     

        int severity;
        ThreatLevel(int severity) {
            this.severity = severity;
        }
    }

    public static void updateVisibility(float visibility) {
        currentVisibility = visibility;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean isHudEnabled = true;
        try {
            if (StealthConfig.CLIENT != null && StealthConfig.CLIENT.HUD_ENABLED != null) {
                isHudEnabled = StealthConfig.CLIENT.HUD_ENABLED.get();
            }
        } catch (Exception ignored) {}
        if (!isHudEnabled) return;

        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        
        // Wir berechnen das ThreatLevel VOR dem Schleichen-Check, damit wir saubere Übergänge tracken
        ThreatLevel currentThreat = getThreatLevel(mc, mc.player);

        // --- SOUND LOGIK ---
        // Wenn man schleicht, das aktuelle Level HUNTED ist UND es im letzten Frame noch NICHT HUNTED war:
        if (mc.player.isCrouching() && currentThreat == ThreatLevel.HUNTED && lastThreatLevel != ThreatLevel.HUNTED) {
            // Spielt den Sound lokal nur für diesen Client ab (Volume 1.0, Pitch 1.0)
            mc.player.playSound(StealthSounds.DETECTED.get(), 1.0f, 1.0f);
        }

        // Speichere das aktuelle Level für den nächsten Tick
        lastThreatLevel = currentThreat;

        // Wenn man nicht schleicht, render das HUD nicht
        if (!mc.player.isCrouching()) return;

        // Wir übergeben das bereits berechnete currentThreat, um Performance zu sparen
        renderStealthHud(event, mc, mc.player, currentThreat);
    }

    private static void renderStealthHud(RenderGuiOverlayEvent.Post event, Minecraft mc, Player player, ThreatLevel maxThreat) {
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        PoseStack pose = event.getGuiGraphics().pose();

        // ---------------------------------------------------------
        // A. BACKSTAB ICON
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
                
                int iconSize = 16;
                int renderY = centerY + 14; 
                
                event.getGuiGraphics().blit(TEX_DAGGER, centerX - iconSize/2, renderY, 0, 0, iconSize, iconSize, iconSize, iconSize);
                
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }

        // ---------------------------------------------------------
        // B. DAS AUGE (Sichtbarkeits-Indikator)
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

        int eyeSize = 16;
        int eyeY = centerY - 24; 

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, 0.9f); 
        event.getGuiGraphics().blit(currentTexture, centerX - eyeSize/2, eyeY, 0, 0, eyeSize, eyeSize, eyeSize, eyeSize);

        // ---------------------------------------------------------
        // C. SCHALLWELLEN (Noise Feedback)
        // ---------------------------------------------------------
        if (currentDisplayNoise > 0.0f) {
            currentDisplayNoise = Math.max(0.0f, currentDisplayNoise - 0.03f);

            int waveSize = 16;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            if (currentDisplayNoise > 0.2f) {
                int rightX = centerX + (eyeSize / 2) + 2;
                int leftX = centerX - (eyeSize / 2) - 2 - waveSize;
                event.getGuiGraphics().blit(TEX_WAVE_1, rightX, eyeY, 0, 0, waveSize, waveSize, waveSize, waveSize);
                event.getGuiGraphics().blit(TEX_WAVE_1_L, leftX, eyeY, 0, 0, waveSize, waveSize, waveSize, waveSize);
            }

            if (currentDisplayNoise > 0.9f) {
                int rightX = centerX + (eyeSize / 2) + 2 + 4;
                int leftX = centerX - (eyeSize / 2) - 2 - waveSize - 4;
                event.getGuiGraphics().blit(TEX_WAVE_2, rightX, eyeY, 0, 0, waveSize, waveSize, waveSize, waveSize);
                event.getGuiGraphics().blit(TEX_WAVE_2_L, leftX, eyeY, 0, 0, waveSize, waveSize, waveSize, waveSize);
            }

            if (currentDisplayNoise > 1.6f) {
                int rightX = centerX + (eyeSize / 2) + 2 + 8;
                int leftX = centerX - (eyeSize / 2) - 2 - waveSize - 8;
                event.getGuiGraphics().blit(TEX_WAVE_3, rightX, eyeY, 0, 0, waveSize, waveSize, waveSize, waveSize);
                event.getGuiGraphics().blit(TEX_WAVE_3_L, leftX, eyeY, 0, 0, waveSize, waveSize, waveSize, waveSize);
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
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