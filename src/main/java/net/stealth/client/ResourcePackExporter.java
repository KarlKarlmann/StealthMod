package net.stealth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * ARCHITEKTUR: DECOUPLED HUD ASSET EXPORTER & SYSTEM ACTIVATOR
 * Exportiert HUD-Konfigurationen und die aktuell im Spiel geladenen Assets.
 * Schreibt die aktuellen RAM-Koordinaten des Editors in die default_hud.properties.
 * Nutzt den ResourceManager von Minecraft, um DYNAMISCH alle Texturen aus stealth:textures/gui/ zu klonen.
 */
public class ResourcePackExporter {

    private static final String PACK_PREFIX = "file/";

    // =========================================================================
    // KOMPATIBILITÄTSSCHICHT FÜR DEN EDITOR SCREEN
    // =========================================================================

    public static void exportResourcePackWithName(String folderName) {
        exportCurrentActiveHUD(folderName);
    }

    public static void exportFromZipToFolder(String zipFileName, String targetFolderName) {
        // GENIALER TRICK: Wir ignorieren die ZIP-Datei auf der Festplatte komplett!
        // Da Minecrafts ResourceManager das aktive ZIP-Pack ohnehin schon in den RAM geladen hat,
        // klonen wir die Dateien einfach direkt über die sichere Pipeline. Keine Zip-Slip-Gefahr!
        exportCurrentActiveHUD(targetFolderName);
    }

    // =========================================================================
    // SICHERE EXPORT-LOGIK (ResourceManager)
    // =========================================================================

