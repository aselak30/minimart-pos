package com.minimartpos.model;

import com.minimartpos.config.AppConfig;
import com.minimartpos.model.enums.Permission;
import com.minimartpos.model.enums.Role;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents a system user (admin or cashier).
 */
public class User {

    private int           id;
    private String        username;
    private String        passwordHash;
    private String        fullName;
    private Role          role;
    private String        email;
    private String        phone;
    private boolean       active;
    private int           failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int           sessionTimeoutMinutes;
    private java.math.BigDecimal cashLimit        = java.math.BigDecimal.ZERO; // 0 = no limit
    private java.math.BigDecimal dailySalesTarget = java.math.BigDecimal.ZERO;

    /** Configurable permissions for cashiers. Admins: always full access. */
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

    // ── Constructors ──────────────────────────────────────────────────────────

    public User() {
        this.active                = true;
        this.sessionTimeoutMinutes = AppConfig.SESSION_TIMEOUT_MIN;
        this.createdAt             = LocalDateTime.now();
    }

    public User(String username, String passwordHash, String fullName, Role role) {
        this();
        this.username     = username;
        this.passwordHash = passwordHash;
        this.fullName     = fullName;
        this.role         = role;
    }

    // ── Permission Helpers ────────────────────────────────────────────────────

    public boolean hasPermission(Permission p) {
        if (role == Role.ADMIN) return true;
        return permissions.contains(p);
    }

    public void grantPermission(Permission p) {
        permissions.add(p);
    }

    public void revokePermission(Permission p) {
        permissions.remove(p);
    }

    public void setPermissions(Set<Permission> perms) {
        this.permissions = EnumSet.copyOf(perms.isEmpty() ? EnumSet.noneOf(Permission.class) : perms);
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int    getId()               { return id; }
    public void   setId(int id)         { this.id = id; }

    public String getUsername()                    { return username; }
    public void   setUsername(String username)     { this.username = username; }

    public String getPasswordHash()                      { return passwordHash; }
    public void   setPasswordHash(String passwordHash)   { this.passwordHash = passwordHash; }

    public String getFullName()                    { return fullName; }
    public void   setFullName(String fullName)     { this.fullName = fullName; }

    public Role   getRole()             { return role; }
    public void   setRole(Role role)    { this.role = role; }

    public String getEmail()                { return email; }
    public void   setEmail(String email)    { this.email = email; }

    public String getPhone()                { return phone; }
    public void   setPhone(String phone)    { this.phone = phone; }

    public boolean isActive()               { return active; }
    public void    setActive(boolean active){ this.active = active; }

    public int  getFailedLoginAttempts()                         { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts)  { this.failedLoginAttempts = failedLoginAttempts; }

    public LocalDateTime getLockedUntil()                        { return lockedUntil; }
    public void          setLockedUntil(LocalDateTime lockedUntil){ this.lockedUntil = lockedUntil; }

    public LocalDateTime getLastLogin()                          { return lastLogin; }
    public void          setLastLogin(LocalDateTime lastLogin)   { this.lastLogin = lastLogin; }

    public LocalDateTime getCreatedAt()                          { return createdAt; }
    public void          setCreatedAt(LocalDateTime createdAt)   { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt()                          { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime updatedAt)   { this.updatedAt = updatedAt; }

    public int  getSessionTimeoutMinutes()                              { return sessionTimeoutMinutes; }
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes)     { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }

    public java.math.BigDecimal getCashLimit()                          { return cashLimit; }
    public void setCashLimit(java.math.BigDecimal cashLimit)            { this.cashLimit = cashLimit != null ? cashLimit : java.math.BigDecimal.ZERO; }
    public boolean hasCashLimit()                                       { return cashLimit != null && cashLimit.compareTo(java.math.BigDecimal.ZERO) > 0; }

    public java.math.BigDecimal getDailySalesTarget()                   { return dailySalesTarget; }
    public void setDailySalesTarget(java.math.BigDecimal t)             { this.dailySalesTarget = t != null ? t : java.math.BigDecimal.ZERO; }

    public Set<Permission> getPermissions()                             { return permissions; }

    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role=" + role + "}";
    }
}
