package com.minimartpos.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minimartpos.model.Bill;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Local offline queue for bills created while the database is unreachable.
 *
 * Strategy:
 *  - Bills are serialized to JSON files in ~/MiniMartPOS/offline/
 *  - Each file = one bill, named by timestamp + bill number
 *  - On reconnection, OfflineSync walks the queue and submits each bill
 *  - Successfully synced files are deleted
 *
 * This covers the spec requirement:
 *   "Offline Capability: Local cache with auto-sync when connection restored"
 */
public class OfflineQueue {

    private static final Logger logger = LogManager.getLogger(OfflineQueue.class);

    private static final Path QUEUE_DIR = Path.of(
        System.getProperty("user.home"), "MiniMartPOS", "offline");

    private static final DateTimeFormatter FILE_TS =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final ObjectMapper json;

    public OfflineQueue() {
        json = new ObjectMapper();
        json.registerModule(new JavaTimeModule());
        json.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try { Files.createDirectories(QUEUE_DIR); }
        catch (IOException e) { logger.warn("Cannot create offline queue dir: {}", e.getMessage()); }
    }

    // ── Enqueue ───────────────────────────────────────────────────────────────

    /**
     * Saves a bill to the local queue when DB is offline.
     * Call this from BillingService when DB save fails due to connectivity.
     *
     * @return true if saved to local queue successfully
     */
    public boolean enqueue(Bill bill) {
        String ts       = LocalDateTime.now().format(FILE_TS);
        String filename = "bill_" + ts + "_" +
            bill.getBillNumber().replace("/", "-") + ".json";
        Path file = QUEUE_DIR.resolve(filename);
        try {
            json.writeValue(file.toFile(), bill);
            logger.info("Bill queued offline: {} → {}", bill.getBillNumber(), filename);
            return true;
        } catch (IOException e) {
            logger.error("Failed to queue bill offline: {}", e.getMessage(), e);
            return false;
        }
    }

    // ── Queue inspection ──────────────────────────────────────────────────────

    /**
     * Returns all pending offline bills in queue order (oldest first).
     */
    public List<Bill> getPending() {
        List<Bill> pending = new ArrayList<>();
        try {
            File[] files = QUEUE_DIR.toFile().listFiles(
                f -> f.isFile() && f.getName().endsWith(".json"));
            if (files == null) return pending;

            Arrays.sort(files, Comparator.comparing(File::getName)); // oldest first

            for (File f : files) {
                try {
                    Bill bill = json.readValue(f, Bill.class);
                    pending.add(bill);
                } catch (IOException e) {
                    logger.warn("Cannot read queued bill {}: {}", f.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("getPending error: {}", e.getMessage(), e);
        }
        return pending;
    }

    public int pendingCount() {
        try {
            File[] files = QUEUE_DIR.toFile().listFiles(
                f -> f.isFile() && f.getName().endsWith(".json"));
            return files != null ? files.length : 0;
        } catch (Exception e) { return 0; }
    }

    public boolean hasPending() { return pendingCount() > 0; }

    // ── Dequeue ───────────────────────────────────────────────────────────────

    /**
     * Removes a bill from the queue after it has been successfully synced.
     *
     * @param billNumber The bill number to remove (matches filename)
     */
    public boolean dequeue(String billNumber) {
        try {
            File[] files = QUEUE_DIR.toFile().listFiles(
                f -> f.isFile() && f.getName().contains(
                    billNumber.replace("/", "-")));
            if (files != null) {
                for (File f : files) {
                    if (f.delete()) {
                        logger.debug("Dequeued offline bill: {}", f.getName());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("dequeue error: {}", e.getMessage(), e);
        }
        return false;
    }

    /** Clears the entire queue (use with caution). */
    public int clearAll() {
        int cleared = 0;
        try {
            File[] files = QUEUE_DIR.toFile().listFiles(
                f -> f.isFile() && f.getName().endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    if (f.delete()) cleared++;
                }
            }
        } catch (Exception e) {
            logger.error("clearAll offline queue: {}", e.getMessage());
        }
        return cleared;
    }
}
