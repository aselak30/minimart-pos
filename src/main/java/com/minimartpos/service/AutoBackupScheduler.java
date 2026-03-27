package com.minimartpos.service;

import com.minimartpos.config.AppConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Schedules and runs daily automatic database backups.
 *
 * How it works:
 *  - On startup, reads the configured backup time (default 02:00)
 *  - Calculates seconds until the next occurrence of that time
 *  - Schedules the first backup, then repeats every 24 hours
 *  - If the configured time has already passed today, schedules for tomorrow
 *  - Keeps the last N days of backups (configurable, default 30)
 *
 * Usage (called from MainApp.init()):
 *   AutoBackupScheduler.getInstance().start();
 *
 * Settings keys used:
 *   auto_backup_enabled   → "1" to enable
 *   auto_backup_time      → "HH:mm" e.g. "02:00"
 *   auto_backup_keep_days → number of days to retain
 *   backup_path           → directory (falls back to ~/MiniMartPOS/backups)
 */
public class AutoBackupScheduler {

    private static final Logger logger = LogManager.getLogger(AutoBackupScheduler.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static volatile AutoBackupScheduler instance;

    private final SettingsService settingsService = new SettingsService();
    private final BackupService   backupService   = new BackupService();

    private ScheduledExecutorService scheduler;
    private volatile boolean         running = false;
    private volatile LocalDateTime   lastBackupTime;
    private volatile boolean         lastBackupSuccess = false;

    /** Optional UI callback: fires on FX thread after each backup attempt. */
    private Consumer<BackupResult> onBackupComplete;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private AutoBackupScheduler() {}

    public static AutoBackupScheduler getInstance() {
        if (instance == null) {
            synchronized (AutoBackupScheduler.class) {
                if (instance == null) instance = new AutoBackupScheduler();
            }
        }
        return instance;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts the scheduler. Safe to call on every app startup —
     * reads settings each time so changes take effect after restart.
     */
    public synchronized void start() {
        if (running) return;
        if (!"1".equals(settingsService.get("auto_backup_enabled", "1"))) {
            logger.info("Auto backup is disabled in settings.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-backup");
            t.setDaemon(true);
            return t;
        });

        long delaySeconds = secondsUntilNextBackup();
        scheduler.scheduleAtFixedRate(
            this::runBackup,
            delaySeconds,
            86_400L,   // repeat every 24 hours
            TimeUnit.SECONDS
        );

        running = true;
        logger.info("AutoBackupScheduler started. Next backup in {} minutes ({} seconds).",
            delaySeconds / 60, delaySeconds);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
        logger.info("AutoBackupScheduler stopped.");
    }

    /** Triggers a backup immediately (used by the Settings screen manual button). */
    public BackupResult runNow() {
        return runBackupInternal();
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public boolean isRunning()              { return running; }
    public LocalDateTime getLastBackupTime(){ return lastBackupTime; }
    public boolean wasLastBackupSuccessful(){ return lastBackupSuccess; }
    public String getNextBackupTime()       {
        String time = settingsService.get("auto_backup_time", "02:00");
        return "Daily at " + time;
    }

    public void setOnBackupComplete(Consumer<BackupResult> callback) {
        this.onBackupComplete = callback;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void runBackup() {
        BackupResult result = runBackupInternal();
        if (onBackupComplete != null) {
            javafx.application.Platform.runLater(() -> onBackupComplete.accept(result));
        }
    }

    private BackupResult runBackupInternal() {
        logger.info("Auto backup starting...");
        try {
            File file = backupService.backup();
            lastBackupTime    = LocalDateTime.now();
            lastBackupSuccess = (file != null);

            if (file != null) {
                logger.info("Auto backup completed: {} ({} KB)",
                    file.getName(), file.length() / 1024);
                return new BackupResult(true, file,
                    "Backup saved: " + file.getName() +
                    " (" + (file.length() / 1024) + " KB)");
            } else {
                logger.error("Auto backup failed — BackupService returned null.");
                return new BackupResult(false, null,
                    "Backup failed. Check that mysqldump is installed and on PATH.");
            }
        } catch (Exception e) {
            lastBackupTime    = LocalDateTime.now();
            lastBackupSuccess = false;
            logger.error("Auto backup error: {}", e.getMessage(), e);
            return new BackupResult(false, null, "Backup error: " + e.getMessage());
        }
    }

    private long secondsUntilNextBackup() {
        String timeStr = settingsService.get("auto_backup_time", "02:00");
        LocalTime target;
        try {
            target = LocalTime.parse(timeStr, TIME_FMT);
        } catch (Exception e) {
            target = LocalTime.of(2, 0); // default 2 AM
        }

        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime next = LocalDate.now().atTime(target);
        if (!next.isAfter(now)) {
            next = next.plusDays(1); // already past today → schedule tomorrow
        }
        return Duration.between(now, next).getSeconds();
    }

    // ── BackupResult ──────────────────────────────────────────────────────────

    public static class BackupResult {
        private final boolean success;
        private final File    file;
        private final String  message;

        public BackupResult(boolean success, File file, String message) {
            this.success = success;
            this.file    = file;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public File    getFile()   { return file; }
        public String  getMessage(){ return message; }
    }
}
