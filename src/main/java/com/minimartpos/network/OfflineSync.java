package com.minimartpos.network;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.model.Bill;
import com.minimartpos.repository.BillRepository;
import com.minimartpos.service.StockService;
import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Monitors database connectivity and drains the offline queue when reconnected.
 *
 * How it works:
 *  1. Checks DB connectivity every 15 seconds
 *  2. When DB comes back online after being offline → drains the OfflineQueue
 *  3. Each queued bill is saved to DB and stock is deducted
 *  4. Successfully synced bills are removed from the queue
 *  5. Fires a UI callback so the POS can show a notification
 *
 * Usage (in MainApp or POSTerminalController):
 *   OfflineSync.getInstance().start();
 *   OfflineSync.getInstance().setOnReconnect(count ->
 *       statusLabel.setText("✔ " + count + " offline bills synced."));
 */
public class OfflineSync {

    private static final Logger logger = LogManager.getLogger(OfflineSync.class);

    private static volatile OfflineSync instance;

    private final OfflineQueue      queue        = new OfflineQueue();
    private final BillRepository    billRepo     = new BillRepository();
    private final StockService      stockService = new StockService();

    private ScheduledExecutorService scheduler;
    private volatile boolean         wasOffline  = false;
    private volatile boolean         running     = false;

    /** Fires on FX thread with the count of bills successfully synced. */
    private Consumer<Integer> onReconnect;
    /** Fires on FX thread when DB goes offline. */
    private Runnable onDisconnect;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private OfflineSync() {}

    public static OfflineSync getInstance() {
        if (instance == null) {
            synchronized (OfflineSync.class) {
                if (instance == null) instance = new OfflineSync();
            }
        }
        return instance;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public synchronized void start() {
        if (running) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "offline-sync");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::checkAndSync, 5, 15, TimeUnit.SECONDS);
        running = true;
        logger.info("OfflineSync started. Pending bills: {}", queue.pendingCount());
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public void setOnReconnect(Consumer<Integer> callback)  { this.onReconnect   = callback; }
    public void setOnDisconnect(Runnable callback)          { this.onDisconnect  = callback; }

    // ── Status ────────────────────────────────────────────────────────────────

    public boolean isOffline()    { return !DatabaseConfig.isConnected(); }
    public int     pendingCount() { return queue.pendingCount(); }
    public boolean hasPending()   { return queue.hasPending(); }

    /**
     * Enqueues a bill for later sync (called by BillingService when DB save fails).
     */
    public boolean enqueue(Bill bill) { return queue.enqueue(bill); }

    // ── Private: connectivity check + drain ───────────────────────────────────

    private void checkAndSync() {
        boolean online = DatabaseConfig.isConnected();

        if (!online) {
            if (!wasOffline) {
                wasOffline = true;
                logger.warn("Database went OFFLINE.");
                if (onDisconnect != null) Platform.runLater(onDisconnect);
            }
            return;
        }

        // Was offline, now back online → drain queue
        if (wasOffline && queue.hasPending()) {
            wasOffline = false;
            logger.info("Database RECONNECTED. Draining {} offline bills.",
                        queue.pendingCount());
            int synced = drainQueue();
            if (synced > 0 && onReconnect != null) {
                final int count = synced;
                Platform.runLater(() -> onReconnect.accept(count));
            }
        } else {
            wasOffline = false;
        }
    }

    private int drainQueue() {
        List<Bill> pending = queue.getPending();
        int synced = 0;

        for (Bill bill : pending) {
            try {
                // Attempt to save the bill to DB
                boolean saved = billRepo.saveBill(bill);
                if (!saved) {
                    logger.warn("Failed to sync offline bill: {}", bill.getBillNumber());
                    continue;
                }

                // Restore stock deductions
                for (var item : bill.getItems()) {
                    stockService.deductStock(item.getProductId(), item.getQuantity(),
                        bill.getId(), bill.getCashierId());
                }

                // Remove from queue
                queue.dequeue(bill.getBillNumber());
                synced++;

                logger.info("Offline bill synced: {}", bill.getBillNumber());

            } catch (Exception e) {
                logger.error("Error syncing offline bill {}: {}",
                             bill.getBillNumber(), e.getMessage(), e);
            }
        }

        return synced;
    }
}
