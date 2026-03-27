package com.minimartpos.model;

import java.time.LocalDateTime;

/**
 * Represents a single entry in the audit_log table.
 */
public class AuditLog {
    private int           id;
    private int           userId;
    private String        username;
    private String        action;
    private String        entityType;
    private int           entityId;
    private String        oldValue;
    private String        newValue;
    private LocalDateTime createdAt;

    public int           getId()                            { return id; }
    public void          setId(int id)                      { this.id = id; }
    public int           getUserId()                        { return userId; }
    public void          setUserId(int v)                   { this.userId = v; }
    public String        getUsername()                      { return username; }
    public void          setUsername(String v)              { this.username = v; }
    public String        getAction()                        { return action; }
    public void          setAction(String v)                { this.action = v; }
    public String        getEntityType()                    { return entityType; }
    public void          setEntityType(String v)            { this.entityType = v; }
    public int           getEntityId()                      { return entityId; }
    public void          setEntityId(int v)                 { this.entityId = v; }
    public String        getOldValue()                      { return oldValue; }
    public void          setOldValue(String v)              { this.oldValue = v; }
    public String        getNewValue()                      { return newValue; }
    public void          setNewValue(String v)              { this.newValue = v; }
    public LocalDateTime getCreatedAt()                     { return createdAt; }
    public void          setCreatedAt(LocalDateTime v)      { this.createdAt = v; }
}
