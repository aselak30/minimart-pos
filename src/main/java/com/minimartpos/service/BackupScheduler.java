package com.minimartpos.service;

import com.minimartpos.config.AppConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.time.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Automatic daily database backup scheduler.
 *
 * Runs a backup once per day at a configurable time (default 02:00).
 * The backup time is read from settings: "backup_auto_time" (HH:mm format).
 * Auto-backup can be toggled: "backup_auto_enabled" = "1" or "0".
 *
 * Usage (in MainApp.init()):
 *   BackupScheduler.getInstance().start();
 *   BackupScheduler.getInstance().setOnBackupComplete(file ->
 *       logger.info("Auto backup: {}", file.getName()));
 */
public class BackupScheduler {

    private static final Logger logger = LogManager.getLogger(BackupScheduler.class);
    private static volatile BackupScheduler instance;

    private final BackupService    backupService   = new BackupService();
    private final SettingsService  settingsService = new SettingsService();

    private ScheduledExecutorService scheduler;
    private volatile boolean          running = false;

    /** Called on FX thread after each backup (success = non-null File). */
    private Consumer<File> onBackupComplete;
    /** Called on FX thread on backup failure. */
    private Consumer<String> onBackupFailed;

    private BackupScheduler() {}

    public static BackupScheduler getInstance() {
        if (instance == null) {
            synchronized (BackupScheduler.class) {
                if (instance == null) instance = new BackupScheduler();
            }
        }
        return instance;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public synchronized void start() {
        if (running) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduleNext();
        running = true;
        logger.info("BackupScheduler started. Next backup at: {}",
            getConfiguredTime());
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
        logger.info("BackupScheduler stopped.");
    }

    public boolean isRunning() { return running; }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public void setOnBackupComplete(Consumer<File>   cb) { this.onBackupComplete = cb; }
    public void setOnBackupFailed(Consumer<String>   cb) { this.onBackupFailed   = cb; }

    // ── Manual trigger ────────────────────────────────────────────────────────

    /**
     * Triggers a backup immediately (e.g. from Settings → Backup Now button).
     * Runs on a background thread; calls onBackupComplete/onBackupFailed when done.
     */
    public void runNow() {
        new Thread(() -> performBackup("MANUAL"), "backup-now").start();
    }

    // ── Config ────────────────────────────────────────────────────────────────

    public LocalTime getConfiguredTime() {
        String raw = settingsService.get("backup_auto_time", "02:00");
        try {
            return LocalTime.parse(raw);
        } catch (Exception e) {
            return LocalTime.of(2, 0);
        }
    }

    public boolean isAutoEnabled() {
        return "1".equals(settingsService.get("backup_auto_enabled", "1"));
    }

    public String getBackupDir() {
        return settingsService.get("backup_path", AppConfig.BACKUPS_DIR);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void scheduleNext() {
        LocalTime  target  = getConfiguredTime();
        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(target);
        if (!next.isAfter(now)) next = next.plusDays(1);

        long delaySec = Duration.between(now, next).getSeconds();

        scheduler.schedule(() -> {
            if (isAutoEnabled()) {
                performBackup("AUTO");
            } else {
                logger.debug("Auto backup skipped (disabled in settings).");
            }
            // Re-schedule for next day
            if (running) scheduleNext();
        }, delaySec, TimeUnit.SECONDS);

        logger.info("Next auto backup scheduled in {}h {}m (at {})",
            delaySec / 3600, (delaySec % 3600) / 60, next);
    }

    private void performBackup(String trigger) {
        logger.info("Starting {} backup...", trigger);
        try {
            File result = backupService.backup();
            if (result != null) {
                logger.info("{} backup complete: {} ({} KB)",
                    trigger, result.getName(), result.length() / 1024);
                if (onBackupComplete != null) {
                    javafx.application.Platform.runLater(() ->
                        onBackupComplete.accept(result));
                }
            } else {
                logger.error("{} backup FAILED.", trigger);
                if (onBackupFailed != null) {
                    javafx.application.Platform.runLater(() ->
                        onBackupFailed.accept(trigger + " backup failed. Check logs."));
                }
            }
        } catch (Exception e) {
            logger.error("{} backup exception: {}", trigger, e.getMessage(), e);
            if (onBackupFailed != null) {
                final String msg = e.getMessage();
                javafx.application.Platform.runLater(() ->
                    onBackupFailed.accept("Backup error: " + msg));
            }
        }
    }
}
