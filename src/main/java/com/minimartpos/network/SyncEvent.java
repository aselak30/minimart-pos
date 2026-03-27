package com.minimartpos.network;

import java.time.LocalDateTime;

/**
 * Represents a single synchronization event broadcast between machines.
 *
 * Events are serialized to JSON and sent via UDP multicast to all
 * workstations on the LAN. Each machine applies the event to its
 * in-memory caches and optionally refreshes UI components.
 *
 * Event types:
 *
 *   PRODUCT_UPDATED    — a product's price, stock, or details changed
 *   PRODUCT_CREATED    — a new product was added
 *   STOCK_CHANGED      — stock quantity changed (after sale, adjustment, return)
 *   BILL_FINALIZED     — a bill was completed (for live dashboard updates)
 *   BILL_VOIDED        — a bill was voided
 *   SETTINGS_CHANGED   — system settings updated by admin
 *   USER_UPDATED       — a user account was modified
 *   PING               — heartbeat to confirm machine is alive
 *   SHUTDOWN           — machine is going offline
 */
public class SyncEvent {

    public enum Type {
        PRODUCT_UPDATED,
        PRODUCT_CREATED,
        STOCK_CHANGED,
        BILL_FINALIZED,
        BILL_VOIDED,
        SETTINGS_CHANGED,
        USER_UPDATED,
        PING,
        SHUTDOWN
    }

    private String        eventId;       // UUID
    private Type          type;
    private int           entityId;      // product ID, bill ID, etc.
    private String        entityData;    // JSON payload (optional)
    private String        sourceMachine; // machine code that sent this
    private int           sourceUserId;
    private LocalDateTime timestamp;

    public SyncEvent() {}

    public SyncEvent(Type type, int entityId, String sourceMachine) {
        this.eventId       = java.util.UUID.randomUUID().toString();
        this.type          = type;
        this.entityId      = entityId;
        this.sourceMachine = sourceMachine;
        this.timestamp     = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String        getEventId()                   { return eventId; }
    public void          setEventId(String v)           { this.eventId = v; }
    public Type          getType()                      { return type; }
    public void          setType(Type v)                { this.type = v; }
    public int           getEntityId()                  { return entityId; }
    public void          setEntityId(int v)             { this.entityId = v; }
    public String        getEntityData()                { return entityData; }
    public void          setEntityData(String v)        { this.entityData = v; }
    public String        getSourceMachine()             { return sourceMachine; }
    public void          setSourceMachine(String v)     { this.sourceMachine = v; }
    public int           getSourceUserId()              { return sourceUserId; }
    public void          setSourceUserId(int v)         { this.sourceUserId = v; }
    public LocalDateTime getTimestamp()                 { return timestamp; }
    public void          setTimestamp(LocalDateTime v)  { this.timestamp = v; }

    @Override
    public String toString() {
        return "SyncEvent{type=" + type + ", entityId=" + entityId +
               ", from=" + sourceMachine + ", at=" + timestamp + "}";
    }
}
