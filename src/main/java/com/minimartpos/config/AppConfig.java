package com.minimartpos.config;

/**
 * Global application constants and configuration defaults.
 * These values can be overridden at runtime via connection.properties.
 */
public final class AppConfig {

    private AppConfig() {}

    // ── Application Identity ──────────────────────────────────────────────────
    public static final String APP_NAME        = "minimartpos POS Ultimate";
    public static final String APP_VERSION     = "1.6.0";
    public static final String APP_TITLE       = "minimartpos POS v" + APP_VERSION;

    // ── Window Dimensions ─────────────────────────────────────────────────────
    public static final double MIN_WIDTH       = 1024;
    public static final double MIN_HEIGHT      = 768;
    public static final double DEFAULT_WIDTH   = 1280;
    public static final double DEFAULT_HEIGHT  = 800;

    // ── Session / Security ────────────────────────────────────────────────────
    /** Session timeout in minutes (default 30). Admin can change per user. */
    public static final int    SESSION_TIMEOUT_MIN  = 30;
    public static final int    MAX_FAILED_LOGINS    = 5;
    public static final int    LOCKOUT_DURATION_MIN = 15;
    public static final int    CAPTCHA_AFTER        = 3;
    public static final int    BCRYPT_STRENGTH      = 12;

    // ── Database ──────────────────────────────────────────────────────────────
    public static final String DB_DRIVER            = "com.mysql.cj.jdbc.Driver";
    public static final int    DB_POOL_MAX_SIZE      = 10;
    public static final int    DB_POOL_MIN_IDLE      = 2;
    public static final long   DB_CONN_TIMEOUT_MS   = 5000;
    public static final long   DB_IDLE_TIMEOUT_MS   = 600_000;
    public static final long   DB_MAX_LIFETIME_MS   = 1_800_000;

    // ── POS Behaviour ─────────────────────────────────────────────────────────
    public static final int    QUICK_PRODUCT_GRID_COLS = 5;
    public static final int    QUICK_PRODUCT_GRID_ROWS = 4;
    public static final double DEFAULT_TAX_RATE        = 0.0;   // 0% default; set per product
    public static final int    LOW_STOCK_WARNING_DAYS  = 30;
    public static final int    EXPIRY_WARNING_DAYS     = 7;

    // ── Receipt ───────────────────────────────────────────────────────────────
    public static final int    RECEIPT_WIDTH_CHARS     = 40;

    // ── Paths (classpath relative) ────────────────────────────────────────────
    public static final String FXML_BASE       = "/fxml/";
    public static final String CSS_BASE        = "/css/";
    public static final String IMG_BASE        = "/images/";
    public static final String SQL_BASE        = "/sql/";
    public static final String CONFIG_FILE     = "/config/connection.properties";
    public static final String LOG4J_CONFIG    = "/config/log4j2.xml";

    // ── Report Output ─────────────────────────────────────────────────────────
    public static final String REPORTS_DIR     = System.getProperty("user.home") + "/MiniMartPOS/reports/";
    public static final String BACKUPS_DIR     = System.getProperty("user.home") + "/MiniMartPOS/backups/";
    public static final String LOGS_DIR        = System.getProperty("user.home") + "/MiniMartPOS/logs/";
}
