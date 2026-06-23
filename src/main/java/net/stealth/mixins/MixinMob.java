package net.stealth.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo; 
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.stealth.capability.StealthStateProvider;
import net.stealth.config.StealthConfig;
import net.stealth.util.StealthMath;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(Mob.class)
public abstract class MixinMob extends LivingEntity implements VibrationSystem, VibrationSystem.User {

    @Unique
    private VibrationSystem.Data stealth$vibrationData;
    @Unique
    private VibrationSystem.Listener stealth$vibrationListener;
    @Unique
    private DynamicGameEventListener<VibrationSystem.Listener> stealth$dynamicListener;
    
    @Unique
    private static Map<String, Double> stealth$cachedPriorities = null;
    @Unique
    private static long stealth$lastConfigCacheTime = 0;

    protected MixinMob(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initStealthVibration(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
        this.stealth$vibrationData = new VibrationSystem.Data();
        
        this.stealth$vibrationListener = new VibrationSystem.Listener(this) {
            @Override
            public boolean handleGameEvent(ServerLevel level, GameEvent event, GameEvent.Context context, net.minecraft.world.phys.Vec3 pos) {
                if (!StealthConfig.COMMON.VIBRATION_DETECTION_ENABLED.get()) return false;
                VibrationSystem.Data data = MixinMob.this.stealth$vibrationData;
                VibrationSystem.User user = MixinMob.this;

                if (data.getCurrentVibration() != null) {
                    return false;
                }

                if (!user.isValidVibration(event, context)) {
                    return false;
                } else {
                    float distance = (float)pos.distanceTo(MixinMob.this.position());
                    
                    data.setCurrentVibration(new VibrationInfo(
                        event, distance, pos, context.sourceEntity()
                    ));
                    data.setTravelTimeInTicks(0);
                    return true;
                }
            }
        };

        this.stealth$dynamicListener = new DynamicGameEventListener<>(this.stealth$vibrationListener);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickStealthVibration(CallbackInfo ci) {
        Level level = this.level();
        if (level instanceof ServerLevel serverLevel && StealthConfig.COMMON.VIBRATION_DETECTION_ENABLED.get()) {
            if (this.stealth$dynamicListener != null && this.tickCount % 20 == 0) {
                this.stealth$dynamicListener.move(serverLevel);
            }
            VibrationSystem.Ticker.tick(serverLevel, this.stealth$vibrationData, this.getVibrationUser());
        }
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> registrar) {
        if (this.level() instanceof ServerLevel serverLevel) {
            registrar.accept(this.stealth$dynamicListener, serverLevel);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveStealthData(CompoundTag tag, CallbackInfo ci) {
        VibrationSystem.Data.CODEC.encodeStart(NbtOps.INSTANCE, this.stealth$vibrationData)
                .resultOrPartial(err -> {})
                .ifPresent(data -> tag.put("stealth_vibration_data", data));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void loadStealthData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("stealth_vibration_data")) {
            VibrationSystem.Data.CODEC.parse(NbtOps.INSTANCE, tag.get("stealth_vibration_data"))
                    .resultOrPartial(err -> {})
                    .ifPresent(data -> this.stealth$vibrationData = data);
        }
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.stealth$vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this; 
    }

    @Override
    public int getListenerRadius() {
        return StealthConfig.COMMON.BASE_HEARING_RANGE.get();
    }

    @Override
    public PositionSource getPositionSource() {
        return new EntityPositionSource(this, this.getEyeHeight());
    }

    @Override
    public TagKey<GameEvent> getListenableEvents() {
        return GameEventTags.VIBRATIONS;
    }

    @Override
    public boolean canTriggerAvoidVibration() {
        return true;
    }

    @Override
    public boolean isValidVibration(GameEvent event, GameEvent.Context context) {
        Entity source = context.sourceEntity();
        if (source != null && source == (Mob)(Object)this) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canReceiveVibration(ServerLevel level, BlockPos pos, GameEvent event, GameEvent.Context context) {
        if (!StealthConfig.COMMON.VIBRATION_DETECTION_ENABLED.get()) return false;
        if (this.isDeadOrDying() || !this.level().getWorldBorder().isWithinBounds(pos)) return false;
        return true;
    }

    @Override
    public void onReceiveVibration(ServerLevel level, BlockPos pos, GameEvent event, @Nullable Entity emitter, @Nullable Entity projectileOwner, float distance) {
        ((Mob)(Object)this).getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
            
            float priority = getPriorityForEvent(event);
            boolean isEscalation = isEscalationEvent(event);
            
            Entity trueSource = projectileOwner != null ? projectileOwner : emitter;

            if (!wouldAttack(trueSource)) {
                priority *= 0.1f; 
            }

            if (trueSource instanceof Player player) {
                float noiseMultiplier = (float) StealthMath.getArmorNoiseMultiplier(player);
                boolean isMovement = event == GameEvent.STEP || event == GameEvent.SWIM || event == GameEvent.ELYTRA_GLIDE || event == GameEvent.HIT_GROUND;
                
                if (player.isCrouching()) {
                    if (isMovement) noiseMultiplier *= 0.1f;
                    else noiseMultiplier *= 0.8f; 
                }
                priority *= noiseMultiplier;
            }
            
            float distanceMalus = distance * (0.8f / StealthConfig.COMMON.BASE_HEARING_RANGE.get().floatValue());
            priority -= distanceMalus;

            if (priority > 0.5f) {
                state.setSuspiciousLocation(pos.getCenter(), priority, level.getGameTime());
                
                float targetAlertLevel = isEscalation ? 1.0f : Math.min(0.8f, priority * 0.1f);
                if (state.getAlertLevel() < targetAlertLevel) {
                    float alertGain = isEscalation ? 0.3f : 0.15f;
                    float newAlert = Math.min(targetAlertLevel, state.getAlertLevel() + alertGain);
                    state.setAlertLevel(newAlert); 
                    
                    // Wir senden kein Paket mehr direkt von hier aus. Das geänderte Alert-Level
                    // wird automatisch beim nächsten spielerzentrierten Sync-Intervall erfasst!
                }
            }
        });
    }

    @Unique
    private boolean isEscalationEvent(GameEvent event) {
        ResourceLocation key = BuiltInRegistries.GAME_EVENT.getKey(event);
        if (key == null) return false;
        
        try {
            List<? extends String> escalations = StealthConfig.COMMON.ESCALATION_EVENTS.get();
            return escalations.contains(key.toString());
        } catch (Exception e) {
            return event == GameEvent.ENTITY_DAMAGE 
                || event == GameEvent.ENTITY_DIE 
                || event == GameEvent.ENTITY_ROAR 
                || event == GameEvent.EXPLODE;
        }
    }

    @Unique
    private boolean wouldAttack(@Nullable Entity emitter) {
        if (!(emitter instanceof LivingEntity target)) return false;
        Mob me = (Mob)(Object)this;
        if (!me.canAttack(target) || me.isAlliedTo(target)) return false;
                
        return me.getCapability(StealthStateProvider.STEALTH_CAPABILITY).map(state -> 
            state.isRawLivingChangeTargetEventTarget(target.getUUID())
        ).orElse(false);
    }

    @Unique
    private float getPriorityForEvent(GameEvent event) {
        long now = System.currentTimeMillis();

        if (stealth$cachedPriorities == null || (now - stealth$lastConfigCacheTime > 10000)) {
            stealth$cachedPriorities = new HashMap<>();
            List<? extends String> configList = StealthConfig.COMMON.VIBRATION_PRIORITIES.get();
            for (String s : configList) {
                int separatorIdx = s.indexOf(';');
                if (separatorIdx != -1) {
                    try {
                        String eventName = s.substring(0, separatorIdx).trim();
                        double prio = Double.parseDouble(s.substring(separatorIdx + 1).trim());
                        stealth$cachedPriorities.put(eventName, prio);
                    } catch (NumberFormatException ignored) {}
                }
            }
            stealth$lastConfigCacheTime = now;
        }

        ResourceLocation key = BuiltInRegistries.GAME_EVENT.getKey(event);
        
        if (key != null && stealth$cachedPriorities.containsKey(key.toString())) {
            return stealth$cachedPriorities.get(key.toString()).floatValue();
        }
        
        return 5.0f; 
    }
}