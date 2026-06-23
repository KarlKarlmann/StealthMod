package net.stealth.mixins;

import net.minecraft.tags.GameEventTags;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.stealth.config.StealthConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GameEventDispatcher.post() berechnet die zu durchsuchende Chunk-Section-Bounding-Box
 * AUSSCHLIESSLICH aus GameEvent.getNotificationRadius() (siehe Vanilla-Quelle):
 *
 *   int n = gameEvent.getNotificationRadius();
 *   ... Bounding-Box aus blockPos +/- n ...
 *
 * Fast alle Vanilla-Events (STEP, SWIM, EXPLODE, ENTITY_DIE, PROJECTILE_SHOOT, ...)
 * haben einen Default-Radius von 16 Blocken. Das heisst: Ein Mob mit
 * getListenerRadius() > 16 (siehe MixinMob, BASE_HEARING_RANGE-Config) wird fuer diese
 * Events NIEMALS gefragt, wenn er weiter als 16 Blocke vom Ereignis entfernt ist - seine
 * Chunk-Section liegt schlicht ausserhalb der vom Dispatcher durchsuchten Box, BEVOR
 * unsere eigene onReceiveVibration-Logik ueberhaupt zum Zug kommt.
 *
 * Dieser Mixin hebt die Such-Box gezielt fuer Vibration-getaggte Events (GameEventTags.VIBRATIONS)
 * auf den groesseren der beiden Werte (Vanilla-Radius vs. unsere konfigurierte Hoerreichweite) an,
 * damit BASE_HEARING_RANGE > 16 ueberhaupt eine Wirkung entfalten kann.
 *
 * WICHTIG: Nur fuer Vibration-Events aufblaehen, nicht global - sonst durchsucht der Server
 * bei JEDEM GameEvent (auch CONTAINER_OPEN, EAT, EQUIP, ...) eine unnoetig grosse Box.
 * Die durchsuchte Section-Anzahl waechst kubisch mit dem Radius ((2r+1)^3), bei z.B.
 * 128 statt 16 Blocken ist das ein Faktor von ueber 260x mehr Sections pro Aufruf -
 * unbedingt im Profiler gegentesten, bevor das an Nutzer ausgeliefert wird.
 */
@Mixin(GameEventDispatcher.class)
public abstract class MixinGameEventDispatcher {

    @Redirect(
        method = "post",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/gameevent/GameEvent;getNotificationRadius()I"
        )
    )
    private int stealth$expandNotificationRadiusForVibrations(GameEvent event) {
        int vanillaRadius = event.getNotificationRadius();

        // Nur Vibration-Events betreffen unsere Hoerlogik (MixinMob#onReceiveVibration).
        // Alles andere bekommt seinen normalen Vanilla-Radius, unverandert.
        if (!event.is(GameEventTags.VIBRATIONS)) {
            return vanillaRadius;
        }

        int stealthHearingRange = StealthConfig.COMMON.BASE_HEARING_RANGE.get();

        // Niemals kleiner als der Vanilla-Wert machen - nur erweitern, nie einschranken.
        return Math.max(vanillaRadius, stealthHearingRange);
    }
}