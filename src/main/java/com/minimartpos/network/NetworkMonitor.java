package com.minimartpos.network;

import com.minimartpos.config.DatabaseConfig;
import com.minimartpos.security.SessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Monitors the health and presence of all workstations on the LAN.
 *
 * Strategy:
 *  - Every 30s this machine broadcasts a PING SyncEvent via UDP multicast
 *  - Machines that haven't been seen for > 90s are marked OFFLINE
 *  - Machine list is also read from the DB `machines` table on startup
 *  - Callbacks are fired when machine status changes (for Admin Dashboard)
 *
 * Also provides:
 *  - Local machine identification (IP, MAC, machine code)
 *  - DB last_seen update for each workstation
 */
public class NetworkMonitor {

    private static final Logger logger = LogManager.getLogger(NetworkMonitor.class);

    // Multicast group — all POS machines join this group
    public static final String  MULTICAST_GROUP = "239.255.1.1";
    public static final int     MULTICAST_PORT  = 45678;
    public static final int     PING_INTERVAL_S = 30;
    public static final int     OFFLINE_TIMEOUT_S = 90;

    private final Map<String, MachineInfo>  knownMachines   = new ConcurrentHashMap<>();
    private final List<Consumer<MachineInfo>> statusListeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService scheduler;
    private SyncManager               syncManager;  // set by SyncManager after it starts
    private volatile boolean          running = false;

    private String localMachineCode;
    private String localIpAddress;
    private String localMacAddress;
    private NetworkInterface selectedNetworkInterface;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts the network monitor.
     * Discovers local network info, loads known machines from DB,
     * and begins the periodic ping cycle.
     */
    public synchronized void start() {
        if (running) return;

        detectLocalNetworkInfo();
        loadMachinesFromDb();

        scheduler = Executors.newScheduledThreadPool(1,
            r -> { Thread t = new Thread(r, "network-monitor"); t.setDaemon(true); return t; });

        // Ping every 30 seconds
        scheduler.scheduleAtFixedRate(this::broadcastPing, 0, PING_INTERVAL_S, TimeUnit.SECONDS);

        // Check for stale machines every 15 seconds
        scheduler.scheduleAtFixedRate(this::sweepStale, 15, 15, TimeUnit.SECONDS);

        running = true;
        logger.info("NetworkMonitor started. Local machine: {} ({})", localMachineCode, localIpAddress);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        logger.info("NetworkMonitor stopped.");
    }

    // ── Machine Registry ──────────────────────────────────────────────────────

    /**
     * Called by SyncManager when a PING event is received from another machine.
     */
    public void onPingReceived(SyncEvent event) {
        String code = event.getSourceMachine();
        if (code == null || code.equals(localMachineCode)) return;

        MachineInfo machine = knownMachines.computeIfAbsent(code,
            k -> new MachineInfo(code, ""));

        MachineInfo.Status oldStatus = machine.getStatus();
        machine.setLastSeen(LocalDateTime.now());
        machine.setStatus(MachineInfo.Status.ONLINE);
        machine.setCurrentUser(event.getEntityData());

        // Fire status change callback
        if (oldStatus != MachineInfo.Status.ONLINE) {
            logger.info("Machine came ONLINE: {}", code);
            notifyListeners(machine);
            updateMachineLastSeen(code);
        }
    }

    /**
     * Called on shutdown to mark this machine offline in the DB.
     */
    public void markSelfOffline() {
        updateMachineStatus(localMachineCode, false);
    }

    /**
     * Returns all known machines including this one.
     */
    public List<MachineInfo> getAllMachines() {
        return new ArrayList<>(knownMachines.values());
    }

    /**
     * Returns only ONLINE machines.
     */
    public List<MachineInfo> getOnlineMachines() {
        List<MachineInfo> online = new ArrayList<>();
        for (MachineInfo m : knownMachines.values()) {
            if (m.getStatus() == MachineInfo.Status.ONLINE) online.add(m);
        }
        return online;
    }

    public int getOnlineCount() { return getOnlineMachines().size(); }

    /**
     * Adds a listener that fires whenever a machine status changes.
     */
    public void addStatusListener(Consumer<MachineInfo> listener) {
        statusListeners.add(listener);
    }

    // ── Local Machine Info ────────────────────────────────────────────────────

    public String getLocalMachineCode()  { return localMachineCode; }
    public String getLocalIpAddress()    { return localIpAddress; }
    public String getLocalMacAddress()   { return localMacAddress; }
    public NetworkInterface getSelectedNetworkInterface() { return selectedNetworkInterface; }
    public boolean isRunning()           { return running; }

    // ── Private: Ping ─────────────────────────────────────────────────────────

    private void broadcastPing() {
        if (!running) return;
        try {
            // Broadcast via SyncManager if available
            if (syncManager != null) {
                String currentUser = SessionManager.isLoggedIn()
                    ? SessionManager.getCurrentUser().getUsername() : null;
                SyncEvent ping = new SyncEvent(
                    SyncEvent.Type.PING, 0, localMachineCode);
                ping.setEntityData(currentUser);
                syncManager.broadcast(ping);
            }
        } catch (Exception e) {
            logger.debug("Ping broadcast error: {}", e.getMessage());
        }
    }

