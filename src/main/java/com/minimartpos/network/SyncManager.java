package com.minimartpos.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minimartpos.service.ProductService;
import com.minimartpos.service.SettingsService;
import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Multi-machine event synchronization via UDP multicast.
 *
 * Architecture:
 * ┌─────────────┐ UDP multicast ┌─────────────┐
 * │ Machine A │ ──── SyncEvent JSON ───▶ │ Machine B │
 * │ (Admin PC) │ ◀─── SyncEvent JSON ──── │ (Cashier) │
 * └─────────────┘ 239.255.1.1:45678 └─────────────┘
 *
 * When to use:
 * - Product price/stock changes → other machines refresh their cache
 * - New bill finalized → Admin Dashboard KPIs update live
 * - Settings changed → all machines reload settings
 * - User logged in/out → Admin sees live cashier status
 *
 * What is NOT synced here (handled by shared DB):
 * - The actual bill/product data itself (always from DB)
 * - This is purely a "please refresh cache X" notification system
 *
 * Usage:
 * SyncManager sync = SyncManager.getInstance();
 * sync.start();
 * sync.broadcast(new SyncEvent(SyncEvent.Type.STOCK_CHANGED, productId,
 * machineCode));
 * sync.addListener(SyncEvent.Type.STOCK_CHANGED, event ->
 * refreshProductCache());
 * sync.stop();
 */
public class SyncManager {

    private static final Logger logger = LogManager.getLogger(SyncManager.class);

    // Singleton
    private static volatile SyncManager instance;

    private final ObjectMapper json;
    private final NetworkMonitor networkMonitor;

    // Multicast socket
    private MulticastSocket receiveSocket;
    private InetAddress multicastGroup;
    private DatagramSocket sendSocket;

    // Event listeners per type
    private final Map<SyncEvent.Type, List<Consumer<SyncEvent>>> listeners = new ConcurrentHashMap<>();

