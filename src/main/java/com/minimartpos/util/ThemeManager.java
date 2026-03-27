package com.minimartpos.util;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.security.SessionManager;
import javafx.scene.Scene;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-user colour theme manager.
 *
 * Themes are stored as CSS variable overrides injected at runtime.
 * Each user's preference is persisted in the app_settings table
 * under the key "theme_<userId>".
 *
 * Usage:
 *   ThemeManager.applyTheme(scene, ThemeManager.getUserTheme());
 *   ThemeManager.setUserTheme("dark");
 */
public final class ThemeManager {

    private static final Logger logger = LogManager.getLogger(ThemeManager.class);

    // ── Built-in themes ───────────────────────────────────────────────────────

    /**
     * Map of theme name → CSS variable overrides applied on .root.
     * Only variables that DIFFER from the default (Blue) theme are listed.
     */
    public static final Map<String, ThemeDefinition> THEMES = new LinkedHashMap<>();

    static {
        THEMES.put("blue", new ThemeDefinition(
            "Blue (Default)", "#1976D2",
            ".root{" +
            "-pos-primary:#1976D2;-pos-primary-dark:#1565C0;-pos-primary-light:#BBDEFB;" +
            "-pos-accent:#FF6F00;-pos-accent-dark:#E65100;" +
            "-pos-bg:#F5F7FA;-pos-surface:#FFFFFF;-pos-surface-alt:#F0F4F8;" +
            "-pos-border:#DDE3EA;-pos-text-primary:#1A2332;-pos-text-secondary:#5A6A7A;" +
            "-pos-sidebar-bg:#1A2332;-pos-sidebar-text:#ECEFF4;-pos-sidebar-hover:#2C3E50;}"
        ));

        THEMES.put("green", new ThemeDefinition(
            "Forest Green", "#2E7D32",
            ".root{" +
            "-pos-primary:#2E7D32;-pos-primary-dark:#1B5E20;-pos-primary-light:#C8E6C9;" +
            "-pos-accent:#FF8F00;-pos-accent-dark:#E65100;" +
            "-pos-bg:#F1F8F1;-pos-surface:#FFFFFF;-pos-surface-alt:#E8F5E9;" +
            "-pos-border:#C8E6C9;-pos-text-primary:#1B2D1B;-pos-text-secondary:#4A6741;" +
            "-pos-sidebar-bg:#1B3A1F;-pos-sidebar-text:#E8F5E9;-pos-sidebar-hover:#2E5733;}"
        ));

        THEMES.put("purple", new ThemeDefinition(
            "Royal Purple", "#6A1B9A",
            ".root{" +
            "-pos-primary:#6A1B9A;-pos-primary-dark:#4A148C;-pos-primary-light:#E1BEE7;" +
            "-pos-accent:#FF6D00;-pos-accent-dark:#E65100;" +
            "-pos-bg:#F9F4FC;-pos-surface:#FFFFFF;-pos-surface-alt:#F3E5F5;" +
            "-pos-border:#CE93D8;-pos-text-primary:#1A0A2E;-pos-text-secondary:#6A4A7A;" +
            "-pos-sidebar-bg:#1A0A2E;-pos-sidebar-text:#F3E5F5;-pos-sidebar-hover:#2D1554;}"
        ));

        THEMES.put("dark", new ThemeDefinition(
            "Dark Mode", "#212121",
            ".root{" +
            "-pos-primary:#90CAF9;-pos-primary-dark:#64B5F6;-pos-primary-light:#1E3A5F;" +
            "-pos-accent:#FFB300;-pos-accent-dark:#FF8F00;" +
            "-pos-bg:#121212;-pos-surface:#1E1E1E;-pos-surface-alt:#2A2A2A;" +
            "-pos-border:#3A3A3A;-pos-text-primary:#ECEFF4;-pos-text-secondary:#9AAAB8;" +
            "-pos-text-disabled:#5A6A7A;-pos-text-on-dark:#FFFFFF;" +
            "-pos-sidebar-bg:#0D0D0D;-pos-sidebar-text:#ECEFF4;-pos-sidebar-hover:#2C2C2C;}"
        ));

        THEMES.put("teal", new ThemeDefinition(
            "Ocean Teal", "#00695C",
            ".root{" +
            "-pos-primary:#00695C;-pos-primary-dark:#004D40;-pos-primary-light:#B2DFDB;" +
            "-pos-accent:#FF6F00;-pos-accent-dark:#E65100;" +
            "-pos-bg:#F0FAFA;-pos-surface:#FFFFFF;-pos-surface-alt:#E0F2F1;" +
            "-pos-border:#B2DFDB;-pos-text-primary:#0A1F1D;-pos-text-secondary:#3A6B65;" +
            "-pos-sidebar-bg:#00251A;-pos-sidebar-text:#E0F2F1;-pos-sidebar-hover:#00402E;}"
        ));

        THEMES.put("red", new ThemeDefinition(
            "Crimson Red", "#B71C1C",
            ".root{" +
            "-pos-primary:#B71C1C;-pos-primary-dark:#7F0000;-pos-primary-light:#FFCDD2;" +
            "-pos-accent:#FF8F00;-pos-accent-dark:#E65100;" +
            "-pos-bg:#FFF8F8;-pos-surface:#FFFFFF;-pos-surface-alt:#FFEBEE;" +
            "-pos-border:#FFCDD2;-pos-text-primary:#2D0A0A;-pos-text-secondary:#7A3A3A;" +
            "-pos-sidebar-bg:#2D0A0A;-pos-sidebar-text:#FFEBEE;-pos-sidebar-hover:#5C1010;}"
        ));
    }