    // ── Private: Stale sweep ──────────────────────────────────────────────────

    private void sweepStale() {
        // First, check local multicast-based staleness
        for (MachineInfo m : knownMachines.values()) {
            if (m.getStatus() == MachineInfo.Status.ONLINE
                    && m.isStale(OFFLINE_TIMEOUT_S)) {
                m.setStatus(MachineInfo.Status.OFFLINE);
            }
        }
        
        // Second, refresh from DB to see if any machines are active but multicast is blocked
        loadMachinesFromDb();
        
        // Notify listeners after full refresh
        for (MachineInfo m : knownMachines.values()) {
            notifyListeners(m);
        }
    }

    // ── Private: Network detection ────────────────────────────────────────────

    private void detectLocalNetworkInfo() {
        try {
            // Find the primary non-loopback network interface
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        selectedNetworkInterface = ni;
                        localIpAddress = addr.getHostAddress();
                        byte[] mac = ni.getHardwareAddress();
                        if (mac != null) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < mac.length; i++) {
                                sb.append(String.format("%02X", mac[i]));
                                if (i < mac.length - 1) sb.append(":");
                            }
                            localMacAddress = sb.toString();
                        }
                        break;
                    }
                }
                if (localIpAddress != null) break;
            }
        } catch (SocketException e) {
            logger.warn("Could not detect network info: {}", e.getMessage());
            localIpAddress = "127.0.0.1";
        }

        // Generate machine code from MAC or IP
        if (localMacAddress != null) {
            localMachineCode = "POS-" + localMacAddress.replace(":", "").substring(6);
        } else if (localIpAddress != null) {
            localMachineCode = "POS-" + localIpAddress.replace(".", "-");
        } else {
            localMachineCode = "POS-" + System.currentTimeMillis() % 10000;
        }

        // Register or update this machine in DB
        registerSelfInDb();
    }

    // ── Private: DB operations ────────────────────────────────────────────────

    private void loadMachinesFromDb() {
        String sql = "SELECT machine_code, machine_name, ip_address, mac_address, " +
                     "machine_type, last_seen FROM machines WHERE active=1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("machine_code");
                MachineInfo m = new MachineInfo();
                m.setMachineCode(code);
                m.setMachineName(rs.getString("machine_name"));
                m.setIpAddress(rs.getString("ip_address"));
                m.setMacAddress(rs.getString("mac_address"));
                m.setMachineType(rs.getString("machine_type"));
                Timestamp ts = rs.getTimestamp("last_seen");
                if (ts != null) {
                    m.setLastSeen(ts.toLocalDateTime());
                    m.setStatus(m.isStale(OFFLINE_TIMEOUT_S)
                        ? MachineInfo.Status.OFFLINE
                        : MachineInfo.Status.ONLINE);
                } else {
                    m.setStatus(MachineInfo.Status.UNKNOWN);
                }
                knownMachines.put(code, m);
            }
            logger.debug("Loaded {} machines from DB", knownMachines.size());
        } catch (SQLException e) {
            logger.error("loadMachinesFromDb: {}", e.getMessage(), e);
        }
    }

    private void registerSelfInDb() {
        String sql =
            "INSERT INTO machines (machine_code, machine_name, ip_address, mac_address, last_seen) " +
            "VALUES (?, ?, ?, ?, NOW()) " +
            "ON DUPLICATE KEY UPDATE ip_address=?, mac_address=?, last_seen=NOW()";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String name = "POS-" + (localIpAddress != null
                ? localIpAddress.substring(localIpAddress.lastIndexOf('.') + 1) : "?");
            ps.setString(1, localMachineCode);
            ps.setString(2, name);
            ps.setString(3, localIpAddress);
            ps.setString(4, localMacAddress);
            ps.setString(5, localIpAddress);
            ps.setString(6, localMacAddress);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("registerSelfInDb: {}", e.getMessage());
        }
    }

    private void updateMachineLastSeen(String machineCode) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE machines SET last_seen=NOW() WHERE machine_code=?")) {
            ps.setString(1, machineCode);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.debug("updateMachineLastSeen: {}", e.getMessage());
        }
    }

    private void updateMachineStatus(String code, boolean online) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE machines SET last_seen=? WHERE machine_code=?")) {
            ps.setTimestamp(1, online ? Timestamp.valueOf(LocalDateTime.now()) : null);
            ps.setString(2, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.debug("updateMachineStatus: {}", e.getMessage());
        }
    }

    private void notifyListeners(MachineInfo machine) {
        for (Consumer<MachineInfo> l : statusListeners) {
            try { l.accept(machine); } catch (Exception ignored) {}
        }
    }

    // ── Package-private setter for SyncManager ────────────────────────────────

    void setSyncManager(SyncManager sm) { this.syncManager = sm; }
}