    // Duplicate suppression: track recently seen event IDs
    private final Set<String> seenEventIds = Collections.newSetFromMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) {
                    return size() > 500;
                }
            });

    private ExecutorService receiveExecutor;
    private volatile boolean running = false;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private SyncManager() {
        json = new ObjectMapper();
        json.registerModule(new JavaTimeModule());
        json.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        networkMonitor = new NetworkMonitor();
        networkMonitor.setSyncManager(this);
    }

    public static SyncManager getInstance() {
        if (instance == null) {
            synchronized (SyncManager.class) {
                if (instance == null)
                    instance = new SyncManager();
            }
        }
        return instance;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts the sync manager: opens multicast socket and begins listening.
     * Also starts the NetworkMonitor for machine presence detection.
     * Safe to call multiple times.
     */
    public synchronized void start() {
        if (running)
            return;
        try {
            multicastGroup = InetAddress.getByName(NetworkMonitor.MULTICAST_GROUP);
            InetSocketAddress groupAddr = new InetSocketAddress(multicastGroup, NetworkMonitor.MULTICAST_PORT);
            NetworkInterface ni = networkMonitor.getSelectedNetworkInterface();

            // Receive socket
            receiveSocket = new MulticastSocket(NetworkMonitor.MULTICAST_PORT);
            receiveSocket.setReuseAddress(true);

            if (ni != null) {
                logger.info("Binding multicast to interface: {} ({})", ni.getName(), ni.getDisplayName());
                receiveSocket.setNetworkInterface(ni);
                receiveSocket.joinGroup(groupAddr, ni);
            } else {
                logger.warn("No specific network interface detected; joining default group.");
                receiveSocket.joinGroup(multicastGroup);
            }
            receiveSocket.setSoTimeout(2000); // 2s read timeout so we can check 'running'

            // Send socket
            sendSocket = new DatagramSocket();
            if (ni != null)
                sendSocket.setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);
            sendSocket.setBroadcast(true);

            // Start receive thread
            receiveExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sync-receiver");
                t.setDaemon(true);
                return t;
            });
            receiveExecutor.submit(this::receiveLoop);

            running = true;
            logger.info("SyncManager started on {}:{}", NetworkMonitor.MULTICAST_GROUP,
                    NetworkMonitor.MULTICAST_PORT);

            // Start network monitor
            networkMonitor.start();

        } catch (Exception e) {
            logger.error("SyncManager failed to start: {}", e.getMessage(), e);
            // Non-fatal: app works without sync (single-machine mode)
        }
    }

    /**
     * Gracefully stops listening and leaves the multicast group.
     */
    public synchronized void stop() {
        if (!running)
            return;
        running = false;

        networkMonitor.markSelfOffline();
        networkMonitor.stop();

        try {
            // Broadcast shutdown notification
            broadcast(new SyncEvent(SyncEvent.Type.SHUTDOWN, 0,
                    networkMonitor.getLocalMachineCode()));
        } catch (Exception ignored) {
        }

        try {
            if (receiveSocket != null)
                receiveSocket.close();
        } catch (Exception ignored) {
        }
        try {
            if (sendSocket != null)
                sendSocket.close();
        } catch (Exception ignored) {
        }
        if (receiveExecutor != null)
            receiveExecutor.shutdownNow();

        logger.info("SyncManager stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    // ── Broadcasting ──────────────────────────────────────────────────────────

    /**
     * Broadcasts a sync event to all machines on the multicast group.
     * Non-blocking — serialize and send on calling thread (fast UDP).
     */
    public void broadcast(SyncEvent event) {
        if (!running || sendSocket == null)
            return;
        try {
            byte[] data = json.writeValueAsBytes(event);
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, multicastGroup, NetworkMonitor.MULTICAST_PORT);
            sendSocket.send(packet);
            logger.debug("Broadcast: {}", event);
        } catch (Exception e) {
            logger.debug("Broadcast error: {}", e.getMessage());
        }
    }

    /**
     * Convenience: broadcast a STOCK_CHANGED event for a product.
     */
    public void notifyStockChanged(int productId) {
        broadcast(new SyncEvent(SyncEvent.Type.STOCK_CHANGED, productId,
                networkMonitor.getLocalMachineCode()));
    }

    /**
     * Convenience: broadcast a PRODUCT_UPDATED event.
     */
    public void notifyProductUpdated(int productId) {
        broadcast(new SyncEvent(SyncEvent.Type.PRODUCT_UPDATED, productId,
                networkMonitor.getLocalMachineCode()));
    }

    /**
     * Convenience: broadcast a BILL_FINALIZED event.
     */
    public void notifyBillFinalized(int billId) {
        broadcast(new SyncEvent(SyncEvent.Type.BILL_FINALIZED, billId,
                networkMonitor.getLocalMachineCode()));
    }

    /**
     * Convenience: broadcast a BILL_VOIDED event.
     */
    public void notifyBillVoided(int billId) {
        broadcast(new SyncEvent(SyncEvent.Type.BILL_VOIDED, billId,
                networkMonitor.getLocalMachineCode()));
    }

    /**
     * Convenience: broadcast SETTINGS_CHANGED.
     */
    public void notifySettingsChanged() {
        broadcast(new SyncEvent(SyncEvent.Type.SETTINGS_CHANGED, 0,
                networkMonitor.getLocalMachineCode()));
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    /**
     * Registers a listener for a specific event type.
     * Listener is called on the JavaFX application thread.
     *
     * @param type     Event type to listen for
     * @param listener Callback receiving the SyncEvent
     */
    public void addListener(SyncEvent.Type type, Consumer<SyncEvent> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void removeListener(SyncEvent.Type type, Consumer<SyncEvent> listener) {
        List<Consumer<SyncEvent>> list = listeners.get(type);
        if (list != null)
            list.remove(listener);
    }

    /**
     * Removes all listeners (call on screen navigation to avoid memory leaks).
     */
    public void clearListeners() {
        listeners.clear();
    }

    // ── Network Monitor ───────────────────────────────────────────────────────

    public NetworkMonitor getNetworkMonitor() {
        return networkMonitor;
    }

    // ── Private: Receive Loop ─────────────────────────────────────────────────

    private void receiveLoop() {
        byte[] buffer = new byte[8192];
        logger.debug("Sync receive loop started.");

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);

                String raw = new String(packet.getData(), 0, packet.getLength());
                SyncEvent event = json.readValue(raw, SyncEvent.class);

                // Ignore our own events
                if (networkMonitor.getLocalMachineCode() != null &&
                        networkMonitor.getLocalMachineCode().equals(event.getSourceMachine())) {
                    continue;
                }

                // Deduplicate
                if (event.getEventId() != null) {
                    synchronized (seenEventIds) {
                        if (seenEventIds.contains(event.getEventId()))
                            continue;
                        seenEventIds.add(event.getEventId());
                    }
                }

                logger.debug("Received sync event: {}", event);
                dispatchEvent(event);

            } catch (SocketTimeoutException e) {
                // Normal — allows while(running) check
            } catch (Exception e) {
                if (running)
                    logger.debug("Receive error: {}", e.getMessage());
            }
        }
        logger.debug("Sync receive loop ended.");
    }

    // ── Private: Dispatch ─────────────────────────────────────────────────────

    private void dispatchEvent(SyncEvent event) {
        // Handle PING for network monitor
        if (event.getType() == SyncEvent.Type.PING) {
            networkMonitor.onPingReceived(event);
        }

        // Apply default cache invalidation
        applyDefaultCacheAction(event);

        // Fire registered listeners on the FX thread
        List<Consumer<SyncEvent>> typeListeners = listeners.get(event.getType());
        if (typeListeners != null && !typeListeners.isEmpty()) {
            Platform.runLater(() -> {
                for (Consumer<SyncEvent> l : typeListeners) {
                    try {
                        l.accept(event);
                    } catch (Exception e) {
                        logger.warn("Listener error: {}", e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Applies automatic cache invalidation based on event type.
     * This runs without any registered listeners, ensuring caches
     * are always fresh even if no UI is currently showing.
     */
    private void applyDefaultCacheAction(SyncEvent event) {
        switch (event.getType()) {
            case PRODUCT_UPDATED, PRODUCT_CREATED, STOCK_CHANGED -> {
                // Invalidate product cache so next search/POS load gets fresh data
                try {
                    new ProductService().invalidateCache();
                    logger.debug("Product cache invalidated due to: {}", event.getType());
                } catch (Exception e) {
                    logger.debug("Cache invalidation error: {}", e.getMessage());
                }
            }
            case SETTINGS_CHANGED -> {
                try {
                    new SettingsService().invalidate();
                    logger.debug("Settings cache invalidated.");
                } catch (Exception e) {
                    logger.debug("Settings cache invalidation error: {}", e.getMessage());
                }
            }
            case SHUTDOWN -> {
                String code = event.getSourceMachine();
                if (code != null) {
                    networkMonitor.getAllMachines().stream()
                            .filter(m -> code.equals(m.getMachineCode()))
                            .findFirst()
                            .ifPresent(m -> m.setStatus(MachineInfo.Status.OFFLINE));
                    logger.info("Machine went offline gracefully: {}", code);
                }
            }
            default -> {
            }
        }
    }
}
