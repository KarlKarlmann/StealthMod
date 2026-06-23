package net.stealth.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber
public class StealthConfig {
    // SPEC is required by StealthMod.java for loading the config
    public static final ForgeConfigSpec SPEC; 
    public static final CommonConfig COMMON;
    
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;

    static {
        final Pair<CommonConfig, ForgeConfigSpec> commonSpecPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        SPEC = commonSpecPair.getRight();
        COMMON = commonSpecPair.getLeft();

        final Pair<ClientConfig, ForgeConfigSpec> clientSpecPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientSpecPair.getRight();
        CLIENT = clientSpecPair.getLeft();
    }

    public static class ClientConfig {
        public final ForgeConfigSpec.BooleanValue HUD_ENABLED;
        public final ForgeConfigSpec.BooleanValue HUD_EDITOR_BUTTON_ENABLED;

        ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.push("client");
            
            HUD_ENABLED = builder
                    .comment("Toggles the entire Stealth HUD (eye, dagger, sound waves) on or off in-game.")
                    .define("hud_enabled", true);

            HUD_EDITOR_BUTTON_ENABLED = builder
                    .comment("Toggles the 'Stealth HUD' button in the pause menu (ESC) on or off.")
                    .define("hud_editor_button_enabled", true);
                    
            builder.pop();
        }
    }

    public static class CommonConfig {
        // --- GENERAL ---
        public final ForgeConfigSpec.BooleanValue ACCURATE_HUD_SYNC;
        public final ForgeConfigSpec.DoubleValue BASE_DETECTION_RANGE;
        public final ForgeConfigSpec.DoubleValue FOV_DEGREES;
        public final ForgeConfigSpec.DoubleValue BACKSTAB_MULTIPLIER;
        public final ForgeConfigSpec.BooleanValue BACKSTAB_ENABLED;
        public final ForgeConfigSpec.ConfigValue<String> BACKSTAB_SOUND;
        public final ForgeConfigSpec.ConfigValue<String> BACKSTAB_PARTICLE;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_MOBS;
        public final ForgeConfigSpec.BooleanValue BOOST_IGNORED_MOBS_RANGE;        
        
        // --- LIGHT ---
        public final ForgeConfigSpec.DoubleValue GLOBAL_LIGHT;
        public final ForgeConfigSpec.BooleanValue DYNAMIC_LIGHTS_ENABLED;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> DYNAMIC_LIGHT_SOURCES;

        // --- VIBRATIONS ---
        public final ForgeConfigSpec.IntValue BASE_HEARING_RANGE;
        public final ForgeConfigSpec.BooleanValue VIBRATION_DETECTION_ENABLED;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> VIBRATION_PRIORITIES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ESCALATION_EVENTS;

        // --- ARMOR & NOISE ---
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ARMOR_NOISE_MULTIPLIERS;

        CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            ACCURATE_HUD_SYNC = builder
                    .comment("If true, the server exactly calculates the player's visibility (incl. accurate skylight) and sends it to the HUD.",
                             "Disable this on large servers to save massive amounts of TPS and network bandwidth! (The client will then estimate it locally).")
                    .define("accurate_hud_sync", true);

            BASE_DETECTION_RANGE = builder
                    .comment("Base detection range of mobs in blocks.")
                    .defineInRange("base_detection_range", 16.0, 1.0, 128.0);
            
            FOV_DEGREES = builder
                    .comment("Field of view (FOV) of mobs in degrees.")
                    .defineInRange("fov_degrees", 80.0, 10.0, 360.0);
            
            BACKSTAB_ENABLED = builder
                    .comment("Enables or disables the damage bonus for attacks from behind.")
                    .define("backstab_enabled", true);

            BACKSTAB_MULTIPLIER = builder
                    .comment("Damage multiplier for successful backstab attacks.")
                    .defineInRange("backstab_multiplier", 3.0, 1.0, 100.0);

            BACKSTAB_SOUND = builder
                    .comment("Sound effect played on a successful backstab attack. Format: 'modid:sound_event' (use 'none' to disable)")
                    .define("backstab_sound", "stealth:backstab");

            BACKSTAB_PARTICLE = builder
                    .comment("Particle effect spawned on a successful backstab attack. Format: 'modid:particle_type' (use 'none' to disable)")
                    .define("backstab_particle", "minecraft:crit");
					
            BLACKLISTED_MOBS = builder
                    .comment("List of mobs that completely ignore the stealth system. Format: 'modid:entity'")
                    .defineList("blacklisted_mobs", Arrays.asList(
                            "minecraft:ender_dragon",
                            "minecraft:wither",
                            "minecraft:vex",
                            "minecraft:warden"
                    ), obj -> obj instanceof String);

            BOOST_IGNORED_MOBS_RANGE = builder
                    .comment("Should the follow range of blacklisted mobs still be boosted to match the stealth config values?")
                    .define("boost_ignored_mobs_range", false);
					
            builder.pop();

            builder.push("light");
            
            GLOBAL_LIGHT = builder
                    .comment("Artificially increases the overall base light level (makes hiding more difficult).")
                    .defineInRange("overall_light_multiplier", 0.0, 0.0, 15.0);

            DYNAMIC_LIGHTS_ENABLED = builder
                    .comment("Should the light from held items (torches, etc.) increase the player's visibility?")
                    .define("dynamic_lights_enabled", true);

            DYNAMIC_LIGHT_SOURCES = builder
                    .comment("List of items and their light levels in the format 'modid:item;level'.")
                    .defineList("dynamic_light_sources", Arrays.asList(
                            "minecraft:torch;14",
                            "minecraft:soul_torch;10",
                            "minecraft:lantern;15",
                            "minecraft:soul_lantern;10",
                            "minecraft:glow_berries;14",
                            "minecraft:lava_bucket;15",
                            "minecraft:sea_lantern;15",
                            "minecraft:glowstone;15"
                    ), obj -> obj instanceof String);

            builder.pop();

            builder.push("vibrations");

            VIBRATION_DETECTION_ENABLED = builder
                    .comment("Should mobs react to vibrations (footsteps, block breaking, etc.)?")
                    .define("vibration_detection_enabled", true);

            BASE_HEARING_RANGE = builder
                    .comment("Base hearing range of mobs in blocks (how far they detect vibrations).")
                    .defineInRange("base_hearing_range", 16, 1, 128);
					
VIBRATION_PRIORITIES = builder
        .comment("Priority of vibration events. Format: 'game_event_id;priority' (Higher = More important).")
        .defineList("vibration_priorities", Arrays.asList(
                "minecraft:block_activate;5.0",
                "minecraft:block_attach;4.0",
                "minecraft:block_change;4.0",
                "minecraft:block_close;5.0",
                "minecraft:block_deactivate;4.0",
                "minecraft:block_destroy;8.0",
                "minecraft:block_detach;4.0",
                "minecraft:block_open;5.0",
                "minecraft:block_place;6.0",
                "minecraft:container_close;5.0",
                "minecraft:container_open;5.0",
                "minecraft:drink;3.0",
                "minecraft:eat;3.0",
                "minecraft:elytra_glide;2.0",
                "minecraft:entity_damage;6.0",
                "minecraft:entity_die;8.0",
                "minecraft:entity_dismount;3.0",
                "minecraft:entity_interact;3.0",
                "minecraft:entity_mount;3.0",
                "minecraft:entity_place;4.0",
                "minecraft:entity_roar;9.0",
                "minecraft:entity_shake;4.0",
                "minecraft:equip;4.0",
                "minecraft:explode;10.0",
                "minecraft:flap;3.0",
                "minecraft:fluid_pickup;5.0",
                "minecraft:fluid_place;5.0",
                "minecraft:hit_ground;5.0",
                "minecraft:instrument_play;7.0",
                "minecraft:item_interact_finish;3.0",
                "minecraft:item_interact_start;3.0",
                "minecraft:jukebox_play;20.0",
                "minecraft:jukebox_stop_play;4.0",
                "minecraft:lightning_strike;15.0",
                "minecraft:note_block_play;6.0",
                "minecraft:prime_fuse;9.0",
                "minecraft:projectile_land;10.0",
                "minecraft:projectile_shoot;7.0",
                "minecraft:sculk_sensor_tendrils_clicking;2.0",
                "minecraft:shear;3.0",
                "minecraft:shriek;12.0",
                "minecraft:splash;6.0",
                "minecraft:step;5.0",
                "minecraft:swim;4.0",
                "minecraft:teleport;12.0",
                "minecraft:resonate_1;5.0",
                "minecraft:resonate_2;5.0",
                "minecraft:resonate_3;5.0",
                "minecraft:resonate_4;5.0",
                "minecraft:resonate_5;5.0",
                "minecraft:resonate_6;5.0",
                "minecraft:resonate_7;5.0",
                "minecraft:resonate_8;5.0",
                "minecraft:resonate_9;5.0",
                "minecraft:resonate_10;5.0",
                "minecraft:resonate_11;5.0",
                "minecraft:resonate_12;5.0",
                "minecraft:resonate_13;5.0",
                "minecraft:resonate_14;5.0",
                "minecraft:resonate_15;5.0"
        ), obj -> obj instanceof String);

            ESCALATION_EVENTS = builder
                    .comment("List of game events that are considered worldwide escalations. Mobs will always investigate these with full priority, even if they don't know the player yet.")
                    .defineList("escalation_events", Arrays.asList(
                            "minecraft:entity_damage",
                            "minecraft:entity_die",
                            "minecraft:entity_roar",
                            "minecraft:explode"
                    ), obj -> obj instanceof String);

            builder.pop();

            builder.push("sound");

            ARMOR_NOISE_MULTIPLIERS = builder
                    .comment("Noise multiplier for armor materials. Format: 'material_name;multiplier'.")
                    .defineList("armor_noise_multipliers", Arrays.asList(
                            "leather;0.05",
                            "chainmail;0.15",
                            "golden;0.20",
                            "iron;0.30",
                            "diamond;0.20",
                            "netherite;0.25"
                    ), obj -> obj instanceof String);

            builder.pop();
        }
    }
}