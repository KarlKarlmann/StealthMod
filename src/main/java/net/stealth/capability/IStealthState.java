package net.stealth.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

public interface IStealthState extends INBTSerializable<CompoundTag> {
    // --- VARIABLE 1: DER PUFFER (Gedächtnis) ---
    void setSuspiciousLocation(Vec3 pos, float priority, long time);
    @Nullable
    Vec3 getSuspiciousLocation();
    float getSuspiciousPriority();
    long getSuspiciousTime();

    // --- VARIABLE 2: DER BEFEHL (Active Distraction) ---
    void setActiveDistraction(@Nullable Vec3 pos);
    @Nullable
    Vec3 getActiveDistraction();
    void clearActiveDistraction();

    // Alert Level & Sonstiges
    void setAlertLevel(float level);
    float getAlertLevel();
    void addAlertLevel(float amount);
    void decayAlertLevel(float amount);
    
    void setLastKnownPos(@Nullable Vec3 pos);
    @Nullable
    Vec3 getLastKnownPos();
    int getTimeSinceLastSeen();
    void setTimeSinceLastSeen(int ticks);
    void incrementTimeSinceLastSeen();
    void addKnownEnemy(java.util.UUID uuid);
    boolean isKnownEnemy(java.util.UUID uuid);
    void clearSuspiciousLocation();
    
    // --- NEU: DAS KAMPF-GEDÄCHTNIS (ITarget) ---
    void setITarget(@Nullable java.util.UUID uuid);
    @Nullable
    java.util.UUID getITarget();

    void copyFrom(IStealthState other);
}