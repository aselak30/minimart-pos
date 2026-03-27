package com.minimartpos.network;

import java.time.LocalDateTime;

/**
 * Represents a workstation (cashier or admin machine) on the LAN.
 * Tracked by the NetworkMonitor via periodic PING events.
 */
public class MachineInfo {

    public enum Status { ONLINE, OFFLINE, UNKNOWN }

    private String        machineCode;     // unique identifier (from DB or config)
    private String        machineName;
    private String        ipAddress;
    private String        macAddress;
    private String        machineType;     // "CASHIER" or "ADMIN"
    private Status        status = Status.UNKNOWN;
    private LocalDateTime lastSeen;
    private String        currentUser;     // username currently logged in
    private int           activeBillCount; // how many draft bills open

    public MachineInfo() {}

    public MachineInfo(String machineCode, String ipAddress) {
        this.machineCode = machineCode;
        this.ipAddress   = ipAddress;
        this.lastSeen    = LocalDateTime.now();
        this.status      = Status.ONLINE;
    }

    public boolean isStale(int timeoutSeconds) {
        if (lastSeen == null) return true;
        return lastSeen.plusSeconds(timeoutSeconds).isBefore(LocalDateTime.now());
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String        getMachineCode()              { return machineCode; }
    public void          setMachineCode(String v)      { this.machineCode = v; }
    public String        getMachineName()              { return machineName; }
    public void          setMachineName(String v)      { this.machineName = v; }
    public String        getIpAddress()                { return ipAddress; }
    public void          setIpAddress(String v)        { this.ipAddress = v; }
    public String        getMacAddress()               { return macAddress; }
    public void          setMacAddress(String v)       { this.macAddress = v; }
    public String        getMachineType()              { return machineType; }
    public void          setMachineType(String v)      { this.machineType = v; }
    public Status        getStatus()                   { return status; }
    public void          setStatus(Status v)           { this.status = v; }
    public LocalDateTime getLastSeen()                 { return lastSeen; }
    public void          setLastSeen(LocalDateTime v)  { this.lastSeen = v; }
    public String        getCurrentUser()              { return currentUser; }
    public void          setCurrentUser(String v)      { this.currentUser = v; }
    public int           getActiveBillCount()          { return activeBillCount; }
    public void          setActiveBillCount(int v)     { this.activeBillCount = v; }
}
