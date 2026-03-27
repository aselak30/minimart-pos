package com.minimartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Customer {
    private int           id;
    private String        name;
    private String        phone;
    private String        email;
    private String        address;
    private BigDecimal    creditLimit   = BigDecimal.ZERO;
    private BigDecimal    creditBalance = BigDecimal.ZERO;
    private int           loyaltyPoints = 0;
    private boolean       active = true;
    private String        notes;
    private LocalDateTime createdAt;

    public int         getId()               { return id; }
    public void        setId(int id)         { this.id = id; }
    public String      getName()             { return name; }
    public void        setName(String n)     { this.name = n; }
    public String      getPhone()            { return phone; }
    public void        setPhone(String p)    { this.phone = p; }
    public String      getEmail()            { return email; }
    public void        setEmail(String e)    { this.email = e; }
    public String      getAddress()          { return address; }
    public void        setAddress(String a)  { this.address = a; }
    public BigDecimal  getCreditLimit()      { return creditLimit; }
    public void        setCreditLimit(BigDecimal v){ this.creditLimit = v; }
    public BigDecimal  getCreditBalance()    { return creditBalance; }
    public void        setCreditBalance(BigDecimal v){ this.creditBalance = v; }
    public int         getLoyaltyPoints()    { return loyaltyPoints; }
    public void        setLoyaltyPoints(int v){ this.loyaltyPoints = v; }
    public boolean     isActive()            { return active; }
    public void        setActive(boolean a)  { this.active = a; }
    public String      getNotes()            { return notes; }
    public void        setNotes(String n)    { this.notes = n; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
    public void        setCreatedAt(LocalDateTime t){ this.createdAt = t; }

    @Override public String toString() { return name + (phone != null ? " (" + phone + ")" : ""); }
}
