package net.stealth.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Hilfsklasse, um die echten physikalischen Dimensionen (Breite/Höhe)
 * einer registrierten PNG-Textur direkt aus OpenGL auszulesen.
 */
public class StealthTextureHelper {
    private static final Map<ResourceLocation, TextureDimensions> CACHE = new HashMap<>();

    public static class TextureDimensions {
        public final int width;
        public final int height;

        public TextureDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Ermittelt die echten Maße eines PNGs.
     * Nutzt OpenGL-Abfragen, um Datei-I/O im Render-Thread komplett zu vermeiden.
     */
    public static TextureDimensions getDimensions(ResourceLocation texture, int defaultWidth, int defaultHeight) {
        return CACHE.computeIfAbsent(texture, loc -> {
            try {
                // Erzwingt, dass Minecraft die Textur in den Grafikspeicher lädt und bindet
                Minecraft.getInstance().getTextureManager().getTexture(loc).bind();
                
                // Fragt OpenGL direkt nach der Breite und Höhe der aktuell gebundenen 2D-Textur
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                
                if (width > 0 && height > 0) {
                    return new TextureDimensions(width, height);
                }
            } catch (Exception e) {
                System.err.println("[Stealth] Fehler beim Auslesen der Bildmaße für " + loc + ": " + e.getMessage());
            }
            // Fallback auf die Standardmaße, falls das Bild nicht geladen werden konnte
            return new TextureDimensions(defaultWidth, defaultHeight);
        });
    }

    /**
     * Leert den Cache. Extrem wichtig, wenn der Spieler F3+T drückt oder das Resourcepack wechselt!
     */
    public static void clearCache() {
        CACHE.clear();
    }
}