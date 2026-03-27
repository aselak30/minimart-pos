package com.minimartpos.service;

import com.minimartpos.config.AppConfig;
import com.minimartpos.config.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles database backup and restore operations.
 *
 * Backup: runs mysqldump to produce a timestamped .sql file.
 * Restore: runs mysql client to import a .sql file.
 *
 * Automatically locates mysqldump/mysql on PATH and common install dirs.
 */
public class BackupService {

    private static final Logger logger = LogManager.getLogger(BackupService.class);

    private static final DateTimeFormatter BACKUP_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Common MySQL bin directories to search when not on PATH */
    private static final String[] MYSQL_SEARCH_DIRS = {
        // Windows - MySQL installer defaults
        "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin",
        "C:\\Program Files\\MySQL\\MySQL Server 8.1\\bin",
        "C:\\Program Files\\MySQL\\MySQL Server 8.2\\bin",
        "C:\\Program Files\\MySQL\\MySQL Server 8.3\\bin",
        "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin",
        "C:\\Program Files (x86)\\MySQL\\MySQL Server 8.0\\bin",
        // XAMPP / WAMP
        "C:\\xampp\\mysql\\bin",
        "C:\\wamp64\\bin\\mysql\\mysql8.0.31\\bin",
        "C:\\wamp\\bin\\mysql\\mysql8.0\\bin",
        // Linux / macOS common paths
        "/usr/bin",
        "/usr/local/bin",
        "/opt/homebrew/bin",
        "/usr/local/mysql/bin",
        "/opt/local/bin"
    };

    private final SettingsService settingsService = new SettingsService();

    // ── Backup ────────────────────────────────────────────────────────────────

