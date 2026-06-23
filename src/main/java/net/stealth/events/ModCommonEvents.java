package net.stealth.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.stealth.ai.DistractionGoal;
import net.stealth.capability.StealthStateProvider;
import net.stealth.config.StealthConfig;
import net.stealth.network.ClientBoundSoundNoisePacket;
import net.stealth.network.ClientBoundPlayerHudPacket;
import net.stealth.network.StealthNetwork;
import net.stealth.registry.StealthAttributes;
import net.stealth.registry.StealthTags;
import net.stealth.util.StealthMath;
import net.stealth.util.ThreatLevel;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class ModCommonEvents {

    // Speicher für den Synchronisations-Status jedes Spielers zur Spam-Vermeidung
    private static final Map<UUID, PlayerHudSyncState> HUD_SYNC_STATE = new HashMap<>();

    private static class PlayerHudSyncState {
        float lastSentVisibility = Float.NaN; // Erzwingt Initial-Sync bei NaN Vergleich
        ThreatLevel lastSentThreat = null;
        int ticksSinceLastSync = 0;
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        HUD_SYNC_STATE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && !event.getLevel().isClientSide()) {
            boolean isIgnored = false;
            ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            if (entityKey != null && StealthConfig.COMMON.BLACKLISTED_MOBS.get().contains(entityKey.toString())) {
                isIgnored = true;
            }
            if (mob.getType().is(StealthTags.Entities.IGNORES_STEALTH)) {
                isIgnored = true;
            }

            if (!isIgnored) {
                mob.goalSelector.addGoal(2, new DistractionGoal(mob));
            }

            if (!isIgnored || StealthConfig.COMMON.BOOST_IGNORED_MOBS_RANGE.get()) {
                var followAttr = mob.getAttribute(Attributes.FOLLOW_RANGE);
                if (followAttr != null) {
                    double maxConfigRange = Math.max(
                        StealthConfig.COMMON.BASE_DETECTION_RANGE.get(),
                        StealthConfig.COMMON.BASE_HEARING_RANGE.get().doubleValue()
                    );
                    if (followAttr.getBaseValue() < maxConfigRange) {
                        followAttr.setBaseValue(maxConfigRange);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onSetTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;

        ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if ((entityKey != null && StealthConfig.COMMON.BLACKLISTED_MOBS.get().contains(entityKey.toString())) || mob.getType().is(StealthTags.Entities.IGNORES_STEALTH)) {
            return;
        }

        LivingEntity newTarget = event.getNewTarget();
        if (newTarget != null) {
            mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
                state.addRawLivingChangeTargetEventTarget(newTarget.getUUID());
            });
        }

        if (event.getNewTarget() == null) {
            if (mob.getTarget() instanceof Player) {
                mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
                    if (state.getLastKnownPos() != null) {
                        state.setSuspiciousLocation(state.getLastKnownPos(), 20.0f, mob.level().getGameTime());
                        state.setLastKnownPos(null);
                    }
                    state.setAlertLevel(0.6f); 
                });
            }
            return;
        }

        if (event.getNewTarget() instanceof Player player) {
            if (!(mob instanceof Enemy)) return;

            mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
                boolean isRememberedTarget = player.getUUID().equals(state.getITarget()) && state.getTimeSinceLastSeen() <= 100;
                float oldAlert = state.getAlertLevel();

                if (oldAlert >= 0.9f || isRememberedTarget) {
                    state.setAlertLevel(1.0f);
                    return; 
                }

                boolean hasLOS = StealthMath.hasLineOfSight(mob, player);
                boolean inFOV = StealthMath.isEntityInFieldOfView(mob, player, StealthConfig.COMMON.FOV_DEGREES.get());
                double visibility = StealthMath.getVisibilityScore(player, mob);
                double detectionRange = StealthConfig.COMMON.BASE_DETECTION_RANGE.get() * visibility;

                boolean tooDarkOrFar = mob.distanceTo(player) > detectionRange;
                boolean safeFromBack = !inFOV && mob.distanceTo(player) > 2.5;

                if (!hasLOS || safeFromBack || tooDarkOrFar) {
                    event.setCanceled(true); 
                    
                    if (hasLOS && !safeFromBack) {
                        state.setSuspiciousLocation(player.position(), 10.0f, mob.level().getGameTime());
                        if (oldAlert < 0.5f) {
                            state.addAlertLevel(0.2f);
                        }
                    }
                } else {
                    state.setAlertLevel(1.0f);
                }
            });
        }
    }

    private static boolean visibilityChanged(float newVis, float oldVis) {
        if (Float.isNaN(oldVis)) return true;
        if (newVis < 0f && oldVis < 0f) return false; 
        return Math.abs(newVis - oldVis) >= 0.05f; 
    }

    private static ThreatLevel computeThreatLevel(ServerPlayer player) {
        double radius = 30.0;
        AABB area = player.getBoundingBox().inflate(radius);
        List<Mob> nearbyMobs = player.level().getEntitiesOfClass(Mob.class, area);
        ThreatLevel maxThreat = ThreatLevel.NONE;
        double fov = StealthConfig.COMMON.FOV_DEGREES.get();

        for (Mob mob : nearbyMobs) {
            ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            if ((entityKey != null && StealthConfig.COMMON.BLACKLISTED_MOBS.get().contains(entityKey.toString()))
                    || mob.getType().is(StealthTags.Entities.IGNORES_STEALTH)) {
                continue;
            }

            ThreatLevel mobThreat = mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).map(state -> {
                float alert = state.getAlertLevel();
                boolean isTargetingMe = (mob.getTarget() == player);
                if (isTargetingMe || alert >= 0.99f) return ThreatLevel.HUNTED;
                if (alert > 0.5f) return ThreatLevel.SUSPICIOUS;
                if (StealthMath.isEntityInFieldOfView(mob, player, fov) && StealthMath.hasLineOfSight(mob, player)) {
                    return ThreatLevel.WATCHED;
                }
                return ThreatLevel.NONE;
            }).orElse(ThreatLevel.NONE);

            if (mobThreat.severity > maxThreat.severity) {
                maxThreat = mobThreat;
            }
        }
        return maxThreat;
    }

    private static void syncPlayerHud(ServerPlayer player, boolean forceImmediate, boolean suppressSound) {
        PlayerHudSyncState syncState = HUD_SYNC_STATE.computeIfAbsent(player.getUUID(), id -> new PlayerHudSyncState());
        boolean accurateSync = StealthConfig.COMMON.ACCURATE_HUD_SYNC.get();
        float visibility = accurateSync
                ? (float) StealthMath.getVisibilityScore(player)
                : ClientBoundPlayerHudPacket.NO_SERVER_VISIBILITY;

        ThreatLevel threat = computeThreatLevel(player);
        boolean visChanged = visibilityChanged(visibility, syncState.lastSentVisibility);
        boolean threatChanged = threat != syncState.lastSentThreat;
        boolean periodicForce = syncState.ticksSinceLastSync > 40; 

        if (forceImmediate || visChanged || threatChanged || periodicForce) {
            StealthNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ClientBoundPlayerHudPacket(visibility, threat, suppressSound)
            );
            syncState.lastSentVisibility = visibility;
            syncState.lastSentThreat = threat;
            syncState.ticksSinceLastSync = 0;
        } else {
            syncState.ticksSinceLastSync++;
        }
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        
        if (event.getEntity() instanceof Player player) {
            if (player.tickCount % 10 != 0) return; 
            
            if (player instanceof ServerPlayer serverPlayer) {
                syncPlayerHud(serverPlayer, false, false);
            }
            return;
        }
        
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.tickCount % 5 != 0) return;

        ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if ((entityKey != null && StealthConfig.COMMON.BLACKLISTED_MOBS.get().contains(entityKey.toString())) || mob.getType().is(StealthTags.Entities.IGNORES_STEALTH)) {
            return;
        }

        mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
            LivingEntity target = mob.getTarget();
            java.util.UUID iTarget = state.getITarget();

            if (target != null) {
                state.setITarget(target.getUUID());
                boolean hasLOS = StealthMath.hasLineOfSight(mob, target);
                if (hasLOS) {
                    state.setLastKnownPos(target.position());
                    state.setTimeSinceLastSeen(0);
                } else {
                    state.incrementTimeSinceLastSeen();
                    if (state.getTimeSinceLastSeen() > 60) {
                        mob.setTarget(null); 
                        state.setITarget(null); 
                    }
                }
            } else {
                if (iTarget != null) {
                    state.incrementTimeSinceLastSeen();
                    if (state.getTimeSinceLastSeen() > 100) { 
                        state.setITarget(null); 
                    }
                }

                state.decayAlertLevel(0.005f); 

                Vec3 susp = state.getSuspiciousLocation();
                Vec3 active = state.getActiveDistraction();

                if (susp != null && state.getAlertLevel() >= 0.295f) {
                    if (active == null || !susp.equals(active)) {
                        state.setActiveDistraction(susp);
                        state.clearSuspiciousLocation();
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker) {
            LivingEntity victim = event.getEntity();
            if (victim instanceof Mob mob) {
                mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
                    state.setAlertLevel(1.0f);
                    state.setLastKnownPos(attacker.position());
                });
                
                // Sofortiger Sync des Angreifers zur Reduzierung spürbarer Lags, Sound unterdrückt
                if (attacker instanceof ServerPlayer serverAttacker) {
                    syncPlayerHud(serverAttacker, true, true);
                }
            }
            
            boolean isCloseEnough = attacker.distanceTo(victim) <= 5.0f;
            boolean isMeleeAttack = event.getSource().getDirectEntity() == attacker;
            
            if (isCloseEnough && isMeleeAttack && StealthConfig.COMMON.BACKSTAB_ENABLED.get() && StealthMath.isBehind(attacker, victim)) {
                
                float flatBonus = 0.0f;
                float multiBonus = 1.0f;

                var flatAttr = attacker.getAttribute(StealthAttributes.BACKSTAB_DAMAGE.get());
                if (flatAttr != null) flatBonus = (float) flatAttr.getValue();

                var multiAttr = attacker.getAttribute(StealthAttributes.BACKSTAB_MULTIPLIER.get());
                if (multiAttr != null) multiBonus = (float) multiAttr.getValue();

                float baseConfigMultiplier = StealthConfig.COMMON.BACKSTAB_MULTIPLIER.get().floatValue();
                float totalMultiplier = baseConfigMultiplier * multiBonus;

                float baseDamage = event.getAmount();
                float newDamage = (baseDamage + flatBonus) * totalMultiplier;
                
                event.setAmount(newDamage);

                // --- DYNAMISCHER SOUND ---
                String soundId = StealthConfig.COMMON.BACKSTAB_SOUND.get();
                if (!soundId.isEmpty() && !soundId.equalsIgnoreCase("none")) {
                    net.minecraft.sounds.SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
                    if (sound != null) {
                        attacker.level().playSound(null, attacker.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.5f);
                    }
                }

                // --- DYNAMISCHE PARTIKEL ---
                String particleId = StealthConfig.COMMON.BACKSTAB_PARTICLE.get();
                if (!particleId.isEmpty() && !particleId.equalsIgnoreCase("none") && attacker.level() instanceof ServerLevel sl) {
                    net.minecraft.core.particles.ParticleType<?> particle = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(particleId));
                    if (particle instanceof net.minecraft.core.particles.SimpleParticleType simpleParticle) {
                        sl.sendParticles(simpleParticle, victim.getX(), victim.getEyeY(), victim.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onGameEvent(net.minecraftforge.event.VanillaGameEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && event.getCause() instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.world.level.gameevent.GameEvent gameEvent = event.getVanillaEvent();
            
            if (!gameEvent.is(GameEventTags.VIBRATIONS)) {
                return;
            }

            ResourceLocation eventKey = BuiltInRegistries.GAME_EVENT.getKey(gameEvent);
            if (eventKey == null) return;

            float wardenPriority = 5.0f; 
            for (String s : StealthConfig.COMMON.VIBRATION_PRIORITIES.get()) {
                String[] parts = s.split(";");
                if (parts.length == 2 && parts[0].trim().equals(eventKey.toString())) {
                    try {
                        wardenPriority = Float.parseFloat(parts[1].trim());
                        break;
                    } catch (NumberFormatException ignored) {}
                }
            }

            float noiseMultiplier = (float) StealthMath.getArmorNoiseMultiplier(player);
            float displayVolume = (wardenPriority / 10.0f) * 2.5f;

            boolean isMovement = gameEvent == GameEvent.STEP || gameEvent == GameEvent.SWIM || gameEvent == GameEvent.ELYTRA_GLIDE || gameEvent == GameEvent.HIT_GROUND;
            
            if (player.isCrouching()) {
                if (isMovement) {
                    noiseMultiplier *= 0.1f; 
                } else {
                    noiseMultiplier *= 0.8f; 
                }
            }

            displayVolume *= noiseMultiplier;

            if (displayVolume > 0.05f) {
                StealthNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ClientBoundSoundNoisePacket(displayVolume)
                );
            }
        }
    }
}