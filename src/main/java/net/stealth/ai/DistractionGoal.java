package net.stealth.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.stealth.capability.StealthStateProvider;

import java.util.EnumSet;

public class DistractionGoal extends Goal {
    private final Mob mob;
    private Vec3 targetPos;
    private int ticksStuck;
    private int scanTicks;
    
    // Neu: Offsets für das nervöse Umschauen
    private double lookOffsetX;
    private double lookOffsetZ;

    public DistractionGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Wir brechen nur ab, wenn wir wirklich im Kampf sind
        if (mob.getTarget() != null) return false;

        // Wir prüfen nur die "ActiveDistraction" Variable (Befehl)
        return mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).map(state -> {
            Vec3 pos = state.getActiveDistraction();
            if (pos == null) return false;
            
            // Sind wir schon da?
            if (mob.position().distanceToSqr(pos) < 4.0) {
                state.clearActiveDistraction();
                return false;
            }
            
            this.targetPos = pos;
            return true;
        }).orElse(false);
    }

    @Override
    public void start() {
        // Wir holen uns das Alert-Level für die Laufgeschwindigkeit
        float alertLevel = mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY)
                              .map(state -> state.getAlertLevel())
                              .orElse(0.0f);
        
        // Dynamische Geschwindigkeit: 
        // Alert 0.0 -> Speed 0.8 (ruhiges Gehen)
        // Alert 1.0 -> Speed 1.4 (panisches Rennen)
        double speed = 0.8 + (alertLevel * 0.6);
        
        mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, speed);
        
        this.ticksStuck = 0;
        this.scanTicks = 0;
        this.lookOffsetX = 0.0;
        this.lookOffsetZ = 0.0;
    }

    @Override
    public void tick() {
        if (targetPos != null) {
            float alertLevel = mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY)
                                  .map(state -> state.getAlertLevel())
                                  .orElse(0.0f);

            // --- NERVÖSES UMSCHAUEN ---
            if (alertLevel > 0.4f) {
                // Je höher der Stress, desto häufiger zuckt der Kopf (alle 5 bis 15 Ticks)
                int twitchRate = alertLevel > 0.8f ? 5 : 15;
                
                // Generiere neue zufällige Blickpunkte in Zielrichtung
                if (this.scanTicks % twitchRate == 0) {
                    this.lookOffsetX = (mob.getRandom().nextDouble() - 0.5) * 8.0; 
                    this.lookOffsetZ = (mob.getRandom().nextDouble() - 0.5) * 8.0;
                }
                
                // Schnelle Kopfdrehung (60.0F statt 30.0F) zu dem zufälligen Punkt
                mob.getLookControl().setLookAt(
                        targetPos.x + this.lookOffsetX, 
                        mob.getEyeY(), // Schaut auf Augenhöhe geradeaus, nicht auf den Boden
                        targetPos.z + this.lookOffsetZ, 
                        60.0F, 60.0F);
            } else {
                // Bei geringem Alert: Ruhiges und langsames Fokussieren exakt auf das Ziel
                mob.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z, 30.0F, 30.0F);
            }
        }
        
        if (mob.getNavigation().isStuck() || ticksStuck > 100) {
            stop();
        }
        
        ticksStuck++;
        scanTicks++;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getTarget() != null) return false;

        return mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).map(state -> {
            Vec3 currentDistraction = state.getActiveDistraction();

            // TUNNELBLICK-FIX: Wenn das Gehirn ein LAUTERES Geräusch empfangen hat,
            // ist currentDistraction plötzlich eine andere Koordinate. Wir brechen DIESES 
            // Goal sofort ab! Es startet im nächsten Frame neu mit der wichtigen Koordinate.
            if (currentDistraction == null || !currentDistraction.equals(this.targetPos)) {
                return false;
            }

            return true;
        }).orElse(false);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        this.scanTicks = 0;
        this.ticksStuck = 0;
    }
}