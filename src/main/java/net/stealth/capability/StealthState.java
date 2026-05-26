package net.stealth.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StealthState implements IStealthState {
    private float alertLevel = 0.0f;

    // Variable 1: Puffer
    private Vec3 suspiciousPos = null;
    private float suspiciousPriority = 0.0f;
    private long suspiciousTime = 0;

    // Variable 2: Befehl
    private Vec3 activeDistraction = null;

    private Vec3 lastKnownPos = null;
    private int timeSinceLastSeen = 0;
    
    // NEU: Das ITarget (Kampf-Gedächtnis)
    private UUID iTarget = null;

    private final java.util.Set<java.util.UUID> knownEnemies = new java.util.HashSet<>();

    @Override
    public void addKnownEnemy(java.util.UUID uuid) {
        this.knownEnemies.add(uuid);
    }

    @Override
    public boolean isKnownEnemy(java.util.UUID uuid) {
        return this.knownEnemies.contains(uuid);
    }

    @Override
    public void setITarget(@Nullable UUID uuid) {
        this.iTarget = uuid;
    }

    @Override
    public @Nullable UUID getITarget() {
        return this.iTarget;
    }

    @Override
    public void setSuspiciousLocation(Vec3 pos, float priority, long currentTime) {
        if (this.suspiciousPos == null) {
            this.suspiciousPos = pos;
            this.suspiciousPriority = priority;
            this.suspiciousTime = currentTime;
            return;
        }
        
        long ageOfOldLocation = currentTime - this.suspiciousTime;
        float decayFactor = 1.0f / (1.0f + (ageOfOldLocation / 200.0f));
        float effectiveOldPriority = this.suspiciousPriority * decayFactor;
        
        if (priority > effectiveOldPriority || ageOfOldLocation > 600) {
            this.suspiciousPos = pos;
            this.suspiciousPriority = priority;
            this.suspiciousTime = currentTime;
        }
    }

    @Override
    public void clearSuspiciousLocation() {
        this.suspiciousPos = null;
        this.suspiciousPriority = 0.0f;
        this.suspiciousTime = 0;
    }

    @Override
    public @Nullable Vec3 getSuspiciousLocation() { return this.suspiciousPos; }
    @Override
    public float getSuspiciousPriority() { return this.suspiciousPriority; }
    @Override
    public long getSuspiciousTime() { return this.suspiciousTime; }

    @Override
    public void setActiveDistraction(@Nullable Vec3 pos) {
        this.activeDistraction = pos;
    }

    @Override
    public @Nullable Vec3 getActiveDistraction() { return this.activeDistraction; }

    @Override
    public void clearActiveDistraction() { this.activeDistraction = null; }

    @Override
    public void setAlertLevel(float level) { this.alertLevel = Mth.clamp(level, 0.0f, 1.0f); }
    @Override
    public float getAlertLevel() { return this.alertLevel; }
    @Override
    public void addAlertLevel(float amount) { setAlertLevel(this.alertLevel + amount); }
    @Override
    public void decayAlertLevel(float amount) { setAlertLevel(this.alertLevel - amount); }

    @Override
    public void setLastKnownPos(@Nullable Vec3 pos) { this.lastKnownPos = pos; }
    @Override
    public @Nullable Vec3 getLastKnownPos() { return this.lastKnownPos; }
    @Override
    public int getTimeSinceLastSeen() { return this.timeSinceLastSeen; }
    @Override
    public void setTimeSinceLastSeen(int ticks) { this.timeSinceLastSeen = ticks; }
    @Override
    public void incrementTimeSinceLastSeen() { this.timeSinceLastSeen++; }

    @Override
    public void copyFrom(IStealthState other) {
        this.alertLevel = other.getAlertLevel();
        this.suspiciousPos = other.getSuspiciousLocation();
        this.suspiciousPriority = other.getSuspiciousPriority();
        this.suspiciousTime = other.getSuspiciousTime();
        this.activeDistraction = other.getActiveDistraction();
        this.lastKnownPos = other.getLastKnownPos();
        this.timeSinceLastSeen = other.getTimeSinceLastSeen();
        this.iTarget = other.getITarget();
        
        if (other instanceof StealthState impl) {
            this.knownEnemies.addAll(impl.knownEnemies);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Alert", alertLevel);
        if (suspiciousPos != null) {
            tag.putDouble("SX", suspiciousPos.x);
            tag.putDouble("SY", suspiciousPos.y);
            tag.putDouble("SZ", suspiciousPos.z);
            tag.putFloat("SP", suspiciousPriority);
            tag.putLong("ST", suspiciousTime);
        }
        if (activeDistraction != null) {
            tag.putDouble("AX", activeDistraction.x);
            tag.putDouble("AY", activeDistraction.y);
            tag.putDouble("AZ", activeDistraction.z);
        }
        if (iTarget != null) {
            tag.putUUID("ITarget", iTarget);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        alertLevel = nbt.getFloat("Alert");
        if (nbt.contains("SX")) {
            suspiciousPos = new Vec3(nbt.getDouble("SX"), nbt.getDouble("SY"), nbt.getDouble("SZ"));
            suspiciousPriority = nbt.getFloat("SP");
            suspiciousTime = nbt.getLong("ST");
        }
        if (nbt.contains("AX")) {
            activeDistraction = new Vec3(nbt.getDouble("AX"), nbt.getDouble("AY"), nbt.getDouble("AZ"));
        }
        if (nbt.contains("ITarget")) {
            iTarget = nbt.getUUID("ITarget");
        }
    }
}