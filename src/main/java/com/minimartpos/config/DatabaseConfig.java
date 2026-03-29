package com.minimartpos.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages the HikariCP connection pool for all database operations.
 * Reads connection settings from /config/connection.properties.
 *
 * Usage:
 *   Connection conn = DatabaseConfig.getConnection();
 *   // ... use conn ...
 *   conn.close();  // returns to pool, does NOT close physical connection
 */
public final class DatabaseConfig {

    private static final Logger logger = LogManager.getLogger(DatabaseConfig.class);

    private static HikariDataSource dataSource;
    private static boolean connected = false;

    // Loaded properties (also used by UI to show current settings)
    private static String dbHost;
    private static int    dbPort;
    private static String dbName;
    private static String dbUser;
    private static String dbPassword; // cached for saveProperties()

    /** Path to the external persistent config file (writable, outside the JAR). */
    private static final Path EXTERNAL_CONFIG_PATH = Paths.get(
        System.getProperty("user.home"), "MiniMartPOS", "config", "connection.properties"
    );

    private DatabaseConfig() {}

    /**
     * Initializes the HikariCP pool from connection.properties.
     * Call once at application startup.
     */
    public static synchronized void initialize() {
        Properties props = loadProperties();
        initialize(
            props.getProperty("db.host",     "localhost"),
            Integer.parseInt(props.getProperty("db.port", "3306")),
            props.getProperty("db.name",     "minimart_pos"),
            props.getProperty("db.user",     "pos_user"),
            props.getProperty("db.password", "")
        );
    }

    /**
     * Saves the current connection settings to the external config file
     * so they are reloaded automatically on next startup.
     * Call this after a successful connection from the DatabaseSetup screen.
     */
    public static synchronized void saveProperties() {
        try {
            Files.createDirectories(EXTERNAL_CONFIG_PATH.getParent());
            Properties props = new Properties();
            props.setProperty("db.host",     dbHost != null  ? dbHost     : "localhost");
            props.setProperty("db.port",     String.valueOf(dbPort > 0 ? dbPort : 3306));
            props.setProperty("db.name",     dbName != null  ? dbName     : "minimart_pos");
            props.setProperty("db.user",     dbUser != null  ? dbUser     : "pos_user");
            props.setProperty("db.password", dbPassword != null ? dbPassword : "");
            try (OutputStream out = new FileOutputStream(EXTERNAL_CONFIG_PATH.toFile())) {
                props.store(out, "MiniMart POS - Database Connection (auto-saved)");
            }
            logger.info("Connection settings saved to: {}", EXTERNAL_CONFIG_PATH);
        } catch (IOException e) {
            logger.warn("Could not save connection properties: {}", e.getMessage());
        }
    }

    /**
     * Initializes the pool with explicit parameters.
     * Used by DatabaseSetup screen when user configures connection.
     */
    public static synchronized void initialize(String host, int port, String dbName,
                                               String user, String password) {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        DatabaseConfig.dbHost     = host;
        DatabaseConfig.dbPort     = port;
        DatabaseConfig.dbName     = dbName;
        DatabaseConfig.dbUser     = user;
        DatabaseConfig.dbPassword = password; // cache for saveProperties()

        try {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName(AppConfig.DB_DRIVER);
            config.setJdbcUrl(buildJdbcUrl(host, port, dbName));
            config.setUsername(user);
            config.setPassword(password);

            // Pool settings
            config.setMaximumPoolSize(AppConfig.DB_POOL_MAX_SIZE);
            config.setMinimumIdle(AppConfig.DB_POOL_MIN_IDLE);
            config.setConnectionTimeout(AppConfig.DB_CONN_TIMEOUT_MS);
            config.setIdleTimeout(AppConfig.DB_IDLE_TIMEOUT_MS);
            config.setMaxLifetime(AppConfig.DB_MAX_LIFETIME_MS);
            config.setPoolName("MiniMartPOS-Pool");

            // Connection test
            config.setConnectionTestQuery("SELECT 1");
            config.setValidationTimeout(3000);

            // MySQL performance settings
            config.addDataSourceProperty("cachePrepStmts",          "true");
            config.addDataSourceProperty("prepStmtCacheSize",        "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit",    "2048");
            config.addDataSourceProperty("useServerPrepStmts",       "true");
            config.addDataSourceProperty("useLocalSessionState",     "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata",   "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits",      "true");
            config.addDataSourceProperty("maintainTimeStats",        "false");

            // Character encoding
            config.addDataSourceProperty("characterEncoding", "UTF-8");
            config.addDataSourceProperty("useUnicode",        "true");

            dataSource = new HikariDataSource(config);
            connected  = true;

            logger.info("Database pool initialized: {}:{}/{} (user={})", host, port, dbName, user);

        } catch (Exception e) {
            connected = false;
            logger.error("Database pool initialization failed: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot connect to database: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a connection from the pool.
     * ALWAYS close() the connection when done to return it to the pool.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database not initialized. Call DatabaseConfig.initialize() first.");
        }
        return dataSource.getConnection();
    }

    /**
     * Returns true if the pool was successfully initialized.
     */
    public static boolean isConnected() {
        return connected && dataSource != null && !dataSource.isClosed();
    }

    /**
     * Gracefully shuts down the connection pool.
     */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
        connected = false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildJdbcUrl(String host, int port, String dbName) {
        return String.format(
            "jdbc:mysql://%s:%d/%s?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
            host, port, dbName
        );
    }

    private static Properties loadProperties() {
        Properties props = new Properties();

        // 1. Try external (user-home) config first — this is written by saveProperties()
        if (Files.exists(EXTERNAL_CONFIG_PATH)) {
            try (InputStream is = new FileInputStream(EXTERNAL_CONFIG_PATH.toFile())) {
                props.load(is);
                logger.info("Loaded connection properties from external file: {}", EXTERNAL_CONFIG_PATH);
                return props;
            } catch (IOException e) {
                logger.warn("Could not read external connection.properties: {}", e.getMessage());
            }
        }

        // 2. Fall back to classpath defaults (inside JAR)
        try (InputStream is = DatabaseConfig.class.getResourceAsStream(AppConfig.CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                logger.info("Loaded connection properties from classpath: {}", AppConfig.CONFIG_FILE);
            } else {
                logger.warn("connection.properties not found; using defaults.");
            }
        } catch (IOException e) {
            logger.warn("Could not read connection.properties: {}", e.getMessage());
        }
        return props;
    }

    // ── Getters (for UI display) ───────────────────────────────────────────────
    public static String getDbHost() { return dbHost; }
    public static int    getDbPort() { return dbPort; }
    public static String getDbName() { return dbName; }
    public static String getDbUser() { return dbUser; }
}
