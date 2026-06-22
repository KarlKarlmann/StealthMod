package net.stealth.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.PlayLevelSoundEvent.AtEntity;
import net.minecraftforge.event.PlayLevelSoundEvent.AtPosition;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.stealth.StealthMod;
import net.stealth.ai.DistractionGoal;
import net.stealth.capability.StealthStateProvider;
import net.stealth.config.StealthConfig;
import net.stealth.network.ClientBoundSoundNoisePacket;
import net.stealth.network.ClientBoundSyncStealthPacket;
import net.stealth.network.ClientBoundVisibilityPacket;
import net.stealth.network.StealthNetwork;
import net.stealth.registry.StealthAttributes;
import net.stealth.registry.StealthTags;
import net.stealth.util.StealthMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.GameEventTags;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class ModCommonEvents {

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && !event.getLevel().isClientSide()) {
            
            // 1. Prüfen, ob der Mob durch Config oder Tag ignoriert wird
            boolean isIgnored = false;
            ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            if (entityKey != null && StealthConfig.COMMON.BLACKLISTED_MOBS.get().contains(entityKey.toString())) {
                isIgnored = true;
            }
            if (mob.getType().is(StealthTags.Entities.IGNORES_STEALTH)) {
                isIgnored = true;
            }

            // 2. Ablenkungs-Goal nur hinzufügen, wenn er NICHT ignoriert wird
            if (!isIgnored) {
                mob.goalSelector.addGoal(2, new DistractionGoal(mob));
            }

            // --- DYNAMISCHE ERHÖHUNG DER VANILLA FOLLOW-RANGE ---
            // Nur ausführen, wenn der Mob nicht ignoriert wird ODER die Config es explizit erlaubt
            if (!isIgnored || StealthConfig.COMMON.BOOST_IGNORED_MOBS_RANGE.get()) {
                var followAttr = mob.getAttribute(Attributes.FOLLOW_RANGE);
                if (followAttr != null) {
                    double maxConfigRange = Math.max(
                        StealthConfig.COMMON.BASE_DETECTION_RANGE.get(),
                        StealthConfig.COMMON.BASE_HEARING_RANGE.get().doubleValue()
                    );
                    // Wir erhöhen das Attribut nur, wenn die eingestellte Reichweite größer als der Standardwert ist
                    if (followAttr.getBaseValue() < maxConfigRange) {
                        followAttr.setBaseValue(maxConfigRange);
                    }
                }
            }
        }
    }

    private boolean isPositionSafe(Level level, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        BlockState state = level.getBlockState(blockPos);
        if (state.getFluidState().is(FluidTags.LAVA)) return false;
        if (state.is(BlockTags.FIRE)) return false;
        BlockPos below = blockPos.below();
        return !level.getBlockState(below).getFluidState().is(FluidTags.LAVA);
    }

    @SubscribeEvent
    public void onSetTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;

        ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if ((entityKey != null && StealthConfig.COMMON.BLACKLISTED_MOBS.get().contains(entityKey.toString())) || mob.getType().is(net.stealth.registry.StealthTags.Entities.IGNORES_STEALTH)) {
            return;
        }
        // WICHTIG: Dieser Block läuft IMMER, sobald Vanilla versucht den Spieler zu targeten -
        // also bereits BEVOR die Stealth-Checks (LOS/FOV/Distance) weiter unten ausgewertet werden
        // und das Event ggf. per event.setCanceled(true) wieder verwerfen.
        // "RawLivingChangeTargetEventTarget" heißt deshalb NICHT "Mob hatte den Spieler gültig (stealth-konform) im Visier",
        // sondern nur "Vanillas eigenes Target-Goal hat irgendwann mal versucht, ihn zu targeten"
        // (Vanilla-LOS + FollowRange reichen dafür, die sind großzügiger als unsere Stealth-Regeln).
        // Ein gecanceltes Event nimmt den Eintrag NICHT zurück - es gibt auch kein removeRawLivingChangeTargetEventTarget().
        // Konsequenz für die Hörlogik (siehe MixinMob#wouldAttack): isRawLivingChangeTargetEventTarget() ist daher in der
        // Praxis fast immer schon true, sobald der Spieler einmal in Vanillas Erkennungsradius war -
        // die 90%-Dämpfung für "unbekannte" Quellen in onReceiveVibration greift seltener als man denkt.
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
                    StealthNetwork.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                        new ClientBoundSyncStealthPacket(mob.getId(), 0.6f)
                    );
                });
            }
            return;
        }

        if (event.getNewTarget() instanceof Player player) {
            if (!(mob instanceof Enemy)) return;

            mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
                boolean isRememberedTarget = player.getUUID().equals(state.getITarget()) && state.getTimeSinceLastSeen() <= 100;

                if (state.getAlertLevel() >= 0.9f || isRememberedTarget) {
                    state.setAlertLevel(1.0f);
                    StealthNetwork.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                        new ClientBoundSyncStealthPacket(mob.getId(), 1.0f)
                    );
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
                        if (state.getAlertLevel() < 0.5f) {
                            state.addAlertLevel(0.2f);
                            StealthNetwork.CHANNEL.send(
                                PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                                new ClientBoundSyncStealthPacket(mob.getId(), state.getAlertLevel())
                            );
                        }
                    }
                } else {
                    state.setAlertLevel(1.0f);
                    StealthNetwork.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                        new ClientBoundSyncStealthPacket(mob.getId(), 1.0f)
                    );
                }
            });
        }
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        
        if (event.getEntity() instanceof Player player) {
            if (player.tickCount % 10 != 0) return; 
            
            double visibility = StealthMath.getVisibilityScore(player);
            StealthNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> (net.minecraft.server.level.ServerPlayer) player),
                new ClientBoundVisibilityPacket((float) visibility)
            );
            return;
        }
        
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.tickCount % 5 != 0) return;

        ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if ((entityKey != null && StealthConfig.COMMON.BLACKLISTED_MOBS.get().contains(entityKey.toString())) || mob.getType().is(net.stealth.registry.StealthTags.Entities.IGNORES_STEALTH)) {
            return;
        }

        mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
            float oldAlert = state.getAlertLevel();
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

            if (Math.abs(state.getAlertLevel() - oldAlert) > 0.0001f) {
                StealthNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                    new ClientBoundSyncStealthPacket(mob.getId(), state.getAlertLevel())
                );
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
                    StealthNetwork.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                        new ClientBoundSyncStealthPacket(mob.getId(), 1.0f)
                    );
                });
            }
            
            boolean isCloseEnough = attacker.distanceTo(victim) <= 5.0f;
            boolean isMeleeAttack = event.getSource().getDirectEntity() == attacker;
            
            if (isCloseEnough && isMeleeAttack && StealthConfig.COMMON.BACKSTAB_ENABLED.get() && StealthMath.isBehind(attacker, victim)) {
                
                // Neue Schadensberechnung mit Attributen
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
				// TODO:
                // 1. Custom Forge Event (z.B. 'BackstabEvent') feuern, damit andere Mods eingreifen können. Zum beispiel Waffenspezifische Effekte: z.B. Keule = Stun-Effekt statt purem Schaden, Dolch = Blutung.
                // 2. Animations-Mods Trigger: Ein NBT-Tag oder Event für Mods wie Epic Fight bereitstellen, um eine spezielle Meuchel-Animation abzuspielen.
               
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