    // Theme CSS is injected as a second stylesheet that overrides main.css variables
    private static final String THEME_STYLE_ID = "minimart-theme-override";

    private ThemeManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the current user's saved theme name (defaults to "blue").
     */
    public static String getUserTheme() {
        try {
            int userId = SessionManager.getCurrentUser().getId();
            return loadThemeFromDb(userId);
        } catch (Exception e) {
            return "blue";
        }
    }

    /**
     * Saves the theme choice for the current user and applies it to all scenes.
     */
    public static void setUserTheme(String themeName) {
        if (!THEMES.containsKey(themeName)) {
            logger.warn("Unknown theme: {}", themeName);
            return;
        }
        try {
            int userId = SessionManager.getCurrentUser().getId();
            saveThemeToDb(userId, themeName);
        } catch (Exception e) {
            logger.warn("Could not save theme preference: {}", e.getMessage());
        }
        // Apply immediately to current scene
        Scene scene = SceneManager.getPrimaryStage().getScene();
        if (scene != null) applyTheme(scene, themeName);
    }

    /**
     * Applies the named theme to a scene (call after every screen navigation).
     */
    public static void applyTheme(Scene scene, String themeName) {
        ThemeDefinition theme = THEMES.getOrDefault(themeName, THEMES.get("blue"));
        // Remove any previous override stylesheet
        scene.getStylesheets().removeIf(s -> s.contains(THEME_STYLE_ID));

        // Inject override as a data URI stylesheet
        String css = theme.css();
        String dataUri = "data:text/css," + encodeForDataUri(css);
        scene.getStylesheets().add(dataUri);
        logger.debug("Applied theme '{}' to scene", themeName);
    }

    /**
     * Applies current user's theme to the given scene.
     * Call this in navigateTo after every screen load.
     */
    public static void applyCurrentUserTheme(Scene scene) {
        applyTheme(scene, getUserTheme());
    }

    // ── DB Persistence ────────────────────────────────────────────────────────

    private static String loadThemeFromDb(int userId) {
        String key = "theme_" + userId;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT setting_value FROM settings WHERE setting_key=? LIMIT 1")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return THEMES.containsKey(v) ? v : "blue";
                }
            }
        } catch (Exception e) {
            logger.debug("Theme load skipped: {}", e.getMessage());
        }
        return "blue";
    }

    private static void saveThemeToDb(int userId, String themeName) {
        String key = "theme_" + userId;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO settings (setting_key, setting_value) VALUES (?,?) " +
                 "ON DUPLICATE KEY UPDATE setting_value=?")) {
            ps.setString(1, key);
            ps.setString(2, themeName);
            ps.setString(3, themeName);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warn("Theme save failed: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String encodeForDataUri(String css) {
        return css.replace(" ", "%20")
                  .replace("{", "%7B").replace("}", "%7D")
                  .replace(":", "%3A").replace(";", "%3B")
                  .replace("#", "%23").replace(",", "%2C")
                  .replace("(", "%28").replace(")", "%29")
                  .replace("'", "%27").replace("\"", "%22")
                  .replace("\n", "").replace("\r", "");
    }

    // ── Inner Types ───────────────────────────────────────────────────────────

    public record ThemeDefinition(String displayName, String primaryColor, String css) {}
}
