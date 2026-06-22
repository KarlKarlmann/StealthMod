package net.stealth.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.stealth.StealthMod;
import net.stealth.client.StealthHud;
import net.stealth.client.StealthHudConfig;
import net.stealth.client.ResourcePackExporter;
import net.stealth.util.StealthTextureHelper;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ARCHITEKTUR: STEALTH HUD CREATOR STUDIO SCREEN (Modular Desktop Edition with Adaptive Safety Prompts)
 * Ein voll-integrierter Ingame-Editor mit nativer Menüleiste, modalen Dialogen und Preset-Kopierern.
 * Layout-Berechnungen basieren ausschließlich auf der Bildschirmmitte (CENTER-only).
 */
public class StealthHudEditorScreen extends Screen {

    private static final ResourceLocation TEX_EYE = new ResourceLocation(StealthMod.MODID, "textures/gui/eyeopen.png");
    private static final ResourceLocation TEX_DAGGER = new ResourceLocation(StealthMod.MODID, "textures/gui/dagger_icon.png");
    private static final ResourceLocation TEX_WAVE = new ResourceLocation(StealthMod.MODID, "textures/gui/sound_wave_3.png");

    // Die 6 neuen, handgezeichneten Stealth-Szenarien für deine Resourcepacks!
    private static final ResourceLocation[] TEX_RP_PREVIEWS = new ResourceLocation[] {
        new ResourceLocation(StealthMod.MODID, "textures/rpacks/rp1.png"),
        new ResourceLocation(StealthMod.MODID, "textures/rpacks/rp2.png"),
        new ResourceLocation(StealthMod.MODID, "textures/rpacks/rp3.png"),
        new ResourceLocation(StealthMod.MODID, "textures/rpacks/rp4.png"),
        new ResourceLocation(StealthMod.MODID, "textures/rpacks/rp5.png"),
        new ResourceLocation(StealthMod.MODID, "textures/rpacks/rp6.png")
    };

    private SelectedElement selectedElement = SelectedElement.NONE;
    private boolean isDragging = false;
    private int dragStartX, dragStartY;
    private int elementStartOffsetValX, elementStartOffsetValY;

    // Projekt-Verwaltung & Änderungsverfolgung
    private final List<String> discoveredPacks = new ArrayList<>();
    private int activePackIndex = 0;
    private boolean saved = false;
    private boolean hasUnsavedChanges = false;

    // UI-Strukturen (Modularisierung)
    private final MenuBar menuBar = new MenuBar();
    private final StudioDialog activeDialog = new StudioDialog();

    private enum SelectedElement {
        NONE("Auswählen"), EYE("Auge"), DAGGER("Dolch"), SOUND("Schallwelle");
        final String displayName;
        SelectedElement(String displayName) { this.displayName = displayName; }
    }

    public StealthHudEditorScreen() {
        super(Component.translatable("gui.stealth.studio.title"));
        StealthTextureHelper.clearCache();
        StealthHudConfig.load();
        scanForStealthPacks();
        detectActiveStealthPack(); // Lädt das aktuell im Spiel aktive Pack direkt beim Starten des Editors!
    }