    private static void exportCurrentActiveHUD(String folderName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        try {
            File mcDir = mc.gameDirectory;
            File packDir = new File(mcDir, "resourcepacks/" + folderName);
            File metaFile = new File(packDir, "pack.mcmeta");
            File readmeFile = new File(packDir, "readme_guide.txt");
            File preferencesFile = new File(packDir, "preferences.txt");
            File targetPackIcon = new File(packDir, "pack.png");
            File hudTexturesDir = new File(packDir, "assets/stealth/textures/gui");
            File configDir = new File(packDir, "assets/stealth/config");

            // Erstelle die benötigten Ordnerstrukturen
            if (!hudTexturesDir.exists()) hudTexturesDir.mkdirs();
            if (!configDir.exists()) configDir.mkdirs();

            // 1. pack.mcmeta schreiben
            if (!metaFile.exists()) {
                String cleanName = folderName.replace("Stealth_HUD_", "");
                String mcmetaContent = "{\n" +
                        "  \"pack\": {\n" +
                        "    \"pack_format\": 15,\n" +
                        "    \"description\": \"Custom Stealth HUD pack named: " + cleanName + "\"\n" +
                        "  }\n" +
                        "}\n";
                Files.writeString(metaFile.toPath(), mcmetaContent, java.nio.charset.StandardCharsets.UTF_8);
            }

            // 2. readme_guide.txt für den Spieler generieren
            if (!readmeFile.exists()) {
                String readmeContent = """
                        === STEALTH HUD CUSTOMIZATION GUIDE ===

                        English:
                        You can now customize all textures of the Stealth system to your liking.

                        --- 1. EDITING FILES ---
                        - Open the directory: "assets/stealth/textures/gui"
                        - IMPORTANT: Your new images do not need to adhere to a strict 16x16 grid!
                          * The game reads the width and height directly from your PNG and renders it at that exact size on your screen.
                          * Combined Soundwaves: Both sides must be in a single PNG! (Default: 48x16 px)

                        --- 2. DEFAULT SETTINGS ---
                        - You can find your editor-configured positions in "assets/stealth/config/default_hud.properties".
                        - This layout will be loaded automatically as soon as this resource pack is activated!

                        --- 3. IN-GAME LIVE TESTING ---
                        - The pack is automatically enabled in-game when you click 'Save'!
                        - Press "F3 + T" at any time in-game to reload the textures and config live!
                        """;
                Files.writeString(readmeFile.toPath(), readmeContent, java.nio.charset.StandardCharsets.UTF_8);
            }

            // 3. preferences.txt für Debugging schreiben (wird immer überschrieben, um aktuell zu bleiben!)
            int screenWidth = mc.getWindow().getScreenWidth();
            int screenHeight = mc.getWindow().getScreenHeight();
            double guiScale = mc.getWindow().getGuiScale();
            
            String prefContent = "=== STEALTH HUD CREATOR PREFERENCES ===\n" +
                    "Dieses Pack wurde mit folgenden Bildschirm-Einstellungen im Editor exportiert:\n\n" +
                    "Aufloesung: " + screenWidth + "x" + screenHeight + " Pixel\n" +
                    "GUI-Skalierung (GUI Scale): " + guiScale + "\n\n" +
                    "Hinweis zur Fehlersuche:\n" +
                    "Wenn du dieses Resourcepack heruntergeladen hast und das HUD bei dir an\n" +
                    "komplett falschen Stellen auf dem Bildschirm auftaucht, liegt das meistens\n" +
                    "an einer abweichenden GUI-Skalierung. Vergleiche deine Video Settings im Spiel\n" +
                    "mit den oben genannten Werten des Creators.";
            Files.writeString(preferencesFile.toPath(), prefContent, java.nio.charset.StandardCharsets.UTF_8);

            // 4. Aktuelle Koordinaten aus dem RAM (StealthHudConfig) in die properties-Datei schreiben
            File defaultHudFile = new File(configDir, "default_hud.properties");
            Properties p = new Properties();
            p.setProperty("eye_x", String.valueOf(StealthHudConfig.eyeX));
            p.setProperty("eye_y", String.valueOf(StealthHudConfig.eyeY));
            p.setProperty("dagger_x", String.valueOf(StealthHudConfig.daggerX));
            p.setProperty("dagger_y", String.valueOf(StealthHudConfig.daggerY));
            p.setProperty("sound_x", String.valueOf(StealthHudConfig.soundX));
            p.setProperty("sound_y", String.valueOf(StealthHudConfig.soundY));

            try (OutputStream out = Files.newOutputStream(defaultHudFile.toPath())) {
                p.store(out, "HUD Default Positions - Exported from the Stealth In-game Editor");
            }

            // Fallback Pack-Icon kopieren (falls der Editor es nicht ohnehin selbst überschreibt)
            if (!targetPackIcon.exists()) {
                ResourceLocation eyeLoc = new ResourceLocation("stealth", "textures/gui/eyeopen.png");
                Optional<Resource> eyeRes = mc.getResourceManager().getResource(eyeLoc);
                if (eyeRes.isPresent()) {
                    try (InputStream is = eyeRes.get().open()) {
                        Files.copy(is, targetPackIcon.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // 5. DYNAMISCHES EXPORTIEREN ALLER TEXTUREN AUS "stealth:textures/gui"
            // Wir scannen das geladene Ressourcen-System nach allen PNGs in diesem Verzeichnis.
            // Das sorgt dafür, dass neue Texturen (wie "hiding_box.png") automatisch mitgenommen werden!
            var resourceManager = mc.getResourceManager();
            Map<ResourceLocation, Resource> activeGuiResources = resourceManager.listResources(
                "textures/gui", 
                rl -> rl.getNamespace().equals("stealth") && rl.getPath().endsWith(".png")
            );

            int exportedCount = 0;
            for (Map.Entry<ResourceLocation, Resource> entry : activeGuiResources.entrySet()) {
                ResourceLocation rl = entry.getKey();
                Resource resource = entry.getValue();

                // Extrahiere den reinen Dateinamen (z.B. "textures/gui/eyeopen.png" -> "eyeopen.png")
                String fullPath = rl.getPath();
                String assetName = fullPath.substring(fullPath.lastIndexOf('/') + 1);

                File targetFile = new File(hudTexturesDir, assetName);
                if (targetFile.exists()) {
                    continue; // Bereits manuell veränderte Texturen des Spielers niemals überschreiben!
                }

                try (InputStream is = resource.open()) {
                    Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    exportedCount++;
                } catch (Exception e) {
                    System.err.println("[Stealth] Fehler beim Exportieren des Assets " + rl + ": " + e.getMessage());
                }
            }

            mc.player.sendSystemMessage(Component.literal("§a[Stealth] HUD pack with " + exportedCount + " textures successfully exported!"));

        } catch (Exception e) {
            mc.player.sendSystemMessage(Component.literal("§c[Stealth] Export error: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    public static void activateResourcePack(String folderName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        try {
            mc.getResourcePackRepository().reload();
            List<String> selectedPacks = new ArrayList<>(mc.options.resourcePacks);
            String fullPackId = PACK_PREFIX + folderName;

            // Filter out all other active Stealth-HUD packs to prevent texture overlapping!
            selectedPacks.removeIf(packId -> packId.contains("Stealth_HUD_") && !packId.equals(fullPackId));

            if (!selectedPacks.contains(fullPackId)) {
                selectedPacks.add(fullPackId);
            }

            mc.options.resourcePacks = selectedPacks;
            mc.options.save();
            mc.getResourcePackRepository().setSelected(selectedPacks);

            mc.player.sendSystemMessage(Component.literal("§6[Stealth] Activating HUD project..."));
            mc.reloadResourcePacks();

        } catch (Exception e) {
            mc.player.sendSystemMessage(Component.literal("§c[Stealth] Activation error: " + e.getMessage()));
        }
    }

    public static void deactivateResourcePack(String folderName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        try {
            List<String> selectedPacks = new ArrayList<>(mc.options.resourcePacks);
            String fullPackId = PACK_PREFIX + folderName;
            
            if (selectedPacks.contains(fullPackId)) {
                selectedPacks.remove(fullPackId);
                mc.options.resourcePacks = selectedPacks;
                mc.options.save();
                mc.getResourcePackRepository().setSelected(selectedPacks);
                
                mc.player.sendSystemMessage(Component.literal("§e[Stealth] HUD resource pack deactivated, resetting to defaults..."));
                mc.reloadResourcePacks();
            }
        } catch (Exception e) {
            System.err.println("[Stealth] Error deactivating resource pack: " + e.getMessage());
        }
    }
}