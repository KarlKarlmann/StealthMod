package net.stealth.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.stealth.StealthMod;
import net.stealth.capability.StealthStateProvider;
import net.stealth.config.StealthConfig;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = StealthMod.MODID)
public class StealthCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stealth")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("info")
                .executes(StealthCommands::checkInfo))
            .then(Commands.literal("blacklist")
                .then(Commands.literal("add")

                    .executes(ctx -> modifyBlacklist(ctx, null, true))

                    .then(Commands.argument("mob", ResourceLocationArgument.id())
                        .executes(ctx -> modifyBlacklist(ctx, ResourceLocationArgument.getId(ctx, "mob").toString(), true))))
                .then(Commands.literal("remove")

                    .executes(ctx -> modifyBlacklist(ctx, null, false))

                    .then(Commands.argument("mob", ResourceLocationArgument.id())
                        .executes(ctx -> modifyBlacklist(ctx, ResourceLocationArgument.getId(ctx, "mob").toString(), false))))
            )
        );
    }

    private static int modifyBlacklist(CommandContext<CommandSourceStack> context, String mobId, boolean isAdding) {
        final String finalMobId;
        if (mobId == null) {
            LivingEntity target = getTargetedEntity(context.getSource().getEntity());
            if (target == null) {
                context.getSource().sendFailure(Component.literal("§c[Stealth] Kein Ziel im Visier und keine Mob-ID angegeben."));
                return 0;
            }
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
            if (key != null) {
                finalMobId = key.toString();
            } else {
                context.getSource().sendFailure(Component.literal("§c[Stealth] Konnte Mob-ID nicht ermitteln."));
                return 0;
            }
        } else {
            finalMobId = mobId;
        }

        // 2. Lade die aktuelle Liste aus der Config in eine bearbeitbare ArrayList
        List<String> currentList = new ArrayList<>((List<String>) StealthConfig.COMMON.BLACKLISTED_MOBS.get());

        // 3. Füge hinzu oder entferne
        if (isAdding) {
            if (currentList.contains(finalMobId)) {
                context.getSource().sendFailure(Component.literal("§e[Stealth] " + finalMobId + " steht bereits auf der Blacklist."));
                return 0;
            }
            currentList.add(finalMobId);
            StealthConfig.COMMON.BLACKLISTED_MOBS.set(currentList);
            context.getSource().sendSuccess(() -> Component.literal("§a[Stealth] " + finalMobId + " wurde zur Blacklist HINZUGEFÜGT."), true);
        } else {
            if (!currentList.contains(finalMobId)) {
                context.getSource().sendFailure(Component.literal("§e[Stealth] " + finalMobId + " ist nicht auf der Blacklist."));
                return 0;
            }
            currentList.remove(finalMobId);
            StealthConfig.COMMON.BLACKLISTED_MOBS.set(currentList);
            context.getSource().sendSuccess(() -> Component.literal("§a[Stealth] " + finalMobId + " wurde von der Blacklist ENTFERNT."), true);
        }

        return 1;
    }

    private static int checkInfo(CommandContext<CommandSourceStack> context) {
        LivingEntity target = getTargetedEntity(context.getSource().getEntity());

        if (target != null) {
            target.getCapability(StealthStateProvider.STEALTH_CAPABILITY).ifPresent(state -> {
                float level = state.getAlertLevel();
                String name = target.getName().getString();
                String color = level >= 1.0 ? "§4" : (level > 0.5 ? "§e" : "§a"); 
                
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7[Stealth] Ziel: §f" + name + " | Status: " + color + String.format("%.2f%%", level * 100)
                ), true);
            });
            return 1;
        } else {
            context.getSource().sendSuccess(() -> Component.literal("§c[Stealth] Kein Ziel im Visier (max 20 Blöcke)."), false);
            return 0;
        }
    }

    // Hilfsmethode, um den Raycast-Code nicht dreimal schreiben zu müssen
    private static LivingEntity getTargetedEntity(Entity source) {
        if (source == null) return null;

        Vec3 viewVec = source.getViewVector(1.0F);
        Vec3 eyePos = source.getEyePosition(1.0F);
        double range = 20.0;
        Vec3 maxDist = eyePos.add(viewVec.scale(range));
        AABB searchBox = source.getBoundingBox().expandTowards(viewVec.scale(range)).inflate(1.0);

        EntityHitResult result = ProjectileUtil.getEntityHitResult(
            source, 
            eyePos, 
            maxDist, 
            searchBox, 
            e -> e instanceof LivingEntity && !e.isSpectator() && e != source, 
            0.0f
        );

        if (result != null && result.getEntity() instanceof LivingEntity target) {
            return target;
        }
        return null;
    }
}