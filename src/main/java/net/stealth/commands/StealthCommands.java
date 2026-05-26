package net.stealth.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stealth.StealthMod;
import net.stealth.capability.StealthStateProvider;

@Mod.EventBusSubscriber(modid = StealthMod.MODID)
public class StealthCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stealth")
            .then(Commands.literal("info")
                .executes(StealthCommands::checkInfo)));
    }

    private static int checkInfo(CommandContext<CommandSourceStack> context) {
        Entity source = context.getSource().getEntity();
        
        if (source == null) return 0;

        // 1. Raycast Setup: Wir schießen einen unsichtbaren Strahl aus den Augen des Spielers
        Vec3 viewVec = source.getViewVector(1.0F);
        Vec3 eyePos = source.getEyePosition(1.0F);
        double range = 20.0; // Reichweite des Befehls
        Vec3 maxDist = eyePos.add(viewVec.scale(range));
        AABB searchBox = source.getBoundingBox().expandTowards(viewVec.scale(range)).inflate(1.0);

        // 2. Suche nach Entity im Strahl
        EntityHitResult result = ProjectileUtil.getEntityHitResult(
            source, 
            eyePos, 
            maxDist, 
            searchBox, 
            e -> e instanceof LivingEntity && !e.isSpectator() && e != source, 
            0.0f
        );

        // 3. Ergebnis auswerten
        if (result != null && result.getEntity() instanceof LivingEntity target) {
            target.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
                float level = state.getAlertLevel();
                String name = target.getName().getString();
                
                // Farbcodierung: Grün (Sicher), Gelb (Verdacht), Rot (Gefahr)
                String color = level >= 1.0 ? "§4" : (level > 0.5 ? "§e" : "§a"); 
                
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7[Stealth] Ziel: §f" + name + " | Status: " + color + String.format("%.2f%%", level * 100)
                ), true);
            });
        } else {
            // Wenn man nichts ansieht, gib eine Meldung aus
            context.getSource().sendSuccess(() -> Component.literal("§c[Stealth] Kein Ziel im Visier (max 20 Blöcke)."), false);
        }
        
        return 1;
    }
}