    /**
     * Creates a full database backup.
     * @return Path to the created backup file, or null on failure
     */
    public File backup() {
        String backupDir = settingsService.get("backup_path", AppConfig.BACKUPS_DIR);
        String timestamp = LocalDateTime.now().format(BACKUP_FMT);
        String fileName  = "minimart_backup_" + timestamp + ".sql";
        File   outputFile = new File(backupDir, fileName);
        outputFile.getParentFile().mkdirs();

        String mysqldumpPath = findMysqlTool("mysqldump");
        if (mysqldumpPath == null) {
            logger.error("mysqldump not found. Searched PATH and common MySQL install directories.");
            return null;
        }
        logger.info("Using mysqldump at: {}", mysqldumpPath);

        String host     = DatabaseConfig.getDbHost();
        String db       = DatabaseConfig.getDbName();
        String user     = DatabaseConfig.getDbUser();
        String password = loadDbPassword();
        int    port     = DatabaseConfig.getDbPort();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(mysqldumpPath);
            cmd.add("--host=" + host);
            cmd.add("--port=" + port);
            cmd.add("--user=" + user);
            if (password != null && !password.isEmpty()) {
                cmd.add("--password=" + password);
            }
            cmd.add("--single-transaction");
            cmd.add("--routines");
            cmd.add("--triggers");
            cmd.add("--add-drop-table");
            cmd.add("--default-character-set=utf8mb4");
            cmd.add(db);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(outputFile);
            pb.redirectErrorStream(false);
            // Don't inherit environment (avoids stale MYSQL_PWD conflicts)
            pb.environment().remove("MYSQL_PWD");

            Process process = pb.start();

            StringBuilder err = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    err.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && outputFile.length() > 0) {
                logger.info("Backup created: {} ({} KB)",
                    outputFile.getAbsolutePath(), outputFile.length() / 1024);
                cleanOldBackups(backupDir, 30);
                return outputFile;
            } else {
                logger.error("mysqldump failed (exit {}): {}", exitCode, err);
                if (outputFile.exists()) outputFile.delete();
                return null;
            }

        } catch (Exception e) {
            logger.error("Backup error: {}", e.getMessage(), e);
            if (outputFile.exists()) outputFile.delete();
            return null;
        }
    }

    /**
     * Returns a human-readable diagnostic string for the Backup UI.
     * Shows whether mysqldump was found and where.
     */
    public String getMysqldumpDiagnostic() {
        String path = findMysqlTool("mysqldump");
        if (path != null) return "mysqldump found: " + path;
        return "mysqldump NOT found. Install MySQL or add its bin folder to PATH.\n" +
               "Searched: PATH + common install directories.";
    }

    /**
     * Restores a backup file into the database.
     * ⚠ WARNING: This overwrites the current database.
     */
    public boolean restore(File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            logger.error("Restore: backup file not found");
            return false;
        }

        String mysqlPath = findMysqlTool("mysql");
        if (mysqlPath == null) {
            logger.error("mysql client not found on PATH or common directories.");
            return false;
        }

        String host     = DatabaseConfig.getDbHost();
        String db       = DatabaseConfig.getDbName();
        String user     = DatabaseConfig.getDbUser();
        String password = loadDbPassword();
        int    port     = DatabaseConfig.getDbPort();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(mysqlPath);
            cmd.add("--host=" + host);
            cmd.add("--port=" + port);
            cmd.add("--user=" + user);
            if (password != null && !password.isEmpty()) {
                cmd.add("--password=" + password);
            }
            cmd.add(db);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectInput(backupFile);
            pb.redirectErrorStream(true);
            pb.environment().remove("MYSQL_PWD");

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Database restored from: {}", backupFile.getName());
                return true;
            } else {
                logger.error("mysql restore failed (exit {})", exitCode);
                return false;
            }

        } catch (Exception e) {
            logger.error("Restore error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Returns all backup files in the backup directory, newest first.
     */
    public File[] listBackups() {
        String backupDir = settingsService.get("backup_path", AppConfig.BACKUPS_DIR);
        File dir = new File(backupDir);
        if (!dir.exists()) return new File[0];

        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".sql"));
        if (files == null) return new File[0];

        java.util.Arrays.sort(files,
            (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    // ── Tool Discovery ────────────────────────────────────────────────────────

    /**
     * Finds mysqldump or mysql binary.
     * Checks: settings override → PATH → common install dirs.
     * Returns full path string, or null if not found.
     */
    private String findMysqlTool(String toolName) {
        // 1. User-configured override in settings
        String settingKey = "mysql_bin_path";
        String overrideDir = settingsService.get(settingKey, "");
        if (!overrideDir.isEmpty()) {
            File f = resolveToolFile(overrideDir, toolName);
            if (f != null) return f.getAbsolutePath();
        }

        // 2. Try plain command (i.e. on system PATH)
        if (isOnPath(toolName)) return toolName;

        // 3. Scan common directories
        for (String dir : MYSQL_SEARCH_DIRS) {
            File f = resolveToolFile(dir, toolName);
            if (f != null) return f.getAbsolutePath();
        }

        return null;
    }

    private File resolveToolFile(String dir, String toolName) {
        // Try with .exe (Windows) and without (Linux/macOS)
        for (String suffix : new String[]{".exe", ""}) {
            File f = new File(dir, toolName + suffix);
            if (f.exists() && f.isFile() && f.canExecute()) return f;
        }
        return null;
    }

    /** Quick check: can we run `toolName --version` without error? */
    private boolean isOnPath(String toolName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(toolName, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Load DB password from connection.properties (parsed same way as DatabaseConfig). */
    private String loadDbPassword() {
        try (java.io.InputStream is = getClass().getResourceAsStream(
                com.minimartpos.config.AppConfig.CONFIG_FILE)) {
            if (is == null) return "";
            java.util.Properties p = new java.util.Properties();
            p.load(is);
            return p.getProperty("db.password", "");
        } catch (Exception e) {
            logger.warn("Could not read db password for backup: {}", e.getMessage());
            return "";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void cleanOldBackups(String backupDir, int keepDays) {
        File dir = new File(backupDir);
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".sql"));
        if (files == null) return;

        long cutoffMs = System.currentTimeMillis() - (long) keepDays * 86_400_000L;
        int  deleted  = 0;
        for (File f : files) {
            if (f.lastModified() < cutoffMs) {
                if (f.delete()) deleted++;
            }
        }
        if (deleted > 0) logger.info("Cleaned {} old backup files.", deleted);
    }
}