    private void scanForStealthPacks() {
        this.discoveredPacks.clear();
        this.discoveredPacks.add("Stealth_HUD_Template_Pack");

        try {
            File packFolder = new File(Minecraft.getInstance().gameDirectory, "resourcepacks");
            if (packFolder.exists() && packFolder.isDirectory()) {
                File[] files = packFolder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        if (name.startsWith("Stealth_HUD_") && !name.equals("Stealth_HUD_Template_Pack")) {
                            this.discoveredPacks.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[StealthStudio] Fehler beim Scannen der Packs: " + e.getMessage());
        }
    }

    /**
     * Prüft Minecrafts aktive Ressourcenpakete in den Optionen.
     * Falls ein "Stealth_HUD_"-Pack bereits geladen ist, wird dieses direkt als aktives Projekt gesetzt.
     */
    private void detectActiveStealthPack() {
        try {
            List<String> activePacks = Minecraft.getInstance().options.resourcePacks;
            for (String active : activePacks) {
                String cleanName = active;
                // Minecraft-Präfix "file/" für lokale Ordner entfernen
                if (cleanName.startsWith("file/")) {
                    cleanName = cleanName.substring(5);
                }
                if (cleanName.startsWith("Stealth_HUD_")) {
                    for (int i = 0; i < this.discoveredPacks.size(); i++) {
                        if (this.discoveredPacks.get(i).equals(cleanName)) {
                            this.activePackIndex = i;
                            loadSpecificPackProperties(cleanName);
                            hasUnsavedChanges = false;
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[StealthStudio] Fehler beim Erkennen des aktiven Packs: " + e.getMessage());
        }
    }

    @Override
    protected void init() {
        // Modale Dialog-Widgets initialisieren (Registriert EditBox und Buttons intern im Dialog-Zustand)
        this.activeDialog.initDialog();
    }

    private String getCleanPackName(String fullName) {
        return fullName.replace("Stealth_HUD_", "").replace(".zip", "").replace("Template_Pack", "Template");
    }

    private void loadSpecificPackProperties(String packFolderName) {
        try {
            File resourcepacksDir = new File(Minecraft.getInstance().gameDirectory, "resourcepacks");
            File packFile = new File(resourcepacksDir, packFolderName);
            Properties p = new Properties();

            if (packFile.exists()) {
                if (packFile.isDirectory()) {
                    File propFile = new File(packFile, "assets/stealth/config/default_hud.properties");
                    if (propFile.exists()) {
                        try (InputStream in = Files.newInputStream(propFile.toPath())) {
                            p.load(in);
                        }
                    }
                } else if (packFile.isFile() && packFolderName.toLowerCase().endsWith(".zip")) {
                    try (ZipFile zip = new ZipFile(packFile)) {
                        ZipEntry entry = zip.getEntry("assets/stealth/config/default_hud.properties");
                        if (entry != null) {
                            try (InputStream in = zip.getInputStream(entry)) {
                                p.load(in);
                            }
                        }
                    }
                }

                if (!p.isEmpty()) {
                    StealthHudConfig.eyeX = Integer.parseInt(p.getProperty("eye_x", "0"));
                    StealthHudConfig.eyeY = Integer.parseInt(p.getProperty("eye_y", "-24"));
                    StealthHudConfig.daggerX = Integer.parseInt(p.getProperty("dagger_x", "0"));
                    StealthHudConfig.daggerY = Integer.parseInt(p.getProperty("dagger_y", "14"));
                    StealthHudConfig.soundX = Integer.parseInt(p.getProperty("sound_x", "0"));
                    StealthHudConfig.soundY = Integer.parseInt(p.getProperty("sound_y", "-24"));
                }
                hasUnsavedChanges = false;
            }
        } catch (Exception e) {
            System.err.println("[StealthStudio] Fehler beim Einlesen des gewählten Packs: " + e.getMessage());
        }
    }

    private void openActivePackDirectory() {
        try {
            String folderName = this.discoveredPacks.get(this.activePackIndex);
            File packFolder = new File(Minecraft.getInstance().gameDirectory, "resourcepacks/" + folderName);
            if (packFolder.exists() && packFolder.isDirectory()) {
                net.minecraft.Util.getPlatform().openFile(packFolder);
            } else {
                net.minecraft.Util.getPlatform().openFile(new File(Minecraft.getInstance().gameDirectory, "resourcepacks"));
            }
        } catch (Exception e) {
            System.err.println("[StealthStudio] Konnte Ordner nicht öffnen: " + e.getMessage());
        }
    }

    private void openActivePackImages() {
        try {
            String folderName = this.discoveredPacks.get(this.activePackIndex);
            File packFolder = new File(Minecraft.getInstance().gameDirectory, "resourcepacks/" + folderName);
            if (packFolder.exists() && packFolder.isDirectory()) {
                File textureDir = new File(packFolder, "assets/stealth/textures/gui");
                if (!textureDir.exists()) textureDir.mkdirs();
                net.minecraft.Util.getPlatform().openFile(textureDir);
            }
        } catch (Exception e) {
            System.err.println("[StealthStudio] Konnte Bilder-Ordner nicht öffnen: " + e.getMessage());
        }
    }

    private void reloadResourcesLive() {
        try {
            StealthTextureHelper.clearCache();
            Minecraft.getInstance().reloadResourcePacks();
            Minecraft.getInstance().getToasts().addToast(new net.minecraft.client.gui.components.toasts.SystemToast(
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.translatable("gui.stealth.studio.toast.title"),
                Component.translatable("gui.stealth.studio.toast.message")
            ));
        } catch (Exception e) {
            System.err.println("[StealthStudio] Fehler beim Live-Reload: " + e.getMessage());
        }
    }

    private void performSave(String customName, int selectedIconIndex) {
        this.saved = true;
        String rawName = customName.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (rawName.isEmpty()) rawName = "Custom_Pack";
        String folderName = "Stealth_HUD_" + rawName;

        String activePack = this.discoveredPacks.get(this.activePackIndex);
        boolean isZip = activePack.toLowerCase().endsWith(".zip");

        if (isZip) {
            ResourcePackExporter.exportFromZipToFolder(activePack, folderName);
        } else {
            ResourcePackExporter.exportResourcePackWithName(folderName);
        }

        // Kopiert das ausgewählte Preset-Icon aus deinen rpacks-Ressourcen als pack.png
        copyPresetIconToPackWithName(folderName, selectedIconIndex);

        ResourcePackExporter.activateResourcePack(folderName);
        scanForStealthPacks();

        for (int i = 0; i < discoveredPacks.size(); i++) {
            if (discoveredPacks.get(i).equals(folderName)) {
                this.activePackIndex = i;
                break;
            }
        }
        hasUnsavedChanges = false;
    }

    private void copyPresetIconToPackWithName(String folderName, int iconIndex) {
        if (folderName.toLowerCase().endsWith(".zip")) return; // Schreibgeschützt

        File packFolder = new File(Minecraft.getInstance().gameDirectory, "resourcepacks/" + folderName);
        if (!packFolder.exists()) packFolder.mkdirs();

        File targetIcon = new File(packFolder, "pack.png");
        String filename = "rp" + (iconIndex + 1) + ".png";
        String sourceAsset = "/assets/stealth/textures/rpacks/" + filename;

        try (InputStream is = getClass().getResourceAsStream(sourceAsset)) {
            if (is != null) {
                Files.copy(is, targetIcon.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[StealthStudio] Fehler beim Kopieren des Preset-Icons: " + e.getMessage());
        }
    }

    private void resetPositions() {
        this.saved = true;
        String currentFolder = this.discoveredPacks.get(this.activePackIndex);
        ResourcePackExporter.deactivateResourcePack(currentFolder);
        StealthHudConfig.load();

        selectedElement = SelectedElement.NONE;
        this.activePackIndex = 0;
        hasUnsavedChanges = false;
        
        // Schließt das Menü und bringt den Spieler sofort zurück ins Spiel
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int screenWidth = this.width;
        int screenHeight = this.height;
        int topBarHeight = 34; // Genügend Platz für den Double-Decker Header

        // Maße der PNGs laden
        StealthTextureHelper.TextureDimensions eyeDims = StealthTextureHelper.getDimensions(TEX_EYE, 16, 16);
        StealthTextureHelper.TextureDimensions dagDims = StealthTextureHelper.getDimensions(TEX_DAGGER, 16, 16);
        StealthTextureHelper.TextureDimensions sndDims = StealthTextureHelper.getDimensions(TEX_WAVE, 48, 16);

        int ex = (screenWidth / 2) - (eyeDims.width / 2) + StealthHudConfig.eyeX;
        int ey = (screenHeight / 2) - (eyeDims.height / 2) + StealthHudConfig.eyeY;

        int dx = (screenWidth / 2) - (dagDims.width / 2) + StealthHudConfig.daggerX;
        int dy = (screenHeight / 2) - (dagDims.height / 2) + StealthHudConfig.daggerY;

        int sx = (screenWidth / 2) - (sndDims.width / 2) + StealthHudConfig.soundX;
        int sy = (screenHeight / 2) - (sndDims.height / 2) + StealthHudConfig.soundY;

        // Render HUD-Elemente im Arbeitsbereich
        if (ey >= topBarHeight) graphics.blit(TEX_EYE, ex, ey, 0, 0, eyeDims.width, eyeDims.height, eyeDims.width, eyeDims.height);
        if (dy >= topBarHeight) graphics.blit(TEX_DAGGER, dx, dy, 0, 0, dagDims.width, dagDims.height, dagDims.width, dagDims.height);
        if (sy >= topBarHeight) graphics.blit(TEX_WAVE, sx, sy, 0, 0, sndDims.width, sndDims.height, sndDims.width, sndDims.height);

        if (ey >= topBarHeight) drawSelectionBox(graphics, ex, ey, eyeDims.width, eyeDims.height, selectedElement == SelectedElement.EYE);
        if (dy >= topBarHeight) drawSelectionBox(graphics, dx, dy, dagDims.width, dagDims.height, selectedElement == SelectedElement.DAGGER);
        if (sy >= topBarHeight) drawSelectionBox(graphics, sx, sy, sndDims.width, sndDims.height, selectedElement == SelectedElement.SOUND);

        // Render Menüleiste (Muss über den HUD-Elementen liegen)
        graphics.fill(0, 0, screenWidth, topBarHeight, 0xF20F0F12); // Edles OS-Dunkelgrau
        graphics.fill(0, topBarHeight - 1, screenWidth, topBarHeight, 0x44FFFFFF); // Trennlinie

        // Zeichne Menübeschriftungen und Dateinamen
        menuBar.render(graphics, mouseX, mouseY);

        // Buttons rendern (Keine klobigen Buttons mehr auf dem Schirm!)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Dropdowns rendern (Z-Order ganz oben)
        menuBar.renderDropdowns(graphics, mouseX, mouseY);

        // Modale Dialoge zeichnen
        if (activeDialog.isOpen()) {
            activeDialog.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawSelectionBox(GuiGraphics graphics, int x, int y, int w, int h, boolean active) {
        int color = active ? 0xFF00FF00 : 0x55FFFFFF;
        graphics.fill(x - 1, y - 1, x + w + 1, y, color);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        graphics.fill(x - 1, y, x, y + h, color);
        graphics.fill(x + w, y, x + w + 1, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Modaler Dialog fängt alle Klicks ab
        if (activeDialog.isOpen()) {
            return activeDialog.mouseClicked(mouseX, mouseY, button);
        }

        // 2. Menüleiste abfangen
        if (menuBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int screenWidth = this.width;
        int screenHeight = this.height;
        int topBarHeight = 34;

        if (mouseY < topBarHeight) return false;

        StealthTextureHelper.TextureDimensions eyeDims = StealthTextureHelper.getDimensions(TEX_EYE, 16, 16);
        StealthTextureHelper.TextureDimensions dagDims = StealthTextureHelper.getDimensions(TEX_DAGGER, 16, 16);
        StealthTextureHelper.TextureDimensions sndDims = StealthTextureHelper.getDimensions(TEX_WAVE, 48, 16);

        int ex = (screenWidth / 2) - (eyeDims.width / 2) + StealthHudConfig.eyeX;
        int ey = (screenHeight / 2) - (eyeDims.height / 2) + StealthHudConfig.eyeY;

        int dx = (screenWidth / 2) - (dagDims.width / 2) + StealthHudConfig.daggerX;
        int dy = (screenHeight / 2) - (dagDims.height / 2) + StealthHudConfig.daggerY;

        int sx = (screenWidth / 2) - (sndDims.width / 2) + StealthHudConfig.soundX;
        int sy = (screenHeight / 2) - (sndDims.height / 2) + StealthHudConfig.soundY;

        // Sicherheitsabfrage: Wurde versucht, ein HUD-Element anzuklicken, während kein editierbares Pack aktiv ist?
        if ((mouseX >= ex && mouseX < ex + eyeDims.width && mouseY >= ey && mouseY < ey + eyeDims.height) ||
            (mouseX >= dx && mouseX < dx + dagDims.width && mouseY >= dy && mouseY < dy + dagDims.height) ||
            (mouseX >= sx && mouseX < sx + sndDims.width && mouseY >= sy && mouseY < sy + sndDims.height)) {

            String activePack = this.discoveredPacks.get(this.activePackIndex);
            boolean isZip = activePack.toLowerCase().endsWith(".zip");
            boolean isTemplate = this.activePackIndex == 0;

            if (isTemplate) {
                activeDialog.open(StudioDialog.DialogType.PROMPT_TEMPLATE);
                return true;
            } else if (isZip) {
                activeDialog.open(StudioDialog.DialogType.PROMPT_ZIP);
                return true;
            }
        }

        // Drag & Drop Initialisierung, falls ein editierbares Paket aktiv ist
        if (mouseX >= ex && mouseX < ex + eyeDims.width && mouseY >= ey && mouseY < ey + eyeDims.height) {
            selectedElement = SelectedElement.EYE;
            startDragging(mouseX, mouseY, StealthHudConfig.eyeX, StealthHudConfig.eyeY);
        } else if (mouseX >= dx && mouseX < dx + dagDims.width && mouseY >= dy && mouseY < dy + dagDims.height) {
            selectedElement = SelectedElement.DAGGER;
            startDragging(mouseX, mouseY, StealthHudConfig.daggerX, StealthHudConfig.daggerY);
        } else if (mouseX >= sx && mouseX < sx + sndDims.width && mouseY >= sy && mouseY < sy + sndDims.height) {
            selectedElement = SelectedElement.SOUND;
            startDragging(mouseX, mouseY, StealthHudConfig.soundX, StealthHudConfig.soundY);
        } else {
            selectedElement = SelectedElement.NONE;
        }

        return true;
    }

    private void startDragging(double mouseX, double mouseY, int currentOffsetX, int currentOffsetY) {
        isDragging = true;
        dragStartX = (int) mouseX;
        dragStartY = (int) mouseY;
        elementStartOffsetValX = currentOffsetX;
        elementStartOffsetValY = currentOffsetY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeDialog.isOpen()) return false;

        if (isDragging && selectedElement != SelectedElement.NONE) {
            String activePack = this.discoveredPacks.get(this.activePackIndex);
            boolean isZip = activePack.toLowerCase().endsWith(".zip");
            boolean isTemplate = this.activePackIndex == 0;

            if (isTemplate) {
                isDragging = false;
                selectedElement = SelectedElement.NONE;
                activeDialog.open(StudioDialog.DialogType.PROMPT_TEMPLATE);
                return false;
            } else if (isZip) {
                isDragging = false;
                selectedElement = SelectedElement.NONE;
                activeDialog.open(StudioDialog.DialogType.PROMPT_ZIP);
                return false;
            }

            int deltaX = (int) mouseX - dragStartX;
            int deltaY = (int) mouseY - dragStartY;

            switch (selectedElement) {
                case EYE:
                    StealthHudConfig.eyeX = elementStartOffsetValX + deltaX;
                    StealthHudConfig.eyeY = elementStartOffsetValY + deltaY;
                    hasUnsavedChanges = true;
                    break;
                case DAGGER:
                    StealthHudConfig.daggerX = elementStartOffsetValX + deltaX;
                    StealthHudConfig.daggerY = elementStartOffsetValY + deltaY;
                    hasUnsavedChanges = true;
                    break;
                case SOUND:
                    StealthHudConfig.soundX = elementStartOffsetValX + deltaX;
                    StealthHudConfig.soundY = elementStartOffsetValY + deltaY;
                    hasUnsavedChanges = true;
                    break;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeDialog.isOpen()) {
            return activeDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        
        // Fängt ESC-Taste zum Sichern vor Datenverlust ab!
        if (keyCode == 256) {
            boolean isTemplate = this.activePackIndex == 0;
            if (hasUnsavedChanges && !isTemplate) {
                activeDialog.open(StudioDialog.DialogType.CONFIRM_EXIT); // Prompt zum Sichern vor Schließen
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeDialog.isOpen()) {
            return activeDialog.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        if (activeDialog.isOpen()) {
            activeDialog.tick();
        }
        super.tick();
    }

    @Override
    public void onClose() {
        if (!this.saved) {
            StealthHudConfig.load();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // =========================================================================
    // MODULAR COMPONENT 1: THE DESKTOP MENU BAR (Double-Decker Layout)
    // =========================================================================
    private class MenuBar {
        private boolean packOpen = false;
        private boolean folderOpen = false;
        private boolean openSelectOpen = false;
        private boolean helpOpen = false;

        private final int menuPackX = 10;
        private final int menuFolderX = 60;
        private final int menuRefreshX = 125;
        
        private final int packWidth = 45;
        private final int folderWidth = 60;
        private final int refreshWidth = 65;

        public void render(GuiGraphics graphics, int mouseX, int mouseY) {
            boolean overPack = mouseX >= menuPackX && mouseX < menuPackX + packWidth && mouseY >= 16 && mouseY < 32;
            boolean overFolder = mouseX >= menuFolderX && mouseX < menuFolderX + folderWidth && mouseY >= 16 && mouseY < 32;
            boolean overRefresh = mouseX >= menuRefreshX && mouseX < menuRefreshX + refreshWidth && mouseY >= 16 && mouseY < 32;
            
            // Rechter Help-Button Hover & Render (y-Wert angepasst)
            boolean overHelp = mouseX >= (width - 46) && mouseX < (width - 10) && mouseY >= 16 && mouseY < 32;

            // --- ZEILE 1 (y = 4): Projektname (Zentriert in der Mitte!) ---
            String activePack = discoveredPacks.get(activePackIndex);
            boolean isZip = activePack.toLowerCase().endsWith(".zip");
            boolean isTemplate = (activePackIndex == 0);
            
            Component flag = Component.translatable(isTemplate ? "gui.stealth.studio.flag.none" : (isZip ? "gui.stealth.studio.flag.zip" : "gui.stealth.studio.flag.folder"));
            Component projectName = isTemplate ? Component.translatable("gui.stealth.studio.none") : Component.literal(getCleanPackName(activePack));
            Component text = Component.translatable("gui.stealth.studio.project", projectName, flag);
            
            int textWidth = font.width(text);
            graphics.drawString(font, text, (width / 2) - (textWidth / 2), 4, 0xFFBBBBBB);

            // --- ZEILE 2 (y = 18): Die Programmtasten ---
            int packColor = packOpen || overPack ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.drawString(font, Component.translatable("gui.stealth.studio.pack"), menuPackX, 18, packColor);

            int folderColor = folderOpen || overFolder ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.drawString(font, Component.translatable("gui.stealth.studio.folder"), menuFolderX, 18, folderColor);

            int refreshColor = overRefresh ? 0xFF66FF66 : 0xFFAAAAAA;
            graphics.drawString(font, Component.translatable("gui.stealth.studio.refresh"), menuRefreshX, 18, refreshColor);

            int helpColor = helpOpen || overHelp ? 0xFFFFDD44 : 0xFFAAAAAA;
            graphics.drawString(font, Component.translatable("gui.stealth.studio.help"), width - 46, 18, helpColor);
        }

        public void renderDropdowns(GuiGraphics graphics, int mouseX, int mouseY) {
            int dropdownY = 34; // Nach unten geschoben unter die Menüleiste
            int itemH = 18;

            // 1. PACK-MENU DROPDOWN
            if (packOpen) {
                int dropX = menuPackX;
                String activePack = discoveredPacks.get(activePackIndex);
                boolean isZip = activePack.toLowerCase().endsWith(".zip");

                Component[] items = {
                    Component.translatable("gui.stealth.studio.dropdown.new"),
                    Component.translatable("gui.stealth.studio.dropdown.open"),
                    Component.translatable("gui.stealth.studio.dropdown.save"),
                    Component.translatable("gui.stealth.studio.dropdown.save_as"),
                    Component.translatable("gui.stealth.studio.dropdown.unload")
                };
                boolean[] disabled = {false, false, isZip, false, false}; // "Speichern" bei ZIPs deaktivieren

                int dropW = 120;
                int dropH = items.length * itemH;

                graphics.fill(dropX, dropdownY, dropX + dropW, dropdownY + dropH, 0xF20B0B0C);
                graphics.renderOutline(dropX, dropdownY, dropW, dropH, 0x44FFFFFF);

                for (int i = 0; i < items.length; i++) {
                    int itemY = dropdownY + (i * itemH);
                    boolean hover = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= itemY && mouseY < itemY + itemH;

                    if (hover && !disabled[i]) {
                        graphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemH, 0x22FFFFFF);
                    }

                    int color = disabled[i] ? 0xFF555555 : (hover ? 0xFFFFFFFF : 0xFFCCCCCC);
                    graphics.drawString(font, items[i], dropX + 6, itemY + 5, color);
                }
            }

            // 2. OPEN SELECT DROPDOWN (Unter-Dropdown für Öffnen)
            if (openSelectOpen) {
                int openX = menuPackX + 115;
                int openY = dropdownY + itemH;
                int dropW = 150;
                int dropH = discoveredPacks.size() * itemH;

                graphics.fill(openX, openY, openX + dropW, openY + dropH, 0xF20B0B0C);
                graphics.renderOutline(openX, openY, dropW, dropH, 0x44FFFFFF);

                for (int i = 0; i < discoveredPacks.size(); i++) {
                    int itemY = openY + (i * itemH);
                    boolean hover = mouseX >= openX && mouseX < openX + dropW && mouseY >= itemY && mouseY < itemY + itemH;
                    boolean active = (i == activePackIndex);

                    if (hover) {
                        graphics.fill(openX + 1, itemY, openX + dropW - 1, itemY + itemH, 0x22FFFFFF);
                    }

                    String displayName = (discoveredPacks.get(i).toLowerCase().endsWith(".zip") ? "🔒 " : "📁 ") + getCleanPackName(discoveredPacks.get(i));
                    int color = active ? 0xFFFFAA00 : (hover ? 0xFFFFFFFF : 0xFF999999);
                    graphics.drawString(font, displayName, openX + 6, itemY + 5, color);
                }
            }

            // 3. FOLDER-MENU DROPDOWN
            if (folderOpen) {
                int dropX = menuFolderX;
                String activePack = discoveredPacks.get(activePackIndex);
                boolean isZip = activePack.toLowerCase().endsWith(".zip");

                Component[] items = {
                    Component.translatable("gui.stealth.studio.dropdown.root"),
                    Component.translatable("gui.stealth.studio.dropdown.images")
                };
                boolean[] disabled = {isZip, isZip}; // Komplett sperren bei ZIPs

                int dropW = 110;
                int dropH = items.length * itemH;

                graphics.fill(dropX, dropdownY, dropX + dropW, dropdownY + dropH, 0xF20B0B0C);
                graphics.renderOutline(dropX, dropdownY, dropW, dropH, 0x44FFFFFF);

                for (int i = 0; i < items.length; i++) {
                    int itemY = dropdownY + (i * itemH);
                    boolean hover = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= itemY && mouseY < itemY + itemH;

                    if (hover && !disabled[i]) {
                        graphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemH, 0x22FFFFFF);
                    }

                    int color = disabled[i] ? 0xFF555555 : (hover ? 0xFFFFFFFF : 0xFFCCCCCC);
                    graphics.drawString(font, items[i], dropX + 6, itemY + 5, color);
                }
            }

            // 4. HELP-MENU DROPDOWN
            if (helpOpen) {
                int dropX = width - 180;
                int dropW = 170;
                
                // Ko-fi, GitHub, Wiki
                int dropH = 54 + (2 * itemH); // Erste Zelle ist vergrößert für den Spendenaufruf

                graphics.fill(dropX, dropdownY, dropX + dropW, dropdownY + dropH, 0xF20B0B0C);
                graphics.renderOutline(dropX, dropdownY, dropW, dropH, 0x44FFFFFF);

                // Ebene A: Ko-fi Kaffee-Aufruf
                boolean hoverKofi = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropdownY && mouseY < dropdownY + 54;
                if (hoverKofi) {
                    graphics.fill(dropX + 1, dropdownY + 1, dropX + dropW - 1, dropdownY + 53, 0x15FFDD44);
                }
                
                graphics.renderOutline(dropX + 4, dropdownY + 4, dropW - 8, 46, hoverKofi ? 0xFFFFDD44 : 0x22FFFFFF);
                graphics.drawString(font, Component.translatable("gui.stealth.studio.help.dropdown.coffee"), dropX + 8, dropdownY + 8, hoverKofi ? 0xFFFFDD44 : 0xFFE4E4E7);
                graphics.drawString(font, Component.translatable("gui.stealth.studio.help.dropdown.coffee.sub1"), dropX + 8, dropdownY + 22, 0xFF888888);
                graphics.drawString(font, Component.translatable("gui.stealth.studio.help.dropdown.coffee.sub2"), dropX + 8, dropdownY + 34, 0xFF888888);

                // Ebene B: GitHub Issue Tracker
                int issueY = dropdownY + 54;
                boolean hoverIssue = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= issueY && mouseY < issueY + itemH;
                if (hoverIssue) {
                    graphics.fill(dropX + 1, issueY, dropX + dropW - 1, issueY + itemH, 0x22FFFFFF);
                }
                graphics.drawString(font, Component.translatable("gui.stealth.studio.help.dropdown.issue"), dropX + 8, issueY + 5, hoverIssue ? 0xFFFFFFFF : 0xFFCCCCCC);

                // Ebene C: Wiki/Readme
                int wikiY = issueY + itemH;
                boolean hoverWiki = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= wikiY && mouseY < wikiY + itemH;
                if (hoverWiki) {
                    graphics.fill(dropX + 1, wikiY, dropX + dropW - 1, wikiY + itemH, 0x22FFFFFF);
                }
                graphics.drawString(font, Component.translatable("gui.stealth.studio.help.dropdown.wiki"), dropX + 8, wikiY + 5, hoverWiki ? 0xFFFFFFFF : 0xFFCCCCCC);
            }
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int dropdownY = 34;
            int itemH = 18;

            // Klick auf Pack Header
            if (mouseX >= menuPackX && mouseX < menuPackX + packWidth && mouseY >= 16 && mouseY < 32) {
                packOpen = !packOpen;
                folderOpen = false;
                helpOpen = false;
                openSelectOpen = false;
                return true;
            }

            // Klick auf Folder Header (Überprüfung ob überhaupt ein editierbares Pack geladen ist)
            if (mouseX >= menuFolderX && mouseX < menuFolderX + folderWidth && mouseY >= 16 && mouseY < 32) {
                String activePack = discoveredPacks.get(activePackIndex);
                boolean isZip = activePack.toLowerCase().endsWith(".zip");
                boolean isTemplate = activePackIndex == 0;

                if (isTemplate) {
                    activeDialog.open(StudioDialog.DialogType.PROMPT_TEMPLATE);
                    return true;
                } else if (isZip) {
                    activeDialog.open(StudioDialog.DialogType.PROMPT_ZIP);
                    return true;
                }

                folderOpen = !folderOpen;
                packOpen = false;
                helpOpen = false;
                openSelectOpen = false;
                return true;
            }

            // Klick auf Refresh Header (Sofortiges Laden!)
            if (mouseX >= menuRefreshX && mouseX < menuRefreshX + refreshWidth && mouseY >= 16 && mouseY < 32) {
                reloadResourcesLive();
                packOpen = false;
                folderOpen = false;
                helpOpen = false;
                openSelectOpen = false;
                return true;
            }

            // Klick auf Help Header
            if (mouseX >= width - 46 && mouseX < width - 10 && mouseY >= 16 && mouseY < 32) {
                helpOpen = !helpOpen;
                packOpen = false;
                folderOpen = false;
                openSelectOpen = false;
                return true;
            }

            // Klick-Abfänge in geöffneten Dropdowns
            if (packOpen) {
                int dropX = menuPackX;
                int dropW = 120;
                String activePack = discoveredPacks.get(activePackIndex);
                boolean isZip = activePack.toLowerCase().endsWith(".zip");

                if (mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropdownY && mouseY < dropdownY + (5 * itemH)) {
                    int clickedIdx = (int) ((mouseY - dropdownY) / itemH);
                    if (clickedIdx == 0) { // Neu
                        activeDialog.open(StudioDialog.DialogType.NEW);
                    } else if (clickedIdx == 1) { // Öffnen Toggle
                        openSelectOpen = !openSelectOpen;
                        return true;
                    } else if (clickedIdx == 2 && !isZip) { // Speichern (Gesperrt bei ZIP)
                        performSave(getCleanPackName(activePack), 0); // Behält das alte Icon bei normalem Speichern
                    } else if (clickedIdx == 3) { // Speichern unter...
                        activeDialog.open(StudioDialog.DialogType.SAVE_AS);
                    } else if (clickedIdx == 4) { // Unload -> Verlässt nun den Editor direkt!
                        resetPositions();
                    }
                    packOpen = false;
                    openSelectOpen = false;
                    return true;
                }
            }

            if (openSelectOpen) {
                int openX = menuPackX + 115;
                int openY = dropdownY + itemH;
                int dropW = 150;
                int totalH = discoveredPacks.size() * itemH;

                if (mouseX >= openX && mouseX < openX + dropW && mouseY >= openY && mouseY < openY + totalH) {
                    int clickedIdx = (int) ((mouseY - openY) / itemH);
                    if (clickedIdx >= 0 && clickedIdx < discoveredPacks.size()) {
                        activePackIndex = clickedIdx;
                        String selected = discoveredPacks.get(clickedIdx);
                        loadSpecificPackProperties(selected);
                        ResourcePackExporter.activateResourcePack(selected);
                    }
                    packOpen = false;
                    openSelectOpen = false;
                    return true;
                }
            }

            if (folderOpen) {
                int dropX = menuFolderX;
                int dropW = 110;
                String activePack = discoveredPacks.get(activePackIndex);
                boolean isZip = activePack.toLowerCase().endsWith(".zip");

                if (!isZip && mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropdownY && mouseY < dropdownY + (2 * itemH)) {
                    int clickedIdx = (int) ((mouseY - dropdownY) / itemH);
                    if (clickedIdx == 0) { // Hauptverzeichnis
                        openActivePackDirectory();
                    } else if (clickedIdx == 1) { // Bilder
                        openActivePackImages();
                    }
                    folderOpen = false;
                    return true;
                }
            }

            if (helpOpen) {
                int dropX = width - 180;
                int dropW = 170;

                // Spendenaufruf (Ebene A)
                if (mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropdownY && mouseY < dropdownY + 54) {
                    try {
                        net.minecraft.Util.getPlatform().openUri(new java.net.URI("https://ko-fi.com/karlkarlmann"));
                    } catch (Exception e) {
                        System.err.println("[StealthStudio] Link fehlerhaft: " + e.getMessage());
                    }
                    helpOpen = false;
                    return true;
                }
                
                // GitHub (Ebene B)
                int issueY = dropdownY + 54;
                if (mouseX >= dropX && mouseX < dropX + dropW && mouseY >= issueY && mouseY < issueY + itemH) {
                    try {
                        net.minecraft.Util.getPlatform().openUri(new java.net.URI("https://github.com/KarlKarlmann/StealthMod/issues/"));
                    } catch (Exception e) {
                        System.err.println("[StealthStudio] Link fehlerhaft: " + e.getMessage());
                    }
                    helpOpen = false;
                    return true;
                }

                // Wiki (Ebene C)
                int wikiY = issueY + itemH;
                if (mouseX >= dropX && mouseX < dropX + dropW && mouseY >= wikiY && mouseY < wikiY + itemH) {
                    try {
                        net.minecraft.Util.getPlatform().openUri(new java.net.URI("https://www.n3g.de/wiki/en/stealth/index.html"));
                    } catch (Exception e) {
                        System.err.println("[StealthStudio] Link fehlerhaft: " + e.getMessage());
                    }
                    helpOpen = false;
                    return true;
                }
            }

            // Schließe Dropdowns bei Klick ins Leere
            if (packOpen || folderOpen || openSelectOpen || helpOpen) {
                packOpen = false;
                folderOpen = false;
                openSelectOpen = false;
                helpOpen = false;
                return true;
            }

            return false;
        }
    }

    // =========================================================================
    // MODULAR COMPONENT 2: THE MODAL DIALOG WINDOW MANAGER (With Icon Selector)
    // =========================================================================
    private class StudioDialog {
        public enum DialogType { NONE, NEW, SAVE_AS, PROMPT_TEMPLATE, PROMPT_ZIP, CONFIRM_EXIT }
        private DialogType currentType = DialogType.NONE;

        private EditBox inputField;
        private Button btnOk;
        private Button btnCancel;
        
        // Icon-Auswahl Zustände (0 bis 5 für deine 6 rp-Bilder!)
        private int selectedIconPreset = 0;

        public boolean isOpen() {
            return currentType != DialogType.NONE;
        }

        private boolean isOpenPrompt() {
            return currentType == DialogType.PROMPT_TEMPLATE || currentType == DialogType.PROMPT_ZIP || currentType == DialogType.CONFIRM_EXIT;
        }

        public void initDialog() {
            int dialogW = 240; // Leicht verbreitert für die 2x3 Grid-Symmetrie
            int dialogH = 168; // Vergrößert für das 6-Icon-Grid
            int x = (width / 2) - (dialogW / 2);
            int y = (height / 2) - (dialogH / 2);

            this.inputField = new EditBox(font, x + 15, y + 25, dialogW - 30, 20, Component.translatable("gui.stealth.studio.dialog.input_label"));
            this.inputField.setMaxLength(32);

            // Statement-Expression Lambda robust in Blocks eingehüllt!
            this.btnOk = Button.builder(Component.translatable("gui.stealth.studio.dialog.button.ok"), b -> { this.onOk(); })
                    .bounds(x + 15, y + 142, 100, 18)
                    .build();

            this.btnCancel = Button.builder(Component.translatable("gui.stealth.studio.dialog.button.cancel"), b -> { this.close(); })
                    .bounds(x + dialogW - 115, y + 142, 100, 18)
                    .build();
        }

        public void open(DialogType type) {
            this.currentType = type;
            int dialogW = 240;
            int dialogH = isOpenPrompt() ? 100 : 168;
            int x = (width / 2) - (dialogW / 2);
            int y = (height / 2) - (dialogH / 2);

            // Widgets zentrieren
            this.inputField.setX(x + 15);
            this.inputField.setY(y + 25);
            
            // Buttons vertikal nach oben schieben bei kompakten Warnungs-Prompts
            int buttonY = y + (isOpenPrompt() ? 72 : 142);
            this.btnOk.setY(buttonY);
            this.btnCancel.setY(buttonY);

            this.selectedIconPreset = 0; // Standardmäßig das erste Pack-Icon vorwählen

            // Standardwerte setzen für Eingabe-Modi
            if (type == DialogType.SAVE_AS) {
                String activePack = discoveredPacks.get(activePackIndex);
                this.inputField.setValue(getCleanPackName(activePack) + "_Kopie");
            } else {
                this.inputField.setValue("Neues_Pack");
            }
            this.inputField.setFocused(true);

            // Button-Beschriftungen kontextbezogen überschreiben
            if (type == DialogType.CONFIRM_EXIT) {
                this.btnOk.setMessage(Component.translatable("gui.stealth.studio.dialog.button.save"));
                this.btnCancel.setMessage(Component.translatable("gui.stealth.studio.dialog.button.discard"));
            } else if (isOpenPrompt()) {
                this.btnOk.setMessage(Component.translatable("gui.stealth.studio.dialog.button.yes"));
                this.btnCancel.setMessage(Component.translatable("gui.stealth.studio.dialog.button.no"));
            } else {
                this.btnOk.setMessage(Component.translatable("gui.stealth.studio.dialog.button.ok"));
                this.btnCancel.setMessage(Component.translatable("gui.stealth.studio.dialog.button.cancel"));
            }
        }

        public void close() {
            this.currentType = DialogType.NONE;
        }

        private void onOk() {
            if (currentType == DialogType.PROMPT_TEMPLATE) {
                // Direkte Weiterleitung auf Dialog "Neues Paket erstellen"
                open(DialogType.NEW);
            } else if (currentType == DialogType.PROMPT_ZIP) {
                // Direkte Weiterleitung auf Dialog "Paket kopieren / Klonen"
                open(DialogType.SAVE_AS);
            } else if (currentType == DialogType.CONFIRM_EXIT) {
                // Schnelles Speichern der vorgenommenen Änderungen direkt im Ordner
                String activePack = discoveredPacks.get(activePackIndex);
                performSave(getCleanPackName(activePack), 0);
                hasUnsavedChanges = false;
                close();
                Minecraft.getInstance().setScreen(null); // GUI schließen
            } else {
                String value = inputField.getValue().trim();
                if (!value.isEmpty()) {
                    performSave(value, selectedIconPreset);
                }
                close();
            }
        }

        private void onCancel() {
            if (currentType == DialogType.CONFIRM_EXIT) {
                // Änderungen verwerfen (erneutes Einlesen von Config-Standards) und schließen
                StealthHudConfig.load();
                hasUnsavedChanges = false;
                close();
                Minecraft.getInstance().setScreen(null); // GUI schließen
            } else {
                close();
            }
        }

        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Bildschirm abdunkeln
            graphics.fill(0, 0, width, height, 0x88000000);

            int dialogW = 240;
            int dialogH = isOpenPrompt() ? 100 : 168;
            int x = (width / 2) - (dialogW / 2);
            int y = (height / 2) - (dialogH / 2);

            // Dialog Fenster zeichnen
            graphics.fill(x, y, x + dialogW, y + dialogH, 0xFF18181B); // Desktop-Window Dunkelgrau
            graphics.renderOutline(x, y, dialogW, dialogH, 0xFF444449); // Feiner Rahmen

            if (isOpenPrompt()) {
                Component title;
                if (currentType == DialogType.CONFIRM_EXIT) {
                    title = Component.translatable("gui.stealth.studio.prompt.exit.title");
                } else {
                    title = currentType == DialogType.PROMPT_TEMPLATE ? Component.translatable("gui.stealth.studio.prompt.template.title") : Component.translatable("gui.stealth.studio.prompt.zip.title");
                }
                graphics.drawString(font, title.copy().withStyle(style -> style.withColor(0xFFFF5555)), x + 15, y + 10, 0xFFE4E4E7);
                
                if (currentType == DialogType.CONFIRM_EXIT) {
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.exit.line1"), x + 15, y + 28, 0xFFCCCCCC);
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.exit.line2"), x + 15, y + 40, 0xFFCCCCCC);
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.exit.line3"), x + 15, y + 54, 0xFFAAAAAA);
                } else if (currentType == DialogType.PROMPT_TEMPLATE) {
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.template.line1"), x + 15, y + 28, 0xFFCCCCCC);
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.template.line2"), x + 15, y + 40, 0xFFCCCCCC);
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.template.line3"), x + 15, y + 54, 0xFFAAAAAA);
                } else {
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.zip.line1"), x + 15, y + 28, 0xFFCCCCCC);
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.zip.line2"), x + 15, y + 40, 0xFFCCCCCC);
                    graphics.drawString(font, Component.translatable("gui.stealth.studio.prompt.zip.line3"), x + 15, y + 54, 0xFFAAAAAA);
                }
            } else {
                Component title = currentType == DialogType.NEW ? Component.translatable("gui.stealth.studio.dialog.title.new") : Component.translatable("gui.stealth.studio.dialog.title.save_as");
                graphics.drawString(font, title, x + 15, y + 10, 0xFFE4E4E7);

                // 1. Textfeld rendern
                this.inputField.render(graphics, mouseX, mouseY, partialTick);

                // 2. Icon Selektor Titel
                graphics.drawString(font, Component.translatable("gui.stealth.studio.dialog.select_icon"), x + 15, y + 50, 0xFFAAAAAA);

                // 3. Sechs interaktive Custom-Vorschau-Bilder in einem feinen 2x3 Grid zeichnen!
                // Spalte 0: x+45 | Spalte 1: x+105 | Spalte 2: x+165
                // Reihe 1: y+64 | Reihe 2: y+100
                for (int i = 0; i < 6; i++) {
                    int col = i % 3;
                    int row = i / 3;
                    int boxX = x + 45 + (col * 60);
                    int boxY = y + 64 + (row * 36);

                    boolean isHovered = mouseX >= boxX && mouseX < boxX + 30 && mouseY >= boxY && mouseY < boxY + 30;
                    boolean isSelected = (selectedIconPreset == i);

                    // Box-Hintergrund zeichnen
                    graphics.fill(boxX, boxY, boxX + 30, boxY + 30, isSelected ? 0x2200FF00 : (isHovered ? 0x11FFFFFF : 0x00000000));
                    
                    // Box-Umriss zeichnen (Grün für das ausgewählte Preset-Szenario)
                    int outlineColor = isSelected ? 0xFF00FF00 : (isHovered ? 0xFFFFFFFF : 0xFF444449);
                    graphics.renderOutline(boxX, boxY, 30, 30, outlineColor);

                    // Rendere die 6 wunderschönen Stealth-Alex Szenarien maßgetreu im Grid!
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    graphics.blit(TEX_RP_PREVIEWS[i], boxX + 2, boxY + 2, 0, 0, 26, 26, 26, 26);
                    RenderSystem.disableBlend();
                }
            }

            // Buttons rendern
            this.btnOk.render(graphics, mouseX, mouseY, partialTick);
            this.btnCancel.render(graphics, mouseX, mouseY, partialTick);
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Wenn ein einfacher Prompt aktiv ist, überspringen wir Eingabefelder und Icon-Klicks
            if (isOpenPrompt()) {
                if (this.btnOk.mouseClicked(mouseX, mouseY, button)) return true;
                if (this.btnCancel.mouseClicked(mouseX, mouseY, button)) return true;
                return true;
            }

            if (this.inputField.mouseClicked(mouseX, mouseY, button)) {
                this.inputField.setFocused(true);
                return true;
            } else {
                this.inputField.setFocused(false);
            }

            // Klicks auf deine 6 Custom Icon-Presets im 2x3 Grid prüfen
            int dialogW = 240;
            int x = (width / 2) - (dialogW / 2);
            int y = (height / 2) - (isOpenPrompt() ? 50 : 84); // Grid y-Offset mathematisch zentrieren

            for (int i = 0; i < 6; i++) {
                int col = i % 3;
                int row = i / 3;
                int boxX = x + 45 + (col * 60);
                int boxY = y + 64 + (row * 36);

                if (mouseX >= boxX && mouseX < boxX + 30 && mouseY >= boxY && mouseY < boxY + 30) {
                    selectedIconPreset = i;
                    return true;
                }
            }

            if (this.btnOk.mouseClicked(mouseX, mouseY, button)) return true;
            if (this.btnCancel.mouseClicked(mouseX, mouseY, button)) return true;

            return true; // Schützt das dahinterliegende System vor Klicks
        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 257 || keyCode == 335) { // Enter-Taste
                onOk();
                return true;
            }
            if (keyCode == 256) { // ESC-Taste
                close();
                return true;
            }
            return !isOpenPrompt() && this.inputField.keyPressed(keyCode, scanCode, modifiers);
        }

        public boolean charTyped(char codePoint, int modifiers) {
            return !isOpenPrompt() && this.inputField.charTyped(codePoint, modifiers);
        }

        public void tick() {
            if (!isOpenPrompt()) {
                this.inputField.tick();
            }
        }
    }
}