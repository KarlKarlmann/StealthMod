package net.stealth.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.stealth.config.StealthConfig;
import net.stealth.registry.StealthAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StealthMath {

    // Cache für Config-Werte um Performance zu sparen
    private static Map<String, Integer> cachedLightSources = null;
    private static Map<String, Double> cachedArmorNoise = null;
    private static long lastConfigCacheTime = 0;

    public static double getVisibilityScore(LivingEntity player) {
        return calculateBaseVisibility(player, false);
    }

    public static double getVisibilityScore(LivingEntity player, LivingEntity observer) {
        boolean observerHasNightVision = observer != null && observer.hasEffect(MobEffects.NIGHT_VISION);
        double baseScore = calculateBaseVisibility(player, observerHasNightVision);

        if (observer == null) return baseScore;

        Vec3 look = observer.getLookAngle().normalize();
        Vec3 toPlayer = player.getEyePosition().subtract(observer.getEyePosition()).normalize();
        double dot = look.dot(toPlayer); 
        
        double angleFactor = (dot >= 0.9) ? 1.0 : Math.max(0.5, dot);

        // --- NEU: ALERT-LEVEL & NAHBEREICH ---
        float alertLevel = 0.0f;
        if (observer instanceof net.minecraft.world.entity.Mob mob) {
            alertLevel = mob.getCapability(net.stealth.capability.StealthStateProvider.STEALTH_CAPABILITY)
                            .map(state -> state.getAlertLevel())
                            .orElse(0.0f);
        }

        // 1. Genereller Paranoia-Boost: Ein alarmierter Mob sieht generell etwas besser.
        // Bei Alert 1.0 verdoppeln wir theoretisch seine Sehstärke im Halbdunkel.
        double alertMultiplier = 1.0 + (alertLevel * 1.0); 

        // 2. Proximity Awareness (Der "Ich stehe direkt vor dir"-Check)
        double distance = player.distanceTo(observer);
        
        // Wenn du unter 5 Blöcke entfernt bist UND er grob in deine Richtung schaut
        if (distance < 5.0 && dot > 0.5) {
            // Je näher du bist (0 bis 5) und je misstrauischer der Mob, desto extremer der Boost.
            // proximityBoost ist 1.0 wenn du in ihm stehst, und 0.0 bei 5 Blöcken Entfernung.
            double proximityBoost = (5.0 - distance) / 5.0; 
            
            // WICHTIG: Wir addieren das direkt auf den baseScore auf! 
            // So durchbricht er die Dunkelheit/Camouflage, selbst wenn baseScore 0.0 war.
            baseScore += (proximityBoost * alertLevel); 
        }

        return Mth.clamp(baseScore * angleFactor * alertMultiplier, 0.0, 1.0);
    }

    private static double calculateBaseVisibility(LivingEntity target, boolean observerHasNightVision) {
        Level level = target.level();
        BlockPos pos = target.blockPosition();

        // 1. Statisches Licht + Globaler Multiplier
        int staticLight = level.getRawBrightness(pos, level.getSkyDarken());
        
        // Config: Overall Lightlevel
        double lightAdd = StealthConfig.COMMON.GLOBAL_LIGHT.get();
        double adjustedLight = staticLight + lightAdd;

        double effectiveLight = adjustedLight;
        
        if (adjustedLight < 15 && !observerHasNightVision) {
            // 2. Dynamisches Licht (nur wenn Config an)
            if (StealthConfig.COMMON.DYNAMIC_LIGHTS_ENABLED.get()) {
                int dynamicLight = calculateDynamicLightAtTarget(target);
                effectiveLight = Math.max(adjustedLight, dynamicLight + lightAdd);
            }
        }

        if (observerHasNightVision || target.isOnFire() || target.hasEffect(MobEffects.GLOWING)) {
            effectiveLight = 15;
        }

        double lightFactor = Math.pow(effectiveLight / 15.0, 2.0);

        // --- BEWEGUNG ---
        double movementSpeed;
        if (target instanceof Player p) {
            float currentWalkDist = p.walkDist;
            float distanceDelta = Math.abs(currentWalkDist - p.walkDistO);
            movementSpeed = distanceDelta;
        } else {
            movementSpeed = target.getDeltaMovement().lengthSqr();
        }

        double movementFactor = 0.5; 
        if (movementSpeed > 0.0061175) {
            movementFactor = target.isSprinting() ? 1.5 : (target.isCrouching() ? 1.5 : 1.0);
        } else if (target.isCrouching()) {
            movementFactor = 0.8; 
        }

        // --- CAMOUFLAGE ATTRIBUT ---
        // Hier wird das Attribut genutzt, damit andere Mods es beeinflussen können.
        double camoValue = 0.0;
        try {
            var attr = target.getAttribute(StealthAttributes.CAMOUFLAGE.get());
            if (attr != null) camoValue = attr.getValue();
        } catch (Exception ignored) {}
        
        // Camouflage reduziert die Sichtbarkeit (1.0 Camo = 0.0 Sichtbarkeit)
        double attributeFactor = Math.max(0.0, 1.0 - camoValue);

        return Mth.clamp(lightFactor * movementFactor * attributeFactor, 0.0, 1.0);
    }

    private static int calculateDynamicLightAtTarget(LivingEntity target) {
        int maxLight = getEntityLightEmission(target);
        if (maxLight >= 15) return 15;

        List<LivingEntity> nearbyEntities = target.level().getEntitiesOfClass(
            LivingEntity.class, 
            target.getBoundingBox().inflate(15.0), 
            e -> e != target && e.isAlive() 
        );

        for (LivingEntity source : nearbyEntities) {
            int sourceEmission = getEntityLightEmission(source);
            if (sourceEmission <= 0) continue;

            double dist = source.position().distanceTo(target.position());
            int lightAtTarget = sourceEmission - (int)Math.ceil(dist);

            if (lightAtTarget > maxLight) {
                if (hasLineOfSight(source, target)) {
                    maxLight = lightAtTarget;
                    if (maxLight >= 15) return 15;
                }
            }
        }
        return maxLight;
    }

    private static int getEntityLightEmission(LivingEntity entity) {
        int maxLight = 0;
        for (ItemStack stack : entity.getHandSlots()) {
            maxLight = Math.max(maxLight, getItemLightEmission(stack, entity.level()));
        }
        return maxLight;
    }

    private static int getItemLightEmission(ItemStack stack, Level level) {
        if (stack.isEmpty()) return 0;

        checkConfigCache(level);

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null && cachedLightSources.containsKey(itemId.toString())) {
            return cachedLightSources.get(itemId.toString());
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            try {
                return blockItem.getBlock().defaultBlockState().getLightEmission(level, BlockPos.ZERO);
            } catch (Exception e) {
                return blockItem.getBlock().defaultBlockState().getLightEmission();
            }
        }

        return 0;
    }

    public static double getArmorNoiseMultiplier(LivingEntity entity) {
        checkConfigCache(entity.level());
        double noiseFromArmor = 0.0;
        
        // 1. Basis-Lärm aus der Config berechnen (Rüstungsmaterial)
        for (ItemStack stack : entity.getArmorSlots()) {
            if (stack.isEmpty()) continue;
            
            if (stack.getItem() instanceof ArmorItem armor) {
                 String matName = armor.getMaterial().getName();
                 // Robustheit: Manche Mods nutzen "modid:material", wir wollen nur "material"
                 if (matName.contains(":")) {
                     matName = matName.split(":")[1];
                 }
                 
                 if (cachedArmorNoise.containsKey(matName)) {
                     noiseFromArmor += cachedArmorNoise.get(matName);
                 } else {
                     noiseFromArmor += 0.15; // Default Fallback
                 }
            }
        }
        
        // 2. MUFFLING ATTRIBUT anwenden
        // Das Attribut erlaubt anderen Mods, den Lärm zu reduzieren (oder zu erhöhen).
        // 0.0 = Normal, 1.0 = Lautlos (Muffling)
        double mufflingValue = 0.0;
        try {
            var attr = entity.getAttribute(StealthAttributes.MUFFLING.get());
            if (attr != null) {
                mufflingValue = attr.getValue();
            }
        } catch (Exception ignored) {}

        // Berechnung: (Basis + Rüstungslärm) * (1.0 - Muffling)
        // Wenn Muffling 1.0 ist, ist das Ergebnis 0.0 (Lautlos).
        double totalNoise = (1.0 + noiseFromArmor) * Math.max(0.0, 1.0 - mufflingValue);
        
        return totalNoise; 
    }

    private static void checkConfigCache(Level level) {
        long now = System.currentTimeMillis();
        if (cachedLightSources == null || cachedArmorNoise == null || (now - lastConfigCacheTime > 10000)) {
            cachedLightSources = new HashMap<>();
            List<? extends String> lights = StealthConfig.COMMON.DYNAMIC_LIGHT_SOURCES.get();
            for (String s : lights) {
                String[] parts = s.split(";");
                if (parts.length == 2) {
                    try {
                        cachedLightSources.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            cachedArmorNoise = new HashMap<>();
            List<? extends String> armors = StealthConfig.COMMON.ARMOR_NOISE_MULTIPLIERS.get();
            for (String s : armors) {
                String[] parts = s.split(";");
                if (parts.length == 2) {
                    try {
                        cachedArmorNoise.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
            lastConfigCacheTime = now;
        }
    }

    public static boolean hasLineOfSight(LivingEntity startEntity, LivingEntity endEntity) {
        if(endEntity.hasEffect(MobEffects.GLOWING)) return true;
        Vec3 start = startEntity.getEyePosition();
        Vec3[] targetPoints = new Vec3[]{
                endEntity.getEyePosition(),
                endEntity.position().add(0, endEntity.getBbHeight() / 2, 0),
                endEntity.position()
        };
        for (Vec3 end : targetPoints) {
            if (startEntity.level().clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, startEntity)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
    
    public static boolean isEntityInFieldOfView(LivingEntity observer, LivingEntity target, double fovDegrees) {
        Vec3 observerLook = observer.getLookAngle().normalize();
        Vec3 toTarget = target.getEyePosition().subtract(observer.getEyePosition()).normalize();
        return observerLook.dot(toTarget) > Math.cos(Math.toRadians(fovDegrees / 2.0));
    }
    
    public static boolean isBehind(LivingEntity attacker, LivingEntity victim) {
         Vec3 attackerLook = attacker.getLookAngle().normalize();
         Vec3 victimLook = victim.getLookAngle().normalize();
         return attackerLook.dot(victimLook) > 0.6;
    }
}