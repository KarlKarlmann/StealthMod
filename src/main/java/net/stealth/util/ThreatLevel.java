package net.stealth.util;

/**
 * Zentralisiertes Enum für die Bedrohungsstufen des Stealth-Systems.
 * Wird nun sowohl serverseitig für die Berechnung als auch clientseitig für das Rendering verwendet.
 */
public enum ThreatLevel {
    NONE(0), 
    WATCHED(1), 
    SUSPICIOUS(2), 
    HUNTED(3);

    public final int severity;

    ThreatLevel(int severity) {
        this.severity = severity;
    }